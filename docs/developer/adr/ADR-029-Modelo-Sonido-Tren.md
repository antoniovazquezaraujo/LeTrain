# ADR-029: Modelo de sonido del tren (materiales por marcha y transiciones)

## Estado: PROPUESTO (borrador para revisión)

> **Enmienda 1 — 2026-10-02.** Motivo: la **forja** de materiales queda descartada (§7) —el
> material por marcha y rampa ya existe, **generado a mano y commiteado en el repo**— y se fija
> la **sincronía audio↔física** (§2): el audio va esclavo de `Locomotive.currentSpeed` y el
> one-shot arranca al inicio del paso físico. El ADR sigue en **PROPUESTO**: la enmienda se aplica
> sobre el borrador antes de su merge a `develop`, por lo que no se abre ADR nuevo.

> **Enmienda 2 — 2026-10-02.** Decisiones de material y hallazgos de la auditoría:
>
> 1. **Clics de onset**: 10 de las 18 transiciones arrancan en escalón hasta −5 dBFS → se corrigen
>    **en el reproductor** con fade-in corto de **10–20 ms** al inicio de cada one-shot; los WAVs
>    **no se regeneran** (§4).
> 2. **Materiales en llegada**: `idle`, `start`, `stop` y `rolling` los está generando el usuario a
>    mano y se añadirán al set; hasta su entrega manda el **fallback legacy** con `train-sound.wav`
>    (§3).
> 3. **Procedencia**: el usuario **confirma autoría propia** de los 28 WAVs, que se commitean bajo la
>    licencia del proyecto; `notch-6` queda documentado como **síntesis propia a partir de material
>    del repo** (el segmento `cruise`), no como asset licenciado. `train-sound.wav` conserva su nota
>    de procedencia por auditar hasta su retirada (§7).
> 4. **Mono**: los 28 WAVs se convierten a **mono 44,1 kHz PCM16** antes de commitearse →
>    **≈13,26 MB** en repo/release y **la RAM no cambia** (≈25,3 MiB decodificados), porque
>    `AudioSample` ya descarta el canal derecho (§6).
>
> **Auditoría de bucles incorporada (§5)**: duraciones exactas por material, puntos
> `loop=<inicio>,<longitud>` medidos para los 10 notches —el wrap a fichero completo **no cierra
> limpio**, así que **`loop=auto` queda descartado**—, niveles (RMS/picos) y escalera de pitch.

> **Enmienda 3 — 2026-10-02.** **Nomenclatura de materiales 100 % en inglés**, decidida por el
> usuario: la taxonomía no mezcla idiomas. Se renombran `rodadura` → **`rolling`** (alias legacy
> `wagons`), `frenos` → **`brakes`** (alias legacy el fichero `train-brakes.wav`; esa label nunca
> existió en `train-sound-labels.txt`) y `bocina` → **`horn`**. El resto ya estaba en inglés
> (`idle`, `notch-1`…`notch-10`, `trans-<from>-<to>`, `start`, `stop`). Los alias siguen
> resolviendo para leer material legacy, pero **el ID canónico y todos los ejemplos usan los
> nombres ingleses**. Cambio sin impacto: **ni el set ni ningún ID de este ADR están todavía
> commiteados**, así que es solo documentación — Contexto, §1, §3, §5, §6 y preguntas abiertas, más
> el bloque de la enmienda 2, que ya citaba `rodadura`—; el español queda reservado a la prosa.

## Contexto

Este ADR **concreta la fase de tren** que [[ADR-026-Audio-Domains]] deja abierta (dominio
`train-audio`) y define el **contrato de material** que consumirá el contenido propietario de
[[ADR-028-Sound-Packs-Externos-Cifrados]]. No cambia la simulación.

### Cómo suena el tren hoy

- Un único WAV, `core/src/main/resources/sound/train-sound.wav`, con regiones etiquetadas en
  `train-sound-labels.txt` y parseadas por `AudacityLabelParser`:

  | Label | Rango (s) | Uso actual |
  |---|---|---|
  | `start` | 0.464–6.780 | arranque del motor (una vez) |
  | `ralenti` | 6.780–24.984 | bucle de la marcha 0 |
  | `cruise` | 24.984–33.715 | **única fuente de las marchas 1–10** |
  | `stop` | 33.715–38.206 | apagado del motor (una vez) |
  | `wagons` | 38.431–87.473 | rodadura de vagones, volumen por velocidad (→ material `rolling`) |

- `TrainSynthesizer.buildNotches()` construye 11 marchas (`SpeedNotch`): 0 = `ralenti` a pitch 1.0;
  1–10 = **el mismo segmento `cruise`** con pitch de reproducción
  `1.10 + (i-1)·0.9/9` → 1.10…2.00, con `rampTime = 2.0 s`.
- La máquina de estados (`OFF`, `STARTING`, `IDLE`, `CRUISING`, `TRANSITIONING_UP/DOWN`, `STOPPING`,
  `LOAD_ONLY`) cambia de marcha **de una en una**: `startTransition()` fija como destino
  `currentNotchIndex ± 1` y aplica una **rampa de pitch de 2 s**; un 2→5 suena como tres rampas
  encadenadas (6 s), no como un salto. `forceIdle()` corta a marcha 0; frenos (`train-brakes.wav`)
  y carga (`load-unload.wav`) son bucles independientes con volumen rampeado.
- La simulación **sí tiene marchas discretas**: `Locomotive.MAX_SPEED = 10`, con
  `currentSpeed`/`targetSpeed` enteros 0–10. `updateInertia()` mueve la velocidad **de marcha en
  marcha** (un notch cada `max(1, currentSpeed × 2)` raíles; `× 1` frenando). `AudioController`
  mapea `loco.getTargetSpeed()` → `synth.setThrottle()` en cada `update()`, con el resto del estado
  (frenada, carga, posición, velocidad de movimiento). El audio no manda en la física.
- Deuda de licencias: la procedencia de los WAV legacy de `core` (`train-sound.wav` incluido) no
  está auditada; [[ADR-026-Audio-Domains]] ya señala la revisión como tarea pendiente. El set
  `train/generic/` **no** comparte esa deuda: su autoría propia está confirmada (§Material).

### Material: propio (ya en repo) y del licenciante (pendiente)

- **Material propio del proyecto (autoría propia confirmada)**: 28 WAVs en
  `core/src/main/resources/sound/train/generic/` — `notch-1`…`notch-10` (bucles de marcha de
  **8,730703 s = 385.024 frames**, la misma longitud que el segmento `cruise` legacy) y las 18
  parejas adyacentes `trans-1-2`…`trans-9-10` (**5,000000 s = 220.500 frames** subiendo) y
  `trans-2-1`…`trans-10-9` (**2,000000 s = 88.200 frames** bajando), **PCM16 44,1 kHz mono** (el
  set se toma en estéreo y se convierte a mono antes de commitearse, §6). El usuario **confirma su
  autoría**: entran en el repo como **assets propios bajo la licencia del proyecto**, y `notch-6`
  queda documentado como **síntesis propia a partir de material del repo** (el segmento `cruise` de
  `train-sound.wav`), no como asset licenciado. Son la fuente de verdad del algoritmo, de la escucha
  en desarrollo y del test contrato (§7).
- **Materiales en llegada**: `idle`, `start`, `stop` y `rolling` **los está generando el usuario a
  mano** y se añadirán al set con el mismo tratamiento; hasta su entrega manda el fallback legacy
  con `train-sound.wav` (§3). `brakes`, `horn`, `trans-0-1` y las parejas compuestas siguen sin
  material.
- **Material del licenciante (Carlos / Railsounds)**: grabaciones reales **por marcha** (el pitch
  real de cada notch, sin pitch-shift) y grabaciones reales de **transiciones entre pares
  ordenados** (p. ej. 2→3, 3→4, 2→5). Un salto compuesto suena distinto —más esfuerzo, cambio de
  torque audible— que una escalera con calma.
- El acuerdo con el licenciante está pendiente de firma: **ningún asset del licenciante entra en
  el repo** ni siquiera temporal ([[ADR-028-Sound-Packs-Externos-Cifrados]]); se entregará como
  pack cifrado y su llegada será un **swap de ficheros**, no de algoritmo (§8).

### Por qué el modelo actual no vale para ese material

1. El pitch-shift de `cruise` **no es el pitch real** de la locomotora en cada marcha: mueve la
   frecuencia, pero la envolvente espectral y los transitorios son los de una única toma
   ("cinta acelerada").
2. **No hay transiciones ordenadas**: toda pareja se reduce a la misma rampa de pitch. La
   diferencia de esfuerzo/torque entre un 2→5 directo y un 2→3→4→5 no es representable.
3. **Una sola toma para todas las locomotoras**: no existen perfiles por tipo de loco.
4. La duración de transición (2 s fijos por notch) está **desacoplada de la inercia real** del tren.
5. No hay dónde declarar **puntos de bucle, ganancia ni metadatos** por material: todo está
   cableado a las cinco labels del fichero único.

### Enfoque acordado: contrato primero, assets a mano en repo

Construir el algoritmo contra el **contrato final de materiales** (taxonomía + metadatos) y
alimentarlo con **assets a mano, commiteados en el repo**: un WAV por marcha
(`notch-1`…`notch-10`) y uno por rampa adyacente (`trans-<from>-<to>`) en
`core/src/main/resources/sound/train/generic/`, más un **descriptor de perfil** versionado junto
al código (§5).

**La forja queda descartada**: no hay módulo `material-forge`, ni manifiesto generado, ni `.ltsp`
de referencia, ni clave de test de material (§7). El determinismo de los tests deja de salir de una
herramienta reproducible y pasa a salir de (a) los props commiteados y (b) WAVs sintéticos
pequeños generados en `src/test/java`.

El `.ltsp` / LeCript de [[ADR-028-Sound-Packs-Externos-Cifrados]] **sigue siendo el vehículo del
pack propietario futuro**, pero **deja de ser prerrequisito del algoritmo**: el modelo se
implementa y se prueba contra el classpath, y el enchufe del pack entra después por un seam (§5).

Así la llegada del material del licenciante sigue siendo un **swap de ficheros**, no un cambio de
algoritmo.

## Decisión (propuesta)

### 1. Taxonomía de materiales por locomotora (perfil)

Cada locomotora se sirve de un **perfil de sonido** con IDs lógicos estables. El perfil se elige
por tipo de locomotora; existe un perfil **`generic`** como fallback. Los IDs son rutas lógicas
compatibles con el modelo de [[ADR-028-Sound-Packs-Externos-Cifrados]]
(`train/<perfil>/<material>.wav`):

| Rol | ID lógico (en) | Tipo | Notas / alias legacy |
|---|---|---|---|
| Ralentí | `idle` | loop | marcha 0 |
| Marchas | `notch-1` … `notch-10` | loop con puntos de bucle | pitch propio por marcha (§5); `notch-6` = síntesis propia de `cruise` (§7) |
| Transiciones | `trans-<from>-<to>` (p. ej. `trans-2-3`, `trans-2-5`, `trans-5-2`) | one-shot | pares **ordenados**, incluidos saltos compuestos |
| Arranque | `start` | one-shot | encendido del motor |
| Parada | `stop` | one-shot | apagado |
| Frenos | `brakes` | loop | reservado; **alias legacy `train-brakes.wav`** (hoy suena como SFX propio); no existía label en el WAV legacy |
| Rodadura | `rolling` | loop | volumen por velocidad; **alias legacy `wagons`** (label de `train-sound-labels.txt`) |
| Bocina | `horn` | one-shot | reservado; **sin alias legacy** (nunca existió) |

- `trans-<from>-<to>` es **direccional**: `trans-5-2` no es `trans-2-5`. Los pasos simples
  (`trans-2-3`, `trans-3-2`, …) son la unidad; los compuestos son material extra que el licenciante
  puede entregar por fases.
- **Nomenclatura (enmienda 3)**: los IDs canónicos van **en inglés**, sin mezclar idiomas —
  `rolling`, `brakes`, `horn`. Los alias legacy (`wagons`, `train-brakes`) siguen resolviendo en el
  resolutor para leer material ya nombrado en clave antigua, pero **no aparecen en los descriptores
  ni en los ejemplos**.
- Cobertura actual del set propio: **10 bucles + 18 transiciones adyacentes**. Faltan `idle`,
  `start`, `stop` y `rolling` (**en generación por el usuario**, §3) y, de forma no prevista,
  `brakes`, `horn`, `trans-0-1` y todas las compuestas → caen al fallback del punto 3.
- Sin perfil específico → `generic`; sin material concreto → fallback del punto 3. Nada deja al tren
  mudo.
- La taxonomía es el **contrato con el licenciante**: nombres, direccionalidad y metadatos.

### 2. Selección event-driven y coalescing

- El tren se describe con **snapshot** ([[ADR-026-Audio-Domains]]): `currentNotch`
  (`Locomotive.currentSpeed`), `targetNotch` (`targetSpeed`), freno, carga, velocidad de
  movimiento. **El audio consume cambios/eventos, no hace polling dentro de la física** (regla de
  evitar lógica en ticks).
- **El audio va esclavo de la física** *(fijado en la enmienda)*: el one-shot `trans-<from>-<to>`
  **arranca al inicio del paso físico**, es decir en cuanto `currentNotch ≠ targetNotch` marca un
  paso pendiente en la dirección de la palanca, y se **encadena** (`trans-<a>-<b>`, luego
  `trans-<b>-<c>`…) hasta que `currentNotch == targetNotch`. `targetNotch` **no** dispara audio por
  sí mismo: actúa solo de *lookahead* para coalescing y precarga. Un cambio de palanca sin paso
  pendiente no suena (solo prepara material).
- **Cuadre con la inercia — por qué duran 5,0 s y 2,0 s**: `Locomotive.updateInertia()` cuesta
  `max(1, currentSpeed × 2)` raíles al acelerar y `max(1, currentSpeed)` al frenar, a
  `50 / currentSpeed` ticks por raíl y 20 TPS ([[ADR-022-Game-Time]]) → **≈100 ticks (4,5–5,0 s)
  por marcha al acelerar** y **≈50 ticks (2,25–2,50 s) al frenar**. *(Enmienda de física: el paso
  `0 → 1` ya no es inmediato — cuesta `Locomotive.START_STEP_TICKS` = 100 ticks (~5 s), como
  cualquier tramo completo de subida, así que `trans-0-1` debe caber en su tramo igual que las
  demás transiciones.)* Las grabaciones están **calibradas contra esa inercia**: la subida cabe
  entera en su paso, y la bajada termina 0,25–0,5 s antes (se espera que haga crossfade al loop y
  espere, §4).
- **La evaluación ocurre una vez por frame, nunca dentro de la física**: `AudioController.update()`
  pasa **ambos** valores al synth — `setThrottle(target)` más **`setCurrentNotch(current)`**
  *(método nuevo)* —; los setters solo guardan el snapshot y marcan la evaluación como pendiente, y
  `update()` del synth la resuelve en **un único punto**. Así los dos valores siempre se ven
  consistentes y la simulación sigue sin conocer el audio (solo el flag `forceIdleSound` que ya
  existe).
- **Coalescing**: `coalesceWindow = 400 ms` (rango propuesto 300–500 ms), **global por ahora**.
  Actúa como *debounce* del borde `currentNotch ≠ targetNotch`: solo se lanza una transición si el
  paso sigue pendiente transcurrida la ventana, de modo que un retobillo de palanca no genera ráfagas
  de one-shots. Si existe material compuesto (`trans-2-5`), la ventana decide además si el tramo se
  colapsa en **una transición compuesta** al arrancar el primer paso físico: los pasos intermedios
  (`current` 3 y 4) no la interrumpen; si el destino se estabiliza en un notch intermedio (**dwell**,
  p. ej. la palanca se queda en 3), se reproducen **transiciones individuales**
  (`trans-2-3`, luego `trans-3-4`…).
- Si durante una transición el destino cambia de forma que ya no cubre el tramo (p. ej. baja a 3), se
  **corta con fade corto** (§4) y se reevalúa. Los cambios rápidos de palanca se resuelven con
  crossfades, nunca dejando la voz muda.

### 3. Fallbacks incrementales

Para cada transición solicitada `from → to` se resuelve en este orden:

1. **Pareja exacta** `trans-<from>-<to>`.
2. **Cadena de saltos simples**: descomposición `from → from±1 → … → to` usando las parejas
   adyacentes disponibles; las etapas sin material caen al punto 3.
3. **Rampa de pitch** del material de la marcha destino partiendo del actual (**modo legado**, el
   comportamiento de hoy, sobre el segmento `cruise` + labels), que garantiza sonido aunque el
   material esté incompleto.
4. **Crossfade al loop de la marcha destino** (`notch-to`): red de seguridad para cuando no hay ni
   material ni rampa legada — por ejemplo la retirada futura de `train-sound.wav` (ver abajo), un
   perfil sin `cruise`, o un material ausente en todos los niveles anteriores.
   *(Nivel añadido en la enmienda: ninguna combinación deja al tren mudo.)*

Igual para loops: `notch-N` ausente → perfil `generic`, re-pitch de la marcha más cercana o, en su
defecto, el nivel 4. El material puede crecer por fases (primero marchas, luego transiciones
adyacentes, luego compuestas) **sin romper nada**: los fallbacks van entrando solos.

**Retención y retirada de `train-sound.wav`.** El WAV legacy y sus labels **se mantienen** mientras
queden materiales sin sustituir —hoy `idle` (ralenti), `start`, `stop` y `rolling` (alias legacy
`wagons`), **ya en generación por el usuario** (§Contexto), más el nivel 3 del fallback— **y
mientras sostengan los tests de regresión existentes** (`AudioControllerTest` depende del segmento
`stop`). **Hasta que esos cuatro materiales lleguen, manda el fallback legacy**: `idle`, `start`,
`stop` y `rolling` se sirven del `train-sound.wav` con sus labels (`ralenti`, `start`, `stop`,
`wagons`), mientras las marchas 1–10 y sus transiciones ya van por el material propio. La retirada
es una **decisión futura explícita**, condicionada a: (a) la entrega efectiva de los materiales
propios de `idle`/`start`/`stop`/`rolling`, (b) confirmar que el nivel 3 ya no se usa en ningún
perfil soportado y (c) decidir si el pitch-shift de emergencia sigue necesitando `cruise`. No se
borra `train-sound-labels.txt` mientras exista el nivel 3.

### 4. Política de duración real vs inercia del juego

- **La física manda**: la grabación dura lo que dura y la simulación no se toca.
- **El one-shot se deja terminar mientras cubra el estado físico.** Al terminar —o al completarse
  el último tramo de una cadena— se hace **crossfade de 200 ms** al loop de la marcha física
  alcanzada (`notch-currentNotch`). Si la subida continúa, el siguiente paso encadena su transición
  desde ahí, sin pasar por el loop intermedio. **Ese crossfade de salida es obligatorio**: la
  auditoría mide que **5 de las 18 transiciones terminan a nivel alto (hasta −4,6 dBFS)**, así que
  un corte seco siempre clicaría (§5).
- **Fade-in de onset**: cada one-shot arranca con un **fade-in de 10–20 ms**. Motivo medido: **10 de
  las 18 transiciones empiezan en escalón hasta −5 dBFS**. Se corrige **en el reproductor** y los
  WAVs **no se regeneran** *(decisión de la enmienda 2)*. Mismo fade en `start`/`stop` cuando
  lleguen.
- **Si el estado físico abandona el tramo** (cambio de destino, frenada, fin de vía, `forceIdle`) se
  **corta con fade de 100 ms** y se reevalúa; nunca se alarga ni se acorta la física para cuadrar el
  audio.
- **Valores por defecto fijados por las enmiendas**: fade-in de onset **10–20 ms** (rango 10–30 ms),
  crossfade **200 ms** (rango 150–300 ms) y corte **100 ms** (rango 50–150 ms). Son **política
  global**; declararlos por material (`cutPolicy`) queda abierto (ver preguntas abiertas).
- **Bajada**: el material dura 2,0 s y su paso físico 2,25–2,50 s, así que el audio termina antes y
  espera en el loop de la marcha actual el resto del paso. Es el comportamiento esperado, no un
  defecto.
- El **corte y el crossfade** son la política por defecto; la puerta queda abierta a **rampas
  data-driven por perfil** (tiempos declarados por material) en el futuro, fuera del alcance de este
  ADR.

### 5. Metadatos del material

- **En el pack** ([[ADR-028-Sound-Packs-Externos-Cifrados]]): el manifiesto ya declara `packId`,
  `packVersion`, procedencia y `kdfSalt`; cada entrada declara id lógico, `codec`, `sampleRate` y
  `channels`. La taxonomía viaja en los IDs. **No hay cambio de formato ni de LeCript.**
- **En el repo (datos abiertos)**: un **descriptor de perfil** por locomotora en
  **`core/src/main/resources/sound/profiles/generic.profile`**, con formato **DSL de texto tipo
  `.sound`** (mismo estilo que los `styles/*.sound` de `soundscape`: `clave = valor`, `#` para
  comentarios, sin dependencias externas). Ejemplo:

  ```
  # ADR-029 §5 — perfil de sonido del tren
  # ('loop' = puntos medidos por la auditoría; 'gain'/'effort' ilustrativos hasta la PR B)
  profile     = generic
  packVersion = 0

  # material  = ruta classpath                          metadatos
  notch.1     = sound/train/generic/notch-1.wav   loop=4.478,4.253  gain=0.0
  notch.2     = sound/train/generic/notch-2.wav   loop=6.301,2.429  gain=0.0
  notch.3     = sound/train/generic/notch-3.wav   loop=6.684,2.046  gain=0.0
  notch.4     = sound/train/generic/notch-4.wav   loop=6.685,2.046  gain=0.0
  notch.5     = sound/train/generic/notch-5.wav   loop=4.734,3.997  gain=0.0
  notch.6     = sound/train/generic/notch-6.wav   loop=3.747,4.983  gain=0.0
  notch.7     = sound/train/generic/notch-7.wav   loop=4.630,4.101  gain=0.0
  notch.8     = sound/train/generic/notch-8.wav   loop=7.764,0.967  gain=0.0
  notch.9     = sound/train/generic/notch-9.wav   loop=3.643,5.088  gain=0.0
  notch.10    = sound/train/generic/notch-10.wav  loop=7.509,1.222  gain=0.0

  trans.1-2   = sound/train/generic/trans-1-2.wav  effort=low  torque=free
  trans.9-10  = sound/train/generic/trans-9-10.wav effort=high torque=loaded
  ...                                                  # las 16 adyacentes restantes

  # FALTAN hoy (→ fallback §3): idle, start, stop, rolling (EN LLEGADA), brakes,
  #   horn, trans-0-1, las compuestas (trans.2-5, …) y sus mismos campos effort/torque
  #
  # EN LLEGADA — mismo formato, IDs canónicos en inglés (enmienda 3):
  # rolling = sound/train/generic/rolling.wav  loop=<medido>  gain=<medido>
  # brakes  = sound/train/generic/brakes.wav   loop=<medido>  gain=<medido>
  # horn    = sound/train/generic/horn.wav                   gain=<medido>
  # (los alias legacy 'wagons' y 'train-brakes' NO se escriben aquí: solo resuelve el resolutor)
  ```

  - **Campos**: **`loop=<inicio>,<longitud>`** con **puntos de bucle en segundos** respecto al
    inicio del fichero — `inicio` = primer sample de la ventana de loop, `longitud` = duración de
    esa ventana, sobre la que `GrainEngine` aplica su crossfade interno de **5.000 muestras**
    (≈113 ms a 44,1 kHz); **`loop` es obligatorio en los bucles**. `gain=<dB>` ganancia por
    material, pensada para **normalizar el RMS medido** (ver auditoría abajo); `effort` y `torque`
    etiquetas opcionales que se parsean y se ignoran hoy (contrato con el licenciante, §1);
    `packVersion` para validar que pack y perfil encajan.
  - **Ubicación**: el descriptor **vive con el código**, se versiona en el repo y mapea IDs lógicos
    (`train/<perfil>/<material>`) a rutas de classpath.
  - **Validación**: ID desconocido, ruta ausente, línea malformada o **bucle sin `loop` declarado** →
    **`WARN` + fallback (§3)**, nunca excepción.
  - **Alias legacy (enmienda 3)**: además del ID canónico, `MaterialResolver` resuelve una tabla
    fija **`wagons` → `rolling`** y **`train-brakes` → `brakes`**, de modo que el material ya
    nombrado en clave legacy sigue resolviendo sin duplicarlo. Los descriptores y los ejemplos
    **solo** escriben los canónicos en inglés; `horn` no tiene alias porque nunca existió.
  - **Auditoría de los bucles (datos medidos, enmienda 2)**:
    - Duraciones exactas: notch **385.024 frames (8,730703 s)**; trans de subida **220.500 frames
      (5,000000 s)**; trans de bajada **88.200 frames (2,000000 s)**. Sin chunk `smpl` en los
      ficheros, por eso los puntos viajan en el descriptor y no en el WAV.
    - **`loop=auto` queda descartado**: el wrap a fichero completo **no cierra limpio** (z ≈ 0,27–0,46;
      residuo −11,6…−13,6 dB). El descriptor **debe** declarar puntos.
    - Calidad de los puntos medidos para los 10 notches (los de arriba): **z5000 = 0,74–0,89 /
      residuo −15…−18 dB**, es decir **paridad con el `cruise` legacy (0,79 / −15,0 dB)**. Son
      puntos de partida válidos; **refinarlos no cambia el formato**.
    - Nivel: RMS de los notches **−9,34…−10,66 dBFS** (spread **1,32 dB**) con picos **≈0 dBFS** →
      `gain` existe para igualarlos; sin normalizar, el paso de marcha se percibe como cambio de
      volumen y no de esfuerzo.
    - Escalera de pitch: **f0 39,95 → 110,25 Hz** (**≈1,12× por marcha**, 2 semitonos), sin
      clipping, y coherente en los 10 → las marchas tienen **pitch propio grabado** (o sintetizado
      en el caso de `notch-6`, §7), no un re-pitch de `cruise` aplicado en runtime.
    - Transiciones: **5 de 18 terminan a nivel alto (hasta −4,6 dBFS)** → el crossfade de salida de
      §4 es obligatorio. **10 de 18 arrancan en escalón hasta −5 dBFS** → el fade-in de onset de §4.
- **Resolución**: el *lookup* de un material pasa por una interfaz propia, **`MaterialResolver`**
  (`MaterialId → Optional<AudioSample>`), que en esta fase resuelve contra el classpath. Esa
  interfaz es el **seam hacia el `SoundProvider` de ADR-028**: cuando el pack exista se añade la
  implementación de pack y el orden *pack → classpath* pasa a ser suyo, **sin tocar el synth ni el
  descriptor**. Así el mismo perfil sirve para classpath y para pack sin ramas en el código.

### 6. Carga y memoria

Datos medidos del set de 28 ficheros. **El set se commitea en mono** 44,1 kHz PCM16 (enmienda 2).
`AudioSample` decodifica a `float` **un sample por frame y descarta el canal derecho**, así que el
cambio a mono **parte el coste en disco pero no altera la RAM decodificada**: sigue siendo
**≈25,3 MiB** para los 28 materiales.

| Conjunto | Ficheros | Duración (frames) | En disco (mono PCM16) | RAM decodificada (`float[]`) |
|---|---|---|---|---|
| `notch-1`…`notch-10` | 10 | 385.024 (8,730703 s) | 7,70 MB | 15,40 MB |
| `trans` de subida | 9 | 220.500 (5,000000 s) | 3,97 MB | 7,94 MB |
| `trans` de bajada | 9 | 88.200 (2,000000 s) | 1,59 MB | 3,18 MB |
| **Total** | **28** | 6.628.540 | **≈13,26 MB (≈12,64 MiB)** | **≈26,51 MB (≈25,3 MiB)** |

- **Carga `EAGER`, compartida y estática al primer arranque de motor**: los 28 se decodifican una
  sola vez en el constructor del **primer** `TrainSynthesizer` (no al abrir la aplicación), igual que
  hoy `sharedSample`. Estimación 150–300 ms de I/O + conversión; **se mide en la PR del banco de
  materiales** y, si resulta perceptible, se adelanta el precalentamiento a un hilo de fondo.
- **`ON_DEMAND` queda como política futura** (caché LRU + `prefetch` desde el cambio de palanca): la
  API del banco no cambia, solo la política, así que puede llegar en una PR aparte sin tocar la
  selección. Mientras no exista no hay que vigilar huecos de audio.
- **El coste residente real depende de la política de carga**: con `EAGER` completo son los
  ≈25,3 MiB de la tabla; con un **`EAGER` parcial** (p. ej. solo las marchas ±1 y sus cuatro
  transiciones, ≈5 MiB) u **`ON_DEMAND`** baja una orden de magnitud. El disco, en cambio, es fijo:
  los 13,26 MB están en el artefacto viva o no la carga.
- **Sin material cargado no se dispara la transición**: se cae al fallback (§3), que ya está en
  memoria.
- **Coste aceptado y declarado**: el artefacto de release (`mvn clean package`) crece **≈ +13,26 MB**
  de WAVs en `core` (de ~76 MB a ~89 MB de recursos de sonido) —la conversión a mono lo deja en la
  mitad del +26 MB del estéreo—. Los cuatro materiales **en llegada** (`idle`, `start`, `stop` y
  `rolling`, §3) sumarán a estas cifras cuando se entreguen.
- El límite de **100 MB por payload de ADR-028** sigue vigente para el pack propietario; este set
  vive en el classpath y no entra en ese presupuesto, pero conviene mantenerlo vigilado si el perfil
  crece a más locomotoras.

### 7. Assets en repo + fixtures de test

- **Los 28 WAVs se commitean** en `core/src/main/resources/sound/train/generic/` como **assets
  propios del proyecto bajo su licencia** —autoría propia confirmada por el usuario (enmienda 2)—
  en **mono 44,1 kHz PCM16**. Única precisión de procedencia: **`notch-6` es síntesis propia a
  partir de material del repo** (el segmento `cruise` de `train-sound.wav`), no un asset licenciado
  ni una copia; el resto se ha generado a mano sin tomar material de terceros. Son la fuente de
  verdad del material que suena en desarrollo. `train-sound.wav` **conserva su nota de procedencia
  por auditar** hasta su retirada (§3): esa deuda es del legacy y no se traspasa al set nuevo.
- **No existe ninguna forja**: quedan descartados el módulo `material-forge`, el manifiesto
  generado, el `.ltsp` de referencia y la clave de test de material.
- **Determinismo de los tests**, que ya no sale de una herramienta reproducible sino de dos fuentes:
  1. **Fixtures WAV sintéticos** generados en `src/test/java` (`WavFixture`: bucles sinusoidales
     cortos escritos en un `@TempDir`, sin binarios commiteados). Permiten construir escenarios
     imposibles con el set real (perfil sin `notch-3`, cadena incompleta, `gain` fuera de rango) y
     mantienen los tests unitarios **rápidos e independientes** de la RAM del set (25,3 MiB).
  2. **Un test contrato sobre los assets reales**: presencia de los 28, formato **mono 44,1 kHz
     PCM16**, duraciones exactas en frames (**385.024 / 220.500 / 88.200**) y resolución de todas
     las parejas adyacentes; conviene añadir **`loop` declarado en los 10 bucles** (los puntos de §5
     pasan a ser parte del contrato). Congela el material ante un commit erróneo y va en la misma PR
     del banco de materiales.
- **Escucha del diseño**: los WAVs reales suenan en el juego desde la primera PR de integración, así
  que no hace falta una herramienta aparte para validar duraciones, cortes y crossfades.
- **Retirada de `train-sound.wav`**: condicionada y futura, ver §3.

### 8. Integración con los packs (ADR-028)

- La taxonomía es el **contrato con el licenciante**; **LeCript y el formato `.ltsp` no cambian**.
- El **swap real es reemplazar el `.ltsp`** (y, si cambian, los descriptores de perfil) sin tocar el
  algoritmo.
- **`MaterialResolver` (§5) es el punto por el que entra el `SoundProvider` de ADR-028** (pack →
  classpath con `FallbackSoundProvider`); un pack parcial o ausente cae al perfil de classpath o al
  modo legado. El algoritmo no se entera de la procedencia.
- El determinismo se mantiene: la selección es función del snapshot y del perfil, sin azar oculto,
  compatible con el replay de [[ADR-020-Command-Journal-Scenarios]]; la variabilidad de tomas (si
  llega, estilo [[ADR-024-Soundscape-Material-Model]]) no afecta a la simulación.

## Consecuencias

- Positivas: pitch propio por marcha y transiciones ordenadas con esfuerzo audible (escalera medida,
  f0 39,95→110,25 Hz, sin clipping); **autoría propia confirmada y licencia clara** de los 28 WAVs;
  los **puntos de bucle medidos alcanzan paridad con el `cruise` legacy** (z5000 0,74–0,89 /
  residuo −15…−18 dB); el material entra en el repo y **el swap del pack licenciado no reescribe el
  algoritmo**; los fallbacks permiten entregas por fases; los **assets a mano + fixtures de test**
  hacen el modelo **testeable y escuchable desde el día uno**; los packs de ADR-028 sirven al tren
  sin tocar el formato; el classpath sigue siendo red de seguridad; el sonido queda alineado con la
  inercia real (§2), que es lo que el jugador percibe como esfuerzo.
- Costes y riesgos: más complejidad de estado (coalescing, cortes, crossfades); el riesgo de clics
  queda **mitigado pero no eliminado** — fade-in de onset 10–20 ms y crossfade de salida 200 ms son
  ahora política obligatoria (§4) porque **10/18** transiciones arrancan en escalón y **5/18**
  terminan a nivel alto, y la costura residual depende de que los puntos `loop` sean buenos;
  **+13,26 MB de WAVs mono en el artefacto de release** (§6) y ≈25,3 MiB de RAM si el `EAGER` es
  completo; **autoría de metadatos por material** (puntos y `gain` escritos a mano en el descriptor,
  con la puerta abierta a refinarlos sin cambiar el formato); `train-sound.wav` se mantiene mientras
  dure la retirada condicionada (§3), con su procedencia aún sin auditar (ADR-026); si la inercia
  futura se acelera respecto a las grabaciones habrá cortes frecuentes y habrá que validar de oído;
  dependencia de las fases de ADR-026/028 para el enchufe definitivo.
- Dependencias: ADR-023 (aislamiento), ADR-024 (materiales y tomas), ADR-025 (sensado), ADR-026
  (`audio-core` y `train-audio`, snapshot de entrada), ADR-028 (packs/N3/LeCript). No afecta a la
  simulación.

## Alternativas consideradas

- **Mantener el pitch-shift de `cruise`**: lo más barato, pero no puede representar el pitch real ni
  las transiciones ordenadas/esfuerzo; desaprovecha el material.
- **Un WAV por locomotora con labels que incluyan las transiciones** (extender el esquema Audacity):
  sencillo y compatible con `AudacityLabelParser`, pero carga el fichero entero en RAM, no escala a
  decenas de materiales, los labels son frágiles ante ediciones/renombrados y no encaja con los
  packs por material de ADR-028.
- **Síntesis procedural en runtime** (`TrainSynth`): control total y sin assets, pero no usa el
  material, suena sintético y es un desarrollo mayor; descartada como fuente principal (puede quedar
  como emergencia si no hay material).
- **Forja de materiales** (módulo dev `material-forge` + manifiesto + `.ltsp` de referencia con
  clave de test): hubiera dado determinismo puro y habría servido para escuchar el diseño antes del
  material real, pero **queda descartada** porque el material ya existe, está generado a mano y se
  commitea; el determinismo de los tests se cubre con fixtures sintéticos y un test contrato (§7), y
  el `.ltsp` sigue siendo exclusivo del pack propietario (ADR-028). *Descartada en la enmienda.*
- **Pre-render offline vs time-stretch en runtime**: pre-render; el material real ya viene grabado y
  el time-stretch de calidad en runtime es caro y no garantiza el timbre.
- **Nativo / streaming desde servidor**: ya descartados en ADR-028 (JNI y multiplicación de
  plataformas; dependencia de red).
- **Reproducción granular con `GrainEngine` para las transiciones** (time-stretch sin cambiar
  pitch): descartada como fuente principal; añade color al timbre sin que el material lo pida. Se
  conserva como herramienta futura opcional.

## Plan por fases

Cada fase es una PR (`feature/...` o `fix/...` desde `develop`, nunca directo a `develop`; PR en
inglés; `mvn clean test` en verde antes de abrir o actualizar):

1. **Este PR (PR A): enmienda del ADR-029** — solo documentación; sin código ni assets.
2. **PR B — banco de materiales**: convertir los 28 WAVs a **mono 44,1 kHz PCM16** y moverlos a
   `core/…/sound/train/generic/`, crear `profiles/generic.profile` **con los puntos `loop` medidos
   (§5)** y el paquete `letrain.audio.material` (`MaterialId`, `MaterialProfile` + parser,
   `MaterialResolver` con impl. de classpath, `MaterialBank` compartido estático); tests de parser,
   del banco y **test contrato de los assets reales** (28 ficheros, mono, frames 385.024/220.500/
   88.200, `loop` declarado). Cero cambio de comportamiento. *Riesgo: bajo.*
3. **PR C — reproductor de dos voces y modos**: segunda voz de locomotora para los one-shots, con
   **fade-in de onset 10–20 ms** y envolventes de crossfade/corte **muestra a muestra** en `read()`;
   modos `AUTO / MATERIAL / LEGACY` con `LEGACY` por defecto (sonido actual intacto); `WavFixture` y
   tests de máquina de estados. *Riesgo: medio (es camino caliente).*
4. **PR D — integración event-driven**: `setCurrentNotch`, evaluación única por frame, selección
   con coalescing, cadena de fallbacks, crossfade/corte; se pasa el default a `AUTO`. *Riesgo: alto
   (es el cambio audible: el sonido deja de adelantarse a la física; requiere escucha A/B).*
5. **PR E — documentación de desarrollador**: página de `docs/developer/` con la máquina de estados,
   la tabla de tiempos físico↔audio (§2), el descriptor y la cadena de fallback; índice y
   `ClassIndex` regenerado; `CREDITS` de los WAVs propios. *Riesgo: bajo.*
6. **PR F (opcional) — carga `ON_DEMAND`**: caché LRU + prefetch, solo si la PR B mide >200 ms o se
   decide bajar la memoria residente. *Riesgo: medio (hueco de audio).*
7. **ADR-026 fases 1–2** (`audio-core`): inventario y extracción del núcleo compartido (salida,
   mezclador, voces, carga/loop, secuenciador de labels). Puede avanzar **en paralelo**: no bloquea
   las PR B–E porque el código nuevo nace en un subpaquete de `core` (`letrain.audio.material`)
   pensado para moverse después a `train-audio` como un refactor de paquetes.
8. **`soundpack` + `lecript`** (ADR-028): `MaterialResolver` gana la implementación de pack y
   validación; el classpath sigue como red de seguridad.
9. **Swap al pack real de Carlos**: firmado el acuerdo, empaquetar, `lecript validate` y publicar
   fuera del repo. Sin cambios de algoritmo.

## Preguntas abiertas

**Resueltas en la enmienda** (se dejan anotadas para traza):

- ~~**Umbral de coalescing**~~ → **`coalesceWindow = 400 ms`, global** (§2). Perfil como extensión
  futura.
- ~~**Política exacta duración/crossfade**~~ → **crossfade 200 ms, corte 100 ms; el one-shot se deja
  terminar mientras cubra el estado físico y se corta con fade si la física abandona el tramo** (§4).
- ~~**Formato de metadatos de perfil**~~ → **DSL de texto tipo `.sound`** en
  `core/src/main/resources/sound/profiles/generic.profile`, campos `loop` / `gain` / `effort` /
  `torque` / `packVersion` (§5).
- ~~**Preload vs demanda**~~ → **`EAGER` compartido estáticamente al primer arranque de motor**;
  `ON_DEMAND` como política futura (§6).
- ~~**Motor de reproducción**~~ → **`GrainEngine` con reproducción directa: loops + crossfade**. Los
  modos granular / *ping-pong* / *reverse* quedan **deshabilitados** para el material real.
- ~~**Costura de los bucles**~~ → **respondida por la auditoría (enmienda 2)**: `loop=auto` **no
  sirve** (el wrap a fichero completo no cierra limpio: z ≈ 0,27–0,46, residuo −11,6…−13,6 dB), así
  que el descriptor **declara `loop=<inicio>,<longitud>` por fichero** con los 10 puntos medidos
  (§5), que ya dan **paridad con el `cruise` legacy** (z5000 0,74–0,89 / residuo −15…−18 dB).

**Aún abiertas**:

- **Refinamiento de los puntos de bucle**: los medidos ya son de paridad, pero son una
  aproximación; ¿se re-mide algún notch concreto (p. ej. `notch-3`/`notch-4`, ambos en 6,68 s, y
  `notch-8`/`notch-10`, con ventanas cortas de 0,967 s y 1,222 s) para mejorar el residuo? **No
  cambia el formato**: el descriptor ya admite cualquier valor.
- **`cutPolicy` por material**: ¿mover cortes y crossfades del descriptor en vez de política global?
- **Tamaño máximo de pack por locomotora** (y total): ¿caben todas las parejas ordenadas o solo
  adyacentes + compuestas aprobadas?
- **Relación con [[ADR-027-Clima-Y-Estaciones]]**: ¿el clima afecta al sonido del tren (lluvia sobre
  el techo, motor en frío, `horn` con niebla)? Fuera de alcance hoy; decidir si entra en la taxonomía
  o se queda en el decorado.
- **`horn` y `brakes`**: disparo (tecla/comando), variantes múltiples y si reciben material propio
  ya en la PR D o después (hoy solo están en la taxonomía, sin assets).
- **Aleatoriedad de tomas**: ¿varias tomas por material (estilo [[ADR-024-Soundscape-Material-Model]])
  o una por material en la primera fase?
