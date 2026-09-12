# Contributing to LeTrain

Thanks for your interest! This is a small project, so the process is intentionally light.

## Ways to help

- Report bugs and request features via [Issues](https://github.com/antoniovazquezaraujo/LeTrain/issues).
- Improve the docs (`docs/user/`, `README.md`).
- Send code or docs via a Pull Request.

## Requirements

- **JDK 17+** and **Maven**.

## Build and test

```bash
mvn clean test                 # unit tests
mvn clean package -DskipTests  # standalone launchers in output/
```

`output/` ends up with:

- `LeTrain` — the 3D client.
- `LeTrain2D` — the terminal client.
- `letrain-check` — the headless scenario (`.ltr`) validator.

Run a single test: `mvn -pl core test -Dtest=ClassName#method`.

## Project layout

- `core/` — game logic, command DSL (ANTLR4) and scenarios. No UI dependencies.
- `ui-terminal/` — the 2D TUI (Lanterna).
- `ui-graphic/` — the 3D client (LibGDX).
- `launcher-*/` — entry points and packaging.

## Workflow

- Branch from `develop`: `feature/...` or `fix/...`. **Never commit directly to `develop`** (it is protected); open a PR instead.
- Keep PRs focused and describe them in **English**.
- Make sure `mvn clean test` passes before opening or updating a PR.
- Small, self-contained changes are easier to review.

## Coding conventions

- Java 17+, 4-space indentation, K&R braces; no wildcard imports.
- Prefer interfaces over implementations in declarations (`List<String>`, not `ArrayList<String>`).
- Tests: JUnit 5 + Mockito, AAA / given-when-then, meaningful names.
- Do not commit generated files (`target/`, `output/`).

## Docs

- User docs live in `docs/user/` and are published to
  <https://antoniovazquezaraujo.github.io/LeTrain/> on every push to `develop`.
- If a change is user-visible, update the manual/grammar or the scenario guide.

## License

By contributing, you agree that your contributions are licensed under the
[Apache-2.0](LICENSE) license.
