# Meter Foundation Implementation Plan

> **For agentic workers:** Execute this plan inline with review checkpoints.

**Goal:** Create a minimal Kotlin + Jetpack Compose + Material 3 Android app with an実機寄り Meter Surface and UI-independent fare domain.

**Architecture:** Keep `FareRule` independent from `EnergyCostProfile`; convert energy cost to a meter rule only in `FuelCostFareFactory`. Keep meter rendering separate from the Material 3 application shell, with no persistence, GPS, or production fare data.

**Tech Stack:** Android, Kotlin, Jetpack Compose, Material 3, Gradle, JUnit.

**Spec:** Approved user request in the conversation.

## Global Constraints

- Fuel codes are R, P, D, and E.
- Powertrains are ICE, HEV, BEV, and PHEV.
- Money is integer yen; distance is integer meters; time is integer seconds.
- Liquid efficiency is km/L and electric efficiency is kWh/100 km.
- Low-speed time is converted to distance-equivalent accumulation; generated time intervals may be rounded to five seconds.
- Meter Surface is not redesigned as Material 3 cards or FABs; Application Shell uses Material 3.

### Task 1: Android project and domain foundation

**Files:** Create the Gradle project, app manifest, and `domain/model` plus `domain/fare` Kotlin files. Add three JUnit cases for initial fare, distance boundary, and low-speed time accumulation.

- [ ] Define the Android project with one `:app` module and Compose enabled.
- [ ] Define `FuelType`, `Powertrain`, `EnergyEfficiency`, `EnergyCostProfile`, and `FareRule` with explicit units.
- [ ] Implement `FareCalculator` using integer meter/second inputs and `FuelCostFareFactory` with explicit rounding.
- [ ] Run the domain tests and Android unit tests.

### Task 2: UI boundary and launch screen

**Files:** Create `MainActivity`, `ui/shell/AppShell`, `ui/meter/MeterScreen`, and theme files.

- [ ] Host the meter screen inside a Material 3 shell without applying card styling to the meter.
- [ ] Render a minimal dark meter face with Japanese state labels and sample amount.
- [ ] Build the debug APK and verify the project compiles.

### Task 3: Initial commit

- [ ] Review status and diff for unrelated files.
- [ ] Commit only the initial project and plan with a concise Japanese message.
