package uaic.css.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class InputParser {

    private final ObjectMapper objectMapper;

    public InputParser() {
        this(new ObjectMapper());
    }

    public InputParser(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper must not be null");
        }
        this.objectMapper = objectMapper;
    }

    public SimulationConfig parse(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("File path must not be null or empty");
        }

        try {
            return objectMapper.readValue(new File(filePath), SimulationConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse simulation config from: " + filePath, e);
        }
    }
}
