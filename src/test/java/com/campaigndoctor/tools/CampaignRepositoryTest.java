package com.campaigndoctor.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CampaignRepositoryTest {

    @Test
    void loadsTheBundledData() throws IOException {
        CampaignRepository repo = CampaignRepository.load(Path.of("data"));

        assertEquals(6, repo.campaigns().size());
        assertEquals(21, repo.daily("C101").size());
        assertEquals(LocalDate.of(2026, 10, 21), repo.latestDate());
    }

    @Test
    void idsAreMatchedCaseInsensitively() throws IOException {
        CampaignRepository repo = CampaignRepository.load(Path.of("data"));

        assertTrue(repo.find("c103").isPresent());
        assertTrue(repo.find(" C103 ").isPresent());
        assertTrue(repo.find("NOPE").isEmpty());
        assertTrue(repo.find(null).isEmpty());
    }

    @Test
    void dailyRowsAreSortedOldestFirst() throws IOException {
        List<DailyRow> rows = CampaignRepository.load(Path.of("data")).daily("C106");

        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i - 1).date().isBefore(rows.get(i).date()));
        }
    }

    @Test
    void unknownCampaignHasNoRows() throws IOException {
        assertTrue(CampaignRepository.load(Path.of("data")).daily("NOPE").isEmpty());
    }
}
