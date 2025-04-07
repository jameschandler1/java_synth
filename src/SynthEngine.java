package src;

import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.unitgen.*;

/**
 * Main synthesizer engine class that manages audio synthesis, voice allocation,
 * and signal processing. This class coordinates all aspects of sound generation
 * including polyphony, filtering, and parameter control.
 */
public class SynthEngine {
    /**
     * Supported filter types for the synthesizer's filter section.
     * Each filter type provides a different frequency response characteristic.
     */
    public enum FilterType {
        LOWPASS("Low Pass"),     // Allows frequencies below cutoff to pass
        HIGHPASS("High Pass"),   // Allows frequencies above cutoff to pass
        BANDPASS("Band Pass");   // Allows frequencies around cutoff to pass
        
        private final String displayName;
        
        FilterType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Filter configuration
    private FilterType currentFilterType = FilterType.LOWPASS;
    private FilterStateVariable filter;
    
    // Core synthesis components
    private Synthesizer synth;
    private LineOut lineOut;
    private Add mixer;          // Mixes all voice outputs together
    private MultiplyAdd amp;    // Controls master volume
    
    // Effects processor
    private FXProcessor fxProcessor;
    
    // Effect state flags
    private boolean delayEnabled = false;
    private boolean reverbEnabled = false;
    private boolean distortionEnabled = false;
    private boolean chorusEnabled = false;
    private boolean delaySyncEnabled = false;
    
    // Current oscillator type for all voices
    private SynthVoice.OscillatorType currentOscillatorType = SynthVoice.OscillatorType.SINE;
    
    // Parameter smoothing to prevent audio artifacts
    private LinearRamp cutoffRamp;    // Smooths filter cutoff changes
    private LinearRamp resonanceRamp;  // Smooths filter resonance changes
    private LinearRamp volumeRamp;     // Smooths volume changes
    
    // Voice management
    private SynthVoice[] voices;
    private static final int NUM_VOICES = 8;  // Maximum number of simultaneous notes

    /**
     * Creates a new synthesizer engine instance and initializes all components.
     * This constructor sets up the audio system and prepares it for sound generation.
     */
    public SynthEngine() {
        synth = JSyn.createSynthesizer();      // Create the core synthesizer
        initializeComponents();                // Set up all synth components
        connectComponents();                   // Wire components together
        synth.start();                        // Start audio processing
    }

    /**
     * Coordinates the complete initialization of all synthesizer components.
     * This method orchestrates the four main setup phases in the correct order.
     */
    private void initializeComponents() {
        createComponents();      // Instantiate all audio components
        addComponentsToSynth();  // Register components with JSyn
        configureComponents();   // Set up default parameters
        initializeVoices();     // Create polyphonic voices
    }

    /**
     * Creates all the individual components needed for the synthesizer.
     * This includes audio output, filters, mixers, and parameter smoothing.
     */
    private void createComponents() {
        // Create main audio processing components
        lineOut = new LineOut();              // Final audio output
        filter = new FilterStateVariable();    // Multi-mode filter
        mixer = new Add();                    // Combines all voices
        amp = new MultiplyAdd();              // Master volume control
        
        // Create effects processor
        fxProcessor = new FXProcessor(synth);
        
        // Create parameter smoothing components
        cutoffRamp = new LinearRamp();
        resonanceRamp = new LinearRamp();
        volumeRamp = new LinearRamp();
        
        // Configure smoothing times
        double smoothingTime = 0.05;  // 50ms
        cutoffRamp.time.set(smoothingTime);
        resonanceRamp.time.set(smoothingTime);
        volumeRamp.time.set(smoothingTime);
    }

    /**
     * Registers all audio components with the JSyn synthesizer.
     * Components must be added to the synth before they can process audio.
     */
    private void addComponentsToSynth() {
        // Add main components
        synth.add(lineOut);         // Audio output
        synth.add(filter);          // Filter module
        synth.add(mixer);           // Voice mixer
        synth.add(amp);             // Master amplitude
        
        // Add parameter smoothing
        synth.add(cutoffRamp);      // Filter cutoff control
        synth.add(resonanceRamp);   // Filter resonance control
        synth.add(volumeRamp);      // Volume control
    }

    /**
     * Sets default values for all parameters and connects parameter smoothing.
     * This establishes the initial state of the synthesizer's sound.
     */
    private void configureComponents() {
        // Set filter defaults
        cutoffRamp.input.set(2000.0);    // Initial cutoff: 2kHz
        resonanceRamp.input.set(0.05);   // Light resonance: 5%
        filter.amplitude.set(0.9);       // Filter gain: 90%
        
        // Connect parameter smoothing to their targets
        cutoffRamp.output.connect(filter.frequency);     // Smooth cutoff changes
        resonanceRamp.output.connect(filter.resonance);  // Smooth resonance changes
        volumeRamp.output.connect(amp.inputB);          // Smooth volume changes to amplifier
    }

    /**
     * Creates the polyphonic voice system by initializing multiple synthesizer voices.
     * Each voice is capable of playing one note simultaneously.
     */
    private void initializeVoices() {
        voices = new SynthVoice[NUM_VOICES];           // Array to hold all voices
        for (int i = 0; i < NUM_VOICES; i++) {        // Create each voice
            voices[i] = new SynthVoice(synth);        // Initialize with synth reference
        }
    }

    /**
     * Establishes all audio connections between components.
     * Creates the complete signal path from voices through to the output.
     */
    private void connectComponents() {
        connectVoicesToMixer();    // Connect all voices to the main mixer
        setupSignalPath();         // Set up the audio processing chain
        configureAmplifier();      // Configure the master volume
        startAudio();              // Begin audio output
    }

    /**
     * Connects all synthesizer voices to the main mixer.
     * This allows multiple voices to be heard simultaneously.
     */
    private void connectVoicesToMixer() {
        for (SynthVoice voice : voices) {           // For each voice
            voice.connectToMixer(mixer);            // Connect to main mixer
        }
    }

    /**
     * Sets up the main audio signal path through the synthesizer.
     * Signal flow: Voices -> Mixer -> Filter -> FX Processor -> Amplifier -> Output
     */
    private void setupSignalPath() {
        mixer.output.connect(0, filter.input, 0);     // Connect mixer to filter input
        connectFilterOutput();                        // Connect filter to FX processor (based on type)
        fxProcessor.connectOutput(amp.inputA);        // Connect FX processor to amplifier
        amp.output.connect(0, lineOut.input, 0);     // Connect amp to audio output
    }

    /**
     * Configures the master amplifier settings.
     * Sets up the amplifier to use the volume ramp for smooth volume control.
     * The MultiplyAdd unit calculates: output = (inputA * inputB) + inputC
     */
    private void configureAmplifier() {
        // Set initial volume level
        volumeRamp.input.set(0.7);  // Set initial volume to 70%
        
        // Configure the amplifier to use multiplication for volume control
        // inputA receives the audio signal from the FX processor
        // inputB receives the volume control from the volume ramp
        // inputC is set to 0.0 (no offset)
        amp.inputC.set(0.0);       // No additional offset
    }

    /**
     * Starts the audio output system.
     * Must be called before any sound can be heard.
     */
    private void startAudio() {
        lineOut.start();  // Begin audio output processing
    }

    /**
     * Triggers a note-on event with the specified MIDI note and velocity.
     * Handles voice allocation and note stealing if necessary.
     * 
     * @param midiNote MIDI note number (0-127, where 60 is middle C)
     * @param velocity Note velocity (0.0-1.0)
     */
    public void noteOn(int midiNote, double velocity) {
        releaseExistingVoices(midiNote);         // Stop any current instances of this note
        SynthVoice selectedVoice = findFreeVoice(); // Get an available voice
        selectedVoice.noteOn(midiNote, velocity);   // Start playing the note
    }

    /**
     * Releases any voices currently playing the specified MIDI note.
     * This prevents the same note from playing multiple times.
     * 
     * @param midiNote MIDI note number to release
     */
    private void releaseExistingVoices(int midiNote) {
        for (SynthVoice voice : voices) {                          // Check each voice
            if (voice.isActive() && voice.getCurrentNote() == midiNote) {  // If playing this note
                voice.noteOff();                                   // Stop the note
            }
        }
    }

    /**
     * Finds an available voice for playing a new note.
     * If all voices are active, steals the oldest voice.
     * 
     * @return SynthVoice Available voice for playing a new note
     */
    private SynthVoice findFreeVoice() {
        // Try to find an inactive voice first
        for (SynthVoice voice : voices) {
            if (!voice.isActive()) {
                return voice;  // Return first available voice
            }
        }
        
        // If all voices are active, steal the first (oldest) one
        return voices[0];
    }

    /**
     * Triggers a note-off event for the specified MIDI note.
     * Releases all voices currently playing this note.
     * 
     * @param midiNote MIDI note number to stop
     */
    public void noteOff(int midiNote) {
        for (SynthVoice voice : voices) {                          // Check each voice
            if (voice.isActive() && voice.getCurrentNote() == midiNote) {  // If playing this note
                voice.noteOff();                                   // Stop the note
            }
        }
    }

    /**
     * Sets the filter cutoff frequency with smoothing.
     * The frequency is clamped to the extended range and uses logarithmic scaling
     * for more natural frequency perception (following the musical scale).
     * 
     * @param frequency Desired cutoff frequency in Hz (20-60000)
     */
    public void setCutoff(double frequency) {
        // Clamp frequency to extended range (20Hz - 60kHz)
        double safeFreq = Math.min(60000.0, Math.max(20.0, frequency));
        
        // Apply logarithmic scaling for more natural frequency perception
        // This follows the musical scale where each octave doubles the frequency
        // If the input is already in Hz (20-60000), we don't need additional scaling
        // as frequency itself is already logarithmic in nature
        
        cutoffRamp.input.set(safeFreq);  // Set with smoothing
    }

    /**
     * Sets the filter resonance with smoothing.
     * The value is scaled to prevent self-oscillation.
     * 
     * @param resonance Resonance value (0.0-3.0)
     */
    public void setResonance(double resonance) {
        // Scale resonance to safe range (0.0 to 0.9)
        // Higher maximum but still prevent extreme self-oscillation
        double scaledResonance = Math.min(0.9, resonance * 0.3);  // Prevent extreme resonance
        resonanceRamp.input.set(scaledResonance);  // Set with smoothing
    }

    /**
     * Sets the master volume with smoothing to prevent clicks.
     * Uses a logarithmic scale for more natural volume perception.
     * 
     * @param volume Volume level (0.0-5.0)
     */
    public void setMasterVolume(double volume) {
        // Ensure volume is within valid range (now up to 5.0 for louder output)
        double safeVolume = Math.min(Math.max(volume, 0.0), 5.0);
        
        // Scale down to 0.0-1.0 range for internal processing
        double normalizedVolume = safeVolume / 5.0;
        
        // Apply logarithmic scaling for more natural volume perception
        // This creates a more natural volume curve where small changes at low volumes
        // are more noticeable than the same changes at high volumes
        double logVolume;
        if (normalizedVolume > 0) {
            // Use a logarithmic curve that gives finer control at lower volumes
            // The constant 3.0 determines how pronounced the logarithmic curve is
            // Lower exponent (3.0 instead of 4.0) provides more headroom at higher volumes
            logVolume = Math.pow(normalizedVolume, 3.0) * 4.0; // Scale up for higher maximum output
        } else {
            logVolume = 0.0; // Handle volume = 0 case
        }
        
        // Apply the scaled volume with smoothing
        // Increased maximum to 3.0 for louder output while still preventing extreme distortion
        volumeRamp.input.set(Math.min(logVolume, 3.0));
    }

    /**
     * Sets the attack time for all voice envelopes.
     * Attack is the time taken for the sound to reach full volume.
     * 
     * @param seconds Attack time in seconds (0.0-3.0)
     */
    public void setEnvelopeAttack(double seconds) {
        System.out.println("Setting envelope attack: " + seconds + " seconds");
        // Update attack time for all voices
        for (SynthVoice voice : voices) {
            voice.setAttack(seconds);          // Set attack time
        }
    }

    /**
     * Sets the decay time for all voice envelopes.
     * Decay is the time taken to reach the sustain level after attack.
     * 
     * @param seconds Decay time in seconds (0.0-3.0)
     */
    public void setEnvelopeDecay(double seconds) {
        System.out.println("Setting envelope decay: " + seconds + " seconds");
        // Update decay time for all voices
        for (SynthVoice voice : voices) {
            voice.setDecay(seconds);           // Set decay time
        }
    }

    /**
     * Sets the sustain level for all voice envelopes.
     * Sustain is the volume level held after decay until note-off.
     * 
     * @param level Sustain level (0.0-3.0)
     */
    public void setEnvelopeSustain(double level) {
        System.out.println("Setting envelope sustain: " + level);
        // Update sustain level for all voices
        for (SynthVoice voice : voices) {
            voice.setSustain(level);           // Set sustain level
        }
    }

    /**
     * Sets the release time for all voice envelopes.
     * Release is the time taken for the sound to fade after note-off.
     * 
     * @param seconds Release time in seconds (0.0-3.0)
     */
    public void setEnvelopeRelease(double seconds) {
        System.out.println("Setting envelope release: " + seconds + " seconds");
        // Update release time for all voices
        for (SynthVoice voice : voices) {
            voice.setRelease(seconds);         // Set release time
        }
    }

    /**
     * Shuts down the synthesizer and stops all audio processing.
     * Should be called when the synthesizer is no longer needed.
     */
    public void shutdown() {
        synth.stop();  // Stop all audio processing
    }

    /**
     * Changes the current filter type and updates signal routing.
     * Supports switching between lowpass, highpass, and bandpass modes.
     * Also updates the FXProcessor to optimize reverb settings for the current filter type.
     * 
     * @param type The new filter type to use
     */
    public void setFilterType(FilterType type) {
        if (type != currentFilterType) {           // Only change if different
            currentFilterType = type;               // Update current type
            connectFilterOutput();                  // Reconnect filter outputs
            
            // Update FXProcessor with the new filter type
            // Convert enum to int value (0=LOWPASS, 1=HIGHPASS, 2=BANDPASS)
            int filterTypeValue = type.ordinal();
            fxProcessor.setCurrentFilterType(filterTypeValue);
        }
    }
    
    // These methods have been replaced by individual effect enable/disable methods
    
    /**
     * Sets the delay time for the delay effect.
     * If delay sync is enabled, the time will be quantized to musical values based on
     * a tempo of 120 BPM, creating rhythmically relevant delay times.
     * 
     * @param seconds Delay time in seconds (0.0-2.0)
     */
    public void setDelayTime(double seconds) {
        if (delaySyncEnabled) {
            // Quantize to musical values (e.g., 16th, 8th, quarter notes at 120 BPM)
            double bpm = 120.0;
            double beatDuration = 60.0 / bpm; // Duration of one beat in seconds
            
            // Available note durations (in beats)
            double[] noteDurations = {
                0.25,  // 16th note
                0.375, // dotted 16th note
                0.5,   // 8th note
                0.75,  // dotted 8th note
                1.0,   // quarter note
                1.5,   // dotted quarter note
                2.0    // half note
            };
            
            // Find the closest musical value
            double closestDuration = noteDurations[0] * beatDuration;
            double minDifference = Math.abs(seconds - closestDuration);
            
            for (int i = 1; i < noteDurations.length; i++) {
                double currentDuration = noteDurations[i] * beatDuration;
                double difference = Math.abs(seconds - currentDuration);
                
                if (difference < minDifference) {
                    minDifference = difference;
                    closestDuration = currentDuration;
                }
            }
            
            // Use the quantized value
            fxProcessor.setDelayTime(closestDuration);
            
            // Print the musical value for user feedback
            String noteName = "";
            double noteValue = closestDuration / beatDuration;
            
            if (noteValue == 0.25) noteName = "16th note";
            else if (noteValue == 0.375) noteName = "dotted 16th note";
            else if (noteValue == 0.5) noteName = "8th note";
            else if (noteValue == 0.75) noteName = "dotted 8th note";
            else if (noteValue == 1.0) noteName = "quarter note";
            else if (noteValue == 1.5) noteName = "dotted quarter note";
            else if (noteValue == 2.0) noteName = "half note";
            
            System.out.println("Delay time quantized to: " + noteName + " (" + closestDuration + " seconds)");
        } else {
            // Use the exact value provided
            fxProcessor.setDelayTime(seconds);
        }
    }
    
    /**
     * Sets the feedback amount for the delay effect.
     * 
     * @param amount Feedback amount (0.0-0.95)
     */
    public void setFeedback(double amount) {
        fxProcessor.setFeedback(amount);
    }
    
    /**
     * Sets the delay feedback amount.
     * Alias for setFeedback to match UI naming convention.
     * 
     * @param amount Feedback amount (0.0-0.95)
     */
    public void setDelayFeedback(double amount) {
        setFeedback(amount);
    }
    
    /**
     * Sets the wet/dry mix for the delay effect.
     * 
     * @param mix Mix amount (0.0=dry, 1.0=wet)
     */
    public void setDelayWetDryMix(double mix) {
        fxProcessor.setDelayWetDryMix(mix);
    }
    
    /**
     * Sets the wet/dry mix for the reverb effect.
     * 
     * @param mix Mix amount (0.0=dry, 1.0=wet)
     */
    public void setReverbWetDryMix(double mix) {
        fxProcessor.setReverbWetDryMix(mix);
    }
    
    /**
     * Sets the wet/dry mix for the distortion effect.
     * 
     * @param mix Mix amount (0.0=dry, 1.0=wet)
     */
    public void setDistortionWetDryMix(double mix) {
        fxProcessor.setDistortionWetDryMix(mix);
    }
    
    /**
     * Sets the effect wet/dry mix for all effects.
     * Legacy method for backward compatibility.
     * 
     * @param mix Mix amount (0.0=dry, 1.0=wet)
     */
    public void setEffectWetDryMix(double mix) {
        setDelayWetDryMix(mix);
        setReverbWetDryMix(mix);
        setDistortionWetDryMix(mix);
        setChorusWetDryMix(mix);
    }
    
    /**
     * Sets the distortion amount.
     * 
     * @param amount Distortion amount (0.0-1.0)
     */
    public void setDistortion(double amount) {
        fxProcessor.setDistortion(amount);
    }
    
    /**
     * Sets the distortion amount.
     * Alias for setDistortion to match UI naming convention.
     * 
     * @param amount Distortion amount (0.0-1.0)
     */
    public void setDistortionAmount(double amount) {
        setDistortion(amount);
    }
    
    /**
     * Enables or disables the delay effect.
     * 
     * @param enabled Whether the delay effect should be enabled
     */
    public void enableDelayEffect(boolean enabled) {
        delayEnabled = enabled;
        updateEffectState();
    }
    
    /**
     * Enables or disables the reverb effect.
     * 
     * @param enabled Whether the reverb effect should be enabled
     */
    public void enableReverbEffect(boolean enabled) {
        reverbEnabled = enabled;
        updateEffectState();
    }
    
    /**
     * Enables or disables the distortion effect.
     * 
     * @param enabled Whether the distortion effect should be enabled
     */
    public void enableDistortionEffect(boolean enabled) {
        distortionEnabled = enabled;
        updateEffectState();
    }
    
    /**
     * Enables or disables the chorus effect.
     * 
     * @param enabled Whether the chorus effect should be enabled
     */
    public void enableChorusEffect(boolean enabled) {
        chorusEnabled = enabled;
        updateEffectState();
    }
    
    /**
     * Enables or disables delay sync mode.
     * When sync is enabled, delay time will be quantized to musical values based on
     * a tempo of 120 BPM, creating rhythmically relevant delay times.
     * 
     * This feature allows for musical delay effects that are synchronized with the
     * implied tempo of the music being played. When enabled, the delay slider will
     * snap to the nearest musical value (16th note, 8th note, quarter note, etc.)
     * 
     * @param enabled Whether sync mode should be enabled
     */
    public void setDelaySyncEnabled(boolean enabled) {
        delaySyncEnabled = enabled;
        
        System.out.println("Delay sync mode " + (enabled ? "enabled" : "disabled"));
        
        // If sync is enabled, quantize the current delay time to musical values
        if (enabled) {
            // Get the current delay time from the FX processor
            double currentDelayTime = fxProcessor.getDelayTime();
            
            // Re-apply it through our setDelayTime method which will now quantize it
            setDelayTime(currentDelayTime);
            
            // Provide feedback about the sync mode
            System.out.println("Delay times will now snap to musical values (based on 120 BPM)");
            System.out.println("Available note values: 16th, dotted 16th, 8th, dotted 8th, quarter, dotted quarter, half");
        } else {
            System.out.println("Delay times can now be set to any value");
        }
    }
    

    
    /**
     * Updates the effect state based on which effects are enabled.
     * This method enables or disables effects in the FX processor.
     */
    private void updateEffectState() {
        fxProcessor.enableDelayEffect(delayEnabled);
        fxProcessor.enableReverbEffect(reverbEnabled);
        fxProcessor.enableDistortionEffect(distortionEnabled);
        fxProcessor.enableChorusEffect(chorusEnabled);
    }
    
    /**
     * Resets all delay components to safe default values.
     * This can be called if audio artifacts or thread safety issues are detected.
     * It resets all delay and reverb parameters to conservative values to prevent
     * buffer overruns and thread safety issues.
     * 
     * @return true if reset was successful, false otherwise
     */
    public boolean resetDelayComponents() {
        try {
            // Call the FXProcessor's reset method to restore safe defaults
            fxProcessor.resetDelayComponents();
            System.out.println("SynthEngine: Successfully reset all delay components");
            return true;
        } catch (Exception e) {
            System.err.println("SynthEngine: Error resetting delay components: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Sets the reverb size (simulated room size).
     * 
     * @param size Room size (0.0-1.0)
     */
    public void setReverbSize(double size) {
        fxProcessor.setReverbSize(size);
    }
    
    /**
     * Sets the reverb decay time.
     * Controls how long the reverb tail persists.
     * 
     * @param decay Decay amount (0.0-1.0)
     */
    public void setReverbDecay(double decay) {
        fxProcessor.setReverbDecay(decay);
    }
    
    /**
     * Sets the reverb gain amount.
     * Controls the overall level of the reverb effect.
     * 
     * @param gain Gain amount (0.0-1.0)
     */
    public void setReverbGain(double gain) {
        fxProcessor.setReverbGain(gain);
    }
    
    /**
     * Sets the reverb frequency (for backward compatibility).
     * Maps to decay in the new implementation.
     * 
     * @param normalizedFreq Normalized frequency value (0.0-1.0)
     */
    public void setReverbFrequency(double normalizedFreq) {
        // Map the old frequency parameter to the new decay parameter
        fxProcessor.setReverbDecay(normalizedFreq);
    }
    
    /**
     * Sets the reverb resonance (for backward compatibility).
     * Maps to gain in the new implementation.
     * 
     * @param resonance Resonance amount (0.0-1.0)
     */
    public void setReverbResonance(double resonance) {
        // Map the old resonance parameter to the new gain parameter
        fxProcessor.setReverbGain(resonance);
    }
    
    /**
     * Sets the chorus rate parameter.
     * Controls the speed of the LFO modulation.
     * 
     * @param rate The chorus rate (0.0-10.0 Hz)
     */
    public void setChorusRate(double rate) {
        fxProcessor.setChorusRate(rate);
    }
    
    /**
     * Sets the chorus depth parameter.
     * Controls how much the delay time varies.
     * 
     * @param depth The chorus depth (0.0-1.0)
     */
    public void setChorusDepth(double depth) {
        fxProcessor.setChorusDepth(depth);
    }
    
    /**
     * Sets the chorus wet/dry mix.
     * Controls the balance between dry and processed signal.
     * 
     * @param mix The wet/dry mix (0.0-1.0)
     */
    public void setChorusWetDryMix(double mix) {
        fxProcessor.setChorusWetDryMix(mix);
    }

    /**
     * Gets the current filter type.
     * 
     * @return The current filter type in use
     */
    public FilterType getCurrentFilterType() {
        return currentFilterType;  // Return current filter mode
    }
    
    /**
     * Gets the current oscillator type.
     * 
     * @return The current oscillator type
     */
    public SynthVoice.OscillatorType getCurrentOscillatorType() {
        return currentOscillatorType;
    }
    
    /**
     * Sets the oscillator type for all voices.
     * Changes the waveform used by all oscillators in the synthesizer.
     * 
     * @param type The new oscillator type to use
     */
    public void setOscillatorType(SynthVoice.OscillatorType type) {
        if (type != currentOscillatorType) {
            currentOscillatorType = type;
            
            // Update all voices with the new oscillator type
            for (SynthVoice voice : voices) {
                voice.setOscillatorType(type);
            }
        }
    }

    /**
     * Updates the filter output connections based on the current filter type.
     * Ensures only one filter output is connected at a time.
     */
    private void connectFilterOutput() {
        // First disconnect all filter outputs from FX processor
        // Disconnect all connections from filter outputs
        filter.lowPass.disconnectAll();    // Disconnect lowpass
        filter.highPass.disconnectAll();   // Disconnect highpass
        filter.bandPass.disconnectAll();   // Disconnect bandpass

        // Connect only the selected filter type to FX processor
        switch (currentFilterType) {
            case LOWPASS:
                fxProcessor.connectInput(filter.lowPass);    // Connect lowpass
                break;
            case HIGHPASS:
                fxProcessor.connectInput(filter.highPass);   // Connect highpass
                break;
            case BANDPASS:
                fxProcessor.connectInput(filter.bandPass);   // Connect bandpass
                break;
        }
    }
}
