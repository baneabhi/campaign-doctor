package com.campaigndoctor.tools;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * YOUR TASK #1: the three metric calculations. Tests: CampaignMetricsTest. Start here; it is the quickest win.
 *
 * <p>These are plain functions on purpose. The model never does this arithmetic: it calls a tool, the tool calls
 * these, and the model quotes the result. That keeps numbers exact and testable.
 */
public final class CampaignMetrics {

    private CampaignMetrics() {}

    /**
     * Actual spend divided by the spend we would expect by now if the budget were spent evenly over the flight.
     *
     * <ul>
     *   <li>Flight days are inclusive: 1 Oct to 10 Oct is 10 days.</li>
     *   <li>Days elapsed = days from start to asOf, inclusive, clamped to [0, flight days]. So asOf before the
     *       start gives 0 days and asOf after the end gives the full flight.</li>
     *   <li>expected = budgetUsd * daysElapsed / flightDays. If expected is 0, return 0.0.</li>
     *   <li>Throw IllegalArgumentException if the flight ends before it starts or the budget is not positive.</li>
     * </ul>
     */
    public static double pacingRatio(double budgetUsd, LocalDate flightStart, LocalDate flightEnd,
                                     LocalDate asOf, double spendToDate) {
        if (flightEnd.isBefore(flightStart)) {
            throw new IllegalArgumentException("flight ends before it starts");
        }
        if (budgetUsd <= 0) {
            throw new IllegalArgumentException("budget must be positive");
        }
        long flightDays = ChronoUnit.DAYS.between(flightStart, flightEnd) + 1;
        long elapsed = Math.max(0, Math.min(flightDays, ChronoUnit.DAYS.between(flightStart, asOf) + 1));
        double expected = budgetUsd * elapsed / flightDays;
        return expected == 0 ? 0.0 : spendToDate / expected;
    }

    /**
     * Wins divided by bids, as a fraction (0.25 means 25%). Zero bids gives 0.0.
     * Throw IllegalArgumentException if either value is negative or wins exceed bids.
     */
    public static double winRate(long bids, long wins) {        
        if (bids < 0 || wins < 0 || wins > bids) {
            throw new IllegalArgumentException("invalid bids/wins");
        }
        return bids == 0 ? 0.0 : (double) wins / bids;
    }

    /**
     * Effective cost per thousand impressions: spend / impressions * 1000. Zero impressions gives 0.0.
     * Throw IllegalArgumentException if spend or impressions is negative.
     */
    public static double effectiveCpm(double spendUsd, long impressions) {
         if (spendUsd < 0 || impressions < 0) {
            throw new IllegalArgumentException("spend and impressions must be non-negative");
        }
        return impressions == 0 ? 0.0 : spendUsd / impressions * 1000;
    }
}
