package src;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import com.formdev.flatlaf.FlatDarkLaf;

public class SynthUI {
    /**
     * Enum defining different parameter types for appropriate logarithmic scaling.
     * Each type has a different scaling factor and visual representation.
     */
    private enum ParameterType {
        VOLUME,     // Volume controls (0-100%)
        FREQUENCY,  // Frequency controls (Hz)
        TIME,       // Time-based controls (ms, seconds)
        RESONANCE,  // Resonance/Q controls (0-100%)
        GAIN,       // Gain controls (0-100%)
        MIX,        // Mix/balance controls (0-100%)
        RATE        // Rate controls (Hz, frequency of modulation)
    }
    private JFrame frame;
    private SynthEngine engine;
    private HashMap<Integer, Integer> keyboardMapping;
    // Set to track currently pressed keys to prevent stuck notes
    private Set<Integer> activeKeys = new HashSet<>();
    // Map to store MIDI note numbers to their corresponding note names (e.g., 60 -> "C4")
    private HashMap<Integer, String> noteNames;
    
    // Label to display the currently pressed note in the UI
    private JLabel currentNoteLabel;
    
    // 80s-style retro font for all text elements
    private Font retroFont;
    
    // Gradient background colors for the 80s aesthetic
    private Color gradientStart = new Color(25, 25, 40);  // Dark blue-purple start color
    private Color gradientEnd = new Color(40, 40, 60);    // Slightly lighter end color

    public SynthUI(SynthEngine engine) {
        this.engine = engine;
        initializeKeyboardMapping();
        initializeNoteNames();
        loadRetroFont();
        createAndShowGUI();
    }
    
    /**
     * Loads the VCR OSD Mono font which has an 80s retro aesthetic.
     * This font resembles old CRT displays and arcade machines.
     * If the font can't be loaded, falls back to a standard monospaced font.
     */
    private void loadRetroFont() {
        try {
            // Load the custom font from the fonts directory
            retroFont = Font.createFont(Font.TRUETYPE_FONT, new File("fonts/VCR_OSD_MONO.ttf"));
            
            // Register the font with the graphics environment so it can be used
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(retroFont);
        } catch (IOException | FontFormatException e) {
            System.out.println("Error loading font: " + e.getMessage());
            retroFont = new Font("Monospaced", Font.BOLD, 12); // Fallback font if custom font fails
        }
    }

    /**
     * Maps computer keyboard keys to MIDI note numbers for virtual keyboard functionality.
     * This allows the user to play the synthesizer using their computer keyboard.
     * The mapping follows a piano-like layout where the home row represents white keys
     * and the row above represents black keys.
     */
    private void initializeKeyboardMapping() {
        keyboardMapping = new HashMap<>();
        // Map computer keyboard to MIDI notes (C4 = 60)
        keyboardMapping.put(KeyEvent.VK_A, 60);  // C4
        keyboardMapping.put(KeyEvent.VK_W, 61);  // C#4
        keyboardMapping.put(KeyEvent.VK_S, 62);  // D4
        keyboardMapping.put(KeyEvent.VK_E, 63);  // D#4
        keyboardMapping.put(KeyEvent.VK_D, 64);  // E4
        keyboardMapping.put(KeyEvent.VK_F, 65);  // F4
        keyboardMapping.put(KeyEvent.VK_T, 66);  // F#4
        keyboardMapping.put(KeyEvent.VK_G, 67);  // G4
        keyboardMapping.put(KeyEvent.VK_Y, 68);  // G#4
        keyboardMapping.put(KeyEvent.VK_H, 69);  // A4
        keyboardMapping.put(KeyEvent.VK_U, 70);  // A#4
        keyboardMapping.put(KeyEvent.VK_J, 71);  // B4
        keyboardMapping.put(KeyEvent.VK_K, 72);  // C5
    }
    
    /**
     * Maps MIDI note numbers to their corresponding musical note names.
     * This mapping is used to display the current note name when a key is pressed.
     * The standard MIDI note number system has 60 as middle C (C4).
     */
    private void initializeNoteNames() {
        noteNames = new HashMap<>();
        // Map MIDI note numbers to note names (C4 = middle C = MIDI note 60)
        noteNames.put(60, "C4");
        noteNames.put(61, "C#4");
        noteNames.put(62, "D4");
        noteNames.put(63, "D#4");
        noteNames.put(64, "E4");
        noteNames.put(65, "F4");
        noteNames.put(66, "F#4");
        noteNames.put(67, "G4");
        noteNames.put(68, "G#4");
        noteNames.put(69, "A4");
        noteNames.put(70, "A#4");
        noteNames.put(71, "B4");
        noteNames.put(72, "C5");
    }

    /**
     * Creates and configures the main user interface for the synthesizer.
     * Sets up the modern dark theme, creates all UI components including sliders,
     * buttons, and panels, and organizes them into a cohesive layout.
     * Also configures event listeners for user interaction.
     */
    private void createAndShowGUI() {
        // Set up modern dark theme
        FlatDarkLaf.setup();
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("Slider.trackWidth", 4);
        UIManager.put("Slider.thumbSize", new Dimension(16, 16));
        
        frame = new JFrame("Synthesizer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                engine.shutdown();
            }
        });

        // Create main panel with modern layout and gradient background
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Create a high-quality gradient background
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int w = getWidth();
                int h = getHeight();
                // Create a vertical gradient from top to bottom
                GradientPaint gp = new GradientPaint(0, 0, gradientStart, 0, h, gradientEnd);
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, w, h);
                g2d.dispose();
            }
        };
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setOpaque(false);

        // Create a container panel that wraps the main panel for proper scrolling behavior
        JPanel containerPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Apply the same gradient to the container panel to ensure consistent background
                // when scrolling or resizing the window
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int w = getWidth();
                int h = getHeight();
                GradientPaint gp = new GradientPaint(0, 0, gradientStart, 0, h, gradientEnd);
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, w, h);
                g2d.dispose();
            }
        };
        containerPanel.setOpaque(true);
        containerPanel.add(mainPanel, BorderLayout.NORTH);

        // Create a scroll pane that only shows vertical scrollbar if needed
        JScrollPane scrollPane = new JScrollPane(containerPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(30, 30, 30));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Set minimum size to ensure controls are usable
        mainPanel.setMinimumSize(new Dimension(400, 600));

        // Create section panels
        JPanel oscillatorSection = createSection("Oscillator");
        JPanel filterSection = createSection("Filter");
        JPanel envelopeSection = createSection("Envelope");
        JPanel masterSection = createSection("Master");
        
        // Add oscillator type selector
        JPanel oscillatorTypePanel = new JPanel();
        oscillatorTypePanel.setLayout(new BoxLayout(oscillatorTypePanel, BoxLayout.X_AXIS));
        oscillatorTypePanel.setOpaque(false);
        oscillatorTypePanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        oscillatorTypePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        oscillatorTypePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        
        JLabel oscLabel = new JLabel("Waveform");
        oscLabel.setForeground(new Color(200, 200, 200));
        oscLabel.setPreferredSize(new Dimension(80, 25));
        oscillatorTypePanel.add(oscLabel);
        oscillatorTypePanel.add(Box.createHorizontalStrut(10));
        
        // Create array of oscillator types
        SynthVoice.OscillatorType[] oscTypes = SynthVoice.OscillatorType.values();
        JComboBox<SynthVoice.OscillatorType> oscTypeCombo = new JComboBox<>(oscTypes);
        oscTypeCombo.setSelectedItem(engine.getCurrentOscillatorType());
        oscTypeCombo.addActionListener(e -> {
            SynthVoice.OscillatorType selectedType = (SynthVoice.OscillatorType) oscTypeCombo.getSelectedItem();
            engine.setOscillatorType(selectedType);
            SwingUtilities.invokeLater(() -> frame.requestFocusInWindow());
        });
        oscillatorTypePanel.add(oscTypeCombo);
        oscillatorTypePanel.add(Box.createHorizontalGlue());
        
        oscillatorSection.add(oscillatorTypePanel);

        // Add filter type selector
        JPanel filterTypePanel = new JPanel();
        filterTypePanel.setLayout(new BoxLayout(filterTypePanel, BoxLayout.X_AXIS));
        filterTypePanel.setOpaque(false);
        filterTypePanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        filterTypePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterTypePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        
        JLabel filterLabel = new JLabel("Type");
        filterLabel.setForeground(new Color(200, 200, 200));
        filterLabel.setPreferredSize(new Dimension(80, 25));
        filterTypePanel.add(filterLabel);
        filterTypePanel.add(Box.createHorizontalStrut(10));
        
        // Create array of filter types manually instead of using values() method
        // This avoids potential issues with enum access across different class files
        SynthEngine.FilterType[] filterTypes = {
            SynthEngine.FilterType.LOWPASS,   // Low pass filter (allows low frequencies)
            SynthEngine.FilterType.HIGHPASS,  // High pass filter (allows high frequencies)
            SynthEngine.FilterType.BANDPASS   // Band pass filter (allows frequencies in a range)
        };
        JComboBox<SynthEngine.FilterType> filterTypeCombo = new JComboBox<>(filterTypes);
        filterTypeCombo.setSelectedItem(engine.getCurrentFilterType());
        filterTypeCombo.addActionListener(e -> {
            SynthEngine.FilterType selectedType = (SynthEngine.FilterType) filterTypeCombo.getSelectedItem();
            engine.setFilterType(selectedType);
            
            // Adjust control ranges based on filter type
            updateControlsForFilterType(selectedType);
            
            SwingUtilities.invokeLater(() -> frame.requestFocusInWindow());
        });
        filterTypePanel.add(filterTypeCombo);
        filterTypePanel.add(Box.createHorizontalGlue());
        
        filterSection.add(filterTypePanel);

        // Add filter controls with logarithmic scaling for more natural control
        // Traditional synth filter range: 20Hz - 20kHz with default at 1kHz
        addLogSlider(filterSection, "Cutoff", 20, 20000, 1000, ParameterType.FREQUENCY, 
            value -> engine.setCutoff(value));
        // Traditional resonance range: 0.0 - 1.0 with default at 0.1 (10%)
        addLogSlider(filterSection, "Resonance", 0, 100, 10, ParameterType.RESONANCE, 
            value -> engine.setResonance(value / 100.0));

        // Add envelope controls with logarithmic scaling for more natural time response
        // Traditional synth ADSR ranges:
        // Attack: 1ms - 5000ms (5 sec) with default at 10ms
        addLogSlider(envelopeSection, "Attack", 1, 5000, 10, ParameterType.TIME, 
            value -> engine.setEnvelopeAttack(value / 1000.0));
        // Decay: 1ms - 5000ms (5 sec) with default at 200ms
        addLogSlider(envelopeSection, "Decay", 1, 5000, 200, ParameterType.TIME, 
            value -> engine.setEnvelopeDecay(value / 1000.0));
        // Sustain: 0-100% with default at 70%
        addLogSlider(envelopeSection, "Sustain", 0, 100, 70, ParameterType.GAIN, 
            value -> engine.setEnvelopeSustain(value / 100.0));
        // Release: 1ms - 10000ms (10 sec) with default at 300ms
        addLogSlider(envelopeSection, "Release", 1, 10000, 300, ParameterType.TIME, 
            value -> engine.setEnvelopeRelease(value / 1000.0));

        // Add master controls with logarithmic volume slider for better control
        // Enhanced master volume: 0-150% with default at 70%
        addLogSlider(masterSection, "Volume", 0, 150, 70, ParameterType.VOLUME, 
            value -> engine.setMasterVolume(value / 30.0)); // Scale 0-150 to 0-5.0 range

        // Create a dedicated panel to display the currently pressed note
        JPanel noteDisplayPanel = createSection("Current Note");
        
        // Create the label that will show the current note name (e.g., "C4")
        currentNoteLabel = new JLabel("Press a key");
        // Apply the retro font with a larger size for emphasis
        currentNoteLabel.setFont(retroFont.deriveFont(24f));
        // Use bright cyan color for authentic 80s digital display look
        currentNoteLabel.setForeground(new Color(0, 255, 255));
        // Center the text both horizontally and vertically
        currentNoteLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        currentNoteLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Create a container for the note label with proper centering
        JPanel noteDisplayContainer = new JPanel();
        noteDisplayContainer.setLayout(new BoxLayout(noteDisplayContainer, BoxLayout.X_AXIS));
        noteDisplayContainer.setOpaque(false); // Keep transparent for gradient background
        noteDisplayContainer.add(Box.createHorizontalGlue()); // Add space on left
        noteDisplayContainer.add(currentNoteLabel);           // Add the note label
        noteDisplayContainer.add(Box.createHorizontalGlue()); // Add space on right
        noteDisplayContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        noteDisplayContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        
        noteDisplayPanel.add(noteDisplayContainer);
        
        // Create effects section
        JPanel effectsSection = createSection("Effects");
        
        // Effect on/off switches panel
        JPanel effectTogglePanel = new JPanel();
        effectTogglePanel.setLayout(new BoxLayout(effectTogglePanel, BoxLayout.X_AXIS));
        effectTogglePanel.setOpaque(false);
        effectTogglePanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        effectTogglePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        effectTogglePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        
        JLabel enableLabel = new JLabel("Enable");
        enableLabel.setForeground(new Color(200, 200, 200));
        enableLabel.setPreferredSize(new Dimension(80, 25));
        effectTogglePanel.add(enableLabel);
        effectTogglePanel.add(Box.createHorizontalStrut(10));
        
        effectsSection.add(effectTogglePanel);
        

        
        JToggleButton delayToggle = new JToggleButton("Delay");
        delayToggle.setFont(retroFont.deriveFont(12f));
        delayToggle.setMaximumSize(new Dimension(80, 25));
        delayToggle.setPreferredSize(new Dimension(80, 25));
        delayToggle.addActionListener(e -> {
            boolean isSelected = delayToggle.isSelected();
            engine.enableDelayEffect(isSelected);
            SwingUtilities.invokeLater(() -> frame.requestFocusInWindow());
        });
        
        JToggleButton reverbToggle = new JToggleButton("Reverb");
        reverbToggle.setFont(retroFont.deriveFont(12f));
        reverbToggle.setMaximumSize(new Dimension(80, 25));
        reverbToggle.setPreferredSize(new Dimension(80, 25));
        reverbToggle.addActionListener(e -> {
            boolean isSelected = reverbToggle.isSelected();
            engine.enableReverbEffect(isSelected);
            SwingUtilities.invokeLater(() -> frame.requestFocusInWindow());
        });
        
        JToggleButton distortionToggle = new JToggleButton("Dist");
        distortionToggle.setFont(retroFont.deriveFont(12f));
        distortionToggle.setMaximumSize(new Dimension(80, 25));
        distortionToggle.setPreferredSize(new Dimension(80, 25));
        distortionToggle.addActionListener(e -> {
            boolean isSelected = distortionToggle.isSelected();
            engine.enableDistortionEffect(isSelected);
            SwingUtilities.invokeLater(() -> frame.requestFocusInWindow());
        });
        
        JToggleButton chorusToggle = new JToggleButton("Chorus");
        chorusToggle.setFont(retroFont.deriveFont(12f));
        chorusToggle.setMaximumSize(new Dimension(80, 25));
        chorusToggle.setPreferredSize(new Dimension(80, 25));
        chorusToggle.addActionListener(e -> {
            boolean isSelected = chorusToggle.isSelected();
            engine.enableChorusEffect(isSelected);
            SwingUtilities.invokeLater(() -> frame.requestFocusInWindow());
        });
        
        effectTogglePanel.add(delayToggle);
        effectTogglePanel.add(Box.createHorizontalStrut(5));
        effectTogglePanel.add(reverbToggle);
        effectTogglePanel.add(Box.createHorizontalStrut(5));
        effectTogglePanel.add(distortionToggle);
        effectTogglePanel.add(Box.createHorizontalStrut(5));
        effectTogglePanel.add(chorusToggle);
        effectTogglePanel.add(Box.createHorizontalGlue());
        
        effectsSection.add(effectTogglePanel);
        
        // Delay controls
        JPanel delayPanel = new JPanel();
        delayPanel.setLayout(new BoxLayout(delayPanel, BoxLayout.Y_AXIS));
        delayPanel.setOpaque(false);
        delayPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        delayPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100), 1, true),
            "Delay",
            0,
            0,
            retroFont.deriveFont(12f),
            new Color(180, 180, 180)
        ));
        
        // Delay time control with logarithmic scaling for more natural time response
        // Traditional delay time: 10ms - 2000ms (2 sec) with default at 300ms
        addLogSlider(delayPanel, "Time", 10, 2000, 300, ParameterType.TIME, value -> {
            // Convert ms to seconds
            engine.setDelayTime(value / 1000.0);
        });
        
        // Delay feedback control with logarithmic scaling for more natural response
        // Traditional delay feedback: 0-90% with default at 40%
        // Limiting to 90% to prevent runaway feedback
        addLogSlider(delayPanel, "Feedback", 0, 90, 40, ParameterType.GAIN, value -> {
            // Convert 0-90 to 0.0-0.9 feedback amount
            engine.setDelayFeedback(value / 100.0);
        });
        
        // Delay sync toggle
        JPanel delaySyncPanel = new JPanel();
        delaySyncPanel.setLayout(new BoxLayout(delaySyncPanel, BoxLayout.X_AXIS));
        delaySyncPanel.setOpaque(false);
        delaySyncPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        delaySyncPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        delaySyncPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        
        JLabel syncLabel = new JLabel("Sync");
        syncLabel.setForeground(new Color(200, 200, 200));
        syncLabel.setPreferredSize(new Dimension(80, 25));
        delaySyncPanel.add(syncLabel);
        delaySyncPanel.add(Box.createHorizontalStrut(10));
        
        JToggleButton syncToggle = new JToggleButton("OFF");
        syncToggle.setFont(retroFont.deriveFont(12f));
        syncToggle.setMaximumSize(new Dimension(80, 25));
        syncToggle.setPreferredSize(new Dimension(80, 25));
        syncToggle.addActionListener(e -> {
            boolean isSelected = syncToggle.isSelected();
            syncToggle.setText(isSelected ? "ON" : "OFF");
            engine.setDelaySyncEnabled(isSelected);
            frame.requestFocusInWindow();
        });
        
        delaySyncPanel.add(syncToggle);
        delaySyncPanel.add(Box.createHorizontalGlue());
        // Delay wet/dry mix control
        // Traditional wet/dry mix: 0-100% with default at 50%
        addLogSlider(delayPanel, "Mix", 0, 100, 50, ParameterType.MIX, 
            value -> engine.setDelayWetDryMix(value / 100.0));
            
        delayPanel.add(delaySyncPanel);
        
        effectsSection.add(delayPanel);
        
        // Reverb controls
        JPanel reverbPanel = new JPanel();
        reverbPanel.setLayout(new BoxLayout(reverbPanel, BoxLayout.Y_AXIS));
        reverbPanel.setOpaque(false);
        reverbPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        reverbPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100), 1, true),
            "Reverb",
            0,
            0,
            retroFont.deriveFont(12f),
            new Color(180, 180, 180)
        ));
        
        // Reverb size control with logarithmic scaling for more natural response
        // Traditional reverb size: 0-100% with default at 50%
        addLogSlider(reverbPanel, "Size", 0, 100, 50, ParameterType.GAIN, value -> {
            engine.setReverbSize(value / 100.0);
        });
        
        // Reverb decay control with logarithmic scaling for more natural time response
        // Traditional reverb decay: 0.1-10 seconds with default at 1.5 seconds
        addLogSlider(reverbPanel, "Decay", 100, 10000, 1500, ParameterType.TIME, value -> {
            engine.setReverbDecay(value / 1000.0);
        });
        
        // Reverb gain control with logarithmic scaling for more natural response
        // Traditional reverb gain: 0-100% with default at 80%
        addLogSlider(reverbPanel, "Gain", 0, 100, 80, ParameterType.GAIN, value -> {
            engine.setReverbGain(value / 100.0);
        });
        
        // Reverb wet/dry mix control with logarithmic scaling
        // Traditional reverb mix: 0-100% with default at 30%
        addLogSlider(reverbPanel, "Mix", 0, 100, 30, ParameterType.MIX, 
            value -> engine.setReverbWetDryMix(value / 100.0));
            
        effectsSection.add(reverbPanel);
        
        // Distortion controls
        JPanel distortionPanel = new JPanel();
        distortionPanel.setLayout(new BoxLayout(distortionPanel, BoxLayout.Y_AXIS));
        distortionPanel.setOpaque(false);
        distortionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        distortionPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100), 1, true),
            "Distortion",
            0,
            0,
            retroFont.deriveFont(12f),
            new Color(180, 180, 180)
        ));
        
        // Distortion gain control with logarithmic scaling for more natural response
        // Traditional distortion drive: 0-100% with default at 30%
        addLogSlider(distortionPanel, "Drive", 0, 100, 30, ParameterType.GAIN, value -> {
            engine.setDistortionAmount(value / 100.0);
        });
        
        // Distortion wet/dry mix control with logarithmic scaling
        // Traditional distortion mix: 0-100% with default at 50%
        addLogSlider(distortionPanel, "Mix", 0, 100, 50, ParameterType.MIX, 
            value -> engine.setDistortionWetDryMix(value / 100.0));
            
        effectsSection.add(distortionPanel);
        
        // Chorus controls
        JPanel chorusPanel = new JPanel();
        chorusPanel.setLayout(new BoxLayout(chorusPanel, BoxLayout.Y_AXIS));
        chorusPanel.setOpaque(false);
        chorusPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        chorusPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100), 1, true),
            "Chorus",
            0,
            0,
            retroFont.deriveFont(12f),
            new Color(180, 180, 180)
        ));
        
        // Chorus rate control with logarithmic scaling for more natural response
        // Standard chorus rate: 0.5-5 Hz with default at 0.8 Hz (typical vintage chorus)
        addLogSlider(chorusPanel, "Rate", 50, 500, 80, ParameterType.FREQUENCY, value -> {
            // Convert 50-500 to 0.5-5.0 Hz
            double rate = value / 100.0;
            engine.setChorusRate(rate / 1.67); // Scale to 0-3 range
            System.out.println("Chorus rate set to: " + rate + " Hz");
        });
        
        // Chorus depth control with logarithmic scaling for more natural response
        // Standard chorus depth: 0-100% with default at 25% (typical vintage chorus)
        addLogSlider(chorusPanel, "Depth", 0, 100, 25, ParameterType.GAIN, value -> {
            // Convert 0-100 to 0.0-3.0 depth amount
            double depth = value / 33.33; // Scale to 0-3 range
            engine.setChorusDepth(depth);
            System.out.println("Chorus depth set to: " + value + "%");
        });
        
        // Chorus wet/dry mix control with logarithmic scaling
        // Standard chorus mix: 0-100% with default at 35% (typical vintage chorus)
        addLogSlider(chorusPanel, "Mix", 0, 100, 35, ParameterType.MIX, 
            value -> {
                double mix = value / 33.33; // Scale to 0-3 range
                engine.setChorusWetDryMix(mix);
                System.out.println("Chorus mix set to: " + value + "%");
            });
            
        effectsSection.add(chorusPanel);
        
        // Add sections to main panel with proper spacing
        mainPanel.add(oscillatorSection);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(filterSection);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(envelopeSection);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(effectsSection);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(masterSection);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(noteDisplayPanel);
        mainPanel.add(Box.createVerticalGlue());

        // Use KeyboardFocusManager to capture all key events regardless of focus
        // This is more reliable than component-level key listeners, especially with dropdowns
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                // Process all key events when our frame is active, regardless of which component has focus
                if (frame.isActive()) {
                    if (e.getID() == KeyEvent.KEY_PRESSED) {
                        Integer note = keyboardMapping.get(e.getKeyCode());
                        if (note != null && !activeKeys.contains(e.getKeyCode())) {
                            // Only trigger note if key wasn't already pressed
                            activeKeys.add(e.getKeyCode());
                            engine.noteOn(note, 0.8);
                            // Update the note display with the current note name
                            String noteName = noteNames.get(note);
                            if (noteName != null) {
                                currentNoteLabel.setText(noteName); // Show the note being played
                            }
                        }
                    } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                        Integer note = keyboardMapping.get(e.getKeyCode());
                        if (note != null) {
                            // Remove from active keys set
                            activeKeys.remove(e.getKeyCode());
                            engine.noteOff(note);
                            // Reset the note display when key is released
                            if (activeKeys.isEmpty()) {
                                currentNoteLabel.setText("Press a key");
                            }
                        }
                    }
                }
                // Return false to allow the event to be processed by other listeners
                return false;
            }
        });
        
        // Add window focus listener to release all notes when window loses focus
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                // Release all active notes when window loses focus
                for (Integer keyCode : new ArrayList<>(activeKeys)) {
                    Integer note = keyboardMapping.get(keyCode);
                    if (note != null) {
                        engine.noteOff(note);
                    }
                }
                activeKeys.clear();
                currentNoteLabel.setText("Press a key");
            }
        });

        frame.add(scrollPane);
        frame.setMinimumSize(new Dimension(450, 650));
        frame.setPreferredSize(new Dimension(500, 700));
        frame.setFocusable(true);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
    /**
     * Updates control ranges based on the selected filter type.
     * Different filter types work best with different parameter ranges.
     * 
     * @param filterType The currently selected filter type
     */
    private void updateControlsForFilterType(SynthEngine.FilterType filterType) {
        // Find the cutoff and resonance sliders
        Component[] components = frame.getContentPane().getComponents();
        JSlider cutoffSlider = null;
        JSlider resonanceSlider = null;
        
        // Search through all components to find our sliders
        for (Component c : components) {
            if (c instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) c;
                if (scrollPane.getViewport().getView() instanceof JPanel) {
                    JPanel containerPanel = (JPanel) scrollPane.getViewport().getView();
                    if (containerPanel.getComponent(0) instanceof JPanel) {
                        JPanel mainPanel = (JPanel) containerPanel.getComponent(0);
                        
                        // Search through the main panel components
                        for (Component section : mainPanel.getComponents()) {
                            if (section instanceof JPanel) {
                                JPanel sectionPanel = (JPanel) section;
                                // Check if this is the filter section
                                if (sectionPanel.getBorder() instanceof javax.swing.border.TitledBorder) {
                                    javax.swing.border.TitledBorder border = 
                                        (javax.swing.border.TitledBorder) sectionPanel.getBorder();
                                    if (border.getTitle().equals("Filter")) {
                                        // Found the filter section, now find the sliders
                                        for (Component control : sectionPanel.getComponents()) {
                                            if (control instanceof JPanel) {
                                                for (Component c2 : ((JPanel) control).getComponents()) {
                                                    if (c2 instanceof JSlider) {
                                                        JSlider slider = (JSlider) c2;
                                                        String name = slider.getName();
                                                        if (name != null) {
                                                            if (name.equals("Cutoff")) {
                                                                cutoffSlider = slider;
                                                            } else if (name.equals("Resonance")) {
                                                                resonanceSlider = slider;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Adjust ranges based on filter type if sliders were found
        if (cutoffSlider != null) {
            int currentValue = cutoffSlider.getValue();
            double currentPct = (double)currentValue / cutoffSlider.getMaximum();
            
            switch (filterType) {
                case LOWPASS:
                    // For lowpass, emphasize lower frequencies
                    cutoffSlider.setMaximum(20000);
                    // Set default to 1kHz for lowpass
                    int newLowpassValue = (int)(currentPct * 20000);
                    cutoffSlider.setValue(Math.max(20, Math.min(20000, newLowpassValue)));
                    break;
                    
                case HIGHPASS:
                    // For highpass, emphasize mid-high frequencies
                    cutoffSlider.setMaximum(15000);
                    // Set default to 5kHz for highpass
                    int newHighpassValue = (int)(currentPct * 15000);
                    cutoffSlider.setValue(Math.max(200, Math.min(15000, newHighpassValue)));
                    break;
                    
                case BANDPASS:
                    // For bandpass, focus on mid-range frequencies
                    cutoffSlider.setMaximum(10000);
                    // Set default to 2kHz for bandpass
                    int newBandpassValue = (int)(currentPct * 10000);
                    cutoffSlider.setValue(Math.max(100, Math.min(10000, newBandpassValue)));
                    break;
            }
        }
        
        if (resonanceSlider != null) {
            int currentValue = resonanceSlider.getValue();
            double currentPct = (double)currentValue / resonanceSlider.getMaximum();
            
            switch (filterType) {
                case LOWPASS:
                    // Lowpass typically uses moderate resonance
                    resonanceSlider.setMaximum(100);
                    resonanceSlider.setValue((int)(currentPct * 100));
                    break;
                    
                case HIGHPASS:
                    // Highpass typically uses less resonance to avoid harshness
                    resonanceSlider.setMaximum(80);
                    resonanceSlider.setValue((int)(currentPct * 80));
                    break;
                    
                case BANDPASS:
                    // Bandpass can use higher resonance for more pronounced effect
                    resonanceSlider.setMaximum(120);
                    resonanceSlider.setValue((int)(currentPct * 120));
                    break;
            }
        }
    }

    /**
     * Creates a styled section panel with a title and rounded corners.
     * Each section represents a logical group of controls (Filter, Envelope, etc.)
     * 
     * @param title The title to display at the top of the section
     * @return A configured panel for containing related controls
     */
    private JPanel createSection(String title) {
        // Create a panel with custom painting for semi-transparent background with rounded corners
        JPanel section = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                // Enable anti-aliasing for smooth rounded corners
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Semi-transparent dark background (230/255 alpha) for section panels
                g2d.setColor(new Color(40, 40, 40, 230));
                // Draw a rounded rectangle with 15px corner radius
                g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15));
                g2d.dispose();
            }
        };
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 100, 100), 1, true),
            new EmptyBorder(15, 15, 15, 15)
        ));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getMaximumSize().height));
        section.setMinimumSize(new Dimension(380, section.getMinimumSize().height));

        JLabel titleLabel = new JLabel(title.toUpperCase());
        titleLabel.setFont(retroFont.deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(new Color(200, 200, 200));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(titleLabel);
        section.add(Box.createVerticalStrut(10));

        return section;
    }

    // This method has been replaced by addLogSlider which provides logarithmic scaling
    // for more natural control of audio parameters

    @FunctionalInterface
    interface SliderCallback {
        void onValueChanged(double value);
    }
    
    /**
     * Formats the value label based on parameter type.
     * 
     * @param value The raw slider value
     * @param paramType The type of parameter (frequency, time, volume, etc.)
     * @param min The minimum value of the parameter range
     * @param max The maximum value of the parameter range
     * @return Formatted string representation of the value with appropriate units
     */
    private String formatValueLabel(int value, ParameterType paramType, int min, int max) {
        switch (paramType) {
            case FREQUENCY:
                if (value >= 1000) {
                    return (value / 1000) + "kHz";
                } else {
                    return value + "Hz";
                }
            case TIME:
                if (value >= 1000) {
                    return (value / 1000.0) + "s";
                } else {
                    return value + "ms";
                }
            case VOLUME:
            case GAIN:
            case RESONANCE:
            case MIX:
            default:
                return value + "%";
        }
    }
    
    /**
     * Adds a logarithmic slider control with visual feedback appropriate for the parameter type.
     * This provides more natural control where small adjustments at lower values 
     * are more noticeable than the same adjustments at higher values.
     * 
     * @param panel The panel to add the slider to
     * @param label The label text for the slider
     * @param min The minimum value
     * @param max The maximum value
     * @param initial The initial value
     * @param paramType The type of parameter (volume, time, frequency, etc.)
     * @param callback The callback to call when the value changes
     */
    private void addLogSlider(JPanel panel, String label, int min, int max, int initial, 
                            ParameterType paramType, SliderCallback callback) {
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));
        controlPanel.setOpaque(false);
        controlPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        controlPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        controlPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));

        // Label with volume icon
        JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(retroFont.deriveFont(12f));
        labelComponent.setForeground(new Color(200, 200, 200));
        labelComponent.setPreferredSize(new Dimension(80, 25));
        controlPanel.add(labelComponent);

        controlPanel.add(Box.createHorizontalStrut(10));

        // Volume slider with custom UI
        JSlider slider = new JSlider(min, max, initial);
        slider.setOpaque(false);
        slider.setPreferredSize(new Dimension(200, 25));
        slider.setMinimumSize(new Dimension(150, 25));
        
        // Custom UI for volume slider with colored track to indicate level
        slider.setUI(new BasicSliderUI(slider) {
            @Override
            public void paintTrack(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                Rectangle trackBounds = trackRect;
                int trackY = trackBounds.y + (trackBounds.height / 2) - 2;
                int trackHeight = 4;
                
                // Calculate filled width based on current value
                int thumbPos = xPositionForValue(slider.getValue());
                int filledWidth = thumbPos - trackBounds.x;
                
                // Draw background track (unfilled portion)
                g2d.setColor(new Color(60, 60, 60));
                g2d.fillRoundRect(trackBounds.x, trackY, trackBounds.width, trackHeight, 4, 4);
                
                // Draw filled portion with gradient color based on parameter type and value
                if (filledWidth > 0) {
                    // Create color gradient based on parameter type and value
                    Color trackColor;
                    float normalizedValue = (float) slider.getValue() / slider.getMaximum();
                    
                    // Choose color scheme based on parameter type
                    switch (paramType) {
                        case VOLUME:
                        case GAIN:
                            // Green to yellow to red gradient for volume/gain
                            if (normalizedValue < 0.5f) {
                                // Green to yellow gradient for lower half
                                float ratio = normalizedValue * 2; // Scale to 0-1 range
                                trackColor = new Color(
                                    0.4f + (0.6f * ratio),  // Red component increases
                                    0.8f,                    // Green component stays high
                                    0.4f * (1 - ratio)       // Blue component decreases
                                );
                            } else {
                                // Yellow to red gradient for upper half
                                float ratio = (normalizedValue - 0.5f) * 2; // Scale to 0-1 range
                                trackColor = new Color(
                                    0.8f + (0.2f * ratio),    // Red component increases to max
                                    0.8f * (1 - ratio),      // Green component decreases
                                    0.0f                     // Blue component stays at 0
                                );
                            }
                            break;
                            
                        case FREQUENCY:
                            // Blue to cyan to white gradient for frequency
                            trackColor = new Color(
                                normalizedValue * 0.5f,       // Red increases slowly
                                0.5f + (normalizedValue * 0.5f), // Green increases
                                0.8f                         // Blue stays high
                            );
                            break;
                            
                        case TIME:
                            // Purple to pink gradient for time
                            trackColor = new Color(
                                0.6f + (normalizedValue * 0.4f), // Red increases
                                0.3f + (normalizedValue * 0.3f), // Green increases slowly
                                0.8f - (normalizedValue * 0.3f)  // Blue decreases slowly
                            );
                            break;
                            
                        case MIX:
                            // Blue to purple gradient for mix
                            trackColor = new Color(
                                0.3f + (normalizedValue * 0.5f), // Red increases
                                0.3f,                          // Green stays low
                                0.8f                           // Blue stays high
                            );
                            break;
                            
                        default:
                            // Default gradient (blue to cyan)
                            trackColor = new Color(
                                0.0f,                          // Red stays low
                                normalizedValue * 0.8f,        // Green increases
                                0.5f + (normalizedValue * 0.5f) // Blue increases
                            );
                    }
                    
                    g2d.setColor(trackColor);
                    g2d.fillRoundRect(trackBounds.x, trackY, filledWidth, trackHeight, 4, 4);
                }
            }
            
            @Override
            public void paintThumb(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Rectangle knobBounds = thumbRect;
                
                // Draw thumb with slight glow effect based on value and parameter type
                float normalizedValue = (float) slider.getValue() / slider.getMaximum();
                Color thumbColor = new Color(150, 150, 150);
                Color glowColor;
                
                // Choose glow color based on parameter type
                switch (paramType) {
                    case VOLUME:
                    case GAIN:
                        if (normalizedValue < 0.5f) {
                            // Green glow for lower volumes/gain
                            glowColor = new Color(0.4f, 0.8f, 0.4f, 0.5f * normalizedValue);
                        } else {
                            // Yellow to red glow for higher volumes/gain
                            float ratio = (normalizedValue - 0.5f) * 2; // Scale to 0-1 range
                            glowColor = new Color(
                                0.8f + (0.2f * ratio),    // Red component increases to max
                                0.8f * (1 - ratio),      // Green component decreases
                                0.0f,                    // Blue component stays at 0
                                0.5f                     // Alpha for glow effect
                            );
                        }
                        break;
                        
                    case FREQUENCY:
                        // Blue to cyan glow for frequency
                        glowColor = new Color(
                            normalizedValue * 0.5f,       // Red increases slowly
                            0.5f + (normalizedValue * 0.5f), // Green increases
                            0.8f,                         // Blue stays high
                            0.5f                          // Alpha for glow effect
                        );
                        break;
                        
                    case TIME:
                        // Purple to pink glow for time
                        glowColor = new Color(
                            0.6f + (normalizedValue * 0.4f), // Red increases
                            0.3f + (normalizedValue * 0.3f), // Green increases slowly
                            0.8f - (normalizedValue * 0.3f), // Blue decreases slowly
                            0.5f                            // Alpha for glow effect
                        );
                        break;
                        
                    case MIX:
                        // Blue to purple glow for mix
                        glowColor = new Color(
                            0.3f + (normalizedValue * 0.5f), // Red increases
                            0.3f,                          // Green stays low
                            0.8f,                          // Blue stays high
                            0.5f                           // Alpha for glow effect
                        );
                        break;
                        
                    default:
                        // Default glow (blue to cyan)
                        glowColor = new Color(
                            0.0f,                          // Red stays low
                            normalizedValue * 0.8f,        // Green increases
                            0.5f + (normalizedValue * 0.5f), // Blue increases
                            0.5f                           // Alpha for glow effect
                        );
                }
                
                // Draw glow effect
                if (normalizedValue > 0.1f) {
                    g2d.setColor(glowColor);
                    g2d.fillRoundRect(
                        knobBounds.x - 2, 
                        knobBounds.y - 2, 
                        knobBounds.width + 4, 
                        knobBounds.height + 4, 
                        10, 10
                    );
                }
                
                // Draw thumb
                g2d.setColor(thumbColor);
                g2d.fillRoundRect(knobBounds.x, knobBounds.y, knobBounds.width, knobBounds.height, 8, 8);
                g2d.setColor(new Color(180, 180, 180));
                g2d.drawRoundRect(knobBounds.x, knobBounds.y, knobBounds.width, knobBounds.height, 8, 8);
            }
        });
        
        // Configure ticks with more granularity at lower volumes
        slider.setMajorTickSpacing(20);
        slider.setMinorTickSpacing(5);
        slider.setPaintTicks(true);
        
        // Set the name of the slider to match its label for later identification
        slider.setName(label);
        
        // Enable snap-to-ticks to prevent continuous value changes that might cause buffer issues
        slider.setSnapToTicks(true);
        
        // For time-based parameters, use larger snap increments to further reduce buffer issues
        if (paramType == ParameterType.TIME) {
            // For time sliders, use larger increments to prevent buffer overruns
            int snapIncrement = Math.max((max - min) / 30, 10); // At most 30 discrete values, minimum 10ms
            slider.setMinorTickSpacing(snapIncrement);
        }
        
        // Value label with appropriate display format based on parameter type
        String labelText = formatValueLabel(initial, paramType, min, max);
        JLabel valueLabel = new JLabel(labelText);
        valueLabel.setFont(retroFont.deriveFont(12f));
        valueLabel.setForeground(new Color(150, 150, 150));
        valueLabel.setPreferredSize(new Dimension(50, 25));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        
        slider.addChangeListener(e -> {
            int sliderValue = slider.getValue();
            
            // Update label with appropriate format
            valueLabel.setText(formatValueLabel(sliderValue, paramType, min, max));
            
            // Update the slider's appearance
            slider.repaint();
            
            // Throttle updates to prevent buffer overruns and thread exceptions
            // Only update when the user releases the slider or at most every 50ms during dragging
            if (!slider.getValueIsAdjusting()) {
                // Call the callback with the raw slider value when slider is released
                // The callbacks are responsible for scaling as needed
                callback.onValueChanged(sliderValue);
                
                // Return focus to the frame but don't trigger key release events
                SwingUtilities.invokeLater(() -> frame.requestFocusInWindow());
            } else {
                // During dragging, use a timer to throttle updates
                // This prevents too many rapid changes that could cause buffer issues
                if (paramType == ParameterType.TIME || paramType == ParameterType.FREQUENCY) {
                    // Use a more aggressive throttling for time and frequency parameters
                    // as these are more likely to cause buffer issues
                    Timer timer = new Timer(50, event -> {
                        callback.onValueChanged(slider.getValue());
                        ((Timer)event.getSource()).stop();
                    });
                    timer.setRepeats(false);
                    timer.start();
                }
            }
        });

        controlPanel.add(slider);
        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(valueLabel);
        controlPanel.add(Box.createHorizontalGlue());
        
        panel.add(controlPanel);
    }
}
