---
name: ada-architect-java
description: '**WORKFLOW SKILL** — Optimize Java architecture and game loop performance for LeTrain. USE FOR: JVM tuning, modular design, evaluating lightweight libraries (LWJGL, LibGDX), and ensuring memory efficiency. DO NOT USE FOR: non-Java projects, runtime debugging, or general coding questions.'
---

# Ada - Java Architecture Optimization

## Purpose
This skill ensures that the core of LeTrain is optimized for performance, modularity, and scalability. It evaluates the need for lightweight libraries and ensures the game loop is efficient and memory usage is minimal.

## Workflow

### Step 1: JVM Optimization
- Analyze JVM settings and garbage collection strategy.
- Ensure the application is tuned for low-latency and high-throughput.
- Use tools like JVisualVM or JFR for profiling.

### Step 2: Modular Design
- Review the codebase for modularity and scalability.
- Refactor monolithic components into reusable modules.
- Ensure adherence to SOLID principles.

### Step 3: Game Loop Evaluation
- Profile the game loop for bottlenecks.
- Optimize rendering and update cycles.
- Evaluate threading model for concurrency issues.

### Step 4: Library Evaluation
- Assess the need for lightweight libraries like LWJGL or LibGDX.
- Ensure libraries are compatible with Steam packaging.
- Avoid libraries that require user-side Java installations.

### Step 5: Memory Efficiency
- Identify and fix memory leaks.
- Optimize object allocation and reuse.
- Use tools like Eclipse MAT for heap analysis.

## Decision Points
- **Library Selection**: Choose libraries only if they significantly improve performance or reduce complexity.
- **Modularity vs. Performance**: Balance modularity with runtime performance.

## Quality Criteria
- JVM settings are optimized for the target platform.
- Codebase adheres to modular design principles.
- Game loop runs efficiently under load.
- Memory usage is within acceptable limits.

## Example Prompts
- "Optimize the JVM settings for LeTrain."
- "Evaluate if LWJGL is suitable for LeTrain."
- "Profile the game loop for performance bottlenecks."

## Related Skills
- JVM Profiling
- Game Loop Optimization
- Modular Java Design