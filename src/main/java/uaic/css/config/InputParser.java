package uaic.css.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class InputParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static SimulationConfig parse(String filePath) {
        assert filePath != null && !filePath.isEmpty() : "File path must not be null or empty";

        try {
            return objectMapper.readValue(new File(filePath), SimulationConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse simulation config from: " + filePath, e);
        }
    }
}
