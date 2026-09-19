package in.simplifymoney.ledgersync.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Immutable canonical transaction representation.
 * [FROZEN CONTRACT - DO NOT MODIFY]
 */
public record NormalizedTxn(
    @JsonProperty("account_last4") String accountLast4,
    @JsonProperty("occurred_at") ZonedDateTime occurredAt,
    @JsonProperty("direction") Direction direction,
    @JsonProperty("amount") BigDecimal amount,
    @JsonProperty("category") Category category,
    @JsonProperty("merchant") String merchant,
    @JsonProperty("source_message_ids") List<String> sourceMessageIds
) {
    public NormalizedTxn {
        Objects.requireNonNull(accountLast4, "accountLast4 must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(sourceMessageIds, "sourceMessageIds must not be null");
        if (amount.scale() != 2) {
            throw new IllegalArgumentException("Amount must have scale 2: " + amount);
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount must be positive; direction carries sign: " + amount);
        }
    }
}
