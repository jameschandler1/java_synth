package src;

import com.jsyn.Synthesizer;
import com.jsyn.unitgen.*;

/**
 * Represents a single voice in the polyphonic synthesizer.
 * Each voice contains its own oscillator and envelope generator,
 * allowing it to play one note independently of other voices.
 */
public class SynthVoice {
    /**
     * Enum defining the available oscillator waveform types.
     * Each waveform has a distinct timbre and harmonic content.
     */
    public enum OscillatorType {
        SINE("Sine"),           // Pure sine wave, no harmonics
        SQUARE("Square"),       // Square wave, rich in odd harmonics
        SAW("Saw"),             // Sawtooth wave, rich in all harmonics
        TRIANGLE("Triangle"),   // Triangle wave, odd harmonics that fall off quickly
        NOISE("Noise");         // White noise, all frequencies at random phases
        
        private final String displayName;
        
        OscillatorType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    private UnitOscillator oscillator;     // Sound generator
    private EnvelopeDAHDSR envelope;        // Volume envelope
    private int currentNote = -1;           // Currently playing MIDI note (-1 = none)
    private boolean active = false;         // Whether voice is currently in use
    private OscillatorType currentOscType = OscillatorType.SINE;  // Current oscillator type
    private Synthesizer synth;              // Reference to the synth for adding/removing units

    /**
     * Creates a new synthesizer voice with its own oscillator and envelope.
     * 
     * @param synth The JSyn synthesizer instance to add components to
     */
    public SynthVoice(Synthesizer synth) {
        this.synth = synth;                 // Store synth reference for later use
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
        // Create default sine wave oscillator
        oscillator = createOscillator(OscillatorType.SINE);
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
     * @param seconds Attack time in seconds (0.0-3.0)
     */
    public void setAttack(double seconds) {
        // Clamp the attack time to 0.0-3.0 seconds range
        double safeAttack = Math.min(Math.max(seconds, 0.0), 3.0);
        
        envelope.attack.set(safeAttack);  // Set attack time
    }

    /**
     * Sets the envelope decay time.
     * 
     * @param seconds Decay time in seconds (0.0-3.0)
     */
    public void setDecay(double seconds) {
        // Clamp the decay time to 0.0-3.0 seconds range
        double safeDecay = Math.min(Math.max(seconds, 0.0), 3.0);
        
        envelope.decay.set(safeDecay);  // Set decay time
    }

    /**
     * Sets the envelope sustain level.
     * 
     * @param level Sustain level (0.0-3.0)
     */
    public void setSustain(double level) {
        // Clamp the sustain level to 0.0-3.0 range
        double safeLevel = Math.min(Math.max(level, 0.0), 3.0);
        
        // Normalize for internal processing (0.0-1.0)
        double normalizedLevel = Math.min(safeLevel / 3.0, 1.0);
        
        envelope.sustain.set(normalizedLevel);  // Set sustain level
    }

    /**
     * Sets the envelope release time.
     * 
     * @param seconds Release time in seconds (0.0-3.0)
     */
    public void setRelease(double seconds) {
        // Clamp the release time to 0.0-3.0 seconds range
        double safeRelease = Math.min(Math.max(seconds, 0.0), 3.0);
        
        envelope.release.set(safeRelease);  // Set release time
    }
    
    /**
     * Changes the oscillator type to the specified waveform.
     * This method replaces the current oscillator with a new one of the specified type,
     * preserving the current frequency and amplitude settings.
     * 
     * @param type The new oscillator type to use
     */
    public void setOscillatorType(OscillatorType type) {
        if (type == currentOscType) {
            return;  // No change needed
        }
        
        // Store current settings
        double currentFreq = 440.0;  // Default frequency
        double currentAmp = 0.3;     // Default amplitude
        
        if (oscillator != null) {
            currentFreq = oscillator.frequency.get();
            currentAmp = oscillator.amplitude.get();
            
            // Disconnect and remove old oscillator
            oscillator.output.disconnect(0, envelope.amplitude, 0);
            synth.remove(oscillator);
        }
        
        // Create new oscillator of the requested type
        oscillator = createOscillator(type);
        synth.add(oscillator);
        
        // Restore settings
        oscillator.frequency.set(currentFreq);
        oscillator.amplitude.set(currentAmp);
        
        // Connect to envelope
        oscillator.output.connect(envelope.amplitude);
        
        // Update current type
        currentOscType = type;
    }
    
    /**
     * Creates a new oscillator of the specified type.
     * 
     * @param type The type of oscillator to create
     * @return A new UnitOscillator instance of the specified type
     */
    private UnitOscillator createOscillator(OscillatorType type) {
        switch (type) {
            case SINE:
                return new SineOscillator();
            case SQUARE:
                return new SquareOscillator();
            case SAW:
                return new SawtoothOscillator();
            case TRIANGLE:
                return new TriangleOscillator();
            case NOISE:
                // WhiteNoise is not a UnitOscillator, so we need to wrap it
                // We'll use a RedNoise (filtered noise) with high frequency to approximate white noise
                RedNoise noise = new RedNoise();
                noise.frequency.set(10000); // High cutoff for whiter noise
                return noise;
            default:
                return new SineOscillator();  // Default to sine
        }
    }
    
    /**
     * Gets the current oscillator type.
     * 
     * @return The current oscillator type
     */
    public OscillatorType getCurrentOscillatorType() {
        return currentOscType;
    }
}
