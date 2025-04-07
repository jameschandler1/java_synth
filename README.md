# Java Synthesizer

A polyphonic synthesizer built with Java and JSyn, featuring a retro 80s-inspired UI design.

## Features

- Multi-mode filter (Low-pass, High-pass, Band-pass)
- Audio effects processor with multiple effect types:
  - Delay with adjustable time and feedback
  - Reverb simulation with size control
  - Distortion with adjustable drive amount
- DAHDSR envelope for amplitude control
- 8-voice polyphony for rich sound
- Computer keyboard control with visual note display
- Modern dark theme UI with gradient background
- Responsive design that works at any screen size

## Requirements

- Java 11 or higher
- JSyn and FlatLaf libraries (included in lib directory)

## Setup and Installation

### Prerequisites
- Java 11 or higher installed
- Git (optional, for cloning)

### Getting Started

1. Clone or download this repository:
   ```bash
   git clone https://github.com/yourusername/java_synth.git
   cd java_synth
   ```

2. Make sure the required libraries are in the `lib` directory:
   - JSyn (jsyn.jar)
   - FlatLaf (flatlaf-3.4.jar, flatlaf-extras-3.4.jar)

3. Ensure the VCR OSD Mono font is in the `fonts` directory:
   ```bash
   ls fonts/VCR_OSD_MONO.ttf
   ```

### Compiling

Compile the Java source files:
```bash
javac -cp "lib/*" src/*.java
```

### Running

Run the synthesizer:
```bash
java -cp "lib/*:." src.Main
```

### Cleaning

Remove compiled class files:
```bash
rm -f src/*.class
```

## UI Sections

### Filter Section
- Type: Choose between Low Pass, High Pass, and Band Pass filters
- Cutoff: Filter cutoff frequency (20Hz - 20kHz)
- Resonance: Filter resonance (0-100%)

### Envelope Section
- Attack: Time for sound to reach full volume (1-1000ms)
- Decay: Time to reach sustain level after attack (1-1000ms)
- Sustain: Volume level held while key is pressed (0-100%)
- Release: Time for sound to fade after key release (1-1000ms)

### Effects Section
- Type: Choose between None, Delay, Reverb, and Distortion effects
- Parameter 1: Controls primary effect parameter (delay time, reverb size, or distortion amount)
- Parameter 2: Controls secondary effect parameter (feedback for delay)
- Mix: Controls wet/dry balance of the effect

### Master Section
- Volume: Overall output volume (0-100%)

### Current Note Display
- Shows the musical note (e.g., "C4") currently being played

## Computer Keyboard

Use these keys to play notes:
- A-K: White keys (C4 to C5)
- W,E,T,Y,U: Black keys

## Code Structure and Optimization

### Core Classes

- **Main**: Entry point for the application with thread safety monitoring
- **SynthEngine**: Central engine managing voices, filters, and audio routing
- **SynthVoice**: Individual voice for polyphonic synthesis
- **FXProcessor**: Audio effects processor for delay, reverb, and distortion
- **SynthUI**: User interface with controls and visual feedback

### Recent Optimizations

- **Logarithmic Scaling**: Implemented for all sliders to provide more natural control over parameters
- **Documentation**: Added comprehensive JavaDoc to all methods explaining their purpose and behavior
- **Code Cleanup**: Removed unused methods, duplicate code, and unnecessary comments
- **Thread Safety**: Improved handling of audio processing with safety monitoring and recovery

### Parameter Scaling

All parameters now use appropriate scaling for their domain:
- **Volume/Gain**: Logarithmic scaling (x⁴) for natural volume perception
- **Frequency**: Logarithmic scaling for natural frequency perception (20Hz-20kHz)
- **Time**: Linear or logarithmic scaling depending on the parameter

### Visual Feedback

Sliders provide visual feedback with color gradients based on parameter type:
- **Frequency**: Blue to cyan
- **Volume/Gain**: Yellow to green
- **Time**: Purple to pink
- **Mix/Balance**: Blue to purple
