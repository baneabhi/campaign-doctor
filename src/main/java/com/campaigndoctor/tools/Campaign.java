package com.campaigndoctor.tools;

import java.time.LocalDate;

public record Campaign(String id, String name, double budgetUsd, LocalDate startDate, LocalDate endDate,
                       double maxBidCpm, int freqCapPerDay, long audienceSize) {}
