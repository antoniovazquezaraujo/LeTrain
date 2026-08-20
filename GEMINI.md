# GEMINI.md

## Project Overview
**LeTrain** is a procedural train simulator combining classic ASCII aesthetics with a modern 3D engine (LibGDX). Players manage rail networks, logistics, and economies in an infinite, procedurally generated world.

## Building and Running
*   **Build & Package:** `mvn package -DskipTests`
    *   This command generates a distribution in the `output/LeTrain` directory.
*   **Tests:** `mvn test`
*   **Running:** The project includes two main launchers, created via `jpackage` during the Maven build phase:
    *   `LeTrain`: Launches the modern 3D experience.
    *   `LeTrain2D`: Launches the classic terminal view.

## Development Conventions
*   **Language:** Java 17.
*   **Build Tool:** Maven.
*   **Testing:** JUnit 5 with Mockito for mocking dependencies.
*   **Architecture:**
    *   **Core:** Procedural generation (Perlin noise), economy management, and rail logistics.
    *   **3D Engine:** LibGDX.
    *   **UI:** Lanterna for terminal interfaces.
    *   **Automation:** ANTLR4 is used for a custom rail automation language (Grammar: `src/main/antlr4/letrain/command/LeTrainProgram.g4`).
    *   **Serialization:** Uses Jackson (`jackson-databind`) for JSON serialization/deserialization.
*   **Project Structure:**
    *   `src/main/java/letrain`: Main source code.
    *   `src/test/java/letrain`: Unit and integration tests.
    *   `src/main/resources`: Configuration (e.g., `logback.xml`) and assets (fonts, audio).
    *   `economy.properties`: Central configuration file for game economy tuning.
*   **Git Workflow & Merging:**
    *   **CRITICAL RULE (ABSOLUTE PROHIBITION):** You are STRICTLY FORBIDDEN from merging any branch into `develop` or `main` on your own initiative.
    *   `main` is the sacred release branch. `develop` is the integration branch. YOU CANNOT TOUCH THEM without explicit, unambiguous permission from the user AFTER they have tested the changes.
    *   When writing code, you MUST commit it to a separate branch (`feature/...` or `fix/...`) and STOP.
    *   Do NOT chain `git merge` commands in your terminal tools (e.g., NEVER run `git commit && git checkout develop && git merge`).
    *   **CRITICAL RULE:** After the user approves and the branch has been successfully merged, ALWAYS delete the branch both locally and remotely (`git branch -d` and `git push origin --delete`) to keep the repository clean.

## Documentation & Navigation
*   **Class Index:** A complete list of project classes is maintained at `docs/architecture/ClassIndex.md`. Consult this file to locate specific components.
*   **Architecture & ADRs:** Detailed technical documentation and architectural decision records are located in the `docs/` directory.

---
*Last updated: 2026-04-16*
