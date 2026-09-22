package io.github.yevhenbozhenko.testfailure;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Asks both models about every evidence file in {@code cases/}, {@value #RUNS} times each — once
 * with the rules and once without — and saves each answer as
 * {@code raw/<model>/<caseId>-<level>-<run>.json}, or under {@code raw-no-rules/} for the second.
 *
 * <p>An answer already saved is skipped, so running again only asks for what is missing. Only
 * good answers are saved; a failed call is printed and tried again by the next run.
 *
 * <p>Needs {@code ANTHROPIC_API_KEY} and {@code OPENAI_API_KEY} set in the shell. Run with
 * {@code mvn exec:java -Dexec.mainClass=io.github.yevhenbozhenko.testfailure.Runner}.
 */
public final class Runner {

    // Not private, so the Scorer uses the same values.
    static final String ANTHROPIC_MODEL = "claude-opus-5";
    static final String OPENAI_MODEL = "gpt-6-astra";
    static final int RUNS = 3;

    private static final Set<String> CASE_FILES =
            Set.of("meta.json", "evidence-full.json", "evidence-thin.json");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    /** Plain JSON, one list item per line. Jackson's default squeezes log lines together. */
    private static final ObjectWriter EVIDENCE = JSON.writer(prettyPrinter());

    public static void main(String[] args) throws IOException, InterruptedException {
        // Check both keys first, so a missing one can't show up after paying for half the run.
        key("ANTHROPIC_API_KEY");
        key("OPENAI_API_KEY");

        String taxonomy = Files.readString(Path.of("TAXONOMY.md")).strip();

        // Taken from the code, so the prompt always lists the answer names the code accepts.
        String classes = String.join(", ",
                Stream.of(FailureClass.values()).map(FailureClass::name).toList());

        // Read every case before the first call, so a broken file stops the run before anything
        // is paid for. Only the evidence files are read: meta.json holds the answers and is never
        // opened.
        Map<String, Evidence> bundles = new TreeMap<>();
        try (Stream<Path> caseDirs = Files.list(Path.of("cases"))) {
            for (Path caseDir : caseDirs.toList()) {
                // A stray file here would otherwise be skipped without a word.
                if (!Files.isDirectory(caseDir)) {
                    throw new IllegalStateException(caseDir + " is not a case folder. Left over?");
                }
                // Only these three file names are allowed, so a misnamed one stops the run instead
                // of being skipped without a word.
                if (!Files.exists(caseDir.resolve("evidence-full.json"))) {
                    throw new IllegalStateException(caseDir + " has no evidence-full.json");
                }
                try (Stream<Path> files = Files.list(caseDir)) {
                    for (Path file : files.toList()) {
                        String name = file.getFileName().toString();
                        if (!CASE_FILES.contains(name)) {
                            throw new IllegalStateException(
                                    file + " is not one of " + CASE_FILES + ". Misnamed?");
                        }
                        if (name.equals("meta.json")) {
                            continue;
                        }
                        Evidence evidence = JSON.readValue(file.toFile(), Evidence.class);
                        if (evidence.isEmpty()) {
                            throw new IllegalStateException(file + " carries no evidence");
                        }
                        String level = name.replace("evidence-", "").replace(".json", "");
                        bundles.put(caseDir.getFileName() + "-" + level, evidence);
                    }
                }
            }
        }

        int sent = 0;
        int skipped = 0;
        int failed = 0;

        // Ask each case twice, with the rules and without, to see whether the rules change the
        // answers. Both prompts are read now, so a missing one fails before anything is paid for.
        var conditions = List.of(
                Map.entry("raw", Files.readString(Path.of("prompts/classify.md"))),
                Map.entry("raw-no-rules", Files.readString(Path.of("prompts/classify-no-rules.md"))));

        // Every answer name must also appear in the prompt text, or renaming one would leave the
        // prompt contradicting itself.
        for (var condition : conditions) {
            for (FailureClass failureClass : FailureClass.values()) {
                if (!condition.getValue().contains(failureClass.name())
                        && !taxonomy.contains(failureClass.name())) {
                    throw new IllegalStateException(
                            failureClass.name() + " is never mentioned in the prompt for "
                                    + condition.getKey());
                }
            }
        }

        for (var condition : conditions) {
            String template = condition.getValue();

            for (String model : List.of(ANTHROPIC_MODEL, OPENAI_MODEL)) {
                for (var bundle : bundles.entrySet()) {
                    String prompt = template
                            .replace("{{TAXONOMY}}", taxonomy)
                            .replace("{{CLASSES}}", classes)
                            .replace("{{EVIDENCE}}", EVIDENCE.writeValueAsString(bundle.getValue()));

                    for (int run = 1; run <= RUNS; run++) {
                        Path out = Path.of(
                                condition.getKey(), model, bundle.getKey() + "-" + run + ".json");
                        if (Files.exists(out)) {
                            skipped++;
                            continue;
                        }

                        HttpResponse<String> response;
                        try {
                            response = HTTP.send(
                                    request(model, prompt), HttpResponse.BodyHandlers.ofString());
                        } catch (IOException e) {
                            // Nothing was paid for or saved, so the next run tries again.
                            failed++;
                            System.out.println("FAIL " + out + "  " + e);
                            continue;
                        }

                        if (response.statusCode() != 200) {
                            failed++;
                            System.out.println("FAIL " + out + "  HTTP " + response.statusCode());
                            System.out.println("     " + response.body());
                            continue;
                        }

                        try {
                            write(out, response.body());
                        } catch (IOException e) {
                            // Paid for but couldn't be saved, so print it rather than lose it.
                            failed++;
                            System.out.println("FAIL " + out + "  reply received but not saved: " + e);
                            System.out.println(response.body());
                            continue;
                        }

                        sent++;
                        System.out.println("ok   " + out);
                    }
                }
            }
        }

        System.out.println("\n" + sent + " written, " + skipped + " already present, " + failed + " failed");
        if (failed > 0) {
            // Fail the build, so a run with failed calls can't look like a success.
            throw new IllegalStateException(
                    failed + " call(s) failed. Re-run to retry them; everything written is skipped.");
        }
    }

    /**
     * Writes to a temporary file, then renames it, so a crash can't leave half an answer that the
     * next run would treat as finished.
     */
    private static void write(Path out, String body) throws IOException {
        Files.createDirectories(out.getParent());
        Path partial = out.resolveSibling(out.getFileName() + ".partial");
        Files.writeString(partial, body);
        Files.move(partial, out, StandardCopyOption.ATOMIC_MOVE);
    }

    private static HttpRequest request(String model, String prompt) throws IOException {
        Map<String, Object> body;
        HttpRequest.Builder builder;

        if (model.equals(ANTHROPIC_MODEL)) {
            body = Map.of(
                    "model", model,
                    "max_tokens", 16000,
                    "messages", List.of(Map.of("role", "user", "content", prompt)));
            builder = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("x-api-key", key("ANTHROPIC_API_KEY"))
                    .header("anthropic-version", "2023-06-01");
        } else {
            // No temperature (both vendors refuse it) and no size limit (answers are short).
            body = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt)));
            builder = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Authorization", "Bearer " + key("OPENAI_API_KEY"));
        }

        return builder
                .header("content-type", "application/json")
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
    }

    private static DefaultPrettyPrinter prettyPrinter() {
        DefaultIndenter indent = new DefaultIndenter("  ", "\n");
        return new DefaultPrettyPrinter()
                .withObjectIndenter(indent)
                .withArrayIndenter(indent)
                .withSeparators(Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER));
    }

    private static String key(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
