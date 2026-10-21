# ADR-031: Modelo de la quick reference de programación (kinds, scopes, placeholders)

## Estado: IMPLEMENTADO ([#828])

## Contexto

Los dos clientes (2D y 3D) pintan la misma quick reference de programación desde
`GrammarReference` (grupo `PROGRAM`). La auditoría de 2026-10-21 encontró que el modelo era
insuficiente: usaba `isHeading` + `snippet`, metía prosa en `snippet` (entradas insertables que
generaban código inválido), usaba `#` como placeholder (¡el carácter de comentario del DSL!) y no
distinguía dónde es válido cada comando. Resultado: snippets insertados que no hacían nada,
plantillas con nombres vacíos y acciones de bloque que la gramática no permite dentro de `{ }`.

## Decisión

### 1. Kinds y scopes

Cada nodo declara un `Kind`:

| Kind | Insertable | Contenido |
|---|---|---|
| `HEADING` | no | etiqueta de sección (solo estructura) |
| `GROUP` | no | contenedor expandible |
| `INFO` | **no** | prosa (`info`); se pinta como nota atenuada |
| `COMMAND` | **sí** | `snippet` + `scope` |

Solo los `COMMAND` llevan `snippet`. **Regla dura: la prosa nunca va en `snippet`.** Un `INFO`
tiene `info` y `snippet == null`; los editores no deben permitir insertarlo.

Cada `COMMAND` declara su `Scope`:

- `BODY_ONLY`: statement de primer nivel del cuerpo (p. ej. `assign itinerary …`, un
  `trigger`/`at`/`every`, `signal …`, `station … invert`).
- `BLOCK_ONLY`: solo dentro de un bloque `{ }` de trigger/`at`/`every` (p. ej. `train at …`).
- `BOTH`: válido en ambos sitios (acciones de tren, fork y semáforo, que la gramática comparte).

> **El scope es exclusivo de PROGRAM.** Describe dónde encaja un comando *dentro del programa*
> (cuerpo vs. bloque de trigger/`at`/`every`). La pestaña **Scenario** (`on build` / `on start`)
> tiene su propio contexto y **no** aplica estos scopes: su quick reference se muestra sin gating.
> La UI pasa `scope = null` a `ProgramTextEdit.isInsertionAllowed`, que con `null` no restringe la
> inserción (cualquier profundidad), porque el scope no describe la receta Scenario. Si en el futuro
> se quiere validar la receta, será un modelo de scopes aparte.

Los waypoints (`add station …` / `add sensor …`) son fragmentos del cuerpo de `create itinerary`;
se marcan `BODY_ONLY` y se validan con la regla `waypoint` de la gramática.

### 2. Placeholders

`letrain.command.Placeholders` es la única fuente de verdad:

- patrón `[<]([a-z]+)[>]`;
- `find(String)` devuelve `start` (incluido) y `end` (exclusivo) en orden de lectura;
- tokens permitidos: `station`, `sensor`, `train`, `fork`, `semaphore`, `signal`, `speed`,
  `limit`, `count`, `name`, `time`, `dir`.

Nunca se usa `#` como placeholder: `#` es el inicio de comentario del lexer. Toda plantilla debe
ser código válido al insertarse (nombres no vacíos, al menos dos waypoints en el template de
itinerario).

### 3. Contrato de UI (2D y 3D)

Este ADR fija el contrato; la implementación de la experiencia (preview, navegación de
placeholders, Tab, auto-indent) es de la UI:

- Una hoja es **insertable solo si `kind == COMMAND`**.
- `INFO` se pinta atenuado, sin `[+]` y sin acción de inserción.
- La UI puede mostrar el `scope` como pista (“top-level only” / “inside `{ }`”) **solo en PROGRAM**;
  en Scenario (`on build`/`on start`) no se muestra ni se aplica.
- **Preview**: al seleccionar una entrada, mostrar el `snippet` tal cual (es código real).
- **Inserción con placeholders**: seleccionar el primer `<token>`; `Tab`/`Shift+Tab` ciclan entre
  placeholders; teclear sustituye el token completo. En 2D (Lanterna no tiene selección) se coloca
  el caret al inicio del token y `Tab` cicla; en 3D se usa la selección del área de texto.
- Comodidad: `Tab` = 2 espacios, `Shift+Tab` = dedent, auto-indent tras `{`, conservar indentación
  al pulsar Enter. La navegación de foco del panel pasa a `Ctrl+Tab` (documentado en el footer).
- Paridad 2D/3D; sin syntax highlighting en esta iteración.

## Consecuencias

- `GrammarReferenceTest` y `DslDocumentationSyntaxTest` validan el modelo: todo `COMMAND` parsea en
  su scope (sustituyendo placeholders por valores válidos), ningún `INFO` es insertable, ningún
  snippet usa `#` ni nombres vacíos, y los placeholders pertenecen al whitelist.
- El contenido es el de `GrammarReference.programTree()` (#828); cualquier cambio de gramática debe
  reflejarse ahí y en `docs/user/grammar.md`.

[#828]: https://github.com/antoniovazquezaraujo/letrain/issues/828
