package com.campaigndoctor.agent;

import com.campaigndoctor.llm.Block;
import com.campaigndoctor.llm.Block.ToolResult;
import com.campaigndoctor.llm.LlmClient;
import com.campaigndoctor.llm.Message;
import com.campaigndoctor.llm.Usage;
import com.campaigndoctor.rag.Retriever;
import com.campaigndoctor.tools.ToolRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * YOUR TASK #3: the agent loop. Tests: AgentTest (it uses a fake LlmClient, so you need no API key to get green).
 *
 * <p>{@code ask(question)} should do this:
 * <ol>
 *   <li><b>Retrieve</b>: {@code retriever.search(question, config.topK())}.</li>
 *   <li><b>Prompt</b>: build the system prompt with {@link Prompts#system}. Append
 *       {@code Message.user(question)} to {@link #history}.</li>
 *   <li><b>Loop</b>, at most {@code config.maxLlmCalls()} times:
 *       <ul>
 *         <li>call {@code llm.send(system, history, tools.definitions())} and add its token usage to a running total;</li>
 *         <li>append the model's reply to history with {@code Message.assistant(response.content())};</li>
 *         <li>if the reply contains no tool calls, its text is the answer: stop;</li>
 *         <li>otherwise run each tool call with {@code tools.execute(name, input)}, record the tool name, and append
 *             one {@code Message.toolResults(...)} holding a {@code Block.ToolResult} per call (same id, and pass
 *             the output's {@code isError} flag through). Then loop again so the model sees the results.</li>
 *       </ul></li>
 *   <li><b>Cap</b>: if the loop ends without an answer, use {@link Prompts#TOOL_LIMIT_MESSAGE} as the text and append
 *       it to history as an assistant message so the conversation keeps alternating user/assistant.</li>
 *   <li><b>Result</b>: citations from {@link Citations#extract}, retrieved sources (distinct, in order), tool names
 *       called, total tokens, and elapsed milliseconds.</li>
 * </ol>
 *
 * <p>Things worth noticing as you build it: the history grows every turn (so does your bill); tool errors are
 * <i>data</i> for the model, not exceptions; and the cap is what stops a runaway loop from burning money.
 */
public final class Agent implements Assistant {

    private final LlmClient llm;
    private final Retriever retriever;
    private final ToolRegistry tools;
    private final AgentConfig config;
    private final List<Message> history = new ArrayList<>();

    public Agent(LlmClient llm, Retriever retriever, ToolRegistry tools, AgentConfig config) {
        this.llm = llm;
        this.retriever = retriever;
        this.tools = tools;
        this.config = config;
    }

    @Override
    public AgentResult ask(String question) {
        long start = System.nanoTime();
        var hits = retriever.search(question, config.topK());
        var systemPrompt = Prompts.system(hits);
        history.add(Message.user(question));
        int numberOfCalls = 1;
        String finalResponse = null;
        Usage total = Usage.ZERO;
        List<String> toolsCalled = new ArrayList<>();
        while (numberOfCalls <= config.maxLlmCalls()) {
            var response = llm.send(systemPrompt, history, tools.definitions());
            System.err.println(response.stopReason() + " " + response.content());
            total = total.plus(response.usage());
            history.add(Message.assistant(response.content()));
            if (response.toolUses().isEmpty()) {
                finalResponse = response.text();
                break;
            }
            List<Block.ToolResult> toolResults = new ArrayList<>();
            response.toolUses().forEach(tool -> { 
                var toolOutput = tools.execute(tool.name(), tool.input());
                toolResults.add(new ToolResult(tool.id(), toolOutput.content(), toolOutput.isError()));
                toolsCalled.add(tool.name());
            });
            history.add(Message.toolResults(toolResults));
            numberOfCalls++;
        }
        if (finalResponse == null) {
            finalResponse = Prompts.TOOL_LIMIT_MESSAGE;
            history.add(Message.assistant(List.of(new Block.Text(finalResponse))));
        }
        long end = System.nanoTime();
        List<String> sources = new ArrayList<>(
        new LinkedHashSet<>(hits.stream().map(h -> h.chunk().source()).toList()));
        return new AgentResult(finalResponse, Citations.extract(finalResponse), toolsCalled, sources, total.inputTokens(), total.outputTokens(), (end - start)/1000000);
    }

    @Override
    public void reset() {
        history.clear();
    }
}
