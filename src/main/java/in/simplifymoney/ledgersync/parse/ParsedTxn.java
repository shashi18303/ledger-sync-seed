package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;

public record ParsedTxn(
    String messageId,
    String accountLast4,
    ZonedDateTime occurredAt,
    Direction direction,
    BigDecimal amount,
    String merchant,
    String channel,
    Optional<BigDecimal> availableBalance
) {}
