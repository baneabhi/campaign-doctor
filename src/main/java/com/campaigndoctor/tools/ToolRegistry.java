package com.campaigndoctor.tools;

import com.campaigndoctor.llm.ToolDef;
import com.campaigndoctor.rag.Hit;
import com.campaigndoctor.rag.Retriever;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The tools the model can call: their JSON schemas, and the code that runs them.
 * Output is plain text (key: value lines), which models read well and which needs no JSON library.
 */
public final class ToolRegistry {

    public static final String LIST_CAMPAIGNS = "list_campaigns";
    public static final String GET_SUMMARY = "get_campaign_summary";
    public static final String GET_DAILY = "get_daily_metrics";
    public static final String SEARCH_DOCS = "search_docs";

    /** Pacing thresholds, documented in data/docs/pacing.md. */
    static final double UNDER_PACING_BELOW = 0.90;
    static final double OVER_PACING_ABOVE = 1.10;

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 30;
    private static final int MAX_HITS = 4;

    private final CampaignRepository repo;
    private final Retriever retriever;

    public ToolRegistry(CampaignRepository repo) {
        this(repo, null);
    }

    public ToolRegistry(CampaignRepository repo, Retriever retriever) {
        this.repo = repo;
        this.retriever = retriever;
    }

    public List<ToolDef> definitions() {
        var definitions = 
            new ArrayList<>(List.of(
                new ToolDef(LIST_CAMPAIGNS,
                        "List all campaigns with id, name, budget and flight dates. Call this first when you do not "
                                + "know a campaign id, or when you need to compare campaigns.",
                        schema(Map.of(), List.of())),
                new ToolDef(GET_SUMMARY,
                        "Get delivery metrics for one campaign over its flight so far: spend, pacing ratio, bids, "
                                + "wins, win rate, effective CPM, max bid, frequency cap and audience size. All "
                                + "figures are computed in code; quote them exactly.",
                        schema(Map.of("campaign_id", prop("string", "Campaign id, for example C101")),
                                List.of("campaign_id"))),
                new ToolDef(GET_DAILY,
                        "Get day-by-day bids, wins, win rate, impressions and spend for one campaign, oldest first. "
                                + "Use it to see when delivery changed.",
                        schema(Map.of(
                                        "campaign_id", prop("string", "Campaign id, for example C101"),
                                        "last_n_days", prop("integer", "How many recent days to return, 1 to 30 (default 7)")),
                                List.of("campaign_id")))                
                ));
        if (this.retriever != null) {
            definitions.add(new ToolDef(SEARCH_DOCS,
                        "Search the reference documents on pacing, bidding and win rate, audience and frequency, "
                                + "creative and supply, and under-delivery troubleshooting. Use it whenever you need a definition, "
                                + "threshold, cause or recommended action. Do not use it for campaign figures: use the campaign tools for those. "
                                + "Returns up to 4 passages, each starting with its [file.md] source; cite that file name. "
                                + "Takes keywords, not a full question, and matches exact words, so try a different word "
                                + "(for example 'delivery' instead of 'delivering') if the first results look unrelated.",
                        schema(Map.of(
                                "query", prop("string", "Keywords for the topic to look up, e.g. 'low win rate bid price'"),
                                "hit_count", prop("integer", "How many passages to fetch, 1 to 4 (default 4")), List.of("query"))));
        }
       
        return List.copyOf(definitions);
    }

    public ToolOutput execute(String name, Map<String, Object> input) {
        if (name == null) {
            return ToolOutput.error("no tool name given");
        }
        try {
            return switch (name) {
                case LIST_CAMPAIGNS -> listCampaigns();
                case GET_SUMMARY -> summary(requireCampaign(input));
                case GET_DAILY -> daily(requireCampaign(input), intArg(input, "last_n_days", DEFAULT_DAYS, 1, MAX_DAYS));
                case SEARCH_DOCS -> searchDocs(requireQuery(input), intArg(input, "hit_count", MAX_HITS, 1, MAX_HITS));
                default -> ToolOutput.error("unknown tool '" + name + "'");
            };
        } catch (IllegalArgumentException e) {
            return ToolOutput.error(e.getMessage());
        }
    }

    private String requireQuery(Map<String, Object> input) {
        Object value = input == null ? null : input.get("query");
        if (!(value instanceof String query) || query.isBlank()) {
            throw new IllegalArgumentException("missing required argument 'query'");
        }
        return query;
    }

    private ToolOutput searchDocs(String query, int maxHits) {
        if (retriever == null) {
            return ToolOutput.error("unknown tool '" + SEARCH_DOCS + "'");
        }
        var hits = retriever.search(query, maxHits);
        if (hits.isEmpty()) {                                      // the no-hits check
            return ToolOutput.ok("No matching reference passages found for: " + query);
        }
        StringBuilder sb = new StringBuilder();
        for(Hit hit : hits) {
            sb.append(String.format(Locale.ROOT, "[%s] %s%n%s%n%n", hit.chunk().source(), hit.chunk().heading(), hit.chunk().text()));
        }
        return ToolOutput.ok(sb.toString().strip());
    }

    private ToolOutput listCampaigns() {
        StringBuilder sb = new StringBuilder();
        for (Campaign c : repo.campaigns()) {
            sb.append(String.format(Locale.ROOT, "%s | %s | budget $%,.0f | flight %s to %s%n",
                    c.id(), c.name(), c.budgetUsd(), c.startDate(), c.endDate()));
        }
        return ToolOutput.ok(sb.toString().strip());
    }

    private ToolOutput summary(Campaign c) {
        List<DailyRow> rows = repo.daily(c.id());
        long bids = rows.stream().mapToLong(DailyRow::bids).sum();
        long wins = rows.stream().mapToLong(DailyRow::wins).sum();
        long impressions = rows.stream().mapToLong(DailyRow::impressions).sum();
        double spend = rows.stream().mapToDouble(DailyRow::spendUsd).sum();

        LocalDate asOf = repo.latestDate();
        double pacing = CampaignMetrics.pacingRatio(c.budgetUsd(), c.startDate(), c.endDate(), asOf, spend);
        long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(asOf, c.endDate()));

        String text = String.format(Locale.ROOT, """
                        campaign_id: %s
                        name: %s
                        as_of: %s
                        flight: %s to %s (%d days remaining)
                        budget_usd: %.2f
                        spend_to_date_usd: %.2f
                        pacing_ratio: %.2f (%s)
                        bids: %d
                        wins: %d
                        impressions: %d
                        win_rate: %.1f%%
                        effective_cpm_usd: %.2f
                        max_bid_cpm_usd: %.2f
                        freq_cap_per_day: %d
                        audience_size: %d""",
                c.id(), c.name(), asOf, c.startDate(), c.endDate(), daysRemaining,
                c.budgetUsd(), spend, pacing, pacingLabel(pacing), bids, wins, impressions,
                CampaignMetrics.winRate(bids, wins) * 100, CampaignMetrics.effectiveCpm(spend, impressions),
                c.maxBidCpm(), c.freqCapPerDay(), c.audienceSize());
        return ToolOutput.ok(text);
    }

    private ToolOutput daily(Campaign c, int lastNDays) {
        List<DailyRow> rows = repo.daily(c.id());
        List<DailyRow> recent = rows.subList(Math.max(0, rows.size() - lastNDays), rows.size());
        StringBuilder sb = new StringBuilder("campaign_id: " + c.id() + "\n");
        for (DailyRow r : recent) {
            sb.append(String.format(Locale.ROOT,
                    "%s | bids %d | wins %d | win_rate %.1f%% | impressions %d | spend_usd %.2f%n",
                    r.date(), r.bids(), r.wins(), CampaignMetrics.winRate(r.bids(), r.wins()) * 100,
                    r.impressions(), r.spendUsd()));
        }
        return ToolOutput.ok(sb.toString().strip());
    }

    static String pacingLabel(double ratio) {
        if (ratio < UNDER_PACING_BELOW) {
            return "under-pacing";
        }
        if (ratio > OVER_PACING_ABOVE) {
            return "over-pacing";
        }
        return "on track";
    }

    private Campaign requireCampaign(Map<String, Object> input) {
        Object value = input == null ? null : input.get("campaign_id");
        if (!(value instanceof String id) || id.isBlank()) {
            throw new IllegalArgumentException("missing required argument 'campaign_id'");
        }
        return repo.find(id).orElseThrow(() -> new IllegalArgumentException(
                "unknown campaign_id '" + id + "'. Call list_campaigns to see valid ids."));
    }

    private static int intArg(Map<String, Object> input, String key, int defaultValue, int min, int max) {
        Object value = input == null ? null : input.get(key);
        int n = value instanceof Number number ? number.intValue() : defaultValue;
        return Math.max(min, Math.min(max, n));
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }
}
