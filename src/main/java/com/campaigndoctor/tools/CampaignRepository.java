package com.campaigndoctor.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** In-memory campaign data. Ids are matched case-insensitively because models often change case. */
public final class CampaignRepository {

    private final Map<String, Campaign> campaigns = new LinkedHashMap<>();
    private final Map<String, List<DailyRow>> daily = new HashMap<>();

    public CampaignRepository(List<Campaign> campaignList, List<DailyRow> rows) {
        for (Campaign c : campaignList) {
            campaigns.put(key(c.id()), c);
        }
        for (DailyRow row : rows) {
            daily.computeIfAbsent(key(row.campaignId()), k -> new ArrayList<>()).add(row);
        }
        for (List<DailyRow> list : daily.values()) {
            list.sort(Comparator.comparing(DailyRow::date));
        }
    }

    /** Reads campaigns.csv and daily.csv from the given directory. */
    public static CampaignRepository load(Path dir) throws IOException {
        List<Campaign> campaignList = new ArrayList<>();
        for (String[] f : readCsv(dir.resolve("campaigns.csv"))) {
            campaignList.add(new Campaign(f[0], f[1], Double.parseDouble(f[2]), LocalDate.parse(f[3]),
                    LocalDate.parse(f[4]), Double.parseDouble(f[5]), Integer.parseInt(f[6]), Long.parseLong(f[7])));
        }
        List<DailyRow> rows = new ArrayList<>();
        for (String[] f : readCsv(dir.resolve("daily.csv"))) {
            rows.add(new DailyRow(f[0], LocalDate.parse(f[1]), Long.parseLong(f[2]), Long.parseLong(f[3]),
                    Long.parseLong(f[4]), Double.parseDouble(f[5])));
        }
        return new CampaignRepository(campaignList, rows);
    }

    public List<Campaign> campaigns() {
        return List.copyOf(campaigns.values());
    }

    public Optional<Campaign> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(campaigns.get(key(id)));
    }

    /** Rows for one campaign, oldest first. Empty if the campaign is unknown or has no rows. */
    public List<DailyRow> daily(String id) {
        return List.copyOf(daily.getOrDefault(key(id), List.of()));
    }

    /** The most recent date with data across all campaigns: our "today" for pacing. */
    public LocalDate latestDate() {
        return daily.values().stream()
                .flatMap(List::stream)
                .map(DailyRow::date)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No daily data loaded"));
    }

    private static String key(String id) {
        return id.trim().toUpperCase(Locale.ROOT);
    }

    private static List<String[]> readCsv(Path file) throws IOException {
        List<String[]> out = new ArrayList<>();
        List<String> lines = Files.readAllLines(file);
        for (int i = 1; i < lines.size(); i++) { // skip header
            String line = lines.get(i).strip();
            if (!line.isEmpty()) {
                out.add(line.split(",", -1));
            }
        }
        return out;
    }
}
