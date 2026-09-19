package io.github.yevhenbozhenko.testfailure;

/**
 * The five possible sources of an automated test failure.
 *
 * <p>The rules that decide between these live in TAXONOMY.md, not in code. This enum only
 * fixes the spelling, so that cases, prompts and scoring all use the same words.
 */
public enum FailureClass {

    /** The application under test is genuinely wrong. */
    PRODUCT_DEFECT,

    /** The test itself is wrong. */
    TEST_CODE_DEFECT,

    /** The data the test relied on was wrong or in a bad state. */
    TEST_DATA,

    /** Infrastructure, configuration, or a third-party dependency. */
    ENVIRONMENT_CONFIG,

    /** The evidence cannot support a confident conclusion. */
    INSUFFICIENT_DATA
}
