package src;

import com.jsyn.unitgen.InterpolatingDelay;

/**
 * A thread-safe wrapper for InterpolatingDelay that prevents ArrayIndexOutOfBoundsException
 * by adding additional safety checks and error handling.
 */
public class SafeInterpolatingDelay extends InterpolatingDelay {
    
    private double maxSafeDelayTime;
    private double defaultDelayTime = 0.1; // 100ms default
    private int bufferSize = 0;
    private boolean errorOccurred = false;
    
    /**
     * Creates a new SafeInterpolatingDelay with default settings.
     */
    public SafeInterpolatingDelay() {
        super();
    }
    
    /**
     * Allocates memory for the delay line with the specified number of frames.
     * Also calculates the maximum safe delay time based on the allocation size.
     * 
     * @param numFrames The number of frames to allocate
     */
    @Override
    public void allocate(int numFrames) {
        // Add extra safety margin to prevent edge cases
        int safeNumFrames = numFrames + 2000; // Add 2000 samples safety margin
        super.allocate(safeNumFrames);
        this.bufferSize = safeNumFrames;
        
        // Calculate maximum safe delay time (in seconds) based on allocation
        // Use 70% of the actual buffer size as an additional safety margin
        maxSafeDelayTime = (safeNumFrames * 0.7) / 44100.0;
        System.out.println("SafeInterpolatingDelay: Allocated " + safeNumFrames + 
                           " frames, max safe delay time: " + maxSafeDelayTime + " seconds");
    }
    
    /**
     * Override the generate method to add robust bounds checking and prevent
     * ArrayIndexOutOfBoundsException.
     */
    @Override
    public void generate(int start, int limit) {
        try {
            // Ensure we don't access the buffer if an error has occurred
            if (!errorOccurred) {
                // Synchronize access to delay parameter during generation
                double currentDelayInFrames;
                synchronized(this) {
                    // Get current delay in frames
                    currentDelayInFrames = delay.getValue() * getFrameRate();
                    
                    // Ensure delay is within safe bounds
                    if (currentDelayInFrames >= bufferSize - 100) { // Increased safety margin
                        // If delay is too large, set it to a safe value
                        System.err.println("Warning: Delay time too large " + currentDelayInFrames + 
                                           " for buffer size " + bufferSize + ", adjusting to safe value");
                        delay.set(defaultDelayTime);
                        currentDelayInFrames = defaultDelayTime * getFrameRate();
                    }
                }
                
                // Call parent generate with additional try-catch for safety
                try {
                    super.generate(start, limit);
                } catch (ArrayIndexOutOfBoundsException e) {
                    // Handle index out of bounds gracefully during super.generate
                    System.err.println("SafeInterpolatingDelay: Caught ArrayIndexOutOfBoundsException during super.generate");
                    // Output silence for this buffer
                    double[] outputs = output.getValues();
                    for (int i = start; i < limit; i++) {
                        outputs[i] = 0.0;
                    }
                    // Set error flag but don't throw - we'll recover
                    errorOccurred = true;
                }
            } else {
                // If an error has occurred, just output silence
                // Access the output array correctly
                double[] outputs = output.getValues();
                for (int i = start; i < limit; i++) {
                    outputs[i] = 0.0;
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // Handle index out of bounds gracefully
            System.err.println("SafeInterpolatingDelay: Caught ArrayIndexOutOfBoundsException in generate method");
            errorOccurred = true;
            
            // Output silence when an error occurs
            double[] outputs = output.getValues();
            for (int i = start; i < limit; i++) {
                outputs[i] = 0.0;
            }
            
            // Reset to safe defaults
            try {
                delay.set(defaultDelayTime);
            } catch (Exception ex) {
                System.err.println("Critical error in delay recovery: " + ex.getMessage());
            }
        } catch (Exception e) {
            // Handle any other exceptions
            System.err.println("SafeInterpolatingDelay: Caught exception in generate method: " + e.getMessage());
            errorOccurred = true;
            
            // Output silence when an error occurs
            double[] outputs = output.getValues();
            for (int i = start; i < limit; i++) {
                outputs[i] = 0.0;
            }
        }
    }
    
    /**
     * Sets the delay time with safety checks to prevent buffer overruns.
     * 
     * @param delayTime The delay time in seconds
     */
    public void setDelayTime(double delayTime) {
        // Reset error state when setting new delay time
        errorOccurred = false;
        
        // Ensure delay time is within safe bounds
        double safeDelayTime = Math.min(Math.max(delayTime, 0.01), maxSafeDelayTime);
        
        try {
            // Use synchronized block to ensure thread safety
            synchronized(this) {
                super.delay.set(safeDelayTime);
            }
        } catch (Exception e) {
            System.err.println("Error setting delay time: " + e.getMessage());
            try {
                // If an error occurs, set to a very safe default value
                synchronized(this) {
                    super.delay.set(defaultDelayTime);
                }
            } catch (Exception ex) {
                // Last resort error handling
                System.err.println("Critical error in delay recovery: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Thread-safe method to get the current delay time.
     * 
     * @return The current delay time in seconds
     */
    public double getDelayTime() {
        synchronized(this) {
            return super.delay.get();
        }
    }
    
    /**
     * Thread-safe method to get the maximum safe delay time.
     * 
     * @return The maximum safe delay time in seconds
     */
    public double getMaxSafeDelayTime() {
        return maxSafeDelayTime;
    }
    
    /**
     * Reset the error state and restore default settings.
     */
    public void reset() {
        errorOccurred = false;
        try {
            synchronized(this) {
                super.delay.set(defaultDelayTime);
            }
        } catch (Exception e) {
            System.err.println("Error in reset: " + e.getMessage());
        }
    }
}
