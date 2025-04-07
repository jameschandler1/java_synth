package src;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.*;

/**
 * Manages audio effects for the synthesizer.
 * This class provides multiple independent effects that can be applied to the audio signal,
 * including delay, reverb, and distortion. Each effect has its own wet/dry control
 * and can be enabled/disabled independently.
 */
public class FXProcessor {
    
    /**
     * Supported effect types for the synthesizer's effects section.
     * Each effect type provides a different sound processing characteristic.
     */
    public enum EffectType {
        DELAY("Delay"),         // Echo effect with feedback
        REVERB("Reverb"),       // Simulates room acoustics
        DISTORTION("Distortion"), // Adds harmonic saturation
        NONE("None");           // No effect (bypass)
        
        private final String displayName;
        
        EffectType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Effect components
    private SafeInterpolatingDelay delay;
    
    // New reverb components
    private SafeInterpolatingDelay reverbEarlyDelay;     // Early reflections delay
    private SafeInterpolatingDelay reverbLateDelay;      // Late reflections delay
    private FilterLowPass reverbLowpass;             // Tone control (low frequencies)
    private FilterHighPass reverbHighpass;           // Tone control (high frequencies)
    private MultiplyAdd reverbFeedback;              // Feedback for decay control
    private MultiplyAdd reverbGain;                  // Gain control for reverb
    private Add reverbMixer;                         // Wet/dry mixer
    
    private MultiplyAdd distortion;                  // Simple distortion using multiplication
    private MultiplyAdd delayFeedback;   // For delay feedback path
    
    // Effect parameters
    private LinearRamp delayTimeRamp;
    private LinearRamp delayFeedbackRamp;
    private LinearRamp delayWetDryRamp;
    
    // New reverb parameters
    private LinearRamp reverbWetDryRamp;
    private LinearRamp reverbSizeRamp;
    private LinearRamp reverbDecayRamp;
    private LinearRamp reverbGainRamp;
    
    private LinearRamp distortionWetDryRamp;
    private LinearRamp distortionAmountRamp;
    
    // Effect state
    private boolean delayEnabled = false;
    private boolean reverbEnabled = false;
    private boolean distortionEnabled = false;
    
    // Current filter type (matches SynthEngine.FilterType enum values)
    private int currentFilterType = 0; // 0=LOWPASS, 1=HIGHPASS, 2=BANDPASS
    
    // Synthesizer reference
    private Synthesizer synth;
    
    // Input/Output
    private Add inputMixer;
    private Add outputMixer;
    
    // Effect chain mixers
    private Add delayMixer;     // Mixes dry signal with delay
    private Add distortionMixer; // Mixes dry signal with distortion
    
    /**
     * Creates a new FX processor with all available effects.
     * This constructor initializes all effect components, configures their default settings,
     * and connects them in the signal path.
     * 
     * @param synth The JSyn synthesizer instance to add components to
     */
    public FXProcessor(Synthesizer synth) {
        this.synth = synth;
        createComponents(synth);
        configureDefaults();
        connectComponents();
        
        // Initialize reverb settings based on the current filter type
        updateReverbSettings();
    }
    
    /**
     * Creates all effect components and adds them to the synthesizer.
     * 
     * @param synth The JSyn synthesizer instance
     */
    private void createComponents(Synthesizer synth) {
        // Create mixers for input and output
        inputMixer = new Add();
        outputMixer = new Add();
        
        // Create effect chain mixers
        delayMixer = new Add();
        reverbMixer = new Add();
        distortionMixer = new Add();
        
        // Create effect units with our SafeInterpolatingDelay wrapper to prevent index out of bounds errors
        delay = new SafeInterpolatingDelay();
        delay.allocate(88200 * 3); // 6 seconds at 44.1kHz (triple the original size)
        
        // Create a new, more effective reverb implementation
        // Early reflections (shorter delay)
        reverbEarlyDelay = new SafeInterpolatingDelay();
        reverbEarlyDelay.allocate(22050 * 3); // 1.5 seconds at 44.1kHz (triple the original size)
        
        // Late reflections (longer delay)
        reverbLateDelay = new SafeInterpolatingDelay();
        reverbLateDelay.allocate(44100 * 3); // 3 seconds at 44.1kHz (triple the original size)
        
        // Tone control filters
        reverbLowpass = new FilterLowPass();
        reverbHighpass = new FilterHighPass();
        
        // Feedback for decay control
        reverbFeedback = new MultiplyAdd();
        
        // Gain control
        reverbGain = new MultiplyAdd();
        
        // Wet/dry mixer
        reverbMixer = new Add();
        
        distortion = new MultiplyAdd();
        
        // Create parameter control ramps for smooth transitions
        delayTimeRamp = new LinearRamp();
        delayFeedbackRamp = new LinearRamp();
        delayWetDryRamp = new LinearRamp();
        
        reverbWetDryRamp = new LinearRamp();
        reverbSizeRamp = new LinearRamp();
        reverbDecayRamp = new LinearRamp();
        reverbGainRamp = new LinearRamp();
        
        distortionWetDryRamp = new LinearRamp();
        distortionAmountRamp = new LinearRamp();
        
        // Add all components to the synthesizer
        synth.add(inputMixer);
        synth.add(outputMixer);
        synth.add(delayMixer);
        synth.add(reverbMixer);
        synth.add(distortionMixer);
        
        synth.add(delay);
        synth.add(reverbEarlyDelay);
        synth.add(reverbLateDelay);
        synth.add(reverbLowpass);
        synth.add(reverbHighpass);
        synth.add(reverbFeedback);
        synth.add(reverbGain);
        synth.add(reverbMixer);
        synth.add(distortion);
        
        synth.add(delayTimeRamp);
        synth.add(delayFeedbackRamp);
        synth.add(delayWetDryRamp);
        
        synth.add(reverbWetDryRamp);
        synth.add(reverbSizeRamp);
        synth.add(reverbDecayRamp);
        synth.add(reverbGainRamp);
        
        synth.add(distortionWetDryRamp);
        synth.add(distortionAmountRamp);
    }
    
    /**
     * Sets default values for all effect parameters.
     * Uses consistent fixed ramp times for thread safety.
     */
    private void configureDefaults() {
        // Configure parameter smoothing with fixed ramp times for thread safety
        // Using consistent values across all parameters to prevent audio artifacts
        // and ensure thread safety when changing parameters during playback
        double smoothingTime = 0.1; // 100ms - slightly longer for better stability
        
        // Set fixed ramp times for all parameters
        delayTimeRamp.time.set(smoothingTime);
        delayFeedbackRamp.time.set(smoothingTime);
        delayWetDryRamp.time.set(smoothingTime);
        reverbWetDryRamp.time.set(smoothingTime);
        reverbSizeRamp.time.set(smoothingTime);
        reverbDecayRamp.time.set(smoothingTime);
        reverbGainRamp.time.set(smoothingTime);
        distortionWetDryRamp.time.set(smoothingTime);
        distortionAmountRamp.time.set(smoothingTime);
        
        // Set default delay parameters
        delayTimeRamp.input.set(0.3); // 300ms delay
        delay.delay.set(0.3);
        delayFeedbackRamp.input.set(0.4); // 40% feedback
        
        // Set default reverb parameters for the new implementation
        reverbSizeRamp.input.set(0.5);  // Medium room size
        reverbDecayRamp.input.set(0.6);  // Medium decay time
        reverbGainRamp.input.set(0.8);   // 80% gain
        
        // Configure reverb delay lines
        reverbEarlyDelay.delay.set(0.03); // 30ms early reflections
        reverbLateDelay.delay.set(0.07);  // 70ms late reflections
        
        // Configure reverb filters
        reverbLowpass.frequency.set(5000);  // 5kHz lowpass filter cutoff
        reverbHighpass.frequency.set(100);  // 100Hz highpass filter cutoff
        
        // Configure feedback for decay
        reverbFeedback.inputB.set(0.6);  // 60% feedback for reverb decay
        reverbFeedback.inputC.set(0.0);  // No offset
        
        // Configure gain
        reverbGain.inputB.set(0.8);  // 80% gain
        reverbGain.inputC.set(0.0);  // No offset
        
        // Set default distortion parameters (using multiply for distortion)
        distortion.inputB.set(2.0); // Gain factor for distortion
        distortion.inputC.set(0.0); // No offset
        
        // Set default wet/dry mix for all effects
        delayWetDryRamp.input.set(0.5); // 50% wet/dry for delay
        reverbWetDryRamp.input.set(0.5); // 50% wet/dry for reverb
        distortionWetDryRamp.input.set(0.5); // 50% wet/dry for distortion
    }
    
    /**
     * Connects all effect components in the signal path.
     */
    private void connectComponents() {
        // Connect input to all effect chains
        inputMixer.output.connect(delay.input);
        inputMixer.output.connect(reverbEarlyDelay.input); // Connect to early reflections
        inputMixer.output.connect(distortion.inputA);
        
        // Connect dry signal to all effect mixers
        inputMixer.output.connect(delayMixer.inputA);
        inputMixer.output.connect(reverbMixer.inputA); // Dry signal to reverb mixer
        inputMixer.output.connect(distortionMixer.inputA);
        
        // Connect new reverb components
        
        // Early reflections path
        reverbEarlyDelay.output.connect(reverbLowpass.input); // Early reflections through lowpass
        
        // Late reflections path
        reverbEarlyDelay.output.connect(reverbLateDelay.input); // Feed early into late reflections
        reverbLateDelay.output.connect(reverbHighpass.input); // Late reflections through highpass
        
        // Feedback path for decay
        reverbLateDelay.output.connect(reverbFeedback.inputA); // Create feedback loop
        reverbFeedback.output.connect(reverbLateDelay.input); // Connect feedback to late delay
        
        // Mix early and late reflections and apply gain
        // Create a mix of both early and late reflections
        reverbLowpass.output.connect(reverbGain.inputA); // Early reflections to gain
        reverbHighpass.output.connect(reverbGain.inputA); // Late reflections to gain
        
        // Connect gain control to reverb output
        reverbGain.output.connect(reverbMixer.inputB); // Wet signal to reverb mixer
        
        // Connect distortion output
        distortion.output.connect(distortionMixer.inputB);
        
        // Connect parameter control ramps
        delayTimeRamp.output.connect(delay.delay);
        
        // Set up feedback path for delay using a multiply unit
        delayFeedback = new MultiplyAdd();
        synth.add(delayFeedback);
        
        // Connect delay output to feedback multiplier
        delay.output.connect(delayFeedback.inputA);
        delayFeedbackRamp.output.connect(delayFeedback.inputB);
        delayFeedback.inputC.set(0.0); // No offset
        
        // Connect feedback multiplier to delay input
        delayFeedback.output.connect(delay.input);
        
        // Connect delay output to delay mixer's wet input
        delay.output.connect(delayMixer.inputB);
        
        // Initialize effect routing
        updateEffectRouting();
        // Initially no connections to output mixer
        
        // Default to dry signal path
        inputMixer.output.connect(outputMixer.inputA);
    }
    
    /**
     * Connects an input signal to the effects processor.
     * 
     * @param source The source unit to connect
     */
    public void connectInput(UnitOutputPort source) {
        source.connect(inputMixer.inputA);
    }
    
    /**
     * Updates the current filter type setting.
     * This affects how the reverb effect is configured.
     * 
     * @param filterType The current filter type from SynthEngine (0=LOWPASS, 1=HIGHPASS, 2=BANDPASS)
     */
    public void setCurrentFilterType(int filterType) {
        this.currentFilterType = filterType;
        updateReverbSettings();
    }
    
    /**
     * Updates reverb settings based on the current filter type.
     * Different filter types work better with different reverb configurations.
     * This method optimizes the reverb character for each filter type.
     */
    private void updateReverbSettings() {
        switch (currentFilterType) {
            case 0: // LOWPASS
                // For lowpass, emphasize higher frequencies in the reverb to add brightness
                // Adjust filters for brighter reverb to complement lowpass filtering
                reverbLowpass.frequency.set(4000);  // Higher lowpass cutoff
                reverbHighpass.frequency.set(250);   // Higher highpass cutoff
                
                // Adjust delay line characteristics
                reverbEarlyDelay.delay.set(0.035);  // 35ms delay
                reverbLateDelay.delay.set(0.055);   // 55ms delay
                
                // Adjust feedback for medium decay
                reverbFeedback.inputB.set(0.65);    // 65% feedback
                break;
                
            case 1: // HIGHPASS
                // For highpass, emphasize lower frequencies in the reverb to add warmth
                // Adjust filters for warmer reverb to complement highpass filtering
                reverbLowpass.frequency.set(2000);  // Lower lowpass cutoff
                reverbHighpass.frequency.set(150);   // Lower highpass cutoff
                
                // Adjust delay line characteristics
                reverbEarlyDelay.delay.set(0.04);   // 40ms delay
                reverbLateDelay.delay.set(0.07);    // 70ms delay
                
                // Adjust feedback for longer decay
                reverbFeedback.inputB.set(0.75);    // 75% feedback
                break;
                
            case 2: // BANDPASS
                // For bandpass, use balanced frequencies in the reverb
                // Adjust filters for balanced reverb to complement bandpass filtering
                reverbLowpass.frequency.set(3000);  // Mid lowpass cutoff
                reverbHighpass.frequency.set(200);   // Mid highpass cutoff
                
                // Adjust delay line characteristics
                reverbEarlyDelay.delay.set(0.03);   // 30ms delay
                reverbLateDelay.delay.set(0.06);    // 60ms delay
                
                // Adjust feedback for medium decay
                reverbFeedback.inputB.set(0.7);     // 70% feedback
                break;
        }
        
        // Apply the current size setting to ensure consistency
        double currentSize = reverbSizeRamp.output.getValue();
        setReverbSize(currentSize);
    }
    
    /**
     * Connects the effects processor output to a destination.
     * 
     * @param destination The destination input port
     */
    public void connectOutput(UnitInputPort destination) {
        outputMixer.output.connect(destination);
    }
    
    /**
     * Enables or disables the delay effect.
     * 
     * @param enabled Whether the delay effect should be enabled
     */
    public void enableDelayEffect(boolean enabled) {
        delayEnabled = enabled;
        updateEffectRouting();
    }
    
    /**
     * Enables or disables the reverb effect.
     * 
     * @param enabled Whether the reverb effect should be enabled
     */
    public void enableReverbEffect(boolean enabled) {
        reverbEnabled = enabled;
        updateEffectRouting();
    }
    
    /**
     * Enables or disables the distortion effect.
     * 
     * @param enabled Whether the distortion effect should be enabled
     */
    public void enableDistortionEffect(boolean enabled) {
        distortionEnabled = enabled;
        updateEffectRouting();
    }
    
    /**
     * Updates the effect routing based on which effects are enabled.
     * Multiple effects can be enabled simultaneously.
     */
    private void updateEffectRouting() {
        // First disconnect all effect chains from the output mixer
        delayMixer.output.disconnect(outputMixer.inputA);
        reverbMixer.output.disconnect(outputMixer.inputA);
        distortionMixer.output.disconnect(outputMixer.inputA);
        inputMixer.output.disconnect(outputMixer.inputA);
        
        // Disconnect any cross-connections between effect mixers
        delayMixer.output.disconnect(reverbMixer.inputA);
        delayMixer.output.disconnect(distortionMixer.inputA);
        reverbMixer.output.disconnect(distortionMixer.inputA);
        
        // Start with a clean slate - always ensure the dry signal is connected
        // to all effect mixers (this is crucial for maintaining signal flow)
        inputMixer.output.connect(delayMixer.inputA);
        inputMixer.output.connect(reverbMixer.inputA);
        inputMixer.output.connect(distortionMixer.inputA);
        
        // We're connecting all effects directly to the output mixer
        
        // Keep effects in parallel rather than chaining them
        // This ensures each effect processes the dry signal independently
        
        // Keep all effects completely separate and parallel
        // Don't chain any effects together
        
        // Create a custom mixer for combining all enabled effects
        // First disconnect all previous connections to the output mixer
        outputMixer.inputA.disconnectAll();
        
        // Connect the dry signal if no effects are enabled
        if (!delayEnabled && !reverbEnabled && !distortionEnabled) {
            inputMixer.output.connect(outputMixer.inputA);
            return;
        }
        
        // Connect each enabled effect to the output mixer
        // Each effect has its own wet/dry control for balance
        
        // Connect each enabled effect
        if (delayEnabled) {
            delayMixer.output.connect(outputMixer.inputA);
        }
        
        if (reverbEnabled) {
            reverbMixer.output.connect(outputMixer.inputA);
        }
        
        if (distortionEnabled) {
            distortionMixer.output.connect(outputMixer.inputA);
        }
        
        // All effects are now directly connected to the output mixer
    }
    
    /**
     * Sets the delay time with parameter smoothing.
     * Includes thread-safe measures to prevent buffer overruns and audio artifacts.
     * Uses synchronized blocks to ensure thread safety during parameter updates.
     * 
     * @param seconds Delay time in seconds (0.0-2.0)
     */
    public void setDelayTime(double seconds) {
        // Calculate maximum safe delay time based on buffer allocation
        // Buffer is now 88200*3 + 10000 samples at 44.1kHz, so max is ~6.0 seconds
        // Use a very conservative safety margin to prevent edge cases
        double maxSafeDelay = 1.5; // Extremely conservative maximum safe delay time in seconds
        
        // Clamp values to safe range with a minimum value to prevent very short delays
        double safeSeconds = Math.min(Math.max(seconds, 0.02), maxSafeDelay);
        
        // Only update if the change is significant to reduce unnecessary updates
        double currentTime = delayTimeRamp.input.get();
        if (Math.abs(currentTime - safeSeconds) > 0.001) {
            // Synchronize access to the delay components to prevent concurrent modification
            synchronized(delayTimeRamp) {
                // Use a fixed ramp time for thread safety
                // Don't dynamically change the ramp time while audio is processing
                // as this can cause thread safety issues
                
                // Update the delay time with smooth transition
                delayTimeRamp.input.set(safeSeconds);
                
                try {
                    // Also update the delay line directly with the same value for consistency
                    // This is synchronized with the ramp to ensure thread safety
                    synchronized(delay) {
                        // Double-check the safety bounds before setting
                        if (safeSeconds <= maxSafeDelay) {
                            delay.delay.set(safeSeconds);
                        } else {
                            // If somehow we got an unsafe value, use a very safe default
                            delay.delay.set(0.5);
                        }
                    }
                } catch (Exception e) {
                    // If an error occurs, set to a safe default value
                    System.err.println("Error setting delay time: " + e.getMessage());
                    try {
                        synchronized(delay) {
                            delay.delay.set(0.3); // 300ms is a safe default
                        }
                    } catch (Exception ex) {
                        // Last resort error handling
                        System.err.println("Critical error in delay recovery: " + ex.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * Sets the delay feedback amount with parameter smoothing.
     * Includes thread-safe measures to prevent audio artifacts.
     * 
     * @param amount Feedback amount (0.0-0.95)
     */
    public void setFeedback(double amount) {
        // Limit feedback to prevent runaway feedback
        double safeAmount = Math.min(Math.max(amount, 0.0), 0.95);
        
        // Only update if the change is significant
        double currentFeedback = delayFeedbackRamp.input.get();
        if (Math.abs(currentFeedback - safeAmount) > 0.001) {
            // Use a fixed ramp time for thread safety
            // Don't dynamically change the ramp time while audio is processing
            
            // Update the feedback amount with smooth transition
            delayFeedbackRamp.input.set(safeAmount);
        }
    }
    
    /**
     * Reset all delay components to safe default values.
     * This can be called if audio artifacts or errors are detected.
     */
    public void resetDelayComponents() {
        try {
            // Reset all delay components to safe defaults
            delay.reset();
            reverbEarlyDelay.reset();
            reverbLateDelay.reset();
            
            // Set all delay parameters to safe values
            setDelayTime(0.3); // 300ms is a safe default
            setFeedback(0.3);
            setDelayWetDryMix(0.3);
            
            // Reset reverb parameters
            setReverbSize(0.3);
            setReverbDecay(0.3);
            setReverbWetDryMix(0.3);
            
            System.out.println("All delay components reset to safe defaults");
        } catch (Exception e) {
            System.err.println("Error resetting delay components: " + e.getMessage());
        }
    }
    
    /**
     * Sets the wet/dry mix for the delay effect.
     * Includes thread-safe measures to prevent audio artifacts.
     * 
     * @param mix Mix amount (0.0=dry, 1.0=wet)
     */
    public void setDelayWetDryMix(double mix) {
        // Clamp the mix value to a valid range
        double safeMix = Math.min(Math.max(mix, 0.0), 1.0);
        
        // Only update if the change is significant
        double currentMix = delayWetDryRamp.input.get();
        if (Math.abs(currentMix - safeMix) > 0.001) {
            // Use a fixed ramp time for thread safety
            // Don't dynamically change the ramp time while audio is processing
            
            // Update the mix parameter with smooth transition
            delayWetDryRamp.input.set(safeMix);
            
            try {
                // Calculate the wet/dry balance
                double dryAmount = 1.0 - safeMix;
                double wetAmount = safeMix;
                
                // Set the mixer inputs for the delay effect gradually
                // This helps prevent clicks and pops when changing the mix
                delayMixer.inputA.set(dryAmount);
                delayMixer.inputB.set(wetAmount);
            } catch (Exception e) {
                // If an error occurs, set to safe default values
                delayMixer.inputA.set(0.5);
                delayMixer.inputB.set(0.5);
            }
        }
    }
    
    /**
     * Sets the wet/dry mix for the reverb effect.
     * Includes thread-safe measures to prevent audio artifacts.
     * 
     * @param mix Mix amount (0.0=dry, 1.0=wet)
     */
    public void setReverbWetDryMix(double mix) {
        // Clamp the mix value to a valid range
        double safeMix = Math.min(Math.max(mix, 0.0), 1.0);
        
        // Only update if the change is significant
        double currentMix = reverbWetDryRamp.input.get();
        if (Math.abs(currentMix - safeMix) > 0.001) {
            // Use a fixed ramp time for thread safety
            // Don't dynamically change the ramp time while audio is processing
            
            // Update the mix parameter with smooth transition
            reverbWetDryRamp.input.set(safeMix);
            
            try {
                // Calculate the wet/dry balance
                double dryAmount = 1.0 - safeMix;
                double wetAmount = safeMix * 1.5; // Boost the wet signal for more pronounced reverb
                
                // Set the mixer inputs for the reverb effect gradually
                reverbMixer.inputA.set(dryAmount);
                reverbMixer.inputB.set(wetAmount);
            } catch (Exception e) {
                // If an error occurs, set to safe default values
                reverbMixer.inputA.set(0.5);
                reverbMixer.inputB.set(0.5);
            }
        }
    }
    
    /**
     * Sets the wet/dry mix for the distortion effect.
     * Includes thread-safe measures to prevent audio artifacts.
     * 
     * @param mix Mix amount (0.0=dry, 1.0=wet)
     */
    public void setDistortionWetDryMix(double mix) {
        // Clamp the mix value to a valid range
        double safeMix = Math.min(Math.max(mix, 0.0), 1.0);
        
        // Only update if the change is significant
        double currentMix = distortionWetDryRamp.input.get();
        if (Math.abs(currentMix - safeMix) > 0.001) {
            // Use a fixed ramp time for thread safety
            // Don't dynamically change the ramp time while audio is processing
            
            // Update the mix parameter with smooth transition
            distortionWetDryRamp.input.set(safeMix);
            
            try {
                // Calculate the wet/dry balance
                double dryAmount = 1.0 - safeMix;
                double wetAmount = safeMix;
                
                // Set the mixer inputs for the distortion effect gradually
                distortionMixer.inputA.set(dryAmount);
                distortionMixer.inputB.set(wetAmount);
            } catch (Exception e) {
                // If an error occurs, set to safe default values
                distortionMixer.inputA.set(0.5);
                distortionMixer.inputB.set(0.5);
            }
        }
    }
    
    /**
     * Sets the distortion amount.
     * Includes thread-safe measures to prevent audio artifacts.
     * Uses logarithmic scaling for more natural control.
     * 
     * @param amount Distortion amount (0.0-1.0)
     */
    public void setDistortion(double amount) {
        // Clamp the amount parameter to 0.0-1.0 range
        double safeAmount = Math.min(Math.max(amount, 0.0), 1.0);
        
        // Only update if the change is significant
        double currentAmount = distortionAmountRamp.input.get();
        if (Math.abs(currentAmount - safeAmount) > 0.001) {
            // Use a fixed ramp time for thread safety
            // Don't dynamically change the ramp time while audio is processing
            
            // Apply logarithmic scaling for more natural control
            // This provides finer control at lower distortion amounts
            double logAmount;
            if (safeAmount > 0) {
                // Use a logarithmic curve that gives finer control at lower settings
                logAmount = Math.pow(safeAmount, 2.5);
            } else {
                logAmount = 0.0; // Handle amount = 0 case
            }
            
            // Update the distortion parameter with smooth transition
            distortionAmountRamp.input.set(logAmount);
            
            try {
                // More gain for more distortion with logarithmic scaling
                // This creates a more natural distortion curve where small changes at low amounts
                // are more noticeable than the same changes at high amounts
                double gain = 1.0 + (logAmount * 5.0); // 1.0-6.0 range
                distortion.inputB.set(gain);
            } catch (Exception e) {
                // If an error occurs, set to a safe default value
                distortion.inputB.set(2.0);
            }
        }
    }
    
    /**
     * Sets the reverb size (simulated room size).
     * Adjusts delay times and filter settings to simulate different room sizes.
     * Includes thread-safe measures to prevent audio artifacts and buffer overruns.
     * 
     * @param size Room size (0.0-1.0)
     */
    public void setReverbSize(double size) {
        // Clamp the size parameter to 0.0-1.0 range
        double safeSize = Math.min(Math.max(size, 0.0), 1.0);
        
        // Only update if the change is significant
        double currentSize = reverbSizeRamp.input.get();
        if (Math.abs(currentSize - safeSize) > 0.001) {
            // Use a fixed ramp time for thread safety
            // Don't dynamically change the ramp time while audio is processing
            
            // Synchronize access to the reverb components to prevent concurrent modification
            synchronized(reverbSizeRamp) {
                // Update the size parameter with smooth transition
                reverbSizeRamp.input.set(safeSize);
                
                try {
                    // Calculate delay times with strict bounds checking
                    // Early reflections (30ms to 80ms)
                    // Make sure we stay well within the allocated buffer size (22050+100 samples = ~0.5s)
                    double maxEarlyDelay = 0.45; // Maximum safe delay time in seconds
                    double earlyDelayTime = Math.min(0.03 + (safeSize * 0.05), maxEarlyDelay);
                    
                    // Synchronize access to the early delay component
                    synchronized(reverbEarlyDelay) {
                        reverbEarlyDelay.delay.set(earlyDelayTime);
                    }
                    
                    // Late reflections (50ms to 150ms)
                    // Make sure we stay well within the allocated buffer size (44100+100 samples = ~1s)
                    double maxLateDelay = 0.9; // Maximum safe delay time in seconds
                    double lateDelayTime = Math.min(0.05 + (safeSize * 0.1), maxLateDelay);
                    
                    // Synchronize access to the late delay component
                    synchronized(reverbLateDelay) {
                        reverbLateDelay.delay.set(lateDelayTime);
                    }
                    
                    // Adjust filter frequencies based on room size
                    // Larger rooms have more low frequency content (damping of high frequencies)
                    double lowpassFreq = 5000 - (safeSize * 2000); // 5000Hz to 3000Hz
                    reverbLowpass.frequency.set(lowpassFreq);
                    
                    // Smaller rooms have less low frequency buildup
                    double highpassFreq = 100 + (safeSize * 50); // 100Hz to 150Hz
                    reverbHighpass.frequency.set(highpassFreq);
                } catch (Exception e) {
                    // If an error occurs, set to safe default values
                    System.err.println("Error setting reverb size: " + e.getMessage());
                    
                    try {
                        synchronized(reverbEarlyDelay) {
                            reverbEarlyDelay.delay.set(0.03);
                        }
                        synchronized(reverbLateDelay) {
                            reverbLateDelay.delay.set(0.05);
                        }
                        reverbLowpass.frequency.set(4000);
                        reverbHighpass.frequency.set(120);
                    } catch (Exception ex) {
                        // Last resort error handling
                        System.err.println("Critical error in reverb recovery: " + ex.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * Sets the reverb decay time.
     * Controls how long the reverb tail persists.
     * Includes thread-safe measures to prevent audio artifacts.
     * Uses synchronized blocks to ensure thread safety during parameter updates.
     * 
     * @param decay Decay amount (0.0-1.0)
     */
    public void setReverbDecay(double decay) {
        // Clamp the decay parameter to 0.0-1.0 range
        double safeDecay = Math.min(Math.max(decay, 0.0), 1.0);
        
        // Only update if the change is significant
        double currentDecay = reverbDecayRamp.input.get();
        if (Math.abs(currentDecay - safeDecay) > 0.001) {
            // Synchronize access to the reverb components to prevent concurrent modification
            synchronized(reverbDecayRamp) {
                // Use a fixed ramp time for thread safety
                // Don't dynamically change the ramp time while audio is processing
                
                // Update the decay parameter with smooth transition
                reverbDecayRamp.input.set(safeDecay);
                
                try {
                    // Map decay to feedback amount (0.4 to 0.9)
                    // Higher feedback = longer decay time
                    // Limit the maximum feedback to prevent buffer overruns
                    double maxFeedback = 0.85; // Limit maximum feedback to prevent excessive delay times
                    double feedback = Math.min(0.4 + (safeDecay * 0.5), maxFeedback);
                    
                    // Synchronize access to the feedback component
                    synchronized(reverbFeedback) {
                        reverbFeedback.inputB.set(feedback);
                    }
                } catch (Exception e) {
                    // If an error occurs, set to a safe default value
                    System.err.println("Error setting reverb decay: " + e.getMessage());
                    try {
                        synchronized(reverbFeedback) {
                            reverbFeedback.inputB.set(0.6);
                        }
                    } catch (Exception ex) {
                        // Last resort error handling
                        System.err.println("Critical error in reverb decay recovery: " + ex.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * Sets the reverb gain amount.
     * Controls the overall level of the reverb effect.
     * Includes thread-safe measures to prevent audio artifacts.
     * 
     * @param gain Gain amount (0.0-1.0)
     */
    public void setReverbGain(double gain) {
        // Clamp the gain parameter to 0.0-1.0 range
        double safeGain = Math.min(Math.max(gain, 0.0), 1.0);
        
        // Only update if the change is significant
        double currentGain = reverbGainRamp.input.get();
        if (Math.abs(currentGain - safeGain) > 0.001) {
            // Use a fixed ramp time for thread safety
            // Don't dynamically change the ramp time while audio is processing
            
            // Update the gain parameter with smooth transition
            reverbGainRamp.input.set(safeGain);
            
            // Map gain to actual gain multiplier (0.5 to 1.5)
            // Use a try-catch block to handle any potential errors
            try {
                double gainMultiplier = 0.5 + safeGain;
                reverbGain.inputB.set(gainMultiplier);
            } catch (Exception e) {
                // If an error occurs, set to a safe default value
                reverbGain.inputB.set(1.0);
            }
        }
    }
}
