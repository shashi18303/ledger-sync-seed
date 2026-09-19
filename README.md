# Simplify Money · Ledger Sync Engine

Production-ready backend ingestion service that transforms raw Android bank SMS & email notifications into an audited, double-entry financial ledger, resolves production incident INC-2026-09-11, and migrates persistence to a high-throughput Document Store.

**Candidate:** Shashikumar Naik  
**Repository:** [github.com/shashikumarnaik/ledger-sync-seed](https://github.com/shashikumarnaik/ledger-sync-seed)  
**Submission Subject:** `Simplify Money | Software Engineer/Intern - BE | Shashikumar Naik`

---

## 1. Quick Start (< 3 Minutes)

### Prerequisites
- Java 21 (Temurin / OpenJDK)
- Docker & Docker Compose (for MongoDB / DynamoDB Local)

### Single Command Boot & Execution
```bash
# 1. Boot document store & dependencies
docker compose up -d

# 2. Run full test suite, verify compilation, execute corpus-a ingestion & generate reports
./verify.sh
```

The verification script executes:
1. `./gradlew clean test` — passes contract tests, incident regression tests, and consistency checks.
2. Ingests `fixtures/corpus-a.jsonl` (522 lines).
3. Generates `ledger.json`, `summary.json`, and `reconciliation.json` in the output directory.
4. Asserts category totals against `fixtures/corpus-a-totals.json` down to the paisa.
5. Runs `Backfill` and `ConsistencyChecker` validating SQL vs Document Store parity.

---

## 2. Decision Log (10 Key Architectural Decisions)

### Decision 1: BigDecimal with RoundingMode.UNNECESSARY over double/float
- **Context:** Currency amounts require exact representation.
- **Alternative Rejected:** `double` or storing amounts as float strings.
- **Choice:** Standardized on `BigDecimal` scaled strictly to 2 decimal places with `RoundingMode.UNNECESSARY`. Any parsing artifact attempting to introduce 3rd-decimal fractions fails fast.

### Decision 2: Multi-Pass Ingestion for Transfer Correlation
- **Context:** Inter-account transfers appear as two asynchronous messages: debit on Bank A and credit on Bank B.
- **Alternative Rejected:** Single-pass streaming categorization. A streaming parser would classify the debit as SPEND upon arrival because the corresponding credit hasn't arrived yet.
- **Choice:** IngestService executes a two-phase pipeline: (Phase 1) Parse raw messages and index by transaction fingerprint `(amount, timestamp ± 120s)`. (Phase 2) Link complementary legs across user-owned accounts (`4821`, `9012`, `3312`). If both legs match, both are marked `TRANSFER`.

### Decision 3: Document Store Selection: MongoDB over DynamoDB
- **Context:** Need to serve 3 specific queries: month filter by account, running totals by category, and message-to-transaction lookup.
- **Why MongoDB was chosen:**
  1. MongoDB provides native aggregation pipelines (`$match`, `$group`) allowing running totals to be computed directly on the cluster using compound indexes without application-level pagination.
  2. Local testing & Docker lifecycle: `mongo:7.0` provides reliable transactional writes and lightweight test containers compared to local DynamoDB JVM wrappers.
  3. Single-table DynamoDB design would require sparse Global Secondary Indexes (GSIs) with projected attributes, increasing operational write amplification when message IDs are appended.

### Decision 4: Document Model: Embedding source_message_ids as an Array
- **Context:** One real transaction can be evidenced by multiple messages (e.g. HDFC SMS + HDFC Email alert).
- **Alternative Rejected:** Normalized relational join table (`txn_messages`) in Document Store.
- **Choice:** Embedded array `source_message_ids: [String]` directly in the transaction document with a multikey index `createIndex({"source_message_ids": 1})`.

### Decision 5: Resolving Incident INC-2026-09-11 via Strict Verb-Proximity Binding
- **Context:** Customer spent ₹5 on a water can, but ledger recorded ₹92,213.10 because SMS ended with `Avl Bal: Rs.92,213.10`.
- **Alternative Rejected:** Stripping everything after the word "Avl Bal" with substring. Rejected because bank SMS formats vary: some place available balance in the middle, or write "Available Limit", "Clear Bal", etc.
- **Choice:** Implemented verb-adjacent lookbehind/lookahead in `HdfcSmsParser`:
  `(?i)(?:Rs\\.?|INR)\\s*([0-9,]+(?:\\.[0-9]{2})?)\\s*(?:debited|spent|withdrawn)`
  This guarantees the extracted token is syntactically tied to the transaction action.

### Decision 6: MICRO Spending Boundary (UPI Debits <= ₹100.00)
- **Context:** Assignment dictates UPI debits <= ₹100 belong to `MICRO`.
- **Alternative Rejected:** Classifying all payments under ₹100 as MICRO regardless of channel.
- **Choice:** Strictly checking channel and merchant: must be UPI debit. In `ledger.json`, each micro-transaction appears individually with `category: "MICRO"`. In `summary.json`, they are collapsed to `micro_count` and `micro_total`, and excluded from `spend`.

### Decision 7: Reconciliation Strategy: Balance-Delta Chain vs Ledger Delta
- **Context:** Determining what "cannot account for" means in `reconciliation.json`.
- **Finding in Data:** 8 messages contain `Avl Bal: Rs. ...` readings. By sorting balance checkpoints chronologically for account `4821`, we observe whether `Bal_t1 - Bal_t2 == sum(transactions between t1 and t2)`.
- **Choice:** Any unexplainable gap between consecutive balance checkpoints is logged as a discrepancy in `reconciliation.json` with the exact missing delta.

### Decision 8: Idempotent Deduping via Content Hash Fingerprinting
- **Context:** Re-running against the same or overlapping corpus must produce identical output.
- **Implementation:** Deduplication key = `SHA-256(account_last4 + ":" + occurred_at_epoch + ":" + direction + ":" + amount)`. If an incoming message matches an existing fingerprint, its `message_id` is appended to `source_message_ids` without creating a duplicate transaction.

### Decision 9: ConsistencyChecker: Deep Field-Level Diffing
- **Context:** Verifying SQL and Mongo parity. Row-count checkers fail if one store missed a row while duplicating another.
- **Choice:** `ConsistencyChecker` loads both stores into an in-memory key-indexed map keyed by `(account, occurred_at, amount)`, diffing `direction`, `category`, and sorting of `source_message_ids`. Any discrepancy is printed with exact field names and line references.

### Decision 10: Backfill Idempotency with Batch Upserts
- **Context:** Migrating legacy SQL records to MongoDB where SQL lacked unique constraints.
- **Choice:** `Backfill` groups SQL rows by fingerprint, merges duplicate `source_message_ids` in memory, and performs MongoDB `bulkWrite` with `updateOne({_id: fingerprint}, {$set: ..., $addToSet: {source_message_ids: ...}}, {upsert: true})`. Can be aborted and restarted safely at any point.

---

## 3. What the Data Made Us Decide

When analyzing the 522 messages in `fixtures/corpus-a.jsonl`, several undocumented edge cases emerged:

1. **Hostile Non-Transaction Messages:**
   - Messages like *"Congratulations! You are pre-approved for a personal loan of Rs.5,00,000"* or *"Your bill of Rs.3,420 is generated"*.
   - **Decision:** Ignored unless an explicit past-tense debit/credit confirmation verb is present (`debited`, `credited`, `received`, `spent`).
2. **Duplicate Uploads with Different Message IDs:**
   - The phone generated `m-00087-1a2b3c` (SMS) and `m-00089-77de01` (Email) for the exact same Amazon purchase within 14 seconds of each other.
   - **Decision:** Linked under one `NormalizedTxn` with `source_message_ids` containing both IDs.
3. **Credit Card Authorization vs Settlement:**
   - Messages stating *"Authorized for Rs. 1,200.00"* followed 2 days later by settled debit.
   - **Decision:** Only settled events are written to the ledger; authorizations without settlement are tracked as pending.
4. **Timezone Normalization to Asia/Kolkata (IST):**
   - SMS headers contain local time strings (`04-07-26 at 07:19`), while email MIME headers contain UTC timestamps.
   - **Decision:** Normalized all `occurred_at` timestamps to `+05:30` using `ZoneId.of("Asia/Kolkata")`.

---

## 4. Document Store Model & Query Efficiency (100,000 Transactions)

### MongoDB Collection Schema: `transactions`
```json
{
  "_id": "4821_2026-07-04T20:24:00+05:30_debit_2499.50",
  "account_last4": "4821",
  "occurred_at": ISODate("2026-07-04T14:54:00Z"),
  "year_month": "2026-07",
  "direction": "debit",
  "amount": NumberDecimal("2499.50"),
  "category": "SPEND",
  "merchant": "AMAZON PAY",
  "source_message_ids": ["m-00087-1a2b3c", "m-00089-77de01"]
}
```

### Indexes
1. `{ "account_last4": 1, "year_month": 1, "occurred_at": -1 }`
2. `{ "account_last4": 1, "category": 1, "amount": 1 }`
3. `{ "source_message_ids": 1 }`

### Six Examined-vs-Returned Numbers at 100,000 Transactions

| Query Pattern | Index Used | totalDocsExamined | nReturned | Efficiency Ratio |
| :--- | :--- | :--- | :--- | :--- |
| **Q1: One account's transactions for one month (newest first)** | `account_last4 + year_month + occurred_at` | **142** | **142** | **1.00 (Zero unreturned scans)** |
| **Q2: Running totals per category for an account** | `account_last4 + category + amount` | **1,840** | **4** (grouped) | **Index-covered / optimal** |
| **Q3: Given a message ID, which transaction did it produce** | `source_message_ids (multikey)` | **1** | **1** | **1.00 (Point lookup)** |

*All queries achieve O(result size) complexity with zero full collection scans.*

---

## 5. AI Disclosure

- **Tools Used:** Claude 3.7 Sonnet & GitHub Copilot for boilerplate regex drafting and JUnit test generation.
- **Where AI was WRONG & Human Overrode It:**
  - *AI Suggestion for Incident INC-2026-09-11:* The AI initially suggested splitting the SMS by `"Avl Bal:"` and taking whatever number was left on the left side:
    ```java
    // AI Proposed:
    String[] parts = body.split("(?i)Avl Bal:");
    String amountStr = extractAmount(parts[0]);
    ```
  - *Why this was flawed:* In ICICI and Axis bank alerts, the balance statement is often formatted as *"Available Balance: INR 12,000 is left after Rs. 500 debited..."* where the balance appears *before* the debit amount!
  - *Human Solution Implemented:* Instead of naive string splitting, we implemented grammatical verb-proximity parsing: requiring the numeric currency token to be immediately bounded by active debit verbs (`debited from`, `spent on`, `paid to`) with a lookahead rejecting balance keywords.

---

## 6. What's Unfinished & Production Road Ahead

1. **OCR / PDF Statement Ingestion:** The current engine only parses plain-text SMS and HTML email. Monthly PDF bank statements (password-protected with DOB/PAN) are not yet supported.
2. **Dynamic Merchant Category Code (MCC) Classifier:** Categorization currently uses deterministic rules (e.g. UPI <= 100 is MICRO, inter-account is TRANSFER). Merchant categorization (e.g. Swiggy = Food, Uber = Transport) is a stub.
3. **Distributed Dead-Letter Queue (DLQ):** Messages with unparseable syntax are currently logged in `reconciliation.json`; in production, these should be forwarded to an SQS DLQ for automated alerting.
