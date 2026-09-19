package in.simplifymoney.ledgersync;

import com.fasterxml.jackson.databind.JsonNode;
import in.simplifymoney.ledgersync.json.Json;
import java.io.File;

public class SelfCheck {
    public static void main(String[] args) throws Exception {
        File expectedFile = new File(args[0]);
        File actualFile = new File(args[1], "summary.json");

        JsonNode expected = Json.MAPPER.readTree(expectedFile).get("accounts");
        JsonNode actual = Json.MAPPER.readTree(actualFile).get("accounts");

        expected.fieldNames().forEachRemaining(account -> {
            JsonNode expAcct = expected.get(account);
            JsonNode actAcct = actual.get(account);

            if (actAcct == null) {
                throw new AssertionError("Account missing from actual output: " + account);
            }

            assertField(account, "spend", expAcct, actAcct);
            assertField(account, "income", expAcct, actAcct);
            assertField(account, "micro_total", expAcct, actAcct);
            assertField(account, "transferred_out", expAcct, actAcct);
            assertField(account, "transferred_in", expAcct, actAcct);
        });

        System.out.println("SelfCheck: ALL TOTALS MATCH TO THE PAISA [100% PASS]");
    }

    private static void assertField(String account, String field, JsonNode exp, JsonNode act) {
        if (exp.has(field)) {
            String expVal = exp.get(field).asText();
            String actVal = act.get(field).asText();
            if (!expVal.equals(actVal)) {
                throw new AssertionError(String.format("Mismatch for %s.%s: expected %s, got %s", account, field, expVal, actVal));
            }
        }
    }
}
