package io.github.yevhenbozhenko.testfailure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the saved answers and prints the results, plus the model's own reasoning for every answer
 * that differs from the expected one.
 *
 * <p>It only reads files, so it is safe to run any time. It compares with rules against without,
 * never one model against the other.
 *
 * <p>Run with {@code mvn exec:java -Dexec.mainClass=io.github.yevhenbozhenko.testfailure.Scorer}.
 */
public final class Scorer {

    private static final List<String> MODELS =
            List.of(Runner.ANTHROPIC_MODEL, Runner.OPENAI_MODEL);
    private static final Map<String, String> CONDITIONS =
            new TreeMap<>(Map.of("raw", "with rules", "raw-no-rules", "no rules"));
    private static final ObjectMapper JSON = new ObjectMapper();

    // Finds where an answer starts. If a model answers twice, the last answer counts.
    private static final Pattern ANSWER = Pattern.compile("\\{\\s*\"class\"");

    /** One saved reply: the answer (null if none), whether it was messy, whether it finished. */
    private record Reply(Classification answer, boolean slip, boolean finished) {

        FailureClass cls() {
            return answer == null ? null : answer.failureClass();
        }
    }

    /** Used when an answer file is missing. */
    private static final Reply MISSING = new Reply(null, false, true);

    /** Each case's meta.json, by case id. */
    private static final Map<String, JsonNode> META = new TreeMap<>();

    /** Each case's expected answer, for full and for thin evidence. */
    private static final Map<String, Map<String, FailureClass>> EXPECTED = new TreeMap<>();

    private static final Map<String, List<Reply>> REPLIES = new TreeMap<>();

    public static void main(String[] args) throws IOException {
        try (Stream<Path> dirs = Files.list(Path.of("cases"))) {
            for (Path dir : dirs.toList()) {
                if (!Files.isDirectory(dir)) {
                    throw new IllegalStateException(dir + " is not a case folder. Left over?");
                }
                String id = dir.getFileName().toString();
                JsonNode meta = JSON.readTree(dir.resolve("meta.json").toFile());
                META.put(id, meta);
                // Turn FULL/THIN into full/thin, as in the answer file names. Locale.ROOT
                // makes this work in any language setting (Turkish would give "thın").
                Map<String, FailureClass> expected = new LinkedHashMap<>();
                meta.get("expectedUnderRules").fields().forEachRemaining(e -> expected.put(
                        e.getKey().toLowerCase(Locale.ROOT),
                        FailureClass.valueOf(e.getValue().asText())));
                EXPECTED.put(id, expected);
            }
        }
        for (String condition : CONDITIONS.keySet()) {
            for (String model : MODELS) {
                for (var c : EXPECTED.entrySet()) {
                    for (String level : c.getValue().keySet()) {
                        List<Reply> runs = new ArrayList<>();
                        for (int run = 1; run <= Runner.RUNS; run++) {
                            String name = c.getKey() + "-" + level + "-" + run + ".json";
                            Path file = Path.of(condition, model, name);
                            // A missing file is counted, not an error.
                            runs.add(Files.exists(file) ? read(file) : MISSING);
                        }
                        REPLIES.put(key(condition, model, c.getKey(), level), runs);
                    }
                }
            }
        }

        List<Reply> all = REPLIES.values().stream().flatMap(List::stream).toList();
        List<Reply> saved = all.stream().filter(r -> r != MISSING).toList();
        System.out.printf("%d answers. Not a single clean answer: %d (scored on the one they ended"
                        + " with). Did not finish normally: %d. No readable answer: %d.%n",
                saved.size(),
                saved.stream().filter(Reply::slip).count(),
                saved.stream().filter(r -> !r.finished()).count(),
                saved.stream().filter(r -> r.answer() == null).count());
        if (saved.size() < all.size()) {
            System.out.printf("WARNING: %d of %d answers missing, so these results are partial."
                    + " Run the runner again to fill them in.%n",
                    all.size() - saved.size(), all.size());
        }

        printPairs();
        printAgreement();
        printWholePicture();
        printDifferences();
    }

    private static void printPairs() {
        section("LOOK-ALIKE PAIRS: told apart when both halves are right in at least 2 of 3 runs;"
                + " MERGED when the model gave both halves the same answer");
        Set<String> seen = new HashSet<>();
        Map<String, Integer> told = new TreeMap<>();
        Map<String, Integer> merged = new TreeMap<>();
        for (String id : META.keySet()) {
            String twin = META.get(id).path("twinOf").asText(null);
            if (twin == null || !seen.add(id.compareTo(twin) < 0 ? id + twin : twin + id)) {
                continue;
            }
            System.out.printf("  %s  vs  %s%n", id, twin);
            for (String condition : CONDITIONS.keySet()) {
                for (String model : MODELS) {
                    List<Reply> runsA = REPLIES.get(key(condition, model, id, "full"));
                    List<Reply> runsB = REPLIES.get(key(condition, model, twin, "full"));
                    int a = count(runsA, expected(id, "full"));
                    int b = count(runsB, expected(twin, "full"));
                    // MERGED only if both cases got the same answer. Two different answers
                    // mean the model saw a difference, even if one answer is wrong.
                    FailureClass sameA = majority(runsA);
                    String verdict = a >= 2 && b >= 2 ? "told apart"
                            : sameA != null && sameA == majority(runsB) ? "MERGED"
                            : a < 2 && b < 2 ? "both wrong" : "one half wrong";
                    if (verdict.equals("told apart")) {
                        told.merge(condition, 1, Integer::sum);
                    }
                    if (verdict.equals("MERGED")) {
                        merged.merge(condition, 1, Integer::sum);
                    }
                    System.out.printf("      %-11s %-14s %-15s (%d/3 and %d/3)%n",
                            CONDITIONS.get(condition), model, verdict, a, b);
                }
            }
        }
        int checks = seen.size() * MODELS.size();
        CONDITIONS.forEach((condition, name) -> System.out.printf(
                "  %-11s %d of %d told apart, %d merged%n", name,
                told.getOrDefault(condition, 0), checks, merged.getOrDefault(condition, 0)));
    }

    private static void printAgreement() {
        // Compare the answer each model gave most often. The runs are separate tries, so
        // matching run 1 with run 1 would mean nothing.
        section("THE TWO MODELS REACH THE SAME CONCLUSION  (each model's majority answer)");
        CONDITIONS.forEach((condition, name) -> {
            int same = 0;
            int total = 0;
            for (var c : EXPECTED.entrySet()) {
                for (String level : c.getValue().keySet()) {
                    total++;
                    String id = c.getKey();
                    List<Reply> firstRuns = REPLIES.get(key(condition, MODELS.get(0), id, level));
                    List<Reply> secondRuns = REPLIES.get(key(condition, MODELS.get(1), id, level));
                    FailureClass first = majority(firstRuns);
                    FailureClass second = majority(secondRuns);
                    if (first != null && first == second) {
                        same++;
                    }
                }
            }
            System.out.printf("  %-11s %d of %d cases%n", name, same, total);
        });
    }

    private static void printWholePicture() {
        section("WHOLE PICTURE  (both models together)");
        String row = "  %-11s %-26s %-26s %-26s %s%n";
        System.out.printf(row, "", "matched expected answer", "matched what really broke",
                "evidence removed -> INSUF", "same answer all 3 runs");
        CONDITIONS.forEach((condition, name) -> {
            int expected = 0;
            int seeded = 0;
            int thin = 0;
            int thinInsufficient = 0;
            int stable = 0;
            int groups = 0;
            for (String model : MODELS) {
                for (String id : META.keySet()) {
                    String seededClass = META.get(id).get("seededClass").asText();
                    FailureClass broke = FailureClass.valueOf(seededClass);
                    for (var level : EXPECTED.get(id).entrySet()) {
                        List<Reply> runs = REPLIES.get(key(condition, model, id, level.getKey()));
                        expected += count(runs, level.getValue());
                        seeded += count(runs, broke);
                        if (level.getKey().equals("thin")) {
                            thin += runs.size();
                            thinInsufficient += count(runs, FailureClass.INSUFFICIENT_DATA);
                        }
                        groups++;
                        // Three unreadable replies are not "the same answer".
                        FailureClass first = runs.get(0).cls();
                        if (first != null && count(runs, first) == runs.size()) {
                            stable++;
                        }
                    }
                }
            }
            int answers = groups * Runner.RUNS;
            System.out.printf(row, name, expected + " of " + answers, seeded + " of " + answers,
                    thinInsufficient + " of " + thin, stable + " of " + groups);
        });
    }

    private static void printDifferences() {
        section("EVERY ANSWER THAT DIFFERS FROM THE EXPECTED ONE, with the model's own reasoning");
        for (var entry : REPLIES.entrySet()) {
            String[] k = entry.getKey().split("\\|");
            FailureClass want = expected(k[2], k[3]);
            List<Reply> runs = entry.getValue();
            List<Reply> wrong = runs.stream().filter(r -> r.cls() != want).toList();
            if (wrong.isEmpty()) {
                continue;
            }
            Classification first = wrong.get(0).answer();
            System.out.printf("%n  %s, %s: %s (%s)%n    expected %s, got %s%n    \"%s\"%n",
                    CONDITIONS.get(k[0]), k[1], k[2], k[3], want,
                    runs.stream().map(r -> String.valueOf(r.cls())).toList(),
                    first == null ? "(no readable answer)" : first.reasoning());
        }
    }

    /** Reads one saved reply. Claude may include its thinking, so only the text parts are read. */
    private static Reply read(Path file) throws IOException {
        JsonNode reply = JSON.readTree(file.toFile());
        String text;
        boolean finished;
        if (reply.has("content")) {
            StringBuilder b = new StringBuilder();
            for (JsonNode block : reply.get("content")) {
                if ("text".equals(block.path("type").asText())) {
                    b.append(block.path("text").asText());
                }
            }
            text = b.toString();
            finished = "end_turn".equals(reply.path("stop_reason").asText());
        } else {
            JsonNode choice = reply.get("choices").get(0);
            JsonNode message = choice.path("message");
            text = message.path("content").asText();
            finished = "stop".equals(choice.path("finish_reason").asText())
                    && !message.hasNonNull("refusal");
        }

        Matcher m = ANSWER.matcher(text);
        int found = 0;
        int last = -1;
        while (m.find()) {
            found++;
            last = m.start();
        }
        Classification answer = null;
        if (last >= 0) {
            try {
                answer = JSON.readValue(text.substring(last), Classification.class);
            } catch (IOException e) {
                // Broken JSON or an unknown class. Counted as "no readable answer".
            }
        }
        return new Reply(answer, found != 1 || !text.strip().startsWith("{"), finished);
    }

    /** The answer given in most runs, or null if there isn't one. */
    private static FailureClass majority(List<Reply> runs) {
        for (Reply r : runs) {
            if (r.cls() != null && count(runs, r.cls()) * 2 > runs.size()) {
                return r.cls();
            }
        }
        return null;
    }

    private static int count(List<Reply> runs, FailureClass want) {
        return (int) runs.stream().filter(r -> r.cls() == want).count();
    }

    private static FailureClass expected(String id, String level) {
        return EXPECTED.get(id).get(level);
    }

    private static String key(String condition, String model, String id, String level) {
        return condition + "|" + model + "|" + id + "|" + level;
    }

    private static void section(String title) {
        System.out.printf("%n%s%n", title);
    }
}
