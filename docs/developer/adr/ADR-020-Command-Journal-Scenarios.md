# ADR-020: Diario de comandos, escenarios, undo/redo y modo experimento

## Estado: PROPUESTO

## Contexto
Hoy conviven dos formas de "programar" que el usuario percibe como separadas: la **consola** (comandos inmediatos y efímeros) y el **programa** del IDE (texto persistente que se re-ejecuta con APPLY). Además, **guardar** solo guarda el *estado* de la partida. El equipo quiere:

- Un lenguaje y una superficie únicos donde convivan comandos directos, construcción y lógica.
- Poder **exportar la "receta"** que reconstruye la partida (infraestructura y trenes), no su estado (posiciones, dinero).
- **Undo/redo** de edición.
- Un **modo borrador/experimento** para probar sin consecuencias.

## Decisión
Distinguir dos artefactos y apoyar todo en un **diario de comandos de edición**:

1. **Estado (savegame)** = el actual JSON. Sirve para continuar donde se dejó.
2. **Escenario / programa** = texto DSL que, aplicado sobre un **mundo nuevo con su semilla**, reconstruye la infraestructura y los trenes (en su posición de origen), sin dinero ni estado en curso. Modo **constructor libre**: sin costes ni delays de construcción.

Mecánica común: el **diario de comandos** registra las acciones de edición del usuario (mover cursor, `write`, `new`, `del`, mover elementos, crear trenes...). Ese mismo diario alimenta:
- **Exportación de escenario** (receta determinista sobre semilla).
- **Undo/redo de edición** (reconstrucción determinista + checkpoints).

3. **Editor de escenario desacoplado (modelo A)**: el escenario es un **fichero de texto en disco** con su DSL (semilla + `on build` + lógica). Se edita libremente, tanto en el editor integrado como con un **editor externo** (p. ej. vim) mientras el juego corre en otra pantalla. Regla de oro: **editar el fichero nunca modifica la partida en curso**; los cambios se aplican al *jugar el escenario* (mundo nuevo con su semilla). Si se quiere, **hot-reload** (file-watch con debounce) recarga el texto y reporta los errores de sintaxis en la consola del juego. Un botón **"re-exportar desde mi partida"** regenera el `on build` canónico a partir del estado real (exportador fiable), para reconciliar tras ediciones que rompan la reconstrucción.

   **Calidad del export (fiel vs legible)**: exportar con diario produce una secuencia **exacta y tan legible como lo que el usuario hizo**. Exportar sin diario (canónico) es siempre **posible** (recorrido del grafo con `go` de reposicionamiento, determinista) y **fiel** (reconstruye el mismo mapa sobre la misma semilla), pero para redes complejas puede ser **poco legible** (parecido a "descompilar código": correcto pero con muchos saltos). El canónico se debe optimizar para parecer humano (rectas largas, viajar por vía ya construida, agrupar por componente, comentarios), pero **no se promete legibilidad tipo humana**: el escenario *autoreado* y claro se obtiene grabando a mano o escribiendo el texto, no descompilando el estado. Esta distinción fiel-vs-legible queda como expectativa explícita del diseño.

### Capas de comandos (qué se guarda y qué no)
No todo lo que el usuario teclea es "construcción". Los comandos se clasifican por su ciclo de vida:

| Capa | Ejemplos | Dónde vive | Al jugar el escenario |
|---|---|---|---|
| **Constructiva** | `write`, `go`, `face`, `new`, `del`, mover elementos | Diario → `on build` | Sí, una vez al crear el mundo |
| **Condiciones iniciales** | `semaphore 1 close`, `train 1 set speed 4` (estado de arranque del escenario) | `on start` (opcional) | Sí, una vez justo antes de ceder el control |
| **Lógica declarativa** | triggers, `create itinerary`, `assign`, `set autopilot` | El texto de programa (también embebido en el save) | Se **registra** (no se ejecuta puntual) |
| **Operativa / runtime** | `train 1 set speed 4`, `semaphore 1 close`, `fork 2 flip` durante el juego | Solo la sesión (efímera); el estado queda en el save | **No** |

Reglas:
- Lo **operativo** actúa sobre el mundo vivo en el instante y no se exporta. Si se quiere comportamiento reproducible, se expresa como **lógica** (trigger/itinerario) o como **condición inicial** en `on start`.
- El **estado guardado** captura cualquier cosa operativa al momento de guardar (semáforo cerrado, velocidad...): al cargar no se re-ejecuta nada, el estado ya lo dice. El escenario solo sirve para arrancar un mundo **fresco**.
- La construcción determinista garantiza que los ids referenciados en `on start` existan tras `on build`.

### Separación red / operador
Como en el ferrocarril real — donde quien construye/mantiene la red y quien hace circular máquinas son entidades distintas — el escenario se estructura en **dos secciones lógicas**:

- **Red (infraestructura)**: `seed` + `on build` (vías, túneles/puentes, desvíos, estaciones, sensores, semáforos, señales) y los autómatas que viven en la red (triggers que accionan desvíos/semáforos al paso de trenes).
- **Operador (flota y circulación)**: qué máquinas/vagones, en qué posición, y sus misiones (itinerarios, autopilot) y condiciones de arranque (`on start`).

Reglas:
- **Orden garantizado**: primero se construye y valida la **red**; después se despliega el **operador** sobre ella. Así las referencias cruzadas (p. ej. colocar un tren en una estación de la red) son deterministas y validables.
- **Modularidad**: una misma red puede servirse con distintos operadores (y viceversa); se permite exportar/compartir solo la red.
- La separación es a nivel de **formato y de orden de ejecución**; no requiere dos gestores distintos en el código.
- El estado guardado contiene red + operador + estado en curso.

### Consistencia save ↔ escenario (huellas)
Save y escenario son artefactos distintos (runtime vs mundo nuevo) y **no deben confundirse**: cada carga es autoconsistente. La divergencia entre ambos es "dos instantes distintos", no corrupción; el diseño la hace **visible y controlable**:

- **Huella de red (fingerprint)**: tanto el save como el escenario guardan un hash canónico de la red (semilla + export canónico de la infra) y una marca temporal. Si al cargar/jugar no coinciden con el último export, se avisa: el save pertenece a una revisión de red distinta del escenario. Nada se rompe; solo se informa.
- **Export sobre un estado consistente**: "exportar escenario" toma un checkpoint del save actual (o pide guardar antes), de modo que el escenario siempre corresponde a un estado base conocido. Nuevas ediciones + re-export sobrescriben con la nueva huella.
- **Re-sincronizar explícito**: cargar un save antiguo y querer el escenario igual → "exportar desde esta partida" regenera el canónico (y la huella).
- **Runtime fuera del escenario (por diseño)**: posiciones, velocidades, dinero y estados *en curso* nunca se exportan; solo las condiciones iniciales (`on start`). El escenario no promete reproducir runtime.
- **Diff/inspección**: poder comparar la infra del save actual contra la del fichero de escenario para decidir cuál es la buena.

### Modelo temporal
- **Edición normal (pausada)**: entrar en modo Rails **pausa la simulación** (trenes, economía, descarrilamientos). La construcción en pausa es instantánea (sin delays). Aquí el diario es exacto y el undo/redo funciona por *reset a un checkpoint + re-ejecutar el diario* (con checkpoints periódicos para no re-ejecutar toda la historia).
- **Modo experimento (en vivo)**: una opción desactiva la pausa; al entrar se toma un **snapshot completo del Model en memoria** (misma maquinaria que save/load, sin fichero). La simulación sigue y el usuario hace experimentos sin diario ni undo/redo. Al salir se **restaura el snapshot** (o se conserva si así se decide). Solo pruebas y diversión.

## Consecuencias
- El **grabador** (`record on/off`) apunta las acciones manuales al diario; el escenario y el undo comparten maquinaria.
- La simulación en vivo NO es reconstruible por el diario (trenes/dinero/descarrilamientos son estado): para "deshacer en vivo" solo valdría un snapshot (viaje en el tiempo), nunca el diario. Por eso el undo de edición exige el modelo pausado.
- Reutiliza lo existente: `GameSaveService`/serialización (snapshot en memoria), el `setModel`/carga en presentadores (restaurar tras experimento), el DSL de tortuga (`write/move/del/clear`, `go`, `new`) y el parser único del CLI.
- La unificación total de gramáticas (consola/programa en una sola ANTLR) es **opcional y posterior**; el parser de consola ya importa el de script.
- El escenario como **fichero de texto** habilita edición externa (vim), versionado (git) y compartición; la edición nunca afecta a la partida en curso (modelo A).

## Roadmap (por partes, en orden sugerido)
1. Bandera de **pausa** de simulación (por modo) en 2D y 3D; construcción instantánea en pausa.
2. **Diario de comandos** de edición (+ grabador opcional) con checkpoints.
3. **Undo/redo** de edición (en pausa).
4. **Escenario como fichero de texto**: exportar/importar (semilla + diario, modo constructor libre), edición externa y hot-reload.
5. **Modo experimento** con snapshot en memoria y restauración.

## Puntos abiertos (a trabajar en este ADR)
- Qué acciones entran en el diario (¿edición y creación de trenes; no conducción/economía?).
- Formato/extensiones de fichero del escenario y dónde vive (¿dentro del save o exportado aparte?).
- Frecuencia de checkpoints para undo y coste de memoria del snapshot del modo experimento.
- Semántica de "conservar" al salir del modo experimento (¿se convierte en acciones de edición?).
- Si algún día se quiere undo "en vivo", pasar a snapshots completos (viaje en el tiempo) — fuera de este diseño.

## Fuera de alcance
- Undo/redo de simulación/economía en vivo.
- Reescritura urgente de gramáticas (se decide después).
