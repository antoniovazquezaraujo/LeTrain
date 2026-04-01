---
name: bug-quality-testing
description: '**WORKFLOW SKILL** — Ensure quality and robustness of LeTrain through automated testing and rigorous QA. USE FOR: writing destructive tests, increasing code coverage, and preventing regressions. DO NOT USE FOR: non-Java projects, exploratory testing without automation, or general debugging.'
---

# Bug - Quality Assurance and Testing

## Purpose
This skill focuses on ensuring the robustness and reliability of LeTrain by writing automated tests that simulate edge cases, increase code coverage, and prevent regressions. It emphasizes destructive creativity to uncover hidden issues.

## Workflow

### Step 1: Test Planning
- Identify critical paths and edge cases in the system.
- Define scenarios for race conditions, deadlocks, and silent failures.
- Prioritize tests based on risk and impact.

### Step 2: Automated Test Development
- Use JUnit and Mockito to write unit and integration tests.
- Simulate edge cases, such as simultaneous train reservations.
- Ensure tests are deterministic and repeatable.

### Step 3: Code Coverage Analysis
- Use tools like JaCoCo to measure code coverage.
- Identify untested paths and add tests to cover them.
- Aim for high coverage without compromising test quality.

### Step 4: Regression Testing
- Run the full test suite after every refactor or major change.
- Ensure no new failures are introduced.
- Use CI/CD pipelines to automate regression testing.

### Step 5: Stress and Load Testing
- Simulate high-load scenarios to test system stability.
- Use tools like JMeter or custom scripts for load testing.
- Analyze results and address performance bottlenecks.

## Decision Points
- **Test Prioritization**: Focus on high-risk areas first.
- **Automation vs. Manual**: Automate repetitive tests; use manual testing for exploratory scenarios.

## Quality Criteria
- All critical paths are covered by automated tests.
- Code coverage meets or exceeds project standards.
- No regressions are introduced during refactoring.
- System remains stable under stress and load.

## Example Prompts
- "Write tests for simultaneous train reservations."
- "Analyze code coverage and add missing tests."
- "Run regression tests after Ada's refactor."

## Related Skills
- Automated Testing with JUnit
- Code Coverage Analysis
- Stress Testing