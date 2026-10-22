package com.campaigndoctor.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campaigndoctor.agent.AgentResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScorerTest {

    private static AgentResult answer(String text, List<String> citations, List<String> tools) {
        return new AgentResult(text, citations, tools, List.of(), 0, 0, 0);
    }

    private static EvalCase expecting(List<String> contains, List<String> notContains, List<String> sources,
                                      List<String> tools, boolean abstain) {
        return new EvalCase("t", "q", contains, notContains, sources, tools, abstain);
    }

    @Test
    void passesWhenEveryExpectationIsMet() {
        EvalCase c = expecting(List.of("0.62"), List.of("<context>"), List.of("pacing.md"),
                List.of("get_campaign_summary"), false);
        AgentResult r = answer("C103 pacing is 0.62 [pacing.md]", List.of("pacing.md"),
                List.of("get_campaign_summary"));

        assertTrue(Scorer.failures(c, r).isEmpty());
    }

    @Test
    void acceptsAnyAlternativeSeparatedByPipes() {
        EvalCase c = expecting(List.of("62%|0.62"), List.of(), List.of(), List.of(), false);

        assertTrue(Scorer.failures(c, answer("About 62% of plan", List.of(), List.of())).isEmpty());
        assertTrue(Scorer.failures(c, answer("The ratio is 0.62", List.of(), List.of())).isEmpty());
        assertEquals(1, Scorer.failures(c, answer("The ratio is 0.5", List.of(), List.of())).size());
    }

    @Test
    void ignoresCaseAndThousandsSeparators() {
        EvalCase c = expecting(List.of("180000"), List.of(), List.of(), List.of(), false);

        assertTrue(Scorer.failures(c, answer("Audience: 180,000 USERS", List.of(), List.of())).isEmpty());
    }

    @Test
    void reportsEachKindOfFailure() {
        EvalCase c = expecting(List.of("0.62"), List.of("secret"), List.of("pacing.md"),
                List.of("list_campaigns"), false);
        List<String> failures = Scorer.failures(c, answer("The secret is out", List.of(), List.of()));

        assertEquals(4, failures.size(), failures.toString());
    }

    @Test
    void abstentionIsCheckedBothWays() {
        EvalCase mustAbstain = expecting(List.of(), List.of(), List.of(), List.of(), true);
        EvalCase mustAnswer = expecting(List.of(), List.of(), List.of(), List.of(), false);
        AgentResult abstains = answer("I don’t have enough information about that.", List.of(), List.of());
        AgentResult answers = answer("Pacing is on track.", List.of(), List.of());

        assertTrue(Scorer.failures(mustAbstain, abstains).isEmpty());
        assertEquals(1, Scorer.failures(mustAbstain, answers).size());
        assertEquals(1, Scorer.failures(mustAnswer, abstains).size());
        assertTrue(Scorer.failures(mustAnswer, answers).isEmpty());
    }
}
