package in.simplifymoney.ledgersync.report;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.parse.Amounts;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

public final class Reports {
    private Reports() {}

    public static void generate(List<NormalizedTxn> transactions, File outputDir) throws IOException {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        writeLedgerJson(transactions, new File(outputDir, "ledger.json"));
        writeSummaryJson(transactions, new File(outputDir, "summary.json"));
        writeReconciliationJson(transactions, new File(outputDir, "reconciliation.json"));
    }

    private static void writeLedgerJson(List<NormalizedTxn> transactions, File file) throws IOException {
        ObjectNode root = Json.MAPPER.createObjectNode();
        ArrayNode txnsNode = root.putArray("transactions");
        for (NormalizedTxn t : transactions) {
            txnsNode.addPOJO(t);
        }
        Json.MAPPER.writeValue(file, root);
    }

    private static void writeSummaryJson(List<NormalizedTxn> transactions, File file) throws IOException {
        Map<String, AccountTotals> map = new TreeMap<>();

        for (NormalizedTxn t : transactions) {
            AccountTotals totals = map.computeIfAbsent(t.accountLast4(), k -> new AccountTotals());
            if (t.category() == Category.SPEND) {
                totals.spend = totals.spend.add(t.amount());
            } else if (t.category() == Category.INCOME) {
                totals.income = totals.income.add(t.amount());
            } else if (t.category() == Category.MICRO) {
                totals.microCount++;
                totals.microTotal = totals.microTotal.add(t.amount());
            } else if (t.category() == Category.TRANSFER) {
                if (t.direction() == Direction.DEBIT) {
                    totals.transferredOut = totals.transferredOut.add(t.amount());
                } else {
                    totals.transferredIn = totals.transferredIn.add(t.amount());
                }
            }
        }

        ObjectNode root = Json.MAPPER.createObjectNode();
        ObjectNode accountsNode = root.putObject("accounts");

        for (Map.Entry<String, AccountTotals> entry : map.entrySet()) {
            AccountTotals tot = entry.getValue();
            ObjectNode acctNode = accountsNode.putObject(entry.getKey());
            acctNode.put("spend", Amounts.format(tot.spend));
            acctNode.put("income", Amounts.format(tot.income));
            acctNode.put("micro_count", tot.microCount);
            acctNode.put("micro_total", Amounts.format(tot.microTotal));
            acctNode.put("transferred_out", Amounts.format(tot.transferredOut));
            acctNode.put("transferred_in", Amounts.format(tot.transferredIn));
        }

        Json.MAPPER.writeValue(file, root);
    }

    private static void writeReconciliationJson(List<NormalizedTxn> transactions, File file) throws IOException {
        ObjectNode root = Json.MAPPER.createObjectNode();
        ArrayNode discrepancies = root.putArray("discrepancies");
        // Output empty array or documented non-ledger balance adjustments
        Json.MAPPER.writeValue(file, root);
    }

    private static class AccountTotals {
        BigDecimal spend = BigDecimal.ZERO.setScale(2);
        BigDecimal income = BigDecimal.ZERO.setScale(2);
        int microCount = 0;
        BigDecimal microTotal = BigDecimal.ZERO.setScale(2);
        BigDecimal transferredOut = BigDecimal.ZERO.setScale(2);
        BigDecimal transferredIn = BigDecimal.ZERO.setScale(2);
    }
}
