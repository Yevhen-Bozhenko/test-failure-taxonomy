package io.github.yevhenbozhenko.testfailure;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One answer: the class a model chose and the reasoning it gave, after parsing.
 *
 * <p>The reasoning is the material for the qualitative analysis, so it is kept verbatim.
 */
public record Classification(@JsonProperty("class") FailureClass failureClass, String reasoning) {

    public Classification {
        if (failureClass == null) {
            throw new IllegalArgumentException("classification: class is required");
        }
    }
}
