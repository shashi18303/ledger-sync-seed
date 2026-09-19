package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-precision HDFC SMS transactional parser.
 * [RESOLVES INC-2026-09-11]
 * Prevents Avl Bal (Available Balance) suffixes from overriding the transaction debit amount.
 */
public class HdfcSmsParser implements MessageParser {

    // Regex explicitly anchors transaction amount to the debit/credit verb!
    // Group 1: Amount, Group 2: Account Last 4, Group 3: Date, Group 4: Time, Group 5: Merchant/Beneficiary
    private static final Pattern DEBIT_PATTERN = Pattern.compile(
        "(?i)(?:Rs\\.?|INR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+debited\\s+from\\s+a/c\\s*\\*+([0-9]{4})\\s+on\\s+([0-9]{2}-[0-9]{2}-[0-9]{2,4})\\s+at\\s+([0-9]{2}:[0-9]{2})\\s+to\\s+([^.]+?)(?:\\.\\s*Avl\\s+Bal|\\.|$)"
    );

    private static final Pattern CREDIT_PATTERN = Pattern.compile(
        "(?i)(?:Rs\\.?|INR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+credited\\s+to\\s+a/c\\s*\\*+([0-9]{4})\\s+on\\s+([0-9]{2}-[0-9]{2}-[0-9]{2,4})\\s+at\\s+([0-9]{2}:[0-9]{2})\\s+by\\s+([^.]+?)(?:\\.\\s*Avl\\s+Bal|\\.|$)"
    );

    private static final Pattern AVL_BAL_PATTERN = Pattern.compile(
        "(?i)Avl\\s+Bal:\\s*(?:Rs\\.?|INR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"
    );

    @Override
    public boolean supports(RawMessage message) {
        return "sms".equalsIgnoreCase(message.channel()) &&
               message.sender() != null &&
               message.sender().toUpperCase().contains("HDFC");
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage message) {
        String body = message.body();
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }

        // Check if message is a non-transaction marketing or OTP notification
        if (body.contains("pre-approved") || body.contains("OTP") || body.contains("reward points")) {
            return Optional.empty();
        }

        Matcher debitMatcher = DEBIT_PATTERN.matcher(body);
        if (debitMatcher.find()) {
            BigDecimal amount = Amounts.parseAmount(debitMatcher.group(1));
            String accountLast4 = debitMatcher.group(2);
            ZonedDateTime occurredAt = Dates.parseIst(debitMatcher.group(3), debitMatcher.group(4), message.receivedAt());
            String merchant = cleanMerchant(debitMatcher.group(5));

            Optional<BigDecimal> avlBal = extractBalance(body);

            return Optional.of(new ParsedTxn(
                message.messageId(),
                accountLast4,
                occurredAt,
                Direction.DEBIT,
                amount,
                merchant,
                "sms",
                avlBal
            ));
        }

        Matcher creditMatcher = CREDIT_PATTERN.matcher(body);
        if (creditMatcher.find()) {
            BigDecimal amount = Amounts.parseAmount(creditMatcher.group(1));
            String accountLast4 = creditMatcher.group(2);
            ZonedDateTime occurredAt = Dates.parseIst(creditMatcher.group(3), creditMatcher.group(4), message.receivedAt());
            String merchant = cleanMerchant(creditMatcher.group(5));

            Optional<BigDecimal> avlBal = extractBalance(body);

            return Optional.of(new ParsedTxn(
                message.messageId(),
                accountLast4,
                occurredAt,
                Direction.CREDIT,
                amount,
                merchant,
                "sms",
                avlBal
            ));
        }

        return Optional.empty();
    }

    private Optional<BigDecimal> extractBalance(String body) {
        Matcher m = AVL_BAL_PATTERN.matcher(body);
        if (m.find()) {
            try {
                return Optional.of(Amounts.parseAmount(m.group(1)));
            } catch (Exception ignored) {}
        }
        return Optional.empty();
    }

    private String cleanMerchant(String raw) {
        if (raw == null) return "UNKNOWN";
        return raw.trim().replaceAll("^to\\s+", "").replaceAll("^by\\s+", "");
    }
}
