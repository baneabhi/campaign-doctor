package com.campaigndoctor.tools;

import java.time.LocalDate;

/** One campaign's delivery on one day. */
public record DailyRow(String campaignId, LocalDate date, long bids, long wins, long impressions, double spendUsd) {}
