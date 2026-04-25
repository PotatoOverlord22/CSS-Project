package uaic.css;

public class Main {

    private static final String DEFAULT_INPUT_PATH = "src/main/resources/input.json";

    public static void main(String[] args) {
        String inputFilePath = args.length >= 1 ? args[0] : DEFAULT_INPUT_PATH;

        ApplicationOrchestrator orchestrator = new ApplicationOrchestrator();
        orchestrator.run(inputFilePath);
    }
}
