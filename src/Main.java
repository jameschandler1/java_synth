package src;

/**
 * Entry point for the synthesizer application.
 * Creates and initializes the synthesizer engine and user interface.
 */
public class Main {
    /**
     * Application entry point. Creates the synthesizer engine and user interface.
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        SynthEngine engine = new SynthEngine();     // Create synth engine
        new SynthUI(engine);                       // Create and show UI
    }
}
