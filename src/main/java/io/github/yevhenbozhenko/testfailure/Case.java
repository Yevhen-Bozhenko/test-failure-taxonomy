package io.github.yevhenbozhenko.testfailure;

/**
 * One seeded failure, read from {@code cases/<id>.json}.
 *
 * <p>{@code trueClass} is the hand-assigned ground truth and is never sent to a model: the
 * prompt is built from {@link #evidence()} alone.
 */
public record Case(String id, FailureClass trueClass, Evidence evidence) {

    public Case {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("case: id is required");
        }
        if (trueClass == null) {
            throw new IllegalArgumentException("case (" + id + "): trueClass is required");
        }
        if (evidence == null || evidence.isEmpty()) {
            throw new IllegalArgumentException("case (" + id + "): evidence is required");
        }
    }
}
