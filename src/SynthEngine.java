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
        volumeRamp.output.connect(lineOut.input);       // Smooth volume changes
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
     * Signal flow: Voices -> Mixer -> Filter -> Amplifier -> Output
     */
    private void setupSignalPath() {
        mixer.output.connect(0, filter.input, 0);     // Connect mixer to filter input
        connectFilterOutput();                        // Connect filter to amp (based on type)
        amp.output.connect(0, lineOut.input, 0);     // Connect amp to audio output
    }

    /**
     * Configures the master amplifier settings.
     * Sets initial volume level to prevent clipping.
     */
    private void configureAmplifier() {
        amp.inputB.set(0.9);  // Set master volume to 90% to prevent clipping
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
     * The frequency is clamped to the audible range.
     * 
     * @param frequency Desired cutoff frequency in Hz (20-20000)
     */
    public void setCutoff(double frequency) {
        // Clamp frequency to audible range (20Hz - 20kHz)
        double scaledFreq = Math.min(20000.0, Math.max(20.0, frequency));
        cutoffRamp.input.set(scaledFreq);  // Set with smoothing
    }

    /**
     * Sets the filter resonance with smoothing.
     * The value is scaled to prevent self-oscillation.
     * 
     * @param resonance Resonance value (0.0-1.0)
     */
    public void setResonance(double resonance) {
        // Scale resonance to safe range (0.0 to 0.6)
        double scaledResonance = resonance * 0.6;  // Prevent extreme resonance
        resonanceRamp.input.set(scaledResonance);  // Set with smoothing
    }

    /**
     * Sets the master volume with smoothing to prevent clicks.
     * 
     * @param volume Volume level (0.0-1.0)
     */
    public void setMasterVolume(double volume) {
        volumeRamp.input.set(volume);  // Set with smoothing
    }

    /**
     * Sets the attack time for all voice envelopes.
     * Attack is the time taken for the sound to reach full volume.
     * 
     * @param seconds Attack time in seconds
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
     * @param seconds Decay time in seconds
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
     * @param level Sustain level (0.0-1.0)
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
     * @param seconds Release time in seconds
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
     * 
     * @param type The new filter type to use
     */
    public void setFilterType(FilterType type) {
        if (type != currentFilterType) {           // Only change if different
            currentFilterType = type;               // Update current type
            connectFilterOutput();                  // Reconnect filter outputs
        }
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
     * Updates the filter output connections based on the current filter type.
     * Ensures only one filter output is connected at a time.
     */
    private void connectFilterOutput() {
        // First disconnect all filter outputs
        filter.lowPass.disconnect(0, amp.inputA, 0);    // Disconnect lowpass
        filter.highPass.disconnect(0, amp.inputA, 0);   // Disconnect highpass
        filter.bandPass.disconnect(0, amp.inputA, 0);   // Disconnect bandpass

        // Connect only the selected filter type
        switch (currentFilterType) {
            case LOWPASS:
                filter.lowPass.connect(0, amp.inputA, 0);    // Connect lowpass
                break;
            case HIGHPASS:
                filter.highPass.connect(0, amp.inputA, 0);   // Connect highpass
                break;
            case BANDPASS:
                filter.bandPass.connect(0, amp.inputA, 0);   // Connect bandpass
                break;
        }
    }
}
