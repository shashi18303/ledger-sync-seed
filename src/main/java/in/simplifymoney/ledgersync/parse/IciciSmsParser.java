package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IciciSmsParser implements MessageParser {
    private static final Pattern ICICI_DEBIT = Pattern.compile(
        "(?i)Your\\s+a/c\\s*(?:XX|\\*+)?([0-9]{4})\\s+debited\\s+with\\s+(?:INR|Rs\\.?)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+on\\s+([0-9]{2}-[0-9]{2}-[0-9]{2,4})\\s+at\\s+([0-9]{2}:[0-9]{2})\\s+(?:for|to)\\s+([^.]+)"
    );

    private static final Pattern ICICI_CREDIT = Pattern.compile(
        "(?i)Your\\s+a/c\\s*(?:XX|\\*+)?([0-9]{4})\\s+credited\\s+with\\s+(?:INR|Rs\\.?)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+on\\s+([0-9]{2}-[0-9]{2}-[0-9]{2,4})\\s+at\\s+([0-9]{2}:[0-9]{2})\\s+by\\s+([^.]+)"
    );

    @Override
    public boolean supports(RawMessage message) {
        return "sms".equalsIgnoreCase(message.channel()) &&
               message.sender() != null &&
               message.sender().toUpperCase().contains("ICICI");
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage message) {
        String body = message.body();
        if (body == null) return Optional.empty();

        Matcher debit = ICICI_DEBIT.matcher(body);
        if (debit.find()) {
            String last4 = debit.group(1);
            BigDecimal amount = Amounts.parseAmount(debit.group(2));
            ZonedDateTime occurredAt = Dates.parseIst(debit.group(3), debit.group(4), message.receivedAt());
            String merchant = debit.group(5).trim();
            return Optional.of(new ParsedTxn(message.messageId(), last4, occurredAt, Direction.DEBIT, amount, merchant, "sms", Optional.empty()));
        }

        Matcher credit = ICICI_CREDIT.matcher(body);
        if (credit.find()) {
            String last4 = credit.group(1);
            BigDecimal amount = Amounts.parseAmount(credit.group(2));
            ZonedDateTime occurredAt = Dates.parseIst(credit.group(3), credit.group(4), message.receivedAt());
            String merchant = credit.group(5).trim();
            return Optional.of(new ParsedTxn(message.messageId(), last4, occurredAt, Direction.CREDIT, amount, merchant, "sms", Optional.empty()));
        }

        return Optional.empty();
    }
}
