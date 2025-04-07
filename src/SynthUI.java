package src;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import com.formdev.flatlaf.FlatDarkLaf;

public class SynthUI {
    private JFrame frame;
    private SynthEngine engine;
    private HashMap<Integer, Integer> keyboardMapping;
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
        JPanel filterSection = createSection("Filter");
        JPanel envelopeSection = createSection("Envelope");
        JPanel masterSection = createSection("Master");

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
            engine.setFilterType((SynthEngine.FilterType) filterTypeCombo.getSelectedItem());
            frame.requestFocusInWindow();
        });
        filterTypePanel.add(filterTypeCombo);
        filterTypePanel.add(Box.createHorizontalGlue());
        
        filterSection.add(filterTypePanel);

        // Add filter controls
        addSliderControl(filterSection, "Cutoff", 20, 20000, 2000, 
            value -> engine.setCutoff(value));
        addSliderControl(filterSection, "Resonance", 0, 100, 5, 
            value -> engine.setResonance(value / 100.0));

        // Add envelope controls
        addSliderControl(envelopeSection, "Attack", 1, 1000, 80, 
            value -> engine.setEnvelopeAttack(value / 1000.0));
        addSliderControl(envelopeSection, "Decay", 1, 1000, 200, 
            value -> engine.setEnvelopeDecay(value / 1000.0));
        addSliderControl(envelopeSection, "Sustain", 0, 100, 70, 
            value -> engine.setEnvelopeSustain(value / 100.0));
        addSliderControl(envelopeSection, "Release", 1, 1000, 300, 
            value -> engine.setEnvelopeRelease(value / 1000.0));

        // Add master controls
        addSliderControl(masterSection, "Volume", 0, 100, 50, 
            value -> engine.setMasterVolume(value / 100.0));

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
        
        // Add sections to main panel with proper spacing
        mainPanel.add(filterSection);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(envelopeSection);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(masterSection);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(noteDisplayPanel);
        mainPanel.add(Box.createVerticalGlue());

        // Add keyboard listener
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                Integer note = keyboardMapping.get(e.getKeyCode());
                if (note != null) {
                    engine.noteOn(note, 0.8);
                    // Update the note display with the current note name
                    String noteName = noteNames.get(note);
                    if (noteName != null) {
                        currentNoteLabel.setText(noteName); // Show the note being played
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                Integer note = keyboardMapping.get(e.getKeyCode());
                if (note != null) {
                    engine.noteOff(note);
                    // Reset the note display when key is released
                    currentNoteLabel.setText("Press a key");
                }
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

    private void addSliderControl(JPanel panel, String label, int min, int max, int initial, 
                                SliderCallback callback) {
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));
        controlPanel.setOpaque(false);
        controlPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        controlPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        controlPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));

        // Label
        JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(retroFont.deriveFont(12f));
        labelComponent.setForeground(new Color(200, 200, 200));
        labelComponent.setPreferredSize(new Dimension(80, 25));
        controlPanel.add(labelComponent);

        controlPanel.add(Box.createHorizontalStrut(10));

        // Slider with custom gray thumb
        JSlider slider = new JSlider(min, max, initial);
        slider.setOpaque(false);
        slider.setPreferredSize(new Dimension(200, 25));
        slider.setMinimumSize(new Dimension(150, 25));
        
        // Custom UI for gray slider thumb to match the 80s aesthetic
        slider.setUI(new BasicSliderUI(slider) {
            @Override
            public void paintThumb(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                // Enable anti-aliasing for smooth rounded corners on the thumb
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Rectangle knobBounds = thumbRect;
                // Fill with medium gray color
                g2d.setColor(new Color(120, 120, 120));
                g2d.fillRoundRect(knobBounds.x, knobBounds.y, knobBounds.width, knobBounds.height, 8, 8);
                // Add a lighter gray border for definition
                g2d.setColor(new Color(180, 180, 180));
                g2d.drawRoundRect(knobBounds.x, knobBounds.y, knobBounds.width, knobBounds.height, 8, 8);
            }
        });
        
        // Configure ticks
        int range = max - min;
        int majorTick = range / 10;
        int minorTick = majorTick / 5;
        majorTick = Math.max(1, majorTick);
        minorTick = Math.max(1, minorTick);
        
        slider.setMajorTickSpacing(majorTick);
        slider.setMinorTickSpacing(minorTick);
        slider.setPaintTicks(true);
        slider.setSnapToTicks(true);
        
        // Value label
        JLabel valueLabel = new JLabel(String.valueOf(initial));
        valueLabel.setFont(retroFont.deriveFont(12f));
        valueLabel.setForeground(new Color(150, 150, 150));
        valueLabel.setPreferredSize(new Dimension(50, 25));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        
        slider.addChangeListener(e -> {
            int value = slider.getValue();
            valueLabel.setText(String.valueOf(value));
            if (!slider.getValueIsAdjusting()) {
                callback.onValueChanged(value);
                frame.requestFocusInWindow();
            }
        });

        controlPanel.add(slider);
        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(valueLabel);
        controlPanel.add(Box.createHorizontalGlue());
        
        panel.add(controlPanel);
    }

    @FunctionalInterface
    interface SliderCallback {
        void onValueChanged(double value);
    }
}
