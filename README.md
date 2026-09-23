# test-failure-taxonomy

A test failed. Was it the product, the test, the data it used, or the environment it ran in?

This project is a taxonomy of test failure causes with rules for deciding between them, based on
the evidence a triaging engineer actually has: the error, stack trace, logs, request and
response, the test code, its history, and the contract. It was checked by asking two AI models,
`claude-opus-5` and `gpt-6-astra`, to classify 21 failures with and without the rules.

**Scope:** 21 constructed cases, drafted with AI help; two models, one run date. Read the results
as examples, not rates. See `FINDINGS.md`.

## Start here

| File                         | What it is                                                                             |
| ---------------------------- | -------------------------------------------------------------------------------------- |
| [`TAXONOMY.md`](TAXONOMY.md) | The classes and the rules. This is the part to reuse                                   |
| [`FINDINGS.md`](FINDINGS.md) | What the check showed: where the AI confused the test with the product, and why        |
| [`RESULTS.md`](RESULTS.md)   | The numbers, and every answer that differed from the expected one                      |
| [`cases/`](cases)            | 21 labelled failures, for testing a model or training people                           |
| [`prompts/`](prompts)        | The exact prompts, with the rules (`classify.md`) and without (`classify-no-rules.md`) |

## The five classes

| Class              | The wrong artifact is                                                                                               |
| ------------------ | ------------------------------------------------------------------------------------------------------------------- |
| PRODUCT_DEFECT     | The product                                                                                                         |
| TEST_CODE_DEFECT   | The test itself: its logic, the values it supplies, or state it carries between runs                                |
| TEST_DATA          | A record the test consumed but did not create during this run                                                       |
| ENVIRONMENT_CONFIG | The environment: infrastructure, configuration, a third-party dependency                                            |
| INSUFFICIENT_DATA  | Can't tell from the evidence. Name the two classes it sits between, and the one piece of evidence that would decide |

## Quick checklist

Ask in this order and stop at the first answer. The full rules and tie-breaks are in
`TAXONOMY.md`.

1. Did the environment stop the action, or serve a different build or configuration from the one
   intended? → **ENVIRONMENT_CONFIG**
2. Did the test do something other than it meant to, or something invalid? → **TEST_CODE_DEFECT**
3. Was a record the test consumed, one it did not create during this run, wrong or missing? →
   **TEST_DATA**
4. Otherwise, which is wrong: the product's answer, or the test's expectation? The product's →
   **PRODUCT_DEFECT**; the expectation → **TEST_CODE_DEFECT**
5. Can't answer one of these, and the answer would change the class? → **INSUFFICIENT_DATA**

Question 4 is the one that gets skipped. The test's own expected value is not proof that the
product is wrong. Something else has to say what "correct" is: a contract, or a build on which the
same test passed.

## Worked example

A test fails with `expected [100.00] but found [99.99]` on an invoice total. The response is a
200 with two line items, 50.005 and 49.995. (`cases/invoice-total-per-line-rounding`)

1. **Environment?** No. The request reached the billing service, and its logs show it handled
   the request normally.
2. **Did the test do something wrong?** No. It sent a valid GET and got a 200.
3. **Bad data?** No. The line items are what the invoice holds; the total is computed by the
   product.
4. **Product or expectation?** The contract says: *"total MUST equal the sum … computed at full
   precision and rounded HALF_UP … exactly once. Implementations MUST NOT round individual line
   items."* The logs show the product rounding each line first. The test passed on the previous
   build and hasn't changed since. → **PRODUCT_DEFECT**

Now take the same failure without the contract, on a new test that has never passed
(`cases/invoice-total-no-independent-statement`). At question 4, only the test says 100.00 is
right. That's not enough. → **INSUFFICIENT_DATA**, between PRODUCT_DEFECT and TEST_CODE_DEFECT;
the deciding evidence is the rounding contract.

This second case is where one model, without the rules, blamed the product. With the rules, both
models answered INSUFFICIENT_DATA. See `FINDINGS.md`.

## Reproduce

Needs Java 17 and Maven.

**See the results again** (free, no API keys; reads the saved answers):

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=io.github.yevhenbozhenko.testfailure.Scorer
```

**Ask the models again** (paid, needs your own `ANTHROPIC_API_KEY` and `OPENAI_API_KEY`). The
runner only asks for answers that are not already saved in `raw/` and `raw-no-rules/`, so move
those folders aside to start fresh. The exact model versions may no longer be available, so new
answers may differ from the saved ones.

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=io.github.yevhenbozhenko.testfailure.Runner
```

**Add a case:** copy an existing folder in `cases/` as a template and edit it. `evidence-full.json`
holds all the evidence, `evidence-thin.json` (optional) holds only the error message, and
`meta.json` holds the expected answer. The runner never reads `meta.json`, so the models never
see the label.

## License

- The taxonomy, findings, results, cases and prompts (`*.md`, `cases/`, `prompts/`):
  [CC BY 4.0](LICENSE-CC-BY-4.0). Reuse them freely, including commercially, with credit.
- The code (`src/`, `pom.xml`): [MIT](LICENSE).
