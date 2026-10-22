package com.campaigndoctor;

import com.campaigndoctor.agent.Agent;
import com.campaigndoctor.agent.AgentConfig;
import com.campaigndoctor.agent.AgentResult;
import com.campaigndoctor.eval.EvalCaseLoader;
import com.campaigndoctor.eval.EvalReport;
import com.campaigndoctor.eval.EvalRunner;
import com.campaigndoctor.llm.AnthropicClient;
import com.campaigndoctor.llm.LlmException;
import com.campaigndoctor.rag.Bm25Retriever;
import com.campaigndoctor.rag.DocLoader;
import com.campaigndoctor.tools.CampaignRepository;
import com.campaigndoctor.tools.ToolRegistry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Entry point.
 *   App chat                  interactive session
 *   App eval [cases.json]     run the eval suite and print a report
 * Run from the project root, or set CAMPAIGN_DOCTOR_HOME to the project directory.
 */
public final class App {

    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0] : "chat";
        Path home = Path.of(System.getenv().getOrDefault("CAMPAIGN_DOCTOR_HOME", "."));
        Agent agent = buildAgent(home);

        switch (mode) {
            case "chat" -> chat(agent);
            case "eval" -> eval(agent, home.resolve(args.length > 1 ? args[1] : "eval/cases.json"));
            default -> System.err.println("Usage: App [chat | eval [cases.json]]");
        }
    }

    private static Agent buildAgent(Path home) throws IOException {
        var chunks = DocLoader.loadDir(home.resolve("data/docs"));
        var repo = CampaignRepository.load(home.resolve("data"));
        return new Agent(AnthropicClient.fromEnv(), new Bm25Retriever(chunks), new ToolRegistry(repo),
                AgentConfig.defaults());
    }

    private static void chat(Agent agent) throws IOException {
        System.out.println("Campaign Doctor. Ask about campaign delivery. Type 'reset' to clear history, 'exit' to quit.");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("\n> ");
            System.out.flush();
            String line = in.readLine();
            if (line == null || line.strip().equalsIgnoreCase("exit")) {
                return;
            }
            if (line.isBlank()) {
                continue;
            }
            if (line.strip().equalsIgnoreCase("reset")) {
                agent.reset();
                System.out.println("(history cleared)");
                continue;
            }
            try {
                AgentResult r = agent.ask(line);
                System.out.println("\n" + r.text());
                System.out.printf("%n[sources: %s | tools: %s | tokens: %d in / %d out | %d ms]%n",
                        r.citations(), r.toolsCalled(), r.inputTokens(), r.outputTokens(), r.latencyMs());
            } catch (UnsupportedOperationException e) {
                System.out.println("Not implemented yet: " + e.getMessage());
            } catch (LlmException e) {
                System.out.println("LLM error: " + e.getMessage());
            }
        }
    }

    private static void eval(Agent agent, Path casesFile) throws IOException {
        EvalReport report = EvalRunner.run(EvalCaseLoader.load(casesFile), agent);
        System.out.println(report.format());
    }
}
