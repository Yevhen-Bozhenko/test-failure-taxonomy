# Taxonomy

## Evidence

The decision rules below are written against the evidence a triaging engineer actually has:
error message, stack trace, logs, HTTP request/response, the test code itself, its history, and any
contract, specification or release note that says what correct behaviour is.

What each kind can and cannot establish on its own:

**Error message.** Establishes the symptom: which assertion tripped and how the two values
differed. Does not establish who was wrong. An `expected 3 but was 4` is exactly as consistent
with a product that counted wrongly as with a test that expected the wrong number, and nothing
in the message itself separates the two.

**Stack trace.** Establishes the site of the failure and whose package it is in. Does not
establish the origin. Product frames at the top are routine when the test handed the product
bad input; test frames at the top are routine when the product returned a shape the test did
not expect. Its most reliable signal is negative: a bare assertion failure with no product frames
in its trace means the product completed normally and only the comparison failed, which rules out the crash-shaped
readings of every class.

**Logs.** Establish sequence and server-side state around the failure — the one evidence type
that can show what happened *before* the test acted, such as residue from a previous run or a
setup step that failed quietly. They do not establish causation, and they are the evidence most
often incomplete: wrong time window, wrong instance, or a level that suppressed the real error.
Absence of a log line is not evidence the event did not happen.

**HTTP request.** Establishes what the test actually sent, which is the single sharpest cut
available between "the test asked for the wrong thing" and "the product did the wrong thing
with the right ask". Does not establish what the test *intended* to send; that needs the test
code.

**HTTP response.** Establishes what the product returned. A 4xx indicts the request unless the
request is provably valid against the contract; a 5xx indicts the product or what sits in front
of it. A 200 establishes nothing about correctness without an independent statement of what the
body should have been.

**Test code.** The only evidence of intent, and therefore the only thing that can convict the
test of being wrong rather than merely unlucky. Shows hard-coded expectations, ordering
assumptions, shared mutable state, and inadequate waits. Does not establish what the product was
supposed to do — a test's expectation may simply be a stale reading of the spec.

**Test history.** Establishes what changed and when: the last build on which this test passed,
and what moved on either side since — the product, the test file, the environment. It is the
only evidence here that speaks to *which side changed*, rather than to what either side did on
this run. A test untouched since it last passed, against a product that has changed, points at
the product; a test edited since points at the test. Does not establish causation: a deploy
landing near the failure is not proof that the deploy caused it. It is also silent for a test
that has never passed, which has no history to compare against.

This is one of the two statements "other than the test's own assertion" that PRODUCT_DEFECT
requires; a contract is the other. Without one of them, a bare disagreement between an assertion
and a response cannot reach PRODUCT_DEFECT at all, however ordinary the failure looks, and falls
to INSUFFICIENT_DATA. With test history, a test that was green on the previous build and has not
been edited since is independent evidence that the expectation was once correct and the product
is what moved.

**Contract and release notes.** Establish what correct behaviour is without relying on the test:
an API contract or specification, or a release note or changelog recording a deliberate change.
This is what lets a documented invariant indict the product, and what the staleness rule requires
before calling a test out of date. Does not establish that either side follows it, and a
contract can itself be out of date or silent on the case in question.

Request and response together are the strongest pair in this set, because only they separate
input from output without requiring intent.

## Classes

Every rule here, and every tie-break after it, is an application of one question: which artifact
is the wrong one? A failure surfaces wherever the assertion happens to sit, and that is rarely
where the fault is. Four artifacts can be wrong, and each names a class: the product; the test
itself — its logic, the values it supplies, and any state it carries between runs; a record the
test consumed; or the environment it ran in. The tie-breaks exist because some failures present
two candidate artifacts at once; each names which of the two is wrong, not which is nearer the
failure. When the evidence cannot identify which artifact is wrong, and the answer would change
the class, that is INSUFFICIENT_DATA.

Work the questions in this order and stop at the first one that answers. They are the question
above asked as an order of elimination: each one rules out an artifact.

1. Is the environment wrong — did it stop the action, or serve a different build or configuration
   from the one intended? If so — **ENVIRONMENT_CONFIG**.
2. Did the test do something other than it meant to, or something invalid? If so —
   **TEST_CODE_DEFECT**.
3. Was a record the test consumed, one it did not create during this run, wrong or missing? If so
   — **TEST_DATA**.
4. With a valid action against correct state in the intended environment, which is wrong — the
   product's answer, or the test's expectation? The product's — **PRODUCT_DEFECT**; the
   expectation — **TEST_CODE_DEFECT**.
5. If the evidence cannot answer one of these and the answer would change the class —
   **INSUFFICIENT_DATA**.

The order matters: it puts the cheapest disqualifications first and reserves PRODUCT_DEFECT for
what survives them, which is also the order in which a triaging engineer clears suspects.

### PRODUCT_DEFECT

The application under test is genuinely wrong.

- Choose it when: the test performed an action that is valid under the product's contract, and
  the response or the resulting state contradicts that contract. Typical shapes: a 5xx with
  product frames in the trace; a 2xx whose body violates a documented invariant; a write that
  reports success and does not persist; correct input in, wrong output out.
- Do not choose it when: the request was malformed or the action was not the one the test
  intended (TEST_CODE_DEFECT); the input came from a fixture that was already wrong
  (TEST_DATA); the failure is a transport error or a timeout with no product frames and no
  server-side record of the request arriving, or the environment was running a different build or
  configuration from the one intended (ENVIRONMENT_CONFIG); or the *only* statement of
  what "correct" means is the test's own expectation, with nothing independent to check it
  against (INSUFFICIENT_DATA).

The last exclusion is the one that gets skipped. A test asserting `total == 100` and a product
returning `99` is not a product defect until something other than that assertion says 100 is
right.

### TEST_CODE_DEFECT

The test itself is wrong.

- Choose it when: the fault is in the test's own logic. Wrong expected value written into the
  test; an assertion on the wrong subject; a locator or selector that no longer matches; a
  missing or inadequate wait; dependence on the order of other tests, or on state they left
  behind in the suite's own machinery — a static field, a shared browser session, a token cached
  between tests; a setup call whose failure the test swallowed and carried on from; an unchecked
  dereference in test-package frames.
- Do not choose it when: the expectation is right and the product changed behaviour without a
  corresponding change to the contract; or the test's data was correct as written and the
  environment supplied something different at run time.

Leftover state only lands here when it is state the test carries between runs. If what an earlier
run left behind is a record the test consumed — an account, an order, a subscription — then that
record is the wrong artifact, this class does not apply, and the stale-account tie-break below
decides between TEST_DATA and ENVIRONMENT_CONFIG. Who caused the staleness never decides the
class; which artifact is stale always does.

A test that is merely stale — the product changed deliberately and the test was never updated —
is a wrong test, so it is TEST_CODE_DEFECT. But that requires evidence that the change was
deliberate, such as release notes or a changelog in the case. Without it, the identical failure
shape is PRODUCT_DEFECT or INSUFFICIENT_DATA, and guessing which is the single most common way
this class gets over-applied.

### TEST_DATA

The data the test relied on was wrong or in a bad state.

- Choose it when: the test's logic and the product's behaviour are both correct for what they
  were given, and the fault is in the input the test consumed from outside itself. A fixture
  record that does not exist; a hard-coded ID pointing at a deleted row; an account left in a
  terminal state by an earlier run; a seed set whose contents contradict the test's premise.
- Do not choose it when: the bad data is itself the product's output (see the tie-break below);
  the data is correct but unreachable because the store is down (ENVIRONMENT_CONFIG); or the
  "data" is a value the test supplies as its own statement of what is correct.

That last line is the boundary that keeps this class from swallowing TEST_CODE_DEFECT. Data is
what the test consumes to set up the case it is checking. A value the test checks the product's
answer against is its own statement of what is correct, and that is code — whether it is written
inline, built by a helper, or kept in a separate expected-values file.

A literal the test uses to look something up is a reference rather than a statement, and a
reference is not the wrong artifact when the record behind it is. If the reference names a record
that was supposed to be there — one the seed data, a fixture, or an earlier setup step was meant to
provide — and that record is missing, removed or changed, the fault is the record's —
**TEST_DATA** — whether or not this particular test ever passed. If the reference names a record
that was never supposed to exist, the test is asking for the wrong thing and is itself the wrong
artifact — **TEST_CODE_DEFECT**. When the evidence does not say which, the case is
**INSUFFICIENT_DATA**, and the missing piece is whatever records which identifier the test should
have found.

### ENVIRONMENT_CONFIG

Infrastructure, configuration, or third-party dependency.

- Choose it when: the failure occurred outside both the product's business logic and the test's
  logic. Connection refused or reset, DNS, expired certificates, transport-level timeouts,
  502/503/504 from something in front of the product, a stale deployed build, a missing or wrong
  environment variable, an unavailable third-party sandbox, a driver or browser version
  mismatch, resource exhaustion on the runner.
- Do not choose it when: the product is slow in a way the product owns (see the timeout
  tie-break); or the configuration in question is intended for that environment and the test
  simply failed to account for it (TEST_CODE_DEFECT).

### INSUFFICIENT_DATA

The evidence cannot support a confident conclusion.

- Choose it when: the evidence is genuinely consistent with two or more classes and contains
  nothing that discriminates between them. Typical shapes: an assertion failure with no
  request, no response and no test code; a bare timeout with no logs; a truncated trace; logs
  from the wrong window.
- Do not choose it when: one class is merely harder to establish but the evidence does support
  it; or the evidence is uncomfortable and this is being used as a hedge.

The operational bar: name the two classes the case sits between, and name the one piece of
evidence that would decide it. If that missing piece cannot be named, the case is not
INSUFFICIENT_DATA and the reluctance is about confidence, not about evidence.

## Tie-breaks

The ambiguous pairs, decided here once.

### Stale test account state — TEST_DATA or ENVIRONMENT_CONFIG

Ask which artifact is wrong: the record the test consumed, or the environment it read that record
through.

An account, its balance, its subscription tier, its permissions — these are rows in the
product's own data model, so stale values in them are **TEST_DATA**, even when the staleness was
caused by an earlier CI run rather than by bad seeding. ENVIRONMENT_CONFIG is for the case where
the account is unusable for a reason outside that data model: the identity provider is down, the
credentials in the secret store were rotated, or the suite authenticated against the wrong
tenant entirely.

Short form: a wrong value in the right place is TEST_DATA; the right value in the wrong place,
or nowhere to read it from, is ENVIRONMENT_CONFIG.

### A fixture created through a broken API call — TEST_DATA or PRODUCT_DEFECT

Classify by where the first wrong thing happened, not by where the assertion failed.

If the setup call reported success and the product nevertheless persisted the fixture wrongly,
the product is defective — **PRODUCT_DEFECT** — and the fact that it surfaced downstream as
something that looks like bad data does not change that. If the setup call actually failed, with
a non-2xx or an error the test did not check, and the test carried on against a fixture that was
never created, the fault is the unchecked setup — **TEST_CODE_DEFECT**, not TEST_DATA.

TEST_DATA is reserved for fixtures the test did not create during this run: pre-seeded records,
shared reference data, leftovers from earlier runs.

Short form: the product built it wrong → PRODUCT_DEFECT. The test ignored that building it
failed → TEST_CODE_DEFECT. The test inherited it → TEST_DATA.

### A timeout — ENVIRONMENT_CONFIG, PRODUCT_DEFECT or TEST_CODE_DEFECT

Decide by how far the request got.

No product frames and no server-side record that the request ever arrived → the request did not
reach the product, so **ENVIRONMENT_CONFIG**. Server logs show the request arrived and was still
being worked on when the clock ran out → the product owns its own latency, so
**PRODUCT_DEFECT**. The request completed within the time the operation has always taken, and
the limit the test set was simply too short for it → **TEST_CODE_DEFECT**.

With none of the three distinguishable — the common case of a bare `TimeoutException` and
nothing else — this is INSUFFICIENT_DATA, and the deciding evidence is the server-side log for
that request.

### A feature flag that is off — ENVIRONMENT_CONFIG or TEST_CODE_DEFECT

If the flag's state in that environment is intended, the test was wrong to assume otherwise →
**TEST_CODE_DEFECT**. If the state is an accident of deployment — configuration that drifted,
was never applied, or reverted to a default on redeploy — → **ENVIRONMENT_CONFIG**.

When the evidence does not say which, this is a true INSUFFICIENT_DATA; the deciding evidence is
the environment's intended flag configuration.

### A failure that passes on retry — no class

A passing retry is not evidence of a class and narrows nothing on its own. Races in the test,
races in the product, and intermittent infrastructure all pass on retry.

Classify from the evidence of the first failure. If that evidence amounts to "it failed and then
it passed", the case is INSUFFICIENT_DATA.
