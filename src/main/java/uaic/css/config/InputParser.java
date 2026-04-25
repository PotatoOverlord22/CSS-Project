package uaic.css.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class InputParser {

    private final ObjectMapper objectMapper;

    public InputParser() {
        this.objectMapper = new ObjectMapper();
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
