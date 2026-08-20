---
applyTo: "**/*"
description: "Project-wide coding and workflow guidelines for LeTrain."
---

# LeTrain - Project Guidelines

## Purpose
This instruction enforces consistent coding standards, build commands, and project workflows across the LeTrain repository. It ensures that all contributors follow the same conventions for clean, maintainable, and efficient code.

## Coding Standards

### General Principles
- Write clean, readable code with descriptive names.
- Follow the Single Responsibility Principle for classes.
- Use the Visitor pattern for rendering.
- Adhere to the MVP (Model-View-Presenter) architecture.
- **Avoid Ticks**: Avoid placing logic, safety checks, or segment reservations inside periodic physics loops (`tick()`, `advance()`, or `update()`). Instead, make components reactive and **event-driven** (triggered by sensor triggers, fork transitions, or explicit startup actions).
  - *Train Safety / Blocks Rule*: Never poll all rail segments or iterate over all train wagons/linkers in periodic loops to reserve or release blocks. Segment reservation must only be triggered when the first linker (head) enters a fork/node, and segment release must only be triggered when the last linker (tail) exits a fork/node.
- Keep components separated, focused, and clear (Single Responsibility Principle).
- **Interfaces**: Create interfaces for each class to enforce decoupling and maintain clean abstractions.

### Naming Conventions
- **Classes**: PascalCase (e.g., `RailTrackMaker`, `GraphicPresenter`)
- **Methods/Variables**: camelCase (e.g., `addTrackConnectionsToFork`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_SPEED`)
- **Packages**: lowercase, simple words (e.g., `letrain.track.rail`)
- **Tests**: End with `Test` (e.g., `RailTrackMakerTest`)

### Types and Annotations
- Use explicit types; avoid `var` unless the type is obvious.
- Use `@Nullable` or `Optional` for potentially null values.
- Mark transient fields with `@JsonIgnore`.
- Use JUnit 5 display names: `@DisplayName("description")`.

### Imports
- Prefer explicit imports over wildcards.
- Import order: `java.*`, `javax.*`, `org.*`, `com.*`, then `letrain.*`.
- Group related imports together.

### Formatting
- Indentation: 4 spaces (no tabs).
- Max line length: 120 characters.
- Place opening braces on the same line.
- Add a blank line between method declarations.
- Avoid trailing whitespace.

### Error Handling
- Use `ValidationUtils.requireNonNull()` and `requirePositive()`.
- Throw specific exceptions: `NullPointerException`, `IllegalArgumentException`, `IllegalStateException`.
- Provide descriptive error messages (e.g., `"field must not be null"`).
- Use assertions in tests (`assertThrows`, `assertEquals`).

### Logging
- Use SLF4J: `private static final Logger logger = LoggerFactory.getLogger(ClassName.class);`.
- Log levels: `ERROR` for failures, `WARN` for recoverable issues, `INFO` for significant events.
- Avoid logging sensitive data.

### Testing Conventions
- Use `@ParameterizedTest` with `@CsvSource` for data-driven tests.
- Group assertions with meaningful messages.
- Test one behavior per method.
- Use Mockito: `@Mock` + `@ExtendWith(MockitoExtension.class)`.

## Build Commands

### ⚠️ MANDATORY RULES
- **ALWAYS** run `mvn clean compile` (or `mvn clean package`) — **NEVER** run `mvn compile` without `clean` first.
- The `target/` directory in WSL gets files locked and `mvn clean` may fail silently. If `clean` fails, delete `target/` manually before compiling.
- This is **NOT optional**. Forgetting it causes false compilation errors from stale classes in the classpath.

### Standard Build
```bash
mvn clean compile        # Clean and compile
mvn test                # Run tests
mvn package             # Build JAR with dependencies
mvn package -DskipTests # Build without tests (recommended)
```

### Individual Tests
```bash
mvn test -Dtest=RailTrackMakerTest
mvn test -Dtest=RailTrackMakerTest#testConnectTrack
mvn test -Dtest=*ValidationUtilsTest
mvn test -X             # Verbose
```

### Run Application
```bash
# 2D Terminal Mode
java -jar target/JLeTrain-1.0-SNAPSHOT-jar-with-dependencies.jar

# 3D Mode
java -jar target/JLeTrain-1.0-SNAPSHOT-jar-with-dependencies.jar --3d
```

## Project Structure

### Main Directories
- `src/main/java/letrain/`: Main source code.
- `src/test/java/letrain/`: Test source code.
- `src/main/resources/`: Resources (e.g., configuration files).
- `output/`: Build output.

### Key Packages
- `track/`: Railway track system.
- `map/`: Map and routing logic.
- `visitor/`: Rendering (2D and 3D).
- `mvp/`: MVP architecture components.
- `audio/`: Audio synthesis engine.

## Common Tasks

### Add New Track Type
1. Create a class in `letrain.track.rail` extending `RailTrack`.
2. Implement required methods from the interface.
3. Add rendering support in `RenderVisitor` (2D) and `Gdx3DRenderer` (3D).

### Add New Test
1. Create a test class in `src/test/java/letrain/` following the package structure.
2. Use JUnit 5: `@Test`, `@ParameterizedTest`, `@DisplayName`.
3. Name the class: `ClassNameTest.java`.

### Modify Economy
Edit `economy.properties` to adjust gameplay values.

## Quality Assurance
- Ensure all code adheres to the above standards.
- Run the full test suite before committing changes.
- Use CI/CD pipelines to automate builds and tests.

## Example Prompts
- "Build the project without running tests."
- "Add a new track type to the railway system."
- "Run all tests with verbose output."
