# ADR-020 en la práctica: qué hemos construido y por qué (guía para entender el código)

> Guía didáctica del trabajo integrado en `develop` vía PR #493, #495 y #496.
> Objetivo: que puedas leer el código con la misma seguridad con la que juegas a LeTrain.
> Si al terminar algo no encaja con lo que ves en el editor, es un fallo de esta guía, no tuyo: dímelo y lo corrijo.

---

## 1. El contexto: qué pedía el ADR-020

LeTrain edita un mundo vivo: trenes circulan mientras tú construyes. El ADR-020 plantea un diario de
comandos para poder **pausar la edición** y luego **deshacer/rehacer** lo construido, y más adelante
guardar/exportar "escenarios" como una semilla + diario.

Estas PRs entregan las tres primeras piezas del roadmap:

| # | Pieza | Problema que resuelve |
|---|-------|-----------------------|
| **1** (PR #493) | **Pausa de edición** (`x`) | Congelar el mundo en los modos de edición: nada se mueve mientras construyes, y construir es instantáneo (sin esperar el "túnel"). |
| **2** (PR #495) | **Diario de comandos** (`record on/off`, `journal;`) | Grabar los comandos que escribes para poder reproducirlos. |
| **3 fase 1** (PR #495 + #496) | **Undo/redo constructivo** | Deshacer y rehacer las ediciones hechas en pausa, tanto escritas en consola como pintadas por teclado, en la 2D y en la 3D. |


Todo lo de abajo se refiere sobre todo a la **pieza 3** (la más jugosa), pero las otras dos son sus cimientos.
---

## 2. La idea central (léela dos veces)

### El problema de "deshacer en un mundo"

En un editor de texto, deshacer es fácil: cada tecla modificó una línea y puedes "volver atrás".
En LeTrain un único comando `write 23` crea 23 vías conectadas entre sí y con el terreno. Si guardaras
una foto del mundo **tras cada comando** para poder volver, cada foto sería enorme y lenta.

La alternativa que usamos:

> **No guardamos el mundo "después de cada paso". Guardamos uno de cada varios pasos (checkpoint) y,
> para deshacer, restauramos el checkpoint más cercano y *re-ejecutamos* los comandos que quedan
> hasta donde queremos volver.**

Es exactamente el mismo truco que un videojuego con **partidas guardadas**:
- si quieres volver a la partida de hace 3 minutos, cargas el guardado de hace 5 y juegas 2 minutos rápido;
- el resultado es idéntico a "haber jugado normal", siempre que el juego sea **determinista**
  (misma entrada → misma salida).

### Las tres consecuencias de esa decisión

1. **Hay que saber re-ejecutar una edición tal cual ocurrió.** Por eso cada comando grabado es
   *canónico y auto-posicionado*: empieza con `go x,y; face d; ...` para que dé igual dónde esté el
   cursor cuando se reproduce.
2. **Solo grabamos en pausa.** Si el mundo corre, re-ejecutar no sería determinista (los trenes se
   mueven). Pausa encendida = "grabadora activa".
3. **El mundo restaurado desde un checkpoint debe comportarse igual que el mundo vivo.**
   Esto parece obvio, pero era falso en dos sitios, y ahí estuvieron los dos bugs gordos (sección 7).

---

## 3. El vocabulario que vas a leer en el código

| Término | Qué significa |
|---------|---------------|
| **Entrada / comando grabado** | Un string canónico, p. ej. `go 10,4; face e; write 5;` |
| **Checkpoint** | Foto (bytes) del mundo completo, tomada cada 10 entradas. El checkpooint 0 = el mundo al pulsar `x`. |
| **Slice / rebanada** | Los comandos entre un checkpoint y el punto donde queremos quedar (`[desde, hasta)`). |
| **Planner** | Quien *calcula* qué hacer (no lo ejecuta): dado "undo 2", decide qué checkpoint cargar y qué comandos re-ejecutar. |
| **Funnel (embudo)** | El punto donde las ediciones *entran* al historial: consola y teclado. |
| **Codec** | Quien serializa/deserializa el mundo a bytes (idéntico al guardado de partida, pero en memoria). |

---

## 4. Mapa del código (dónde vive cada cosa)

```
core/src/main/java/letrain/command/UndoRedoHistory.java   → el "planner" (motor, sin UI)
core/src/main/java/letrain/command/PlayerCommandExecutor.java → DSL undo/redo + ejecución
core/src/main/java/letrain/mvp/impl/RailTrackMaker.java   → grabador de pintado por TECLADO
core/src/main/java/letrain/mvp/impl/GameSaveService.java  → toBytes/fromBytes (codec de checkpoints)
ui-terminal/.../TerminalPresenter.java                    → wiring 2D (teclas, aplicar undo)
ui-graphic/.../GraphicPresenter.java                      → wiring 3D (igual pero con swap ligero)
ui-graphic/.../Gdx3DInputHandler.java                     → consola/historial/teclas 3D
ui-graphic/.../CommandHistory.java                        → historial de la consola 3D
```

Regla de oro del diseño:

> **El motor (`UndoRedoHistory`) NO toca el mundo.** Solo dice "carga este checkpoint y re-ejecuta
> estos comandos". Quien ejecuta es el presenter (2D o 3D), que ya sabe cómo aplicar un mundo nuevo.
> Por eso hay un único motor y dos capas de UI delgadas, y por eso es testeable sin pantalla.

### El contrato del historial (léelo en el Javadoc de `UndoRedoHistory`)

- `begin(mundo)` → arranca sesión al activar pausa; guarda el checkpoint 0.
- `record(comando)` → se llama **después** de que el comando ya se aplicó al mundo vivo.
- `planUndo(n)` / `planRedo(n)` → devuelven un `UndoPlan(base?, desde, hasta, destino)`.
- `restore(plan)` → deserializa el checkpoint base en un mundo **nuevo** (no muta el vivo).
- `commit(n)` → el llamador confirma "el mundo actual refleja exactamente n comandos".
- `end()` → al salir de pausa, se descarta todo (el mundo vuelve a correr).

Fíjate en la pareja `applied` / `size`:
- `size` = comandos totales grabados.
- `applied` = cuántos de esos comandos están **reflejados ahora mismo** en el mundo.
- Hacer undo mueve `applied` hacia atrás; rehacer lo mueve hacia delante; **grabar algo nuevo después
  de un undo borra la "cola de redo"** (como cualquier editor).

---

## 5. Qué ocurre exactamente cuando pulsas `u`

Supón: pausa ON, has hecho 3 comandos (`size=3`, `applied=3`) y pulsas `u`.

1. `TerminalPresenter.undo(1)` (o `GraphicPresenter.undo(1)`).
2. `planUndo(1)` → quiere `applied=2`. Como no hay checkpoint en 2, elige el **checkpoint 0**
   (la foto de cuando pulsaste `x`) y el slice = comandos `[0, 2)`, es decir "re-ejecuta los dos
   primeros".
3. `restore(plan)` → deserializa el checkpoint 0 en un mundo nuevo y vacío.
4. El presenter aplica ese mundo (`applyModel`): sin ruido en 2D; en 3D conserva cámara y audio.
5. Re-ejecuta los dos comandos del slice (igual que si fueran nuevos, con el mismo turtle).
6. `commit(2)` → el historial queda en `applied=2`.

```
checkpoints:        [0] -----(cada 10)------ [10] ...
comandos:            c0 c1 c2 c3 ... c9 c10 c11
                      ^________________^
                        slice a re-ejecutar
```

Redo es lo simétrico pero sin restaurar nada: parte del mundo actual y re-ejecuta hacia delante.

### Por qué "cada 10"

Menos checkpoints = menos memoria; más checkpoints = menos re-ejecución. Cada 10 es un equilibrio
razonable. El número está en `CHECKPOINT_EVERY`.

---

## 6. Los tres embudos: de dónde salen las entradas

Hay **tres** formas de editar, y las tres acaban siendo el mismo tipo de entrada:

1. **Consola 2D/3D** (`:` → comando → Enter): el presenter captura el prefijo
   `go x,y; face d; ` **antes** de ejecutar, ejecuta, y si fue bien y hay pausa, graba
   `prefijo + comando`.
2. **Teclado** (pintar con Shift+flechas, Insert/Home/Delete...): lo hace el `RailTrackMaker`
   directamente, y graba **una entrada por baldosa**, p. ej. `go 10,4; face e; write 1;`.
3. **Repetir con `.`** : es "consola" otra vez con el último comando.

**Regla anti-duplicados:** cuando un comando de consola como `write 5;` se ejecuta, la tortuga pinta
5 baldosas y cada una *podría* disparar el grabador de teclado. Para evitarlo, `TurtleBuilder`
activa `setJournalSuppressed(true)` durante las secuencias de consola: el que graba es el funnel de
consola (una entrada por línea), no el de teclado (que solo actúa cuando pintas a mano).

> Prueba mental: pinta 3 baldosas a mano → historial con 3 entradas; escribe `write 3;` → historial
> con 1 entrada. Ambos deshacen igual de "correcto", solo cambia la *granularidad*.

---

## 7. Los dos bugs que nos hicieron sudar (y por qué eran de diseño, no de código suelto)

El punto 2 decía: "re-ejecutar debe reproducir exactamente lo que pasó". Los dos fallos que viste en
el juego eran violaciones de eso, y aparecieron porque el replay es *sensible al estado que no se ve*.

### Bug A: el "encadenado" del pintor se pierde al cruzar un checkpoint

- El `RailTrackMaker` (el "pintor") guarda memoria interna: `oldTrack`, la baldosa anterior, para que
  la siguiente pieza **continúe** la vía (curvas, forks, conexiones).
- Esa memoria **no se serializa** (es estado de UI). Al restaurar un checkpoint en mitad de un trazo
  largo y re-ejecutar, el pintor recién creado no sabía que debía *continuar* la baldosa anterior:
  la primera pieza del slice se ponía suelta o girada, y a veces ni se colocaba.
- **Síntoma:** al pintar una línea larga a mano, el undo "rompía" la vía a la altura del checkpoint.
- **Fix:** cada entrada de teclado que *encadena* guarda además el punto de origen del encadenado
  (`resumeFrom` en `UndoRedoHistory`); antes de re-ejecutar un slice, el presenter "siembra" al pintor
  con la baldosa predecesora (`resumeChainFrom`). Resultado: el trazo se reconstruye exacto.

### Bug B: el terreno no era determinista fuera de lo ya materializado

- El mundo genera el terreno con ruido (Perlin) a partir de una semilla, pero solo **materializa**
  (guarda valores) las celdas que están en pantalla/radio del cursor, en "bloques".
- Los checkpoints guardan el modelo serializado... y las celdas de terreno (`cells`) no se serializan:
  solo los rectángulos de bloques ya materializados + la semilla para regenerarlos.
- Al deshacer un trazo largo (tipo `write 10,l,l,l,l,11,...` repetido con `.`), el replay se pintaba
  **más allá** de lo materializado en el checkpoint. Esas celdas devolvían "vacío" (`-1`) y el pintor
  **fallaba en silencio**: el undo parecía haberse comido vías de más, aunque el cursor retrocedía bien.
- **Fix en el motor** (`GroundMap`): `getValueAt` ahora, si la celda no está materializada, la
  **calcula sobre la marcha** con la misma fórmula determinista (extraída a `computeTerrainValue`,
  compartida con `generateTerrain`). Vivo y replay calculan *siempre* lo mismo en cualquier
  coordenada. Como el ruido se reconstruye desde la semilla del modelo al cargar, ambos coinciden.

> Moraleja: en un sistema "checkpoint + re-ejecutar", **todo estado que influya en la ejecución debe
> ser reproducible**. Esa es la invariante #1 del sistema, y es la primera pregunta que hay que
> hacerse ante cualquier bug raro de undo: "¿qué estado no estamos reproduciendo?"

---

## 8. Cómo se probó (y cómo probarlo tú)

### Los tests del motor (en `core/src/test/.../command/` y `mvp/impl/`)

- `UndoRedoHistoryTest` — la semántica pura del planner: round-trip undo→redo, determinismo multi-paso,
  descarte de la cola de redo, saltos por checkpoint, clamp al inicio.
- `UndoRedoDslTest` — el DSL `undo;`/`redo;`.
- `KeyboardRecorderTest` — que pintar por teclado graba y que el journal **reproduce byte-idéntico**
  entre dos copias del mismo mundo (incluye la regresión del Bug A: undo de una línea pintada que
  cruza el checkpoint, recta y con curva de 45°).
- `Gdx3DInputHandlerConsoleTest` (3D) — historial de consola (flechas) y `.`.

### Cómo ejecutarlos

```bash
mvn -pl core clean test -Dtest=UndoRedoHistoryTest,UndoRedoDslTest,KeyboardRecorderTest
mvn -pl ui-graphic test -Dtest=Gdx3DInputHandlerConsoleTest
```

### Un detalle importante de metodología

Al principio comparábamos mundos **por bytes** (serializar y comparar). Resultó ruidoso: dos mundos
semánticamente idénticos pueden serializar distinto (orden de HashMap, ids de Jackson). Para decidir
si el replay es fiel hay que comparar **semánticamente**: cursor + baldosas + routers. Si algún día
un test de bytes te falla raro, sospecha primero del comparador, no de la lógica.

---

## 9. Las decisiones que tomamos y su "por qué" (para que no parezcan arbitrarias)

| Decisión | Motivo |
|----------|--------|
| Motor en `core`, UI delgada en cada presenter | Un solo sitio con la lógica; testeable sin pantalla; 2D y 3D comparten. |
| Planner que **no ejecuta** | El "cómo aplicar un mundo" ya existe en los presenters (`applyModel`). No duplicamos ejecución. |
| Grabar **solo en pausa** | Re-ejecutar requiere mundo congelado; fuera de pausa no hay undo (no sería fiel). |
| Comando canónico con `go/face` | Que el replay no dependa de dónde esté el cursor en el momento de deshacer. |
| `u` = undo / `Ctrl+R` = redo (y no Ctrl+Z) | Ctrl+Z en terminal lanza SIGTSTP (congela la app). Por eso se evitó a propósito. |
| `u` fuera de pausa sigue siendo UNLINK | No rompemos atajos existentes fuera del contexto de edición. |
| Historial de consola vive en el **presenter** (no en el handler 3D) | El handler se recrea en cada undo; si el historial viviera ahí se borraría (bug real que vimos). |
| Swap de modelo "ligero" en 3D | Reutiliza renderer y conserva cámara/audio; recrear todo por undo sería caro. |
| Terreno determinista bajo demanda en `GroundMap` | Es la única forma de que checkpoint+replay sea fiel en cualquier coordenada. |

---

## 10. Cómo no perder el control (lectura rápida cuando algo falle)

Si un undo se comporta raro en el futuro, sigue esta escalera:

1. **¿El mundo estaba en pausa?** Sin pausa no hay historial: comprueba que la tecla `x` está ON.
2. **¿El comando entró en el historial?** En el código, busca el funnel que corresponda (consola o
   teclado). Pista: solo entran comandos **exitosos** y en pausa.
3. **¿El replay reproduce lo que pasó?** Ejecuta el escenario en un test con comparación **semántica**.
   Si falla, pregúntate (sección 7): "¿qué estado no se está reproduciendo?".
4. **¿El cursor/`applied` cuadran?** El log de `applyPlan` imprime `from/to/target/applied/size`;
   el cursor tras el undo debe coincidir con el inicio del siguiente comando grabado.

### Los ficheros que "mandan"

- Si dudas de la **semántica de undo/redo**: `UndoRedoHistory.java` (y sus tests).
- Si dudas de **qué se graba**: los funnels (`TerminalPresenter.executeCommand`,
  `GraphicPresenter`/`Gdx3DInputHandler`, `RailTrackMaker.journalKeyboardEdit`).
- Si dudas de **cómo se aplica un mundo nuevo**: `applyModel` de cada presenter.
- Si dudas del **terreno**: `GroundMap.computeTerrainValue` y `getValueAt`.

---

## 11. Glosario exprés

- **Checkpoint** — foto del mundo cada 10 entradas (y la 0 al empezar pausa).
- **Slice** — comandos a re-ejecutar sobre el checkpoint.
- **Funnel** — punto de entrada de las ediciones al historial.
- **Canónico/auto-posicionado** — comando que se basta a sí mismo (`go x,y; face d; ...`).
- **Codec** — serializa/deserializa el mundo en memoria.
- **Determinismo** — misma entrada + mismo estado ⇒ misma salida. Es el pilar de todo.
- **Encadenado (chaining)** — memoria interna del pintor para continuar una vía.
- **Materializar terreno** — generar y cachear los valores de terreno de una zona.

---

*Fin. Si has llegado hasta aquí y el editor te sigue pareciendo una caja negra, dime qué sección no
te encaja y la amplío con un ejemplo concreto partiendo de tu duda.*
