package com.campaigndoctor.eval;

import java.util.List;

/**
 * One test question and what a good answer must (and must not) contain. All checks are deterministic.
 *
 * <p>Entries in {@code expectContains} may list alternatives separated by "|", for example "62%|0.62".
 * Matching ignores case and thousands separators.
 *
 * @param expectContains    each entry must appear in the answer
 * @param expectNotContains none of these may appear
 * @param expectSources     each must be cited as [file.md]
 * @param expectTools       each tool must have been called
 * @param expectAbstain     true if the right answer is "I don't have enough information"
 */
public record EvalCase(String id, String question, List<String> expectContains, List<String> expectNotContains,
                       List<String> expectSources, List<String> expectTools, boolean expectAbstain) {

    public EvalCase {
        expectContains = expectContains == null ? List.of() : List.copyOf(expectContains);
        expectNotContains = expectNotContains == null ? List.of() : List.copyOf(expectNotContains);
        expectSources = expectSources == null ? List.of() : List.copyOf(expectSources);
        expectTools = expectTools == null ? List.of() : List.copyOf(expectTools);
    }
}
