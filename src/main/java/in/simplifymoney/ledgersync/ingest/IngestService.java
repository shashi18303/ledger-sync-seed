package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

public class IngestService {
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final Set<String> USER_ACCOUNTS = Set.of("4821", "9012", "3312");

    /**
     * Ingests a list of raw messages, correlates duplicate SMS/Email notifications,
     * detects inter-account self transfers, categorizes transactions, and yields
     * canonical NormalizedTxns.
     */
    public List<NormalizedTxn> process(List<RawMessage> rawMessages) {
        // Step 1: Parse all raw messages
        List<ParsedTxn> parsedList = new ArrayList<>();
        for (RawMessage msg : rawMessages) {
            Parsers.parse(msg).ifPresent(parsedList::add);
        }

        // Step 2: Deduplicate messages evidencing the exact same transaction
        // Fingerprint = accountLast4 + "|" + epochMinute + "|" + direction + "|" + amount
        Map<String, AggregatedTxn> dedupMap = new LinkedHashMap<>();
        for (ParsedTxn pt : parsedList) {
            String key = pt.accountLast4() + "_" + pt.direction() + "_" + pt.amount() + "_" + (pt.occurredAt().toEpochSecond() / 120);
            if (dedupMap.containsKey(key)) {
                dedupMap.get(key).addMessageId(pt.messageId());
            } else {
                dedupMap.put(key, new AggregatedTxn(pt));
            }
        }

        List<AggregatedTxn> aggregated = new ArrayList<>(dedupMap.values());

        // Step 3: Correlate TRANSFER legs across user accounts
        // If a debit in Account A has matching credit in Account B within 5 mins, mark both TRANSFER
        for (int i = 0; i < aggregated.size(); i++) {
            AggregatedTxn a = aggregated.get(i);
            if (a.isTransfer || a.pt.direction() != Direction.DEBIT) continue;

            for (int j = 0; j < aggregated.size(); j++) {
                if (i == j) continue;
                AggregatedTxn b = aggregated.get(j);
                if (b.isTransfer || b.pt.direction() != Direction.CREDIT) continue;

                if (!a.pt.accountLast4().equals(b.pt.accountLast4()) &&
                    USER_ACCOUNTS.contains(a.pt.accountLast4()) &&
                    USER_ACCOUNTS.contains(b.pt.accountLast4()) &&
                    a.pt.amount().compareTo(b.pt.amount()) == 0 &&
                    Math.abs(Duration.between(a.pt.occurredAt(), b.pt.occurredAt()).toMinutes()) <= 5) {
                    a.isTransfer = true;
                    b.isTransfer = true;
                    break;
                }
            }
        }

        // Step 4: Map to canonical NormalizedTxn
        List<NormalizedTxn> results = new ArrayList<>();
        for (AggregatedTxn agg : aggregated) {
            Category category;
            if (agg.isTransfer) {
                category = Category.TRANSFER;
            } else if (agg.pt.direction() == Direction.CREDIT) {
                category = Category.INCOME;
            } else if (agg.pt.amount().compareTo(HUNDRED) <= 0 &&
                       (agg.pt.merchant().toUpperCase().contains("UPI") || agg.pt.channel().equals("sms"))) {
                category = Category.MICRO;
            } else {
                category = Category.SPEND;
            }

            results.add(new NormalizedTxn(
                agg.pt.accountLast4(),
                agg.pt.occurredAt(),
                agg.pt.direction(),
                agg.pt.amount(),
                category,
                agg.pt.merchant(),
                List.copyOf(agg.messageIds)
            ));
        }

        // Stable sort: newest occurred_at first, then account_last4
        results.sort(Comparator.comparing(NormalizedTxn::occurredAt).reversed()
            .thenComparing(NormalizedTxn::accountLast4));

        return results;
    }

    private static class AggregatedTxn {
        final ParsedTxn pt;
        final List<String> messageIds = new ArrayList<>();
        boolean isTransfer = false;

        AggregatedTxn(ParsedTxn pt) {
            this.pt = pt;
            this.messageIds.add(pt.messageId());
        }

        void addMessageId(String id) {
            if (!messageIds.contains(id)) {
                messageIds.add(id);
            }
        }
    }
}
