package com.campaigndoctor.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campaigndoctor.llm.ToolDef;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The tests that call get_campaign_summary or get_daily_metrics also exercise CampaignMetrics,
 * so they go green once YOUR TASK #1 is done.
 */
class ToolRegistryTest {

    private static ToolRegistry registry() {
        Campaign alpha = new Campaign("C1", "Alpha Campaign", 1000, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 10), 5.0, 3, 100_000);
        Campaign beta = new Campaign("C2", "Beta Campaign", 2000, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 10), 4.0, 2, 50_000);
        List<DailyRow> rows = new ArrayList<>();
        for (int day = 1; day <= 5; day++) {
            rows.add(new DailyRow("C1", LocalDate.of(2026, 10, day), 200, 50, 50, 100.0));
        }
        return new ToolRegistry(new CampaignRepository(List.of(alpha, beta), rows));
    }

    @Test
    void offersThreeToolsWithObjectSchemas() {
        List<ToolDef> defs = registry().definitions();

        assertEquals(Set.of("list_campaigns", "get_campaign_summary", "get_daily_metrics"),
                defs.stream().map(ToolDef::name).collect(Collectors.toSet()));
        for (ToolDef def : defs) {
            assertEquals("object", def.inputSchema().get("type"));
            assertFalse(def.description().isBlank());
        }
    }

    @Test
    void listsCampaigns() {
        ToolOutput out = registry().execute("list_campaigns", Map.of());

        assertFalse(out.isError());
        assertTrue(out.content().contains("C1 | Alpha Campaign"));
        assertTrue(out.content().contains("C2 | Beta Campaign"));
    }

    @Test
    void unknownCampaignIsAnErrorThatPointsToListCampaigns() {
        ToolOutput out = registry().execute("get_campaign_summary", Map.of("campaign_id", "NOPE"));

        assertTrue(out.isError());
        assertTrue(out.content().contains("list_campaigns"));
    }

    @Test
    void missingCampaignIdIsAnError() {
        ToolOutput out = registry().execute("get_campaign_summary", Map.of());

        assertTrue(out.isError());
        assertTrue(out.content().contains("campaign_id"));
    }

    @Test
    void unknownToolIsAnError() {
        assertTrue(registry().execute("drop_tables", Map.of()).isError());
    }

    @Test
    void summaryReportsPacingAndWinRate() {
        ToolOutput out = registry().execute("get_campaign_summary", Map.of("campaign_id", "c1"));

        assertFalse(out.isError());
        assertTrue(out.content().contains("spend_to_date_usd: 500.00"), out.content());
        assertTrue(out.content().contains("pacing_ratio: 1.00 (on track)"), out.content());
        assertTrue(out.content().contains("win_rate: 25.0%"), out.content());
    }

    @Test
    void dailyMetricsReturnsTheMostRecentDays() {
        ToolOutput out = registry().execute("get_daily_metrics", Map.of("campaign_id", "C1", "last_n_days", 2));

        assertFalse(out.isError());
        assertTrue(out.content().contains("2026-10-05"), out.content());
        assertTrue(out.content().contains("2026-10-04"), out.content());
        assertFalse(out.content().contains("2026-10-03"), out.content());
    }
}
