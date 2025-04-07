package src;

/**
 * Entry point for the synthesizer application.
 * Creates and initializes the synthesizer engine and user interface.
 * Includes a thread safety monitor to detect and recover from audio processing issues.
 */
public class Main {
    /**
     * Application entry point. Creates the synthesizer engine and user interface.
     * Sets up error handling to recover from thread safety issues.
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        // Set up uncaught exception handler to detect thread safety issues
        Thread.setDefaultUncaughtExceptionHandler(new ThreadSafetyMonitor());
        
        // Create synth engine
        SynthEngine engine = new SynthEngine();
        
        // Store engine reference for exception handler
        ThreadSafetyMonitor.setEngine(engine);
        
        // Create and show UI
        new SynthUI(engine);
        
        System.out.println("Thread safety monitoring active");
    }
    
    /**
     * Thread safety monitor that detects and recovers from audio processing exceptions.
     * This class catches uncaught exceptions, particularly ArrayIndexOutOfBoundsException,
     * and attempts to reset the delay components to recover from the error.
     */
    private static class ThreadSafetyMonitor implements Thread.UncaughtExceptionHandler {
        private static SynthEngine engine;
        
        /**
         * Sets the synthesizer engine reference for error recovery.
         * 
         * @param synthEngine The synthesizer engine instance
         */
        public static void setEngine(SynthEngine synthEngine) {
            engine = synthEngine;
        }
        
        /**
         * Handles uncaught exceptions by attempting to reset delay components.
         * 
         * @param thread The thread where the exception occurred
         * @param exception The uncaught exception
         */
        @Override
        public void uncaughtException(Thread thread, Throwable exception) {
            if (exception instanceof ArrayIndexOutOfBoundsException) {
                System.err.println("Thread safety issue detected in thread: " + thread.getName());
                System.err.println("Exception: " + exception.getMessage());
                
                // Attempt to recover by resetting delay components
                if (engine != null) {
                    System.out.println("Attempting to recover by resetting delay components...");
                    boolean recoverySuccessful = engine.resetDelayComponents();
                    
                    if (recoverySuccessful) {
                        System.out.println("Recovery successful - synthesizer should continue operating");
                    } else {
                        System.err.println("Recovery failed - synthesizer may need to be restarted");
                    }
                } else {
                    System.err.println("Cannot recover - engine reference not available");
                }
            } else {
                // For other exceptions, print the error but don't attempt recovery
                System.err.println("Uncaught exception in thread: " + thread.getName());
                System.err.println("Exception: " + exception.getMessage());
                exception.printStackTrace();
            }
        }
    }
}
