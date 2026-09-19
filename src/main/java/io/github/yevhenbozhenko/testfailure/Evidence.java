package io.github.yevhenbozhenko.testfailure;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * What a model is shown for one case: the evidence a triaging engineer would have.
 *
 * <p>Every field is optional; absent fields are omitted from the JSON rather than written
 * as null, so a model is never shown an empty section.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Evidence(
        String errorMessage,
        String stackTrace,
        List<String> logs,
        String httpRequest,
        String httpResponse,
        String testCode) {

    public Evidence {
        logs = logs == null ? null : List.copyOf(logs);
    }

    /** True when the case carries no evidence at all, which is an authoring mistake. */
    boolean isEmpty() {
        return isBlank(errorMessage)
                && isBlank(stackTrace)
                && (logs == null || logs.isEmpty())
                && isBlank(httpRequest)
                && isBlank(httpResponse)
                && isBlank(testCode);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
