package com.campaigndoctor.eval;

import com.campaigndoctor.agent.AgentResult;
import java.util.List;

public record CaseResult(EvalCase evalCase, AgentResult result, List<String> failures) {

    public boolean passed() {
        return failures.isEmpty();
    }
}
