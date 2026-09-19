package in.simplifymoney.ledgersync.parse;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Amounts {
    private Amounts() {}

    /**
     * Normalizes string currency representations into exact two-decimal scale BigDecimals.
     * Handles comma groupings like "92,213.10" or "5".
     */
    public static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Amount cannot be null or empty");
        }
        String clean = raw.replaceAll("[^0-9.]", "");
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("No numeric digits found in amount: " + raw);
        }
        BigDecimal bd = new BigDecimal(clean);
        return bd.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static String format(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
