# Findings

**A failing test tells you that something broke, not what.** The product, the test, the data it
used, or the environment it ran in: each answer sends the work to a different place. A wrong
answer wastes engineers' time chasing a product bug that was a test bug, or lets a real product
bug be dismissed as "just the test". The question is increasingly handed to AI, usually without
explicit rules.

This project gives that question a rulebook, based on evidence, and tests it against two AI
models from two different vendors, `claude-opus-5` and `gpt-6-astra`.

## The value in brief

- **Ready to use today.** A taxonomy and decision rules any team can adopt for triage: by people,
  or pasted as-is into an AI assistant (`prompts/classify.md`). (`TAXONOMY.md`)
- **Clear enough to apply the same way.** With the rules, two models from two vendors applied
  them the same way: they matched the expected labels on 123 of 126 answers on full
  evidence, gave the same answer on every rerun (54 of 54), and reached the same conclusion as
  each other on 26 of 27 evidence files.
- **Reads the evidence, not the surface.** Look-alike failures, with the same symptoms but
  different causes, were told apart in 15 of 16 checks with the rules, and never merged.
- **"Not enough evidence" becomes a to-do list.** With the rules, every time a model answered
  "not enough evidence" on full evidence (21 of 21 answers, both models, listed in
  `RESULTS.md`), it named the exact evidence that would decide: "the billing rounding contract",
  "the server-side log for that request", "the fixture or seed definition". That is the
  taxonomy's bar for this class at work.
- **Each rule does real work.** Without the rules, the models misread three cases, including one
  real confusion of the test with the product. Each was put right by one specific, named rule.
- **It finds its own weak spots.** The one case the rules did not settle exposed a rule that needs
  a clearer evidence requirement. The method shows exactly where the taxonomy should improve next.

**Scope.** 21 cases, 27 evidence files, two models, three runs each, one run date. The cases are
constructed, not captured from real failing builds: each was built to test one rule and to carry
exactly the evidence that rule needs, and each describes the change that would cause the failure
(`howSeeded` in its `meta.json`). It is written as if the change had been made, but it is a
recipe: nothing was run. The evidence is therefore cleaner than real failures, so these results
are likely a best case. The cases and their labels were drafted with AI help, partly by
`claude-opus-5`, one of the two tested models. The numbers are in `RESULTS.md`; the limits
are set out at the end.

## What this project contributes

1. **A taxonomy built on one question: which artifact is wrong?** The product, the test's own
   code, the data the test consumed, or the environment it ran in. Each of the four classes is
   one answer to that question, and INSUFFICIENT_DATA is what you answer when the evidence
   cannot decide it. One question instead of a list of symptoms is what makes the classes
   distinct and the taxonomy usable by someone who did not write it. (`TAXONOMY.md`)
2. **A decision framework based on evidence.** For each kind of evidence — error, stack trace,
   logs, request and response, the test code, its history, the contract — what it can and
   cannot prove. For each class, when to choose it and when not to. Five tie-breaks settle the
   situations that are easily confused, such as a timeout or a feature flag that is off.
   (`TAXONOMY.md`)
3. **"Insufficient data" as a real answer, with a bar to meet.** Name the two classes the case
   sits between, and the one piece of evidence that would decide it. If that piece cannot be
   named, the answer is not INSUFFICIENT_DATA. This turns "we don't know" into a to-do list.
4. **A method for testing any triage rules, and the material to do it with.** Look-alike pairs
   (two failures that look the same but have different causes), the same case with full
   evidence and with only the error message, and asking with and without the rules. The 21
   labelled cases and the exact prompts are included. (`cases/`, `prompts/`)
5. **Evidence that the rules act where they were meant to.** Each of the three cases the models
   misread without the rules — one test/product confusion, one test-side mix-up, one case left
   undecided — was put right by one specific rule. The rest of this file shows that.

## Who can use it, and how

| Who                                   | How                                                                                                |
| ------------------------------------- | -------------------------------------------------------------------------------------------------- |
| Test engineers and QA leads           | Triage a failure with the five questions in `TAXONOMY.md`, and collect the evidence each one needs |
| Teams building AI triage              | Use `prompts/classify.md` as the prompt; check a model against `cases/` before trusting it         |
| Anyone writing their own triage rules | Use look-alike pairs, and asking with and without the rules, to see whether the rules do real work |
| Trainers                              | 21 labelled cases, each with the deciding evidence named in its `meta.json`                        |

## What is new compared with common practice

Teams commonly sort failing tests by symptom — flaky, timeout, assertion error — or route them by
which team owns the code. A symptom says what happened, not what is wrong: the same timeout can
be the product, the environment or the test. Routing by owner decides who looks first, not what
is broken. This taxonomy sorts by which artifact is wrong, ties each answer to the evidence that
can prove it, and says when the honest answer is "not enough evidence" and what would settle it.

## The answer to the question

*Where does the AI confuse the test with the product, and why?*

- **Where:** once in this set. Without the rules, `claude-opus-5` took a new test's expected
  value as proof that the product was wrong. It happened in one specific gap: nothing but the
  test itself said what "correct" was.
- **Why:** with a gap in the evidence, the model filled it with the most plausible story, and
  the test's own assertion was the most available material for that story. It treated what the
  test *says* as a fact about the product.
- **Not systematic:** the confusion repeated across runs (2 of 3), so it is not a one-off. But
  the other look-alike pair on the test/product line — `fixture-persisted-wrong-by-product` vs
  `setup-call-failed-unchecked` — did not produce it: no model blamed the product for the test's
  fault or the other way round. In this set, the confusion depends on the gap, not on the model
  confusing test and product in general.
- **Specific to the model, not to AI in general:** `gpt-6-astra` did not make this confusion; it
  declined to decide the case even without the rules. Its one difference without the rules went
  the other way: it left undecided a case the rules do decide. One model over-committed, the
  other held back.
- **The rules closed the gap for both:** with the rules, both models gave the expected answer on
  every case but one, and that one case's label is itself disputed.

## Lessons for practitioners

- **A failing new test proves nothing about the product on its own.** Before calling a product
  defect, find something other than the test that says what correct is: a contract, a
  specification, or a build on which the same test passed.
- **A setup step that failed without being checked is the test's fault,** not the data's, even
  though what fails later looks like missing data.
- **Give an AI triage assistant explicit rules.** Without them, the two models behaved
  differently — one guessed, one held back. With them, both reached the same conclusion on 26 of
  27 evidence files. `prompts/classify.md` is the prompt that did it.
- **Collect the evidence that decides.** Server logs for timeouts, a contract or test history for
  wrong values, fixture definitions for missing records. In the cases below, the models asked
  for exactly these: a rounding contract, and the fixture that records which ID was meant.
- **Test your triage process with look-alike pairs.** A process, human or AI, that gives both
  halves the same answer is reading the surface, not the evidence.

## Where the models differed from the labels

### 1. Test vs product: the test's expected value taken as the truth

`invoice-total-no-independent-statement`. The product returns an invoice total of 99.99; the
test expects 100.00. The test is new and has never passed, and nothing else in the evidence says
how the total should be rounded.

Its look-alike, `invoice-total-per-line-rounding`, has the same failure, but also includes the
billing contract ("rounded HALF_UP … exactly once. Implementations MUST NOT round individual line
items before summation") and a history of the test passing.

Without the rules, `claude-opus-5` answered PRODUCT_DEFECT to both cases, in 2 of 3 runs for the
first. Its reasoning on the case *without* the contract:

> The expected value of 100.00 is therefore arithmetically right, so the assembler's rounding
> logic is the fault, not the assertion. The test being brand new with no prior green build
> explains why this latent rounding bug is only now surfacing, not that the test is wrong.

With the rules, it answered INSUFFICIENT_DATA in all 3 runs:

> the only statement that 100.00 is correct is the test's own assertion. There is no contract,
> specification or release note on how line-item amounts (50.005 + 49.995) should be rounded …
> the deciding evidence would be the billing rounding contract or spec for invoice totals.

**Why it happened.** The model's arithmetic was fine, and its answer even matches what was really
broken. The mistake is how it got there: with no contract in the evidence, it let the test's own
assertion stand in for the contract. A new test that has never passed is exactly the one whose
expectation is least proven, and the model turned that around ("explains why this latent
rounding bug is only now surfacing"). This is the rule `TAXONOMY.md` calls "the one that gets
skipped": a product is not wrong until something *other than the test* says what right is.

This was the only look-alike pair merged in either condition. `gpt-6-astra` declined to decide
this case even without the rules.

### 2. Test code vs test data: the test's own request called "data"

Not a test/product confusion — both answers are on the test's side — but the same kind of
mistake: something the test made was taken for something it was given.

`setup-call-failed-unchecked`. The test's setup step creates an order; the product rejects it
(422, a newly required field is missing); the test does not check, carries on with an empty ID,
and fails on a 404.

Without the rules, `claude-opus-5` answered TEST_DATA in 2 of 3 runs:

> the test's request data is simply stale. The missing status assertion in @BeforeMethod is a
> secondary hygiene flaw that only masked the real cause, which is the outdated payload.

With the rules, TEST_CODE_DEFECT in all 3 runs, citing the fixture tie-break: a setup call that
failed and was not checked is the test's fault, and TEST_DATA is kept for records the test did
not create during the run.

**Why it happened.** Without a definition, "test data" means anything that looks like data,
including a request body the test itself builds. The rules draw the line by role, not by
appearance: what the test consumes from outside is data; what the test creates or asserts is
code.

### 3. Not a confusion: caution where the rules decide by choice

`timeout-product-still-computing`. The client times out after 30 seconds; the server logs show
the request arrived and was still running a query that took 107 seconds, where the previous
build took 1.2.

Without the rules, `gpt-6-astra` answered INSUFFICIENT_DATA in all 3 runs:

> the evidence does not establish whether the slowdown from the previous 1.2-second run resulted
> from an application defect, changed data, or database infrastructure/configuration.

With the rules, PRODUCT_DEFECT in all 3 runs, citing the timeout tie-break.

**Why it happened.** The model's doubt is reasonable: a slow query can come from data volume or
database settings. The rules settle it by a choice — if the request reached the product and was
still being worked on, the product owns its latency. That is a convention the taxonomy adopts,
not something the evidence proves. What the case shows is that the convention is clear enough
to apply.

## What the rules changed

Full evidence only, both models together:

|                                                         | With rules | Without rules |
| ------------------------------------------------------- | ---------- | ------------- |
| Matched the expected answer                             | 123 of 126 | 116 of 126    |
| Look-alike pairs told apart                             | 15 of 16   | 12 of 16      |
| Look-alike pairs merged                                 | 0          | 1             |
| The two models reach the same conclusion (all 27 files) | 26 of 27   | 23 of 27      |
| Same answer in all 3 runs (all 27 files)                | 54 of 54   | 52 of 54      |

Two things are worth saying about these numbers.

- **Without the rules, the models already agree with the taxonomy on most cases.** The five
  classes, given only one-line definitions, are natural enough to apply. That is not the same as
  showing engineers think this way; the models are not engineers.
- **With the rules, the differences land exactly on the tie-breaks.** The three cases above each
  change under one named rule, and the only two unstable results without the rules (the model
  changing its answer between runs) are two of those same cases.

What "with rules" can and cannot show: the expected labels were written from the same rules the
models were given. So agreement shows the rules are **clear enough to apply consistently**. It
does not show they are **correct**.

## Where the rules themselves are weak

`hardcoded-id-typo-never-existed`. A test looks up customer `CUST-1881`, which does not exist.
The test file was edited the day after it last passed, in a commit called "tidy test constants".
The case is labelled TEST_CODE_DEFECT: the test asks for an ID that was never supposed to exist.

`claude-opus-5` answered TEST_CODE_DEFECT, with and without the rules, reasoning from the audit
log and the commit. `gpt-6-astra` answered INSUFFICIENT_DATA in all 3 runs, with and without the
rules:

> The missing-record reference rule leaves this between TEST_DATA if CUST-1881 was supposed to be
> provided by a fixture or seed, and TEST_CODE_DEFECT if the test references an identifier that
> was never supposed to exist. … the deciding evidence is the fixture or seed definition recording
> the intended customer identifier.

That is a correct reading of the rule. The rule asks whether the record was *supposed* to be
there, and the evidence only shows that it never *was*. The case was written by `claude-opus-5`
after that rule was reworded, and it still leans on the old question — so `claude-opus-5`
agreeing with it may reflect the author's own reading rather than a correct one. The label was
left unchanged, and the result is reported as it is: with the rules, all three answers that
missed their label are this one case.

**What it says about the taxonomy:** this rule is the hardest one to apply. It needs a kind of
evidence the other rules do not — a record of which identifier the test was *meant* to use —
and the evidence section does not name it. That is left as future work (see below), because
changing the rules now would mean they were no longer the rules the models were tested on.

## What did not separate

Six files carry only the error message. Both models answered INSUFFICIENT_DATA every time: 36
replies with the rules and 36 without, 72 in all.

That means: given only an error message, neither model guessed. It does not mean the models do
not overclaim. A bare error message is the easiest case to decline, because there is nothing to
build a story from. Overclaiming happens with some evidence pointing the wrong way — and the one
case built like that, the invoice without a contract, is exactly where the confident wrong
answer appeared.

## Limits

- **Constructed cases.** Each case was built to test one rule, not captured from a real failing
  build. Real evidence is messier, so these results are likely a best case.
- **Small.** 21 cases, 2 models, 3 runs, one date. The differences are a handful of answers;
  they show where the rules act, not how often.
- **Labels from the same rules,** see "What the rules changed" above, **and drafted with AI
  help,** see "Scope" at the top.
- **One disputed label,** reported rather than changed.
- **Two models only,** run at default settings; temperature could not be set. Another model, or
  the same one later, may behave differently.

## Next steps

- **Real evidence.** Seed two or three of the look-alike pairs into an open-source demo app,
  capture the real errors, traces and logs, and run the same comparison.
- **The weak rule.** Name the missing evidence type in `TAXONOMY.md`, add a worked example for
  the missing-record rule, and run the cases again.
- **The middle ground.** More cases like the invoice without a contract: some evidence, pointing
  the wrong way. That is where overclaiming appears.

## Where to look

- The rules: `TAXONOMY.md`
- The prompts: `prompts/classify.md` (with the rules) and `prompts/classify-no-rules.md`
- The numbers and every differing answer: `RESULTS.md`
- The cases and their labels: `cases/<case>/meta.json`
- Every reply in full: `raw/` (with rules) and `raw-no-rules/` (without)
