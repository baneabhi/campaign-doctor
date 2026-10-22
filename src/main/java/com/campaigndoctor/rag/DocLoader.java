package com.campaigndoctor.rag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Loads markdown files and splits each one into chunks at its headings. */
public final class DocLoader {

    private DocLoader() {}

    public static List<Chunk> loadDir(Path dir) throws IOException {
        List<Path> files;
        try (Stream<Path> listing = Files.list(dir)) {
            files = listing.filter(p -> p.getFileName().toString().endsWith(".md")).sorted().toList();
        }
        List<Chunk> chunks = new ArrayList<>();
        for (Path file : files) {
            chunks.addAll(splitMarkdown(file.getFileName().toString(), Files.readString(file)));
        }
        return chunks;
    }

    /** One chunk per heading (any level). Text before the first heading is labelled with the file name. */
    public static List<Chunk> splitMarkdown(String source, String markdown) {
        List<Chunk> out = new ArrayList<>();
        String heading = source;
        StringBuilder body = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("#")) {
                flush(out, source, heading, body);
                heading = line.replaceFirst("^#+\\s*", "").trim();
                body.setLength(0);
            } else {
                body.append(line).append('\n');
            }
        }
        flush(out, source, heading, body);
        return out;
    }

    private static void flush(List<Chunk> out, String source, String heading, StringBuilder body) {
        String text = body.toString().strip();
        if (!text.isEmpty()) {
            out.add(new Chunk(source, heading, text));
        }
    }
}
