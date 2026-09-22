# Results

What the two models answered, checked against the expected labels. What it means is in
`FINDINGS.md`; this file is the numbers behind it. The numbers are the scorer's output, laid out
as tables.

## What produced these numbers

| Item          | Value                                                                            |
| ------------- | -------------------------------------------------------------------------------- |
| Models        | `claude-opus-5` and `gpt-6-astra` (each saved reply names its model)             |
| Temperature   | Not set; neither model accepts one                                               |
| Cases         | 21 cases in `cases/`, 27 evidence files (21 full, 6 with only the error message) |
| Cases are     | Constructed, not taken from real test runs                                       |
| Answers       | 324: 2 models × with and without the rules × 27 evidence files × 3 runs          |
| Answers saved | Commit `9117f38`, 2026-09-21, in `raw/` and `raw-no-rules/`                      |
| Scorer        | Commit `4376bfe`                                                                 |
| Scoring rule  | The last `{"class": ...}` in a reply is its answer                               |
| Labels        | Set before the run, none changed after it                                        |

## The replies

| Check                                              | Count                                                                                        |
| -------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Replies saved                                      | 324                                                                                          |
| Answered more than once, scored on the last answer | 3 (all Claude, no rules, thin evidence; a misspelled class name corrected to the same class) |
| Did not finish normally                            | 0                                                                                            |
| No readable answer                                 | 0                                                                                            |

## Summary

Both models together.

| Measure                                        | With rules | No rules   |
| ---------------------------------------------- | ---------- | ---------- |
| Look-alike pairs told apart                    | 15 of 16   | 12 of 16   |
| Look-alike pairs merged                        | 0          | 1          |
| The two models reach the same conclusion       | 26 of 27   | 23 of 27   |
| Matched the expected answer                    | 159 of 162 | 152 of 162 |
| Only the error message → "not enough evidence" | 36 of 36   | 36 of 36   |
| Same answer in all 3 runs                      | 54 of 54   | 52 of 54   |
| Matched what really broke (see note)           | 117 of 162 | 114 of 162 |

- **The two models reach the same conclusion:** each model's most common answer, compared case by
  case.
- **Matched the expected answer:** the answer the rules allow for that evidence.
- **Same answer in all 3 runs:** counted per case and model.
- **Note — "matched what really broke" is not an accuracy score.** 42 of the 162 answers should
  *not* match what broke: the 36 given only the error message, and the 6 for
  `invoice-total-no-independent-statement`, where the rules say the evidence cannot decide. So
  the most possible is 120, not 162. That case's seeded cause is PRODUCT_DEFECT, so this row also
  counts a wrong PRODUCT_DEFECT answer there as correct.

## Look-alike pairs

Two cases that look the same but have different causes.

- **told apart:** both cases answered right in at least 2 of 3 runs.
- **MERGED:** both cases got the same answer, so the model did not see the difference.
- **one half wrong:** different answers, one of them wrong.

The numbers show how many of the 3 runs were right, for the first case and then the second.

| Pair                                                                                 | With rules, claude-opus-5 | With rules, gpt-6-astra   | No rules, claude-opus-5   | No rules, gpt-6-astra     |
| ------------------------------------------------------------------------------------ | ------------------------- | ------------------------- | ------------------------- | ------------------------- |
| `auth-token-cached-in-static-field` vs `shared-account-left-cancelled`               | told apart                | told apart                | told apart                | told apart                |
| `feature-flag-off-by-design` vs `feature-flag-reverted-on-redeploy`                  | told apart                | told apart                | told apart                | told apart                |
| `field-absent-because-old-build-deployed` vs `status-enum-case-changed-deliberately` | told apart                | told apart                | told apart                | told apart                |
| `fixture-persisted-wrong-by-product` vs `setup-call-failed-unchecked`                | told apart                | told apart                | one half wrong (3/3, 1/3) | told apart                |
| `hardcoded-id-row-purged` vs `hardcoded-id-typo-never-existed`                       | told apart                | one half wrong (3/3, 0/3) | told apart                | one half wrong (3/3, 0/3) |
| `invoice-total-no-independent-statement` vs `invoice-total-per-line-rounding`        | told apart                | told apart                | **MERGED** (1/3, 3/3)     | told apart                |
| `service-client-secret-expired` vs `auth-token-cached-in-static-field`               | told apart                | told apart                | told apart                | told apart                |
| `timeout-product-still-computing` vs `timeout-request-never-arrived`                 | told apart                | told apart                | told apart                | one half wrong (0/3, 3/3) |

"Told apart" is 3/3 for both cases every time.

## Answers that differ from the expected one

All five are on the full evidence.

| #   | Rules      | Model         | Case                                     | Expected          | Run 1             | Run 2             | Run 3             |
| --- | ---------- | ------------- | ---------------------------------------- | ----------------- | ----------------- | ----------------- | ----------------- |
| 1   | no rules   | claude-opus-5 | `invoice-total-no-independent-statement` | INSUFFICIENT_DATA | INSUFFICIENT_DATA | PRODUCT_DEFECT    | PRODUCT_DEFECT    |
| 2   | no rules   | claude-opus-5 | `setup-call-failed-unchecked`            | TEST_CODE_DEFECT  | TEST_DATA         | TEST_CODE_DEFECT  | TEST_DATA         |
| 3   | no rules   | gpt-6-astra   | `hardcoded-id-typo-never-existed`        | TEST_CODE_DEFECT  | INSUFFICIENT_DATA | INSUFFICIENT_DATA | INSUFFICIENT_DATA |
| 4   | no rules   | gpt-6-astra   | `timeout-product-still-computing`        | PRODUCT_DEFECT    | INSUFFICIENT_DATA | INSUFFICIENT_DATA | INSUFFICIENT_DATA |
| 5   | with rules | gpt-6-astra   | `hardcoded-id-typo-never-existed`        | TEST_CODE_DEFECT  | INSUFFICIENT_DATA | INSUFFICIENT_DATA | INSUFFICIENT_DATA |

Rows 3 and 5 are one case, `hardcoded-id-typo-never-existed`, and its label is disputed. The
evidence does not show which ID the test was meant to use, which the rules need. The label was
left unchanged; see `FINDINGS.md`.

The model's own reasoning, from the first differing reply of each. The full replies are in
`raw/` and `raw-no-rules/`.

**1. No rules, claude-opus-5, `invoice-total-no-independent-statement`**

> The response body's own line items sum to exactly 100.000 (50.005 + 49.995), yet the service
> reports a total of 99.99, which is what you get only by truncating each line down to cents
> before summing rather than rounding correctly or summing at full precision. The expected value
> of 100.00 is therefore arithmetically right, so the assembler's rounding logic is the fault, not
> the assertion. The test being brand new with no prior green build explains why this latent
> rounding bug is only now surfacing, not that the test is wrong.

**2. No rules, claude-opus-5, `setup-call-failed-unchecked`**

> The setup POST returned 422 because the test's fixture payload omits the now-required
> `currency` field, so orderId was null and the assertion under test hit GET
> /api/v1/orders/null -> 404. The service change (commit 55d0e19 'feat: require currency on order
> creation') is intentional behavior, so the application is not wrong; the test's request data is
> simply stale. The missing status assertion in @BeforeMethod is a secondary hygiene flaw that
> only masked the real cause, which is the outdated payload.

**3. No rules, gpt-6-astra, `hardcoded-id-typo-never-existed`**

> The service returned CUSTOMER_NOT_FOUND for CUST-1881, and the audit search found no lifecycle
> events for that ID. The recent 'tidy test constants' commit suggests a possible incorrect test
> ID, but without its diff or evidence of the expected fixture, we cannot distinguish a test-code
> error from missing test data.

**4. No rules, gpt-6-astra, `timeout-product-still-computing`**

> The request reached the application, but its ledger aggregation took 107,418 ms, exceeding the
> client's 30,000 ms read timeout. This identifies the immediate cause, but the evidence does not
> establish whether the slowdown from the previous 1.2-second run resulted from an application
> defect, changed data, or database infrastructure/configuration.

**5. With rules, gpt-6-astra, `hardcoded-id-typo-never-existed`**

> The missing-record reference rule leaves this between TEST_DATA if CUST-1881 was supposed to be
> provided by a fixture or seed, and TEST_CODE_DEFECT if the test references an identifier that
> was never supposed to exist. The 404, absent audit events, and recent constants edit do not
> establish which identifier the test should use; the deciding evidence is the fixture or seed
> definition recording the intended customer identifier.

## To produce it again

Free, no API keys needed. From the project folder:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=io.github.yevhenbozhenko.testfailure.Scorer
```

It prints the same numbers as plain text.
