You are triaging one failed automated test. Decide what caused the failure.

Apply the rules below as written. They are the only authority for this decision. Where your own
experience of what usually causes a failure of this shape disagrees with them, the rules win.

{{TAXONOMY}}

# Evidence

Everything known about this failure is below. A field that is absent was not collected — its
absence is not itself evidence about what happened.

{{EVIDENCE}}

# Answer

Reply with one JSON object and nothing else: no markdown fence, no text before or after it.

{"class": "...", "reasoning": "..."}

`class` is exactly one of: PRODUCT_DEFECT, TEST_CODE_DEFECT, TEST_DATA, ENVIRONMENT_CONFIG,
INSUFFICIENT_DATA.

`reasoning` is two or three sentences saying which rule decided it and which piece of evidence
made that rule apply.
