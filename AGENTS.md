# AGENTS.md — Guía para Agentes de Desarrollo

## Equipo de Desarrollo

Este proyecto usa un equipo de agentes definido en `.opencode/agents/`:

- **Dani** (dani-coordinator) — Coordinador principal
- **Alex** (alex-java) — Experto en Java
- **Bicho** (bicho-testing) — Experto en testing
- **Jorge** (jorge-ui) — Experto en UI/UX

**IMPORTANTE: Al iniciar cada sesión, lee TODOS los ficheros en `.opencode/agents/` antes de hacer cualquier otra cosa.** Estos ficheros definen las personalidades, reglas y formatos de respuesta de cada agente del equipo.

**IMPORTANTE: Al iniciar cada sesión, lee TODOS los ficheros en `instructions/`.** Contienen las directrices del proyecto (coding standards, build commands, estructura, convenciones) que deben aplicarse a todo el código.

## Estructura del Proyecto

```
.opencode/
├── agents/
│   ├── dani-coordinator.md    # Coordinador (agente principal)
│   ├── alex-java.md           # Experto Java
│   ├── bicho-testing.md       # Experto testing
│   └── jorge-ui.md            # Experta UI/UX
├── package.json               # Dependencias OpenCode
└── .gitignore
```

## Comandos de Build/Lint/Test

Este proyecto no tiene código fuente tradicional — es una configuración de agentes para OpenCode. Sin embargo, para proyectos derivados:

- **Java/Maven**: `mvn clean compile`, `mvn clean test`, `mvn clean test -Dtest=ClassName`
- **Java/Gradle**: `./gradlew build`, `./gradlew test --tests ClassName`
- **Lint Java**: `mvn checkstyle:check` o `./gradlew checkstyleMain`
- **Tests unitarios**: Ejecutar tests específicos con `-Dtest=ClassName#methodName` (Maven) o `--tests "ClassName.methodName"` (Gradle)

## Convenciones de Código

### Java
- **Versión**: Java 17+ (preferir 21+ cuando sea posible)
- **Imports**: Organizar por grupos (java.*, javax.*, org.*, com.*), sin wildcards
- **Naming**: CamelCase para clases, camelCase para métodos/variables, UPPER_SNAKE_CASE para constantes
- **Formato**: 4 espacios de indentación, llaves en estilo K&R
- **Tipos**: Preferir interfaces sobre implementaciones en declaraciones (`List<String>` no `ArrayList<String>`)
- **Optional**: Usar `Optional<T>` para retornos que pueden ser nulos, nunca para parámetros
- **Excepciones**: Preferir excepciones checked para errores recuperables, unchecked para bugs de programación

### Testing
- **Framework**: JUnit 5 con Mockito
- **Naming**: `should_ReturnUser_When_ValidId` o `givenValidUser_whenSaved_thenReturnsId`
- **Estructura**: AAA (Arrange-Act-Assert) o Given-When-Then
- **Cobertura**: Mínimo 80% de línea, 70% de rama
- **Tests aislados**: Cada test debe ser independiente y reproducible

### UI/UX
- **Patrones**: MVC/MVVM según complejidad
- **Accesibilidad**: Seguir WCAG 2.1 AA como mínimo
- **Responsive**: Mobile-first cuando aplique
- **Consistencia**: Reutilizar componentes, mantener paleta de colores uniforme

## Flujo de Trabajo con Agentes

1. **Recibir tarea** → Dani analiza y delega
2. **Desarrollo** → Alex (backend) / Jorge (UI) implementan
3. **Testing** → Bicho crea/valida tests
4. **Revisión** → Dani coordina y presenta resultados

### Formato de Respuesta
Siempre usar el formato `NOMBRE: respuesta` al presentar resultados al usuario.

## Reglas Generales

- **NUNCA** inventar respuestas técnicas que correspondan a un experto
- **SIEMPRE** leer los ficheros de agentes antes de actuar
- **PRIORIZAR** código limpio y mantenible sobre soluciones clever
- **EXPLICAR** el "por qué" detrás de cada decisión técnica
- **INCLUIR** edge cases y escenarios de error en tests

## Notas

- No hay reglas de Cursor (`.cursor/rules/`, `.cursorrules`) ni Copilot (`.github/copilot-instructions.md`) en este repositorio
- Las dependencias se gestionan vía `.opencode/package.json`
- Temperatura recomendada: 0.3 para tareas técnicas, 0.5 para coordinación, 0.6 para diseño UI
