apackage in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailParser implements MessageParser {
    private static final Pattern EMAIL_TXN_PATTERN = Pattern.compile(
        "(?i)Account\\s*(?:ending\\s+in|XX|\\*+)\\s*([0-9]{4}).*?(?:spent|debited|charged)\\s+(?:INR|Rs\\.?)\\s*([0-9,]+(?:\\.[0-9]{1,2})?).*?at\\s+([^\\n<]+)"
    );

    @Override
    public boolean supports(RawMessage message) {
        return "email".equalsIgnoreCase(message.channel());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage message) {
        String body = message.body();
        if (body == null) return Optional.empty();

        Matcher m = EMAIL_TXN_PATTERN.matcher(body);
        if (m.find()) {
            String last4 = m.group(1);
            BigDecimal amount = Amounts.parseAmount(m.group(2));
            String merchant = m.group(3).trim();
            ZonedDateTime occurredAt = message.receivedAt().withZoneSameInstant(Dates.IST);
            return Optional.of(new ParsedTxn(message.messageId(), last4, occurredAt, Direction.DEBIT, amount, merchant, "email", Optional.empty()));
        }
        return Optional.empty();
    }
}
