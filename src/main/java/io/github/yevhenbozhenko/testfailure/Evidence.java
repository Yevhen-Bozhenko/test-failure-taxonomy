package io.github.yevhenbozhenko.testfailure;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.stream.Stream;

/**
 * What a model is shown for one case: the evidence a tester would have.
 *
 * <p>Empty fields are left out, so the model never sees an empty section. {@code caseId} and
 * {@code evidenceLevel} are dropped when the file is read, because case names give the answer
 * away. {@code logs} is split into lines so each log line shows on its own row.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"caseId", "evidenceLevel"})
public record Evidence(
        String errorMessage,
        String stackTrace,
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> logs,
        String httpRequest,
        String httpResponse,
        String testCode,
        String testHistory,
        String contractExcerpt) {

    public Evidence {
        logs = logs == null ? null : logs.stream().flatMap(entry -> Stream.of(entry.split("\n"))).toList();
    }

    /** True if the file has no evidence at all, which means the case was written wrong. */
    boolean isEmpty() {
        return isBlank(errorMessage)
                && isBlank(stackTrace)
                && (logs == null || logs.isEmpty())
                && isBlank(httpRequest)
                && isBlank(httpResponse)
                && isBlank(testCode)
                && isBlank(testHistory)
                && isBlank(contractExcerpt);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
