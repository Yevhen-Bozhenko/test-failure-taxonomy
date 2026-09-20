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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Asks both models to label every case, {@value #RUNS} times each, twice over: once with the
 * rules in the prompt, saving to {@code raw/<model>/<caseId>-<run>.json}, and once without them,
 * saving to {@code raw-no-rules/...}. Comparing the two shows whether the rules change the
 * answers. That is 2 x 2 x 12 x {@value #RUNS} = 144 calls on a fresh checkout.
 *
 * <p>If a reply is already saved, that call is skipped. So running this again after a failure
 * only asks for what is missing, and a saved reply is never changed.
 *
 * <p>Only a good reply is saved. A failed call is printed and left for the next run to pick up,
 * which is why there is no retry code here.
 *
 * <p>Set {@code ANTHROPIC_API_KEY} and {@code OPENAI_API_KEY} in your shell first. Nothing here
 * reads a {@code .env} file.
 *
 * <p>Run with {@code mvn exec:java -Dexec.mainClass=io.github.yevhenbozhenko.testfailure.Runner}.
 */
public final class Runner {

    private static final String ANTHROPIC_MODEL = "claude-opus-5";
    private static final String OPENAI_MODEL = "gpt-6-astra";
    private static final int RUNS = 3;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    /**
     * Normal-looking JSON: two spaces, and {@code "key": value}. Jackson's own version puts a
     * space before the colon and squeezes a list onto one line, which would jam a case's log
     * lines together in the prompt.
     */
    private static final ObjectWriter EVIDENCE = JSON.writer(prettyPrinter());

    public static void main(String[] args) throws IOException, InterruptedException {
        // Check both keys now. Finding out later that the OpenAI key is missing would mean
        // paying for every Claude call first.
        key("ANTHROPIC_API_KEY");
        key("OPENAI_API_KEY");

        String taxonomy = Files.readString(Path.of("TAXONOMY.md")).strip();

        // Taken from the code, so renaming an answer cannot leave the prompt asking for a name
        // that nothing will accept later.
        String classes = String.join(", ",
                Stream.of(FailureClass.values()).map(FailureClass::name).toList());

        // Read every case before the first call, for the same reason as the keys above. A typo
        // in the last file would otherwise stop the run partway, after calls we had paid for.
        List<Path> caseFiles;
        try (Stream<Path> files = Files.list(Path.of("cases"))) {
            caseFiles = files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        List<Case> cases = new ArrayList<>();
        for (Path caseFile : caseFiles) {
            cases.add(JSON.readValue(caseFile.toFile(), Case.class));
        }

        int sent = 0;
        int skipped = 0;
        int failed = 0;

        // The same 12 cases are asked twice over: once with the rules in the prompt, once with
        // only the five answers and no rules. Comparing the two is how we find out whether the
        // rules change what the models say. Both files are read now, not when their turn comes,
        // so a missing one cannot surface after 72 calls we have already paid for.
        var conditions = List.of(
                Map.entry("raw", Files.readString(Path.of("prompts/classify.md"))),
                Map.entry("raw-no-rules", Files.readString(Path.of("prompts/classify-no-rules.md"))));

        // Every answer name has to appear in the words the model reads, not only in the list we
        // generate. Otherwise renaming one would leave a prompt that contradicts itself.
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
                for (Case testCase : cases) {
                    String prompt = template
                            .replace("{{TAXONOMY}}", taxonomy)
                            .replace("{{CLASSES}}", classes)
                            .replace("{{EVIDENCE}}", EVIDENCE.writeValueAsString(testCase.evidence()));

                    for (int run = 1; run <= RUNS; run++) {
                        Path out = Path.of(
                                condition.getKey(), model, testCase.id() + "-" + run + ".json");
                        if (Files.exists(out)) {
                            skipped++;
                            continue;
                        }

                        HttpResponse<String> response;
                        try {
                            response = HTTP.send(
                                    request(model, prompt), HttpResponse.BodyHandlers.ofString());
                        } catch (IOException e) {
                            // Nothing was sent, or nothing came back. We paid for nothing and
                            // saved nothing, so the next run simply tries this one again.
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
                            // We paid for this reply but could not save it: a full disk, or a
                            // virus scanner holding the file. Print it rather than lose it.
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
            // Throwing makes the build fail. Otherwise a run where every call was rejected still
            // looks like a success, and whatever reads raw/ next would find it empty.
            throw new IllegalStateException(
                    failed + " call(s) failed. Re-run to retry them; everything written is skipped.");
        }
    }

    /**
     * Writes to a temporary file first, then renames it. A crash halfway through would otherwise
     * leave half a reply on disk, and the skip at the top of the loop would treat that file as a
     * finished call for ever.
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
            // No temperature and no size limit. Both vendors' current models refuse a
            // temperature setting, and the answer is only a few hundred words.
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
