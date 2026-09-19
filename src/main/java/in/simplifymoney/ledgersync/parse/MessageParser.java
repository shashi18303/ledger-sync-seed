package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.RawMessage;
import java.util.Optional;

public interface MessageParser {
    boolean supports(RawMessage message);
    Optional<ParsedTxn> parse(RawMessage message);
}
