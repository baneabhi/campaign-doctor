package com.campaigndoctor.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds [file.md] citations in an answer. */
public final class Citations {

    private static final Pattern CITATION = Pattern.compile("\\[([A-Za-z0-9_\\-]+\\.md)\\]");

    private Citations() {}

    /** Distinct cited file names, in order of first appearance. */
    public static List<String> extract(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text != null) {
            Matcher m = CITATION.matcher(text);
            while (m.find()) {
                found.add(m.group(1));
            }
        }
        return new ArrayList<>(found);
    }
}
