package src;

import com.jsyn.Synthesizer;
import com.jsyn.unitgen.*;

/**
 * Represents a single voice in the polyphonic synthesizer.
 * Each voice contains its own oscillator and envelope generator,
 * allowing it to play one note independently of other voices.
 */
public class SynthVoice {
    private UnitOscillator oscillator;     // Sound generator
    private EnvelopeDAHDSR envelope;        // Volume envelope
    private int currentNote = -1;           // Currently playing MIDI note (-1 = none)
    private boolean active = false;         // Whether voice is currently in use

    /**
     * Creates a new synthesizer voice with its own oscillator and envelope.
     * 
     * @param synth The JSyn synthesizer instance to add components to
     */
    public SynthVoice(Synthesizer synth) {
        initializeComponents(synth);     // Create and register components
        configureDefaults();            // Set default parameters
        connectComponents();            // Connect signal path
    }

    /**
     * Creates and registers the audio components for this voice.
     * 
     * @param synth The JSyn synthesizer to register components with
     */
    private void initializeComponents(Synthesizer synth) {
        oscillator = new SineOscillator();      // Create sine wave oscillator
        envelope = new EnvelopeDAHDSR();        // Create DAHDSR envelope
        synth.add(oscillator);                  // Register with synth
        synth.add(envelope);                    // Register with synth
    }

    /**
     * Sets default values for all voice parameters.
     * Configures initial oscillator amplitude and envelope timings.
     */
    private void configureDefaults() {
        // Set oscillator defaults
        oscillator.amplitude.set(0.3);          // 30% amplitude to prevent clipping

        // Configure envelope defaults
        envelope.attack.set(0.08);              // 80ms attack time
        envelope.decay.set(0.2);                // 200ms decay time
        envelope.sustain.set(0.7);              // 70% sustain level
        envelope.release.set(0.3);              // 300ms release time
        envelope.hold.set(0.02);                // 20ms hold time
    }

    /**
     * Connects the oscillator to the envelope generator.
     * Creates the basic signal path: Oscillator -> Envelope
     */
    private void connectComponents() {
        oscillator.output.connect(envelope.amplitude);  // Route oscillator through envelope
    }

    /**
     * Connects this voice's output to a mixer.
     * Allows the voice to be combined with other voices.
     * 
     * @param mixer The mixer to connect this voice to
     */
    public void connectToMixer(Add mixer) {
        envelope.output.connect(mixer.inputA);  // Connect to mixer input
    }

    /**
     * Triggers a note with the specified MIDI note number and velocity.
     * Converts MIDI note to frequency and starts the envelope.
     * 
     * @param midiNote MIDI note number (0-127, where 60 is middle C)
     * @param velocity Note velocity (0.0-1.0)
     */
    public void noteOn(int midiNote, double velocity) {
        currentNote = midiNote;                    // Store current note
        active = true;                             // Mark voice as active
        
        // Convert MIDI note to frequency (A4 = 69 = 440Hz)
        double frequency = 440.0 * Math.pow(2.0, (midiNote - 69.0) / 12.0);
        oscillator.frequency.set(frequency);        // Set oscillator pitch
        
        // Set and trigger envelope
        envelope.input.set(velocity);              // Set envelope amplitude
        envelope.input.on();                       // Start envelope
    }

    /**
     * Releases the current note by starting the envelope's release phase.
     * The voice becomes inactive once the envelope completes.
     */
    public void noteOff() {
        envelope.input.off();           // Start envelope release
        active = false;                 // Mark voice as inactive
    }

    /**
     * Checks if this voice is currently playing a note.
     * 
     * @return true if the voice is active, false otherwise
     */
    public boolean isActive() {
        return active;  // Return active state
    }

    /**
     * Gets the MIDI note number currently being played.
     * 
     * @return The current MIDI note, or -1 if no note is playing
     */
    public int getCurrentNote() {
        return currentNote;  // Return current MIDI note
    }

    /**
     * Sets the envelope attack time.
     * 
     * @param seconds Attack time in seconds
     */
    public void setAttack(double seconds) {
        envelope.attack.set(seconds);  // Set attack time
    }

    /**
     * Sets the envelope decay time.
     * 
     * @param seconds Decay time in seconds
     */
    public void setDecay(double seconds) {
        envelope.decay.set(seconds);  // Set decay time
    }

    /**
     * Sets the envelope sustain level.
     * 
     * @param level Sustain level (0.0-1.0)
     */
    public void setSustain(double level) {
        envelope.sustain.set(level);  // Set sustain level
    }

    /**
     * Sets the envelope release time.
     * 
     * @param seconds Release time in seconds
     */
    public void setRelease(double seconds) {
        envelope.release.set(seconds);  // Set release time
    }
}
