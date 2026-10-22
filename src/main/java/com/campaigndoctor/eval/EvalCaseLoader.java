package com.campaigndoctor.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Reads eval cases from a JSON array file. */
public final class EvalCaseLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EvalCaseLoader() {}

    public static List<EvalCase> load(Path file) throws IOException {
        return MAPPER.readValue(file.toFile(), new TypeReference<List<EvalCase>>() {});
    }
}
