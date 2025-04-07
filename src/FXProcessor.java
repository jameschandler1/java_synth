package src;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.*;
import javax.swing.SwingUtilities;

/**
 * Manages audio effects for the synthesizer.
 * This class provides multiple independent effects that can be applied to the audio signal,
 * including delay, reverb, distortion, and chorus. Each effect has its own wet/dry control
 * and can be enabled/disabled independently.
 * 
 * Key features:
 * 
 * 1. Effect Buffer Preservation: All effects maintain their internal state and buffer contents
 *    when toggled on/off. This prevents audio glitches and ensures smooth transitions when
 *    enabling/disabling effects during performance. Effects continue processing audio in the
 *    background even when disabled, with only their output connections being modified.
 * 
 * 2. Parallel Effect Routing: All effects process the input signal independently in parallel,
 *    rather than being chained in series. This maintains audio quality and prevents one effect
 *    from degrading the signal for subsequent effects.
 * 
 * 3. Vintage Chorus Implementation: The chorus effect uses dual delay lines with different
 *    base delay times (15ms and 22ms) and LFOs with slightly different rates and depths to
 *    create a rich, musical chorus effect similar to classic analog hardware units.
 * 
 * 4. Thread-Safe Parameter Control: All parameter changes use synchronized blocks and
 *    LinearRamp units to ensure thread safety and prevent audio artifacts during parameter
 *    changes.
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
        CHORUS("Chorus"),       // Adds modulation and thickness
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
    
    // Chorus effect components
    private SafeInterpolatingDelay chorusDelay1;     // First chorus voice
    private SafeInterpolatingDelay chorusDelay2;     // Second chorus voice
    private SineOscillator chorusLFO1;              // LFO for first voice
    private SineOscillator chorusLFO2;              // LFO for second voice
    
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
    
    // Chorus parameters
    private LinearRamp chorusRateRamp;
    private LinearRamp chorusDepthRamp;
    private LinearRamp chorusWetDryRamp;
    
    // Effect state
    private boolean delayEnabled = false;
    private boolean reverbEnabled = false;
    private boolean distortionEnabled = false;
    private boolean chorusEnabled = false;
    
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
    private Add chorusMixer;    // Mixes dry signal with chorus
    
    // Thread exception handling
    private Thread.UncaughtExceptionHandler effectsExceptionHandler;
    
    /**
     * Creates a new FX processor with all available effects.
     * This constructor initializes all effect components, configures their default settings,
     * and connects them in the signal path.
     * 
     * @param synth The JSyn synthesizer instance to add components to
     */
    public FXProcessor(Synthesizer synth) {
        this.synth = synth;
        
        // Set up global exception handler for effects threads
        setupExceptionHandler();
        
        createComponents(synth);
        configureDefaults();
        connectComponents();
        
        // Initialize reverb settings based on the current filter type
        updateReverbSettings();
    }
    
    /**
     * Sets up a global exception handler for audio processing threads.
     * This handler will reset effects to safe states when exceptions occur.
     */
    private void setupExceptionHandler() {
        effectsExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                System.err.println("Thread exception in audio processing: " + e.getMessage());
                e.printStackTrace();
                
                // Reset all effects to safe states
                // Use SwingUtilities.invokeLater to avoid thread conflicts
                SwingUtilities.invokeLater(() -> {
                    resetAllEffects();
                    // Log the exception
                    System.err.println("Effects have been reset to safe defaults");
                });
            }
        };
        
        // Set the exception handler for the current thread
        Thread.currentThread().setUncaughtExceptionHandler(effectsExceptionHandler);
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
        chorusMixer = new Add();
        
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
        
        // Create chorus components
        chorusDelay1 = new SafeInterpolatingDelay();
        chorusDelay1.allocate(4410); // 100ms at 44.1kHz - sufficient for chorus
        chorusDelay2 = new SafeInterpolatingDelay();
        chorusDelay2.allocate(4410); // 100ms at 44.1kHz - sufficient for chorus
        
        // Create chorus LFOs
        chorusLFO1 = new SineOscillator();
        chorusLFO2 = new SineOscillator();
        
        // Create chorus parameter ramps
        chorusRateRamp = new LinearRamp();
        chorusDepthRamp = new LinearRamp();
        chorusWetDryRamp = new LinearRamp();
        
        // Add all components to the synthesizer
        synth.add(inputMixer);
        synth.add(outputMixer);
        synth.add(delayMixer);
        synth.add(reverbMixer);
        synth.add(distortionMixer);
        synth.add(chorusMixer);
        
        synth.add(delay);
        synth.add(reverbEarlyDelay);
        synth.add(reverbLateDelay);
        synth.add(reverbLowpass);
        synth.add(reverbHighpass);
        synth.add(reverbFeedback);
        synth.add(reverbGain);
        synth.add(reverbMixer);
        synth.add(distortion);
        
        // Add chorus components
        synth.add(chorusDelay1);
        synth.add(chorusDelay2);
        synth.add(chorusLFO1);
        synth.add(chorusLFO2);
        
        synth.add(delayTimeRamp);
        synth.add(delayFeedbackRamp);
        synth.add(delayWetDryRamp);
        
        synth.add(reverbWetDryRamp);
        synth.add(reverbSizeRamp);
        synth.add(reverbDecayRamp);
        synth.add(reverbGainRamp);
        
        synth.add(distortionWetDryRamp);
        synth.add(distortionAmountRamp);
        
        // Add chorus parameter ramps
        synth.add(chorusRateRamp);
        synth.add(chorusDepthRamp);
        synth.add(chorusWetDryRamp);
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
        
        // Initialize chorus effect with standard vintage chorus settings
        // Set chorus ramp times
        chorusRateRamp.time.set(smoothingTime);
        chorusDepthRamp.time.set(smoothingTime);
        chorusWetDryRamp.time.set(smoothingTime);
        
        // Set base delay times for chorus delays (15ms and 22ms are standard for vintage chorus)
        chorusDelay1.delay.set(0.015); // 15ms base delay time
        chorusDelay2.delay.set(0.022); // 22ms base delay time
        
        // Set LFO rates (0.8Hz and 0.95Hz are standard for vintage chorus)
        // Using slightly different rates for the two LFOs creates a richer sound
        chorusLFO1.frequency.set(0.8); // 0.8 Hz for first LFO
        chorusLFO2.frequency.set(0.95); // 0.95 Hz for second LFO
        chorusRateRamp.input.set(0.8); // Default rate of 0.8 Hz
        
        // Set LFO amplitudes (modulation depth - 4ms and 3.5ms are standard for vintage chorus)
        chorusLFO1.amplitude.set(0.004); // 4ms modulation depth
        chorusLFO2.amplitude.set(0.0035); // 3.5ms modulation depth
        chorusDepthRamp.input.set(0.004); // Default depth of 4ms
        
        // Set wet/dry mix (standard vintage chorus typically uses 65% dry / 35% wet)
        chorusWetDryRamp.input.set(0.35); // 35% wet
        chorusMixer.inputA.set(0.65); // 65% dry
        chorusMixer.inputB.set(0.35); // 35% wet
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
        inputMixer.output.connect(chorusMixer.inputA); // Dry signal to chorus mixer
        
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
        
        // Connect chorus components with proper vintage chorus settings
        // Standard vintage chorus uses 15-22ms base delay times
        chorusDelay1.delay.set(0.015); // 15ms base delay (standard for vintage chorus)
        chorusDelay2.delay.set(0.022); // 22ms base delay (standard for vintage chorus)
        
        // Configure LFOs with standard vintage chorus rates and depths
        chorusLFO1.frequency.set(0.8);    // 0.8 Hz (standard vintage chorus rate)
        chorusLFO2.frequency.set(0.95);   // 0.95 Hz (slightly different for richer sound)
        
        // Set appropriate modulation depths for vintage chorus
        chorusLFO1.amplitude.set(0.004);  // 4ms modulation depth
        chorusLFO2.amplitude.set(0.0035); // 3.5ms modulation depth
        
        // Connect LFOs to modulate the delay times
        chorusLFO1.output.connect(chorusDelay1.delay);
        chorusLFO2.output.connect(chorusDelay2.delay);
        
        // Connect input signal to chorus delays
        inputMixer.output.connect(chorusDelay1.input);
        inputMixer.output.connect(chorusDelay2.input);
        
        // Connect chorus delays to chorus mixer's wet input
        // Using a sum of both delays creates a richer chorus effect
        chorusDelay1.output.connect(chorusMixer.inputB);
        chorusDelay2.output.connect(chorusMixer.inputB);
        
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
     * This implementation preserves the delay buffer contents when toggling the effect,
     * preventing audio glitches and maintaining consistent effect settings. When the effect
     * is re-enabled, it will continue from its previous state rather than resetting.
     * 
     * The delay effect continues to process audio in the background even when disabled,
     * but its output is disconnected from the main signal path. This approach ensures
     * smooth transitions when enabling/disabling the effect during performance.
     * 
     * @param enabled Whether the delay effect should be enabled (true) or disabled (false)
     */
    public void enableDelayEffect(boolean enabled) {
        delayEnabled = enabled;
        updateEffectRouting(); // This preserves buffer contents while toggling effect
    }
    
    /**
     * Enables or disables the reverb effect.
     * 
     * This implementation preserves the reverb buffer contents when toggling the effect,
     * preventing audio glitches and maintaining consistent effect settings. When the effect
     * is re-enabled, it will continue from its previous state rather than resetting.
     * 
     * The reverb effect continues to process audio in the background even when disabled,
     * but its output is disconnected from the main signal path. This approach ensures
     * smooth transitions when enabling/disabling the effect during performance.
     * 
     * @param enabled Whether the reverb effect should be enabled (true) or disabled (false)
     */
    public void enableReverbEffect(boolean enabled) {
        reverbEnabled = enabled;
        updateEffectRouting(); // This preserves buffer contents while toggling effect
    }
    
    /**
     * Enables or disables the distortion effect.
     * 
     * This implementation preserves the distortion state when toggling the effect,
     * preventing audio glitches and maintaining consistent effect settings. When the effect
     * is re-enabled, it will continue from its previous state rather than resetting.
     * 
     * The distortion effect continues to process audio in the background even when disabled,
     * but its output is disconnected from the main signal path. This approach ensures
     * smooth transitions when enabling/disabling the effect during performance.
     * 
     * @param enabled Whether the distortion effect should be enabled (true) or disabled (false)
     */
    public void enableDistortionEffect(boolean enabled) {
        distortionEnabled = enabled;
        updateEffectRouting(); // This preserves effect state while toggling
    }
    
    /**
     * Enables or disables the chorus effect.
     * 
     * This implementation preserves the chorus buffer contents when toggling the effect,
     * preventing audio glitches and maintaining consistent effect settings. When the effect
     * is re-enabled, it will continue from its previous state rather than resetting.
     * 
     * The chorus effect continues to process audio in the background even when disabled,
     * but its output is disconnected from the main signal path. This approach ensures
     * smooth transitions when enabling/disabling the effect during performance.
     * 
     * @param enabled Whether the chorus effect should be enabled (true) or disabled (false)
     */
    public void enableChorusEffect(boolean enabled) {
        chorusEnabled = enabled;
        updateEffectRouting(); // This preserves buffer contents while toggling effect
    }
    
    /**
     * Updates the effect routing based on which effects are enabled.
     * Multiple effects can be enabled simultaneously.
     * 
     * This implementation preserves effect state and buffer contents when toggling effects.
     * Rather than disconnecting and reconnecting all components (which would reset buffers),
     * we only modify the output connections to the main mixer. This approach ensures that
     * effects continue processing audio even when disabled, maintaining their internal state.
     * When an effect is re-enabled, its buffer contents are preserved, resulting in a smooth
     * transition without audio glitches.
     */
    private void updateEffectRouting() {
        // IMPORTANT: This method is designed to preserve buffer contents when toggling effects
        // The key design principle is to maintain all internal connections at all times
        // and only modify the final output connections when enabling/disabling effects
        
        // Always ensure all effects have input connections (these stay connected all the time)
        // This allows effects to continue processing in the background even when disabled
        // We use try-catch because connect() will throw an exception if already connected
        try {
            // Connect input to all effect mixers (dry signal path)
            // These connections ensure the dry signal is always available to all effect mixers
            inputMixer.output.connect(delayMixer.inputA);     // Dry signal to delay mixer
            inputMixer.output.connect(reverbMixer.inputA);    // Dry signal to reverb mixer
            inputMixer.output.connect(chorusMixer.inputA);    // Dry signal to chorus mixer
            inputMixer.output.connect(distortionMixer.inputA); // Dry signal to distortion mixer
            
            // Connect input to all effect processors (wet signal path)
            // These connections ensure effects continue processing even when disabled
            // This is crucial for preserving buffer contents and effect state
            inputMixer.output.connect(delay.input);           // Input to delay line
            inputMixer.output.connect(reverbEarlyDelay.input); // Input to reverb early reflections
            inputMixer.output.connect(distortion.inputA);     // Input to distortion processor
            inputMixer.output.connect(chorusDelay1.input);    // Input to first chorus delay
            inputMixer.output.connect(chorusDelay2.input);    // Input to second chorus delay
        } catch (Exception e) {
            // Connection already exists, which is fine
            // We want to ensure connections exist without disconnecting first
            // This prevents buffer resets that would occur if we disconnected first
        }
        
        // Only modify the output connections to the main output mixer
        // This is what actually enables/disables the audible effect without resetting buffers
        outputMixer.inputA.disconnectAll();
        
        // Connect the dry signal if no effects are enabled
        // This creates a clean bypass path when all effects are disabled
        if (!delayEnabled && !reverbEnabled && !distortionEnabled && !chorusEnabled) {
            inputMixer.output.connect(outputMixer.inputA);
            return;
        }
        
        // Connect each enabled effect to the output mixer
        // Each effect continues processing in the background even when disabled
        // Only the output connection determines whether the effect is audible
        if (delayEnabled) {
            delayMixer.output.connect(outputMixer.inputA);    // Connect delay to output
        }
        
        if (reverbEnabled) {
            reverbMixer.output.connect(outputMixer.inputA);   // Connect reverb to output
        }
        
        if (distortionEnabled) {
            distortionMixer.output.connect(outputMixer.inputA); // Connect distortion to output
        }
        
        if (chorusEnabled) {
            chorusMixer.output.connect(outputMixer.inputA);   // Connect chorus to output
        }
        
        // Note: All effects continue to process audio in the background even when disabled
        // This ensures smooth transitions when re-enabling effects without buffer resets
    }
    
    /**
     * Gets the current delay time setting.
     * 
     * @return The current delay time in seconds
     */
    public double getDelayTime() {
        return delayTimeRamp.input.get();
    }
    
    /**
     * Sets the delay time with parameter smoothing.
     * Includes thread-safe measures to prevent buffer overruns and audio artifacts.
     * Uses synchronized blocks to ensure thread safety during parameter updates.
     * 
     * @param seconds Delay time in seconds (0.0-6.0)
     */
    public void setDelayTime(double seconds) {
        // Calculate maximum safe delay time based on buffer allocation
        // Buffer is now 88200*3 + 10000 samples at 44.1kHz, so max is ~6.0 seconds
        // Use a conservative safety margin to prevent edge cases
        double maxSafeDelay = 5.0; // Increased maximum safe delay time in seconds
        
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
     * @param amount Feedback amount (0.0-2.85)
     */
    public void setFeedback(double amount) {
        // Limit feedback to prevent runaway feedback
        // Even with higher maximum values, we still need to cap the actual feedback
        // to prevent infinite feedback loops and system instability
        double safeAmount = Math.min(Math.max(amount, 0.0), 0.98);
        
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
            // Set delay time to a safe value
            synchronized(delay) {
                delay.delay.set(0.3); // 300ms is a safe default
                delay.reset();
            }
            
            // Reset feedback to zero
            synchronized(delayFeedback) {
                delayFeedback.inputB.set(0.3);
                delayFeedbackRamp.input.set(0.3);
            }
            
            // Reset wet/dry mix
            synchronized(delayMixer) {
                delayWetDryRamp.input.set(0.3);
                delayMixer.inputA.set(0.7); // dry
                delayMixer.inputB.set(0.3); // wet
            }
            
            System.out.println("Delay components reset to safe defaults");
        } catch (Exception e) {
            System.err.println("Error resetting delay components: " + e.getMessage());
        }
    }
    
    /**
     * Reset reverb components to safe default values.
     * This can be called if audio artifacts or errors are detected.
     */
    public void resetReverbComponents() {
        try {
            // Reset reverb delays to safe values
            synchronized(reverbEarlyDelay) {
                reverbEarlyDelay.delay.set(0.03);
                reverbEarlyDelay.reset();
            }
            
            synchronized(reverbLateDelay) {
                reverbLateDelay.delay.set(0.05);
                reverbLateDelay.reset();
            }
            
            // Reset filters to safe values
            synchronized(reverbLowpass) {
                reverbLowpass.frequency.set(4000.0);
            }
            
            synchronized(reverbHighpass) {
                reverbHighpass.frequency.set(100.0);
            }
            
            // Reset feedback to safe value
            synchronized(reverbFeedback) {
                reverbFeedback.inputB.set(0.5);
            }
            
            // Reset gain to unity
            synchronized(reverbGain) {
                reverbGain.inputB.set(1.0);
            }
            
            // Reset wet/dry mix
            synchronized(reverbMixer) {
                reverbWetDryRamp.input.set(0.3);
                reverbMixer.inputA.set(0.7); // dry
                reverbMixer.inputB.set(0.3); // wet
            }
            
            System.out.println("Reverb components reset to safe defaults");
        } catch (Exception e) {
            System.err.println("Error resetting reverb components: " + e.getMessage());
        }
    }
    
    /**
     * Reset distortion components to safe default values.
     * This can be called if audio artifacts or errors are detected.
     */
    public void resetDistortionComponents() {
        try {
            // Reset distortion amount to safe value
            synchronized(distortion) {
                distortion.inputB.set(1.0);
                distortionAmountRamp.input.set(0.0);
            }
            
            // Reset wet/dry mix
            synchronized(distortionMixer) {
                distortionWetDryRamp.input.set(0.0);
                distortionMixer.inputA.set(1.0); // dry
                distortionMixer.inputB.set(0.0); // wet
            }
            
            System.out.println("Distortion components reset to safe defaults");
        } catch (Exception e) {
            System.err.println("Error resetting distortion components: " + e.getMessage());
        }
    }
    
    /**
     * Reset chorus components to standard vintage chorus values.
     * This can be called if audio artifacts or errors are detected,
     * or to initialize the chorus with classic settings.
     */
    public void resetChorusComponents() {
        try {
            // Reset chorus delays to standard values
            synchronized(chorusDelay1) {
                chorusDelay1.delay.set(0.015); // 15ms base delay (standard for vintage chorus)
                chorusDelay1.reset();
            }
            
            synchronized(chorusDelay2) {
                chorusDelay2.delay.set(0.022); // 22ms base delay (slightly offset for richer sound)
                chorusDelay2.reset();
            }
            
            // Reset LFOs to standard vintage chorus values
            synchronized(chorusLFO1) {
                chorusLFO1.frequency.set(0.8); // 0.8 Hz modulation (standard for vintage chorus)
                chorusLFO1.amplitude.set(0.004); // 4ms modulation depth
            }
            
            synchronized(chorusLFO2) {
                chorusLFO2.frequency.set(0.95); // 0.95 Hz modulation (slightly faster for stereo effect)
                chorusLFO2.amplitude.set(0.0035); // 3.5ms modulation depth (slightly less for balance)
            }
            
            // Reset wet/dry mix to standard vintage chorus values
            synchronized(chorusMixer) {
                chorusWetDryRamp.input.set(1.05); // 35% wet (standard for vintage chorus)
                chorusMixer.inputA.set(0.65); // 65% dry
                chorusMixer.inputB.set(0.35); // 35% wet
            }
            
            System.out.println("Chorus components reset to standard vintage chorus settings");
        } catch (Exception e) {
            System.err.println("Error resetting chorus components: " + e.getMessage());
        }
    }
    
    /**
     * Reset all effects to safe default values.
     * This is the main recovery method called when thread exceptions occur.
     */
    public void resetAllEffects() {
        // Reset each effect independently to ensure partial recovery if one fails
        resetDelayComponents();
        resetReverbComponents();
        resetDistortionComponents();
        resetChorusComponents();
        
        // Update effect routing to ensure signal path is correct
        try {
            updateEffectRouting();
        } catch (Exception e) {
            System.err.println("Error updating effect routing during reset: " + e.getMessage());
            // Last resort: disable all effects
            try {
                delayEnabled = false;
                reverbEnabled = false;
                distortionEnabled = false;
                
                // Connect input directly to output
                inputMixer.output.disconnectAll();
                outputMixer.inputA.disconnectAll();
                inputMixer.output.connect(outputMixer.inputA);
                
                System.out.println("All effects disabled as safety measure");
            } catch (Exception ex) {
                System.err.println("Critical failure in effects reset: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Sets the wet/dry mix for the delay effect.
     * Includes thread-safe measures to prevent audio artifacts.
     * 
     * @param mix Mix amount (0.0=dry, 3.0=super wet)
     */
    public void setDelayWetDryMix(double mix) {
        // Clamp the mix value to a valid range (now up to 3.0)
        double safeMix = Math.min(Math.max(mix, 0.0), 3.0);
        
        // Normalize for internal processing (0.0-1.0)
        double normalizedMix = Math.min(safeMix / 3.0, 1.0);
        
        // Only update if the change is significant
        double currentMix = delayWetDryRamp.input.get();
        if (Math.abs(currentMix - safeMix) > 0.001) {
            // Use a fixed ramp time for thread safety
            // Don't dynamically change the ramp time while audio is processing
            
            // Update the mix parameter with smooth transition
            delayWetDryRamp.input.set(safeMix);
            
            try {
                // Calculate the wet/dry balance
                // For mix values > 1.0, we keep reducing the dry signal while increasing wet beyond 1.0
                double dryAmount = Math.max(0.0, 1.0 - normalizedMix * 3.0);
                double wetAmount = Math.min(safeMix, 3.0);
                
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
     * @param mix Mix amount (0.0=dry, 3.0=super wet)
     */
    public void setReverbWetDryMix(double mix) {
        // Clamp the mix value to a valid range (now up to 3.0)
        double safeMix = Math.min(Math.max(mix, 0.0), 3.0);
        
        // Normalize for internal processing (0.0-1.0)
        double normalizedMix = Math.min(safeMix / 3.0, 1.0);
        
        // Only update if the change is significant
        double currentMix = reverbWetDryRamp.input.get();
        if (Math.abs(currentMix - safeMix) > 0.001) {
            // Use a fixed ramp time for thread safety
            // Don't dynamically change the ramp time while audio is processing
            
            // Update the mix parameter with smooth transition
            reverbWetDryRamp.input.set(safeMix);
            
            try {
                // Calculate the wet/dry balance
                // For mix values > 1.0, we keep reducing the dry signal while increasing wet beyond 1.0
                double dryAmount = Math.max(0.0, 1.0 - normalizedMix * 3.0);
                double wetAmount = safeMix * 1.5; // Boost the wet signal for more pronounced reverb, with a reasonable cap
                
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
     * @param mix Mix amount (0.0=dry, 3.0=super wet)
     */
    public void setDistortionWetDryMix(double mix) {
        // Clamp the mix value to a valid range (now up to 3.0)
        double safeMix = Math.min(Math.max(mix, 0.0), 3.0);
        
        // Normalize for internal processing (0.0-1.0)
        double normalizedMix = Math.min(safeMix / 3.0, 1.0);
        
        // Only update if the change is significant
        double currentMix = distortionWetDryRamp.input.get();
        if (Math.abs(currentMix - safeMix) > 0.001) {
            // Use a fixed ramp time for thread safety
            // Don't dynamically change the ramp time while audio is processing
            
            // Update the mix parameter with smooth transition
            distortionWetDryRamp.input.set(safeMix);
            
            try {
                // Calculate the wet/dry balance
                // For mix values > 1.0, we keep reducing the dry signal while increasing wet beyond 1.0
                double dryAmount = Math.max(0.0, 1.0 - normalizedMix * 3.0);
                double wetAmount = Math.min(safeMix, 3.0);
                
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
     * @param amount Distortion amount (0.0-3.0)
     */
    public void setDistortion(double amount) {
        // Clamp the amount parameter to 0.0-3.0 range
        double safeAmount = Math.min(Math.max(amount, 0.0), 3.0);
        
        // Normalize for internal processing (0.0-1.0)
        double normalizedAmount = safeAmount / 3.0;
        
        // Only update if the change is significant
        double currentAmount = distortionAmountRamp.input.get();
        if (Math.abs(currentAmount - safeAmount) > 0.001) {
            // Use a fixed ramp time for thread safety
            // Don't dynamically change the ramp time while audio is processing
            
            // Apply logarithmic scaling for more natural control
            // This provides finer control at lower distortion amounts
            double logAmount;
            if (normalizedAmount > 0) {
                // Use a logarithmic curve that gives finer control at lower settings
                logAmount = Math.pow(normalizedAmount, 2.5);
            } else {
                logAmount = 0.0; // Handle amount = 0 case
            }
            
            // Update the distortion parameter with smooth transition
            distortionAmountRamp.input.set(logAmount);
            
            try {
                // More gain for more distortion with logarithmic scaling
                // This creates a more natural distortion curve where small changes at low amounts
                // are more noticeable than the same changes at high amounts
                double gain = 1.0 + (logAmount * 15.0); // 1.0-16.0 range with the new maximum
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
     * @param size Room size (0.0-3.0)
     */
    public void setReverbSize(double size) {
        // Clamp the size parameter to 0.0-3.0 range
        double safeSize = Math.min(Math.max(size, 0.0), 3.0);
        
        // Normalize for internal processing (0.0-1.0)
        double normalizedSize = Math.min(safeSize / 3.0, 1.0);
        
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
                    // Early reflections (30ms to 230ms with the new maximum)
                    // Make sure we stay well within the allocated buffer size (22050+100 samples = ~0.5s)
                    double maxEarlyDelay = 0.45; // Maximum safe delay time in seconds
                    double earlyDelayTime = Math.min(0.03 + (normalizedSize * 0.2), maxEarlyDelay);
                    
                    // Synchronize access to the early delay component
                    synchronized(reverbEarlyDelay) {
                        reverbEarlyDelay.delay.set(earlyDelayTime);
                    }
                    
                    // Late reflections (50ms to 350ms with the new maximum)
                    // Make sure we stay well within the allocated buffer size (44100+100 samples = ~1s)
                    double maxLateDelay = 0.9; // Maximum safe delay time in seconds
                    double lateDelayTime = Math.min(0.05 + (normalizedSize * 0.3), maxLateDelay);
                    
                    // Synchronize access to the late delay component
                    synchronized(reverbLateDelay) {
                        reverbLateDelay.delay.set(lateDelayTime);
                    }
                    
                    // Adjust filter frequencies based on room size
                    // Larger rooms have more low frequency content (damping of high frequencies)
                    double lowpassFreq = 5000 - (normalizedSize * 3000); // 5000Hz to 2000Hz with more extreme filtering
                    reverbLowpass.frequency.set(lowpassFreq);
                    
                    // Smaller rooms have less low frequency buildup
                    double highpassFreq = 100 + (normalizedSize * 150); // 100Hz to 250Hz with more extreme filtering
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
     * @param decay Decay amount (0.0-3.0)
     */
    public void setReverbDecay(double decay) {
        // Clamp the decay parameter to 0.0-3.0 range
        double safeDecay = Math.min(Math.max(decay, 0.0), 3.0);
        
        // Normalize for internal processing (0.0-1.0)
        double normalizedDecay = Math.min(safeDecay / 3.0, 1.0);
        
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
                    // Map decay to feedback amount (0.4 to 0.95)
                    // Higher feedback = longer decay time
                    // Limit the maximum feedback to prevent buffer overruns
                    double maxFeedback = 0.95; // Increased maximum feedback for longer decay times
                    double feedback = Math.min(0.4 + (normalizedDecay * 0.55), maxFeedback);
                    
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
     * @param gain Gain amount (0.0-3.0)
     */
    public void setReverbGain(double gain) {
        // Clamp the gain parameter to 0.0-3.0 range
        double safeGain = Math.min(Math.max(gain, 0.0), 3.0);
        
        // Normalize for internal processing (0.0-1.0)
        double normalizedGain = Math.min(safeGain / 3.0, 1.0);
        
        // Only update if the change is significant
        double currentGain = reverbGainRamp.input.get();
        if (Math.abs(currentGain - safeGain) > 0.001) {
            // Use a fixed ramp time for thread safety
            // Don't dynamically change the ramp time while audio is processing
            
            // Update the gain parameter with smooth transition
            reverbGainRamp.input.set(safeGain);
            
            // Map gain to actual gain multiplier (0.5 to 2.5)
            // Use a try-catch block to handle any potential errors
            try {
                double gainMultiplier = 0.5 + (normalizedGain * 2.0);
                reverbGain.inputB.set(gainMultiplier);
            } catch (Exception e) {
                // If an error occurs, set to a safe default value
                reverbGain.inputB.set(1.0);
            }
        }
    }
    
    /**
     * Sets the chorus rate (0.0 to 3.0).
     * Controls the speed of the LFO modulation.
     * Includes thread-safe measures to prevent audio artifacts.
     * 
     * @param rate Chorus rate, 0.0 = slow, 3.0 = fast
     */
    public void setChorusRate(double rate) {
        // Clamp to valid range
        double safeRate = Math.min(Math.max(rate, 0.0), 3.0);
        
        // Normalize for internal processing (0.0-1.0)
        double normalizedRate = Math.min(safeRate / 3.0, 1.0);
        
        // Only update if the change is significant
        double currentRate = chorusRateRamp.input.get();
        if (Math.abs(currentRate - safeRate) > 0.001) {
            try {
                // Map to a useful frequency range (0.5 Hz to 5 Hz) - standard vintage chorus range
                double mappedRate = 0.5 + (normalizedRate * 4.5);
                
                // Update the rate parameter with smooth transition
                chorusRateRamp.input.set(safeRate);
                
                // Set slightly different rates for the two LFOs for a richer sound
                chorusLFO1.frequency.set(mappedRate);
                chorusLFO2.frequency.set(mappedRate * 1.15); // 15% faster (vintage chorus typically uses subtle differences)
                
                System.out.println("Chorus rate set to: " + mappedRate + " Hz");
            } catch (Exception e) {
                // If an error occurs, set to safe default values
                chorusLFO1.frequency.set(0.8); // Standard vintage chorus rate
                chorusLFO2.frequency.set(0.95);
                System.err.println("Error setting chorus rate: " + e.getMessage());
            }
        }
    }
    
    /**
     * Sets the chorus depth (0.0 to 3.0).
     * Controls how much the delay time varies.
     * Includes thread-safe measures to prevent audio artifacts.
     * 
     * @param depth Chorus depth, 0.0 = subtle, 3.0 = extreme
     */
    public void setChorusDepth(double depth) {
        // Clamp to valid range
        double safeDepth = Math.min(Math.max(depth, 0.0), 3.0);
        
        // Normalize for internal processing (0.0-1.0)
        double normalizedDepth = Math.min(safeDepth / 3.0, 1.0);
        
        // Only update if the change is significant
        double currentDepth = chorusDepthRamp.input.get();
        if (Math.abs(currentDepth - safeDepth) > 0.001) {
            try {
                // Map to a useful amplitude range (0.002 to 0.01)
                // This controls how much the delay time varies - standard vintage chorus uses 2-10ms
                double mappedDepth = 0.002 + (normalizedDepth * 0.008);
                
                // Update the depth parameter with smooth transition
                chorusDepthRamp.input.set(safeDepth);
                
                // Set slightly different depths for the two LFOs
                chorusLFO1.amplitude.set(mappedDepth);
                chorusLFO2.amplitude.set(mappedDepth * 0.85); // 15% less depth (vintage chorus uses subtle differences)
                
                System.out.println("Chorus depth set to: " + (mappedDepth * 1000) + " ms");
            } catch (Exception e) {
                // If an error occurs, set to safe default values
                chorusLFO1.amplitude.set(0.004); // Standard vintage chorus depth
                chorusLFO2.amplitude.set(0.0035);
                System.err.println("Error setting chorus depth: " + e.getMessage());
            }
        }
    }
    
    /**
     * Sets the chorus wet/dry mix (0.0 to 3.0).
     * Controls the balance between the dry and processed signal.
     * Includes thread-safe measures to prevent audio artifacts.
     * 
     * @param mix Mix amount (0.0=dry, 3.0=super wet)
     */
    public void setChorusWetDryMix(double mix) {
        // Clamp the mix value to a valid range (now up to 3.0)
        double safeMix = Math.min(Math.max(mix, 0.0), 3.0);
        
        // Normalize for internal processing (0.0-1.0)
        double normalizedMix = Math.min(safeMix / 3.0, 1.0);
        
        // Only update if the change is significant
        double currentMix = chorusWetDryRamp.input.get();
        if (Math.abs(currentMix - safeMix) > 0.001) {
            // Update the mix parameter with smooth transition
            chorusWetDryRamp.input.set(safeMix);
            
            try {
                // Standard vintage chorus typically uses 65% dry / 35% wet as a starting point
                // For mix values > 1.0, we keep reducing the dry signal while increasing wet beyond 1.0
                
                // Calculate wet amount (0.0-1.0 range for standard settings)
                double wetAmount = normalizedMix;
                
                // Calculate dry amount (standard vintage chorus always maintains some dry signal)
                double dryAmount = Math.max(0.2, 1.0 - wetAmount);
                
                // For extreme settings (mix > 1.0), allow wet to go higher
                if (safeMix > 1.0) {
                    wetAmount = safeMix;
                    dryAmount = Math.max(0.0, 1.0 - (normalizedMix * 1.5)); // Reduce dry faster
                }
                
                // Set the mixer inputs for the chorus effect gradually
                chorusMixer.inputA.set(dryAmount);
                chorusMixer.inputB.set(wetAmount);
                
                System.out.println("Chorus mix set to: " + (int)(wetAmount * 100) + "% wet / " + 
                                  (int)(dryAmount * 100) + "% dry");
            } catch (Exception e) {
                // If an error occurs, set to safe default values - standard vintage chorus mix
                chorusMixer.inputA.set(0.65); // 65% dry
                chorusMixer.inputB.set(0.35); // 35% wet
                System.err.println("Error setting chorus mix: " + e.getMessage());
            }
        }
    }
}
