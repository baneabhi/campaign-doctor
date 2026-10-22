package com.campaigndoctor.eval;

import com.campaigndoctor.agent.AgentResult;
import com.campaigndoctor.agent.Assistant;
import java.util.ArrayList;
import java.util.List;

/** Runs every case against a fresh conversation and scores the answers. */
public final class EvalRunner {

    private EvalRunner() {}

    public static EvalReport run(List<EvalCase> cases, Assistant assistant) {
        List<CaseResult> results = new ArrayList<>();
        for (EvalCase c : cases) {
            assistant.reset();
            try {
                AgentResult result = assistant.ask(c.question());
                results.add(new CaseResult(c, result, Scorer.failures(c, result)));
            } catch (RuntimeException e) {
                results.add(new CaseResult(c, AgentResult.empty(), List.of("exception: " + e)));
            }
        }
        return new EvalReport(results);
    }
}
