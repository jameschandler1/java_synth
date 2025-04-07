# Java Synthesizer

A polyphonic synthesizer built with Java and JSyn, featuring a retro 80s-inspired UI design.

## Features

- Multi-mode filter (Low-pass, High-pass, Band-pass)
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

### Master Section
- Volume: Overall output volume (0-100%)

### Current Note Display
- Shows the musical note (e.g., "C4") currently being played

## Computer Keyboard

Use these keys to play notes:
- A-K: White keys (C4 to C5)
- W,E,T,Y,U: Black keys
