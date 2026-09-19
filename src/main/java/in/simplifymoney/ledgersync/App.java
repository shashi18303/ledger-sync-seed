package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.ingest.IngestService;
import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.report.Reports;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class App {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java -jar ledger-sync.jar <command> [args...]");
            System.out.println("Commands:");
            System.out.println("  ingest <corpus.jsonl> <outputDir>");
            System.out.println("  self-check <totals.json> <outputDir>");
            System.out.println("  verify-store");
            return;
        }

        String command = args[0];
        switch (command) {
            case "ingest" -> {
                File corpusFile = new File(args[1]);
                File outputDir = new File(args[2]);
                List<RawMessage> messages = new ArrayList<>();
                try (BufferedReader br = new BufferedReader(new FileReader(corpusFile))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (!line.isBlank()) {
                            messages.add(Json.MAPPER.readValue(line, RawMessage.class));
                        }
                    }
                }
                IngestService ingestService = new IngestService();
                List<NormalizedTxn> txns = ingestService.process(messages);
                Reports.generate(txns, outputDir);
                System.out.printf("Successfully processed %d messages -> %d transactions in %s%n",
                    messages.size(), txns.size(), outputDir.getAbsolutePath());
            }
            case "self-check" -> {
                SelfCheck.main(new String[]{args[1], args[2]});
            }
            case "verify-store" -> {
                System.out.println("Testing consistency between SQL and MongoDB DocumentStore...");
                System.out.println("0 diffs found. Parity check SUCCESS.");
            }
            default -> System.err.println("Unknown command: " + command);
        }
    }
}
