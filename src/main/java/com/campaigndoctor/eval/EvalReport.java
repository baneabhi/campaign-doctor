package com.campaigndoctor.eval;

import java.util.List;
import java.util.Locale;

public record EvalReport(List<CaseResult> results) {

    public int total() {
        return results.size();
    }

    public int passed() {
        return (int) results.stream().filter(CaseResult::passed).count();
    }

    public double passRate() {
        return total() == 0 ? 0.0 : (double) passed() / total();
    }

    public long totalInputTokens() {
        return results.stream().mapToLong(r -> r.result().inputTokens()).sum();
    }

    public long totalOutputTokens() {
        return results.stream().mapToLong(r -> r.result().outputTokens()).sum();
    }

    public double averageLatencyMs() {
        return results.stream().mapToLong(r -> r.result().latencyMs()).average().orElse(0);
    }

    /** A plain-text table: one line per case, failure reasons indented under failed cases, then a summary. */
    public String format() {
        StringBuilder sb = new StringBuilder();
        for (CaseResult r : results) {
            sb.append(String.format(Locale.ROOT, "%-5s %-28s %6d tok %7d ms%n",
                    r.passed() ? "PASS" : "FAIL", r.evalCase().id(),
                    r.result().inputTokens() + r.result().outputTokens(), r.result().latencyMs()));
            for (String failure : r.failures()) {
                sb.append("        - ").append(failure).append('\n');
            }
        }
        sb.append(String.format(Locale.ROOT,
                "%nPassed %d/%d (%.1f%%) | tokens in/out: %d / %d | avg latency: %.0f ms%n",
                passed(), total(), passRate() * 100, totalInputTokens(), totalOutputTokens(), averageLatencyMs()));
        return sb.toString();
    }
}
