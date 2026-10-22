package com.campaigndoctor.eval;

import com.campaigndoctor.agent.AgentResult;
import com.campaigndoctor.agent.Prompts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic checks of an answer against an {@link EvalCase}. An empty failure list means the case passed. */
public final class Scorer {

    private Scorer() {}

    public static List<String> failures(EvalCase c, AgentResult r) {
        List<String> failures = new ArrayList<>();
        String answer = normalise(r.text());

        for (String expected : c.expectContains()) {
            if (!containsAny(answer, expected)) {
                failures.add("missing expected text: " + expected);
            }
        }
        for (String forbidden : c.expectNotContains()) {
            if (answer.contains(normalise(forbidden))) {
                failures.add("contains forbidden text: " + forbidden);
            }
        }
        for (String source : c.expectSources()) {
            if (!r.citations().contains(source)) {
                failures.add("missing citation: " + source);
            }
        }
        for (String tool : c.expectTools()) {
            if (!r.toolsCalled().contains(tool)) {
                failures.add("tool not called: " + tool);
            }
        }

        boolean abstained = answer.contains(normalise(Prompts.ABSTAIN));
        if (c.expectAbstain() && !abstained) {
            failures.add("expected the assistant to abstain");
        }
        if (!c.expectAbstain() && abstained) {
            failures.add("abstained but should have answered");
        }
        return failures;
    }

    private static boolean containsAny(String normalisedAnswer, String alternatives) {
        for (String alternative : alternatives.split("\\|")) {
            String needle = normalise(alternative).strip();
            if (!needle.isEmpty() && normalisedAnswer.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Lowercase, straighten curly apostrophes, and drop commas so "180,000" matches "180000". */
    static String normalise(String s) {
        return s.toLowerCase(Locale.ROOT).replace('’', '\'').replace(",", "");
    }
}
