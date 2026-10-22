package com.campaigndoctor.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campaigndoctor.agent.AgentResult;
import com.campaigndoctor.agent.Assistant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvalRunnerTest {

    private static final class ScriptedAssistant implements Assistant {
        int resets;

        @Override
        public AgentResult ask(String question) {
            if (question.equals("boom")) {
                throw new IllegalStateException("boom");
            }
            String text = question.equals("good") ? "The answer is 42" : "wrong";
            return new AgentResult(text, List.of(), List.of(), List.of(), 100, 20, 5);
        }

        @Override
        public void reset() {
            resets++;
        }
    }

    private static EvalCase caseFor(String id, String question) {
        return new EvalCase(id, question, List.of("42"), List.of(), List.of(), List.of(), false);
    }

    @Test
    void runsEveryCaseOnAFreshConversationAndScoresIt() {
        ScriptedAssistant assistant = new ScriptedAssistant();
        EvalReport report = EvalRunner.run(
                List.of(caseFor("a", "good"), caseFor("b", "bad"), caseFor("c", "boom")), assistant);

        assertEquals(3, report.total());
        assertEquals(1, report.passed());
        assertEquals(3, assistant.resets);
        assertTrue(report.results().get(0).passed());
        assertFalse(report.results().get(1).passed());
        assertTrue(report.results().get(2).failures().get(0).startsWith("exception:"));
    }

    @Test
    void summarisesTokensAndFormatsAReport() {
        EvalReport report = EvalRunner.run(List.of(caseFor("a", "good"), caseFor("b", "bad")),
                new ScriptedAssistant());

        assertEquals(200, report.totalInputTokens());
        assertEquals(40, report.totalOutputTokens());
        assertEquals(0.5, report.passRate(), 1e-9);

        String text = report.format();
        assertTrue(text.contains("PASS"));
        assertTrue(text.contains("FAIL"));
        assertTrue(text.contains("Passed 1/2"));
        assertTrue(text.contains("missing expected text: 42"));
    }
}
