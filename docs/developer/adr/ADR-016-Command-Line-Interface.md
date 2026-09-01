# ADR 016: In-Game Command Line Interface (CLI) y Sistema Modal (Estilo Vim)

## 1. Contexto

LeTrain necesita escalar la forma en que los jugadores interactúan con el mundo. A medida que las redes ferroviarias crecen, el uso exclusivo del ratón para construir vías, asignar trenes o configurar rutas se vuelve lento e ineficiente. 

Adicionalmente, se requiere sentar las bases para la futura programación y automatización de trenes mediante scripts. Por lo tanto, necesitamos un sistema que permita acciones complejas mediante texto (CLI) y que unifique la experiencia gráfica (atajos de teclado) con la consola de comandos.

## 2. Decisión Arquitectónica

Se implementará un motor de comandos basado en **ANTLR4** y un sistema de control de UI basado en un **Patrón Estado (State Machine)** con filosofía estricta tipo Vim.

Esta decisión se divide en las siguientes directrices técnicas:

### 2.1. Separación de Gramáticas (Player vs Scripts)
Para evitar que los scripts en segundo plano puedan alterar la topología del mundo (crear vías, mover el cursor), la gramática ANTLR4 se dividirá en dos ficheros estrictamente separados:
*   **`PlayerCommands.g4`**: Contiene todo el superconjunto de comandos (instanciación `new`, borrado `del`, movimiento de cursor `go`, modos `mode write`). Solo accesible por la terminal interactiva del jugador.
*   **`ScriptLogic.g4`**: Subconjunto restrictivo destinado a la automatización de trenes. Los comandos prohibidos (creación, cursor) generarán un error sintáctico temprano, garantizando la seguridad en tiempo de ejecución.

### 2.2. Interfaz Gráfica como Generador de Texto (Dogfooding)
Se adopta una filosofía de "Dogfooding" absoluto. Los atajos de teclado de la interfaz (tanto en 2D como en 3D) no invocarán métodos del motor de juego directamente (ej. `engine.moveCursor()`).
En su lugar, la interfaz actuará como una capa de macros que **generará comandos de texto puros** y los enviará al parser `PlayerCommands`.
*   *Ejemplo*: Si el usuario está en el modo correspondiente y pulsa `g` seguido de `e`, la interfaz genera el string `"go en"` y lo ejecuta de forma invisible. 
*   *Beneficio*: Garantiza paridad total. Cualquier cosa que se pueda hacer con atajos, se puede automatizar o escribir a mano.

### 2.3. Máquina de Estados Modal (Filosofía Vim)
Para evitar conflictos de teclado (Issue #370 donde `l` se usaba para "Link" y para moverse a la derecha), la captura de inputs utilizará un patrón estado estricto:
*   **`NORMAL_MODE`**: Estado por defecto. Las teclas cambian de modo. (Ej: pulsar `r` entra en `RAILS_MODE`).
*   **Modos Específicos (`RAILS_MODE`, `FORKS_MODE`, etc.)**: Sub-estados donde el teclado tiene funciones dedicadas. (Ej: en `RAILS_MODE`, `h, j, k, l` mueven el cursor, y `w` hace un toggle del lápiz).
*   **`COMMAND_MODE`**: Se entra pulsando `:`. Abre la consola de texto en la parte inferior. Al ejecutar el comando (Enter) o cancelar (Esc), el sistema **siempre** retorna a `NORMAL_MODE`, reseteando el estado anterior.

### 2.4. Resolución de Variables Desacoplada
El jugador podrá guardar variables temporales en su sesión (Ej: `$s1 = new station "Alpha"`). 
*   Estas variables residen en la consola y **solo guardan identificadores (UUIDs o Strings)**, nunca referencias en memoria (`hard references`) a los objetos del motor físico.
*   Si un objeto del motor se destruye (por un choque o por borrado manual), la variable `$s1` queda huérfana. Si el usuario intenta usar `$s1` de nuevo, la consola hará un *lookup* al motor, fallará al no encontrarlo y escupirá un error controlado.
*   *Beneficio*: Acoplamiento cero. El motor físico de LeTrain no necesita saber que la Consola existe ni notificarle la destrucción de entidades para hacer *garbage collection*.

### 2.5. Cursor "Turtle Graphics" y Filosofía UNIX
El cursor del jugador es un ente persistente con modos propios pasados como comandos al motor (`mode write`, `mode move`, `mode del`).
Se soportarán secuencias concatenadas (ej. `20, l, 10` -> avanza 20, gira izquierda, avanza 10).
*   **Ausencia de Rollback**: Siguiendo la filosofía UNIX, si una macro de construcción choca o falla en el paso 15 de 30, **no hay dry-run ni se deshace lo construido**. Se construye hasta el fallo, se cobra el dinero correspondiente, se escupe el error a la consola y se detiene la ejecución.
*   **Responsabilidad del jugador**: El sistema no protegerá al jugador de la bancarrota si ejecuta secuencias masivas sin fondos.

## 3. Consecuencias

### Positivas
*   **Resolución de conflictos UI**: Elimina de raíz problemas como el de la issue #370. El modo normal libera todas las teclas del teclado para comandos rápidos.
*   **Arquitectura Limpia**: Separar las gramáticas y usar la UI como un generador de strings asegura que el Core solo tiene una puerta de entrada para modificar el mundo, simplificando drásticamente el testing.
*   **Escalabilidad**: Preparados desde el día 1 para macros, *blueprints* y carga de scripts externos.

### Negativas / Riesgos
*   **Curva de Aprendizaje**: Los jugadores que no conozcan Vim o no lean la documentación podrían frustrarse si accidentalmente entran en un modo y no saben salir (requiere UX clara que indique "Pulsa ESC para modo normal").
*   **Latencia Perceptible**: Si los atajos de teclado generan texto, lo parsean (ANTLR4) y luego lo ejecutan, hay un pequeñísimo *overhead* vs llamadas directas. ANTLR4 es rápido, pero habrá que vigilar el rendimiento si un jugador deja pulsada una tecla.

## 4. Anexo: Diccionario del State Machine

| Estado Actual | Tecla Pulsada | Transición / Comando Generado |
|---|---|---|
| `NORMAL` | `r` | Entra a `RAILS_MODE` |
| `NORMAL` | `f` | Entra a `FORKS_MODE` |
| `NORMAL` | `:` | Entra a `COMMAND_MODE` (Abre UI de consola) |
| `Cualquiera` | `Esc` | Entra a `NORMAL_MODE` |
| `RAILS_MODE` | `h,j,k,l` | Genera comando `go w`, `go s`, `go n`, `go e` |
| `RAILS_MODE` | `w` | Genera comando `mode write` |
| `RAILS_MODE` | `x` | Genera comando `mode del` |
| `RAILS_MODE` | `g` + `e` | Genera comando `go en` |
