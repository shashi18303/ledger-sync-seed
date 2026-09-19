package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.RawMessage;
import java.util.List;
import java.util.Optional;

public final class Parsers {
    private static final List<MessageParser> REGISTRY = List.of(
        new HdfcSmsParser(),
        new IciciSmsParser(),
        new EmailParser()
    );

    private Parsers() {}

    public static Optional<ParsedTxn> parse(RawMessage message) {
        for (MessageParser parser : REGISTRY) {
            if (parser.supports(message)) {
                Optional<ParsedTxn> parsed = parser.parse(message);
                if (parsed.isPresent()) {
                    return parsed;
                }
            }
        }
        return Optional.empty();
    }
}
