package src;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the synthesizer application.
 * Creates and initializes the synthesizer engine and user interface.
 * Includes a thread safety monitor to detect and recover from audio processing issues.
 * Can restart the application when recovery fails.
 */
public class Main {
    /**
     * Application entry point. Creates the synthesizer engine and user interface.
     * Sets up error handling to recover from thread safety issues.
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        // Check if this is a restart after a crash
        for (String arg : args) {
            if (arg.equals("--restart")) {
                System.out.println("Application restarted after a crash recovery");
                break;
            }
        }
        
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
     * If recovery fails, it can restart the entire application.
     */
    private static class ThreadSafetyMonitor implements Thread.UncaughtExceptionHandler {
        private static SynthEngine engine;
        private static final int MAX_RESTART_ATTEMPTS = 3;
        private static int restartAttempts = 0;
        private static final String RESTART_FLAG = "--restart";
        
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
         * If recovery fails, attempts to restart the application.
         * 
         * @param thread The thread where the exception occurred
         * @param exception The uncaught exception
         */
        @Override
        public void uncaughtException(Thread thread, Throwable exception) {
            System.err.println("Thread safety issue detected in thread: " + thread.getName());
            System.err.println("Exception: " + exception.getMessage());
            exception.printStackTrace();
            
            boolean shouldRestart = false;
            
            if (exception instanceof ArrayIndexOutOfBoundsException) {
                // Attempt to recover by resetting delay components
                if (engine != null) {
                    System.out.println("Attempting to recover by resetting delay components...");
                    boolean recoverySuccessful = engine.resetDelayComponents();
                    
                    if (recoverySuccessful) {
                        System.out.println("Recovery successful - synthesizer should continue operating");
                        return; // No need to restart if recovery was successful
                    } else {
                        System.err.println("Recovery failed - attempting to restart the application");
                        shouldRestart = true;
                    }
                } else {
                    System.err.println("Cannot recover - engine reference not available");
                    shouldRestart = true;
                }
            } else {
                // For other severe exceptions, attempt to restart
                System.err.println("Severe exception detected - attempting to restart the application");
                shouldRestart = true;
            }
            
            if (shouldRestart) {
                restartApplication();
            }
        }
        
        /**
         * Restarts the application with the same JVM arguments.
         * Limits the number of restart attempts to prevent infinite restart loops.
         */
        private void restartApplication() {
            if (restartAttempts >= MAX_RESTART_ATTEMPTS) {
                System.err.println("Maximum restart attempts reached. Please restart the application manually.");
                System.exit(1);
                return;
            }
            
            restartAttempts++;
            System.out.println("Restarting application (attempt " + restartAttempts + "/" + MAX_RESTART_ATTEMPTS + ")");
            
            try {
                // Get the path to the Java executable
                String javaHome = System.getProperty("java.home");
                String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
                
                // Get current JVM arguments
                List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
                
                // Build command: java [JVM args] -cp [classpath] [MainClass] --restart
                List<String> command = new ArrayList<>();
                command.add(javaBin);
                command.addAll(inputArguments);
                
                // Add classpath
                String classpath = System.getProperty("java.class.path");
                command.add("-cp");
                command.add(classpath);
                
                // Add main class
                command.add(Main.class.getName());
                
                // Add restart flag
                command.add(RESTART_FLAG);
                
                // Create and start a new process
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.inheritIO(); // Redirect IO to parent process
                builder.start(); // Start the process but don't store the reference since we don't need it
                
                // Exit current JVM
                System.exit(0);
                
            } catch (Exception e) {
                System.err.println("Failed to restart application: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
    }
}
