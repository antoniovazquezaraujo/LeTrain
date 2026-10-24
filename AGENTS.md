# AGENTS.md — Guía para Agentes de Desarrollo

## Equipo de Desarrollo

Este proyecto usa un equipo de agentes definido en `.opencode/agents/`:

- **Dani** (dani-coordinator) — Coordinador principal
- **Alex** (alex-java) — Experto en Java
- **Bicho** (bicho-testing) — Experto en testing
- **Jorge** (jorge-ui) — Experto en UI/UX

**IMPORTANTE: Al iniciar cada sesión, lee TODOS los ficheros en `.opencode/agents/` antes de hacer cualquier otra cosa.** Estos ficheros definen las personalidades, reglas y formatos de respuesta de cada agente del equipo.

**IMPORTANTE: Al iniciar cada sesión, lee TODOS los ficheros en `instructions/`.** Contienen las directrices del proyecto (coding standards, build commands, estructura, convenciones) que deben aplicarse a todo el código.

**IMPORTANTE: Al iniciar cada sesión, lee `CONTRIBUTING.md`.** Define el flujo Git del proyecto: ramas `feature/...` o `fix/...` desde `develop`, **PROHIBIDO commitear directamente a `develop`** (está protegida), PRs en inglés y `mvn clean test` en verde antes de abrir o actualizar un PR.

## Espacio de trabajo auxiliar (`letrain-lab`)

Los artefactos que **no deben commitearse** viven **fuera del repositorio**, en la **carpeta hermana del repo** (por defecto `/home/antonio/dev/letrain-lab/`):

- `notes/` — borradores de diseño, contratos y documentos de trabajo; se revisan antes de formalizarse en `docs/`.
- `demo/` — prototipos independientes (pueden usar las clases compiladas del juego, p. ej. `core/target/classes`).
- `wt/` — worktrees temporales de los agentes:
  `git worktree add /home/antonio/dev/letrain-lab/wt/<nombre> -b <rama> origin/develop`.

Reglas: nada de `letrain-lab/` se commitea; **no guardar allí ni en `/tmp` nada que deba sobrevivir a la sesión** (`/tmp` se borra al salir); los worktrees **nunca** dentro del checkout principal; **al terminar una tarea**, eliminar su worktree (`git worktree remove`) y borrar la rama.

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

LeTrain es un simulador ferroviario en Java 17 modularizado con Maven (`core`, `ui-terminal`, `ui-graphic`, `launcher-terminal`, `launcher-graphic`, `launcher-check`):

- **Compilación**: `mvn clean compile` (obligatorio siempre `clean`)
- **Tests unitarios**: `mvn clean test` o tests específicos con `mvn -pl <modulo> test -Dtest=ClassName#methodName`
- **Empaquetado**: `mvn clean package -DskipTests` (genera las aplicaciones en `output/`)
- **Formato Java**: 4 espacios, llaves K&R (ver `eclipse-java-google-style.xml`)

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

## Autoría y comunicación externa

- **La voz pública del proyecto es del dueño.** `README.md`, `README_es.md`, los textos de presentación (itch.io, Snap Store, páginas de proyecto) y cualquier prosa dirigida al exterior son **autoría del dueño**. El README no es documentación técnica: es una declaración del autor.
- Los agentes **no redactan, reescriben ni “mejoran”** esos textos. Pueden hacer comprobaciones mecánicas (enlaces, badges, comandos de build, rutas de imágenes) **solo cuando el dueño lo pida**, y deben reportar los problemas sin editar la prosa.
- **Comunicación externa:** ningún agente responde a terceros (issues, PRs, Reddit, Hacker News, foros, correo) sin el **OK previo del dueño** y sin enseñarle antes el texto propuesto.
- La documentación técnica interna (`docs/developer/`), los mensajes de commit y las descripciones de PR sí pueden ser redactados por agentes.

## Reglas Generales

- **NUNCA** inventar respuestas técnicas que correspondan a un experto
- **NUNCA** commitear directamente a `develop`: siempre rama `feature/...` o `fix/...` + PR
- **SIEMPRE** leer los ficheros de agentes antes de actuar
- **PRIORIZAR** código limpio y mantenible sobre soluciones clever
- **EXPLICAR** el "por qué" detrás de cada decisión técnica
- **INCLUIR** edge cases y escenarios de error en tests

## Notas

- No hay reglas de Cursor (`.cursor/rules/`, `.cursorrules`) ni Copilot (`.github/copilot-instructions.md`) en este repositorio
- Las dependencias se gestionan vía `.opencode/package.json`
- Temperatura recomendada: 0.3 para tareas técnicas, 0.5 para coordinación, 0.6 para diseño UI
