You are triaging one failed automated test. Decide what caused the failure.

# Answers

PRODUCT_DEFECT — the application under test is genuinely wrong.
TEST_CODE_DEFECT — the test itself is wrong.
TEST_DATA — the data the test relied on was wrong or in a bad state.
ENVIRONMENT_CONFIG — infrastructure, configuration, or third-party dependency.
INSUFFICIENT_DATA — the evidence cannot support a confident conclusion.

# This failure

Everything known about this failure is below. A field that is absent was not collected — its
absence is not itself evidence about what happened.

{{EVIDENCE}}

# Answer

Reply with one JSON object and nothing else: no markdown fence, no text before or after it.

{"class": "...", "reasoning": "..."}

`class` is exactly one of: {{CLASSES}}.

`reasoning` is two or three sentences saying why you chose it and which piece of evidence made
you choose it.
