package com.campaigndoctor.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Spec for YOUR TASK #1. These fail until you implement CampaignMetrics. */
class CampaignMetricsTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 10); // 10 days, inclusive
    private static final LocalDate DAY_5 = LocalDate.of(2026, 10, 5);

    @Test
    void pacingIsOneWhenSpendingEvenly() {
        assertEquals(1.0, CampaignMetrics.pacingRatio(1000, START, END, DAY_5, 500), 1e-9);
    }

    @Test
    void pacingBelowOneWhenSpendingSlowly() {
        assertEquals(0.5, CampaignMetrics.pacingRatio(1000, START, END, DAY_5, 250), 1e-9);
    }

    @Test
    void pacingAboveOneWhenSpendingFast() {
        assertEquals(1.5, CampaignMetrics.pacingRatio(1000, START, END, DAY_5, 750), 1e-9);
    }

    @Test
    void firstDayCountsAsOneDay() {
        assertEquals(1.0, CampaignMetrics.pacingRatio(1000, START, END, START, 100), 1e-9);
    }

    @Test
    void afterTheFlightEndsExpectedSpendIsTheWholeBudget() {
        assertEquals(1.0, CampaignMetrics.pacingRatio(1000, START, END, LocalDate.of(2026, 10, 20), 1000), 1e-9);
        assertEquals(0.8, CampaignMetrics.pacingRatio(1000, START, END, LocalDate.of(2026, 10, 20), 800), 1e-9);
    }

    @Test
    void beforeTheFlightStartsPacingIsZero() {
        assertEquals(0.0, CampaignMetrics.pacingRatio(1000, START, END, LocalDate.of(2026, 9, 30), 0), 1e-9);
    }

    @Test
    void pacingRejectsAFlightThatEndsBeforeItStarts() {
        assertThrows(IllegalArgumentException.class,
                () -> CampaignMetrics.pacingRatio(1000, END, START, DAY_5, 100));
    }

    @Test
    void pacingRejectsANonPositiveBudget() {
        assertThrows(IllegalArgumentException.class,
                () -> CampaignMetrics.pacingRatio(0, START, END, DAY_5, 100));
    }

    @Test
    void winRateIsWinsOverBids() {
        assertEquals(0.25, CampaignMetrics.winRate(100, 25), 1e-9);
    }

    @Test
    void winRateIsZeroWithNoBids() {
        assertEquals(0.0, CampaignMetrics.winRate(0, 0), 1e-9);
    }

    @Test
    void winRateRejectsMoreWinsThanBids() {
        assertThrows(IllegalArgumentException.class, () -> CampaignMetrics.winRate(10, 11));
    }

    @Test
    void winRateRejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () -> CampaignMetrics.winRate(-1, 0));
    }

    @Test
    void effectiveCpmIsSpendPerThousandImpressions() {
        assertEquals(2.5, CampaignMetrics.effectiveCpm(12.5, 5000), 1e-9);
    }

    @Test
    void effectiveCpmIsZeroWithNoImpressions() {
        assertEquals(0.0, CampaignMetrics.effectiveCpm(0, 0), 1e-9);
    }

    @Test
    void effectiveCpmRejectsNegativeInputs() {
        assertThrows(IllegalArgumentException.class, () -> CampaignMetrics.effectiveCpm(-1, 100));
        assertThrows(IllegalArgumentException.class, () -> CampaignMetrics.effectiveCpm(1, -100));
    }
}
