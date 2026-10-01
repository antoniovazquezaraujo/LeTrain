# ADR-029: Modelo de sonido del tren (materiales por marcha y transiciones)

## Estado: PROPUESTO (borrador para revisión)

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
  | `wagons` | 38.431–87.473 | rodadura de vagones, volumen por velocidad |

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
  está auditada; [[ADR-026-Audio-Domains]] ya señala la revisión como tarea pendiente.

### Material nuevo del licenciante (Carlos / Railsounds)

- Grabaciones reales **por marcha**: el pitch real de cada notch, sin pitch-shift.
- Grabaciones reales de **transiciones entre pares ordenados** (p. ej. 2→3, 3→4, 2→5). Un salto
  compuesto suena distinto —más esfuerzo, cambio de torque audible— que una escalera con calma.
- El acuerdo está pendiente de firma: **ningún asset real entra en el repo** ni siquiera temporal
  ([[ADR-028-Sound-Packs-Externos-Cifrados]]); hasta entonces todo se prueba con WAVs sintéticos.

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

### Enfoque acordado: contrato primero, doble sintético después

Construir el algoritmo contra el **contrato final de materiales** (taxonomía + metadatos) y generar
un **doble sintético** a partir del WAV actual: cortes de `cruise` re-pitcheados **offline** (un WAV
por marcha), transiciones **pre-renderizadas** con su duración real de rampa, un **manifiesto** con
la taxonomía final y el empaquetado con **LeCript** como si fuera el pack de Carlos.

Así la llegada del material real es un **swap de ficheros**, no un cambio de algoritmo. El pack
sintético es un artefacto de **desarrollo y pruebas** (determinista, clave de test): **no es un
producto distribuible**, y su base (`train-sound.wav`) exige verificar antes la licencia/procedencia.

## Decisión (propuesta)

### 1. Taxonomía de materiales por locomotora (perfil)

Cada locomotora se sirve de un **perfil de sonido** con IDs lógicos estables. El perfil se elige
por tipo de locomotora; existe un perfil **`generic`** como fallback. Los IDs son rutas lógicas
compatibles con el modelo de [[ADR-028-Sound-Packs-Externos-Cifrados]]
(`train/<perfil>/<material>.wav`):

| Rol | ID lógico | Tipo | Notas |
|---|---|---|---|
| Ralentí | `idle` | loop | marcha 0 |
| Marchas | `notch-1` … `notch-10` | loop con puntos de bucle | pitch real grabado |
| Transiciones | `trans-<from>-<to>` (p. ej. `trans-2-3`, `trans-2-5`, `trans-5-2`) | one-shot | pares **ordenados**, incluidos saltos compuestos |
| Arranque | `start` | one-shot | encendido del motor |
| Parada | `stop` | one-shot | apagado |
| Frenos | `frenos` | loop | reservado; hoy `train-brakes.wav` |
| Rodadura | `rodadura` (alias legacy `wagons`) | loop | volumen por velocidad |
| Bocina | `bocina` | one-shot | reservado |

- `trans-<from>-<to>` es **direccional**: `trans-5-2` no es `trans-2-5`. Los pasos simples
  (`trans-2-3`, `trans-3-2`, …) son la unidad; los compuestos son material extra que el licenciante
  puede entregar por fases.
- Sin perfil específico → `generic`; sin material concreto → fallback del punto 3. Nada deja al tren
  mudo.
- La taxonomía es el **contrato con el licenciante**: nombres, direccionalidad y metadatos.

### 2. Selección event-driven y coalescing

- El tren se describe con **snapshot** ([[ADR-026-Audio-Domains]]): `currentNotch`
  (`Locomotive.currentSpeed`), `targetNotch` (`targetSpeed`), freno, carga, velocidad de movimiento.
  **El audio consume cambios/eventos, no hace polling dentro de la física** (regla de evitar lógica
  en ticks).
- El **cambio efectivo de `currentNotch`** dispara la reproducción; `targetNotch` actúa de
  *lookahead* para coalescing y precarga. Un cambio de palanca sin cambio de marcha no dispara audio
  (solo prepara material).
- **Coalescing**: si el destino salta varios notches (p. ej. 2→5) y existe `trans-2-5`, la subida
  completa se colapsa en **una transición compuesta** cuando arranca el primer paso físico. Los
  pasos intermedios (`current` 3 y 4) no la interrumpen: la compuesta cubre el tramo. Si el destino
  se queda en un notch intermedio (**dwell**, p. ej. la palanca se estabiliza en 3), se reproducen
  **transiciones individuales** (`trans-2-3`, luego `trans-3-4`…). El umbral de asentamiento de la
  palanca es **configurable** (`coalesceWindow`, propuesto 300–500 ms): solo el destino estable
  dispara compuestas; pulsaciones rápidas no generan ráfagas.
- Si durante una compuesta el destino cambia de forma que ya no cubre el tramo (p. ej. baja a 3), se
  **corta con fade corto** y se reevalúa. Los cambios rápidos de palanca se resuelven con
  crossfades, nunca dejando la voz muda.

### 3. Fallbacks incrementales

Para cada transición solicitada `from → to` se resuelve en este orden:

1. **Pareja exacta** `trans-<from>-<to>`.
2. **Cadena de saltos simples**: descomposición `from → from±1 → … → to` usando las parejas
   adyacentes disponibles; las etapas sin material caen al punto 3.
3. **Rampa de pitch** del material de la marcha destino partiendo del actual (**modo legado**, el
   comportamiento de hoy), que garantiza sonido aunque el material esté incompleto.

Igual para loops: `notch-N` ausente → perfil `generic` o re-pitch de la marcha más cercana como
último recurso. El material del licenciante puede crecer por fases (primero marchas, luego
transiciones adyacentes, luego compuestas) **sin romper nada**: los fallbacks van entrando solos.

### 4. Política de duración real vs inercia del juego

- **La física manda**: la grabación dura lo que dura y la simulación no se toca.
- Un material de transición se reproduce entero mientras cubra el estado físico; al terminar, se
  hace **crossfade** (~150–300 ms) al loop de la marcha física alcanzada (`notch-currentNotch`). Si
  la subida continúa, el siguiente paso encadena su transición desde ahí.
- Si el estado físico abandona el tramo de la transición (cambio de destino, frenada, fin de vía),
  se **corta con fade corto** (~50–150 ms) y se reevalúa; nunca se alarga ni se acorta la física
  para cuadrar el audio.
- El **corte y el crossfade** son la política por defecto; la puerta queda abierta a **rampas
  data-driven por perfil** (tiempos declarados por material) en el futuro, fuera del alcance de este
  ADR.

### 5. Metadatos del material

- **En el pack** ([[ADR-028-Sound-Packs-Externos-Cifrados]]): el manifiesto ya declara `packId`,
  `packVersion`, procedencia y `kdfSalt`; cada entrada declara id lógico, `codec`, `sampleRate` y
  `channels`. La taxonomía viaja en los IDs. **No hay cambio de formato ni de LeCript.**
- **En `train-audio`** (repo, datos abiertos): un **descriptor de perfil** por locomotora con la
  metadata fina de cada material:

  ```
  profile = generic
  notch.1  = train/generic/notch-1.wav   loop=0.10,3.80  gain=-3.0
  notch.2  = train/generic/notch-2.wav   loop=0.08,3.60  gain=-3.2
  trans.2-3 = train/generic/trans-2-3.wav
  trans.2-5 = train/generic/trans-2-5.wav  effort=high  torque=loaded
  ```

  Campos: puntos de bucle (s o muestras), duración, **ganancia normalizada**, etiquetas opcionales
  de **esfuerzo/torque** y `packVersion` compatible. El descriptor vive con el código
  (`train-audio/src/main/resources/profiles/…`), se versiona en el repo y permite validar que el
  pack y el perfil encajan; un desajuste produce `WARN` y fallback.
- La resolución de un ID pasa siempre por `SoundProvider` ([[ADR-028-Sound-Packs-Externos-Cifrados]]),
  así el mismo perfil sirve para classpath y para pack sin ramas.

### 6. Carga y memoria

- **Descifrado bajo demanda por material** (ADR-028 §2–3: en memoria, nunca a disco), con caché
  LRU pequeña y **preload de la marcha actual ±1**; al cambiar la palanca se precarga además el loop
  destino y la compuesta candidata, para que la transición no arranque en silencio.
- Presupuesto orientativo por perfil (PCM16 44.1 kHz mono ≈ 88 KB/s): 10 loops de ~8 s ≈ 7 MB;
  ~18 parejas adyacentes de ~2.5 s ≈ 4 MB; compuestas seleccionadas aparte. **12–15 MB por
  locomotora** antes de compresión; el pack completo de varias locomotoras puede crecer a decenas
  de MB, a vigilar por el descifrado íntegro en RAM (límite de 100 MB por payload de ADR-028).
- La caché prioriza actual ±1 y libera los materiales lejanos; sin material cargado no se dispara
  la transición (se usa el fallback legado, que ya está en memoria).

### 7. Forja de materiales + pack de referencia

- **Herramienta de desarrollo separada de LeCript**, no empaquetada con el juego (módulo dev
  `material-forge`, sin launcher en `output/`).
- **Entradas**: `train-sound.wav` + labels (hoy); opcionalmente WAVs reales en el futuro para
  validar el contrato.
- **Salidas**: una carpeta con un WAV por material (cortes de `cruise` re-pitcheados offline para
  `notch-1..10`, transiciones pre-renderizadas con la duración real de la rampa, `idle`/`start`/
  `stop`/`rodadura` desde sus labels), el **manifiesto** con la taxonomía final y el `.ltsp` de
  referencia generado con **LeCript** y clave de test.
- **Determinismo**: misma entrada + misma versión de herramienta ⇒ bytes idénticos (orden de
  ficheros fijo, sin timestamps, resampling determinista). Es la **clave de los tests** de BICHO
  (selección, fallbacks, descifrado y reproducción sin material real).
- **Generación en local/CI con clave de test**: los artefactos se generan en `target/` y no se
  commitean (`*.ltsp` ya está en `.gitignore` por ADR-028). La clave de test es **solo para el pack
  sintético**: nunca se usa para packs reales ni aparece la clave real en el repo.
- También sirve para **escuchar el diseño** (duraciones, cortes, crossfades) antes de tener el
  material real.
- **No distribuible**: deriva de `train-sound.wav`; si su procedencia no se puede confirmar, la base
  se sustituye por una toma propia o CC0 antes de sacarla de tests.

### 8. Integración con los packs (ADR-028)

- La taxonomía es el **contrato con el licenciante**; **LeCript y el formato `.ltsp` no cambian**.
- El **swap real es reemplazar el `.ltsp`** (y, si cambian, los descriptores de perfil) sin tocar el
  algoritmo.
- `train-audio` resuelve materiales con el `SoundProvider` de ADR-028 (pack → classpath con
  `FallbackSoundProvider`); un pack parcial o ausente cae al perfil de classpath o al modo legado.
- El determinismo se mantiene: la selección es función del snapshot y del perfil, sin azar oculto,
  compatible con el replay de [[ADR-020-Command-Journal-Scenarios]]; la variabilidad de tomas (si
  llega, estilo [[ADR-024-Soundscape-Material-Model]]) no afecta a la simulación.

## Consecuencias

- Positivas: pitch real por marcha y transiciones ordenadas con esfuerzo audible; el material real
  entra por **swap** sin reescribir el algoritmo; los fallbacks permiten entregas por fases; el pack
  sintético hace el modelo **testeable y escuchable** desde el día uno; los packs de ADR-028 sirven
  al tren sin tocar el formato; el classpath sigue siendo red de seguridad.
- Costes y riesgos: más complejidad de estado (coalescing, cortes, crossfades) y riesgo de clics o
  costuras si el empalme no se trabaja; peso y descifrado en RAM (ADR-028), con caché y presupuesto
  a vigilar; autoría de metadatos por material (un punto de bucle malo se oye); si la inercia es más
  rápida que las grabaciones habrá cortes frecuentes y habrá que validar de oído; la procedencia de
  `train-sound.wav` debe confirmarse antes de que el pack sintético salga de tests; dependencia de
  las fases de ADR-026/028 para el enchufe definitivo.
- Dependencias: ADR-023 (aislamiento), ADR-024 (materiales y tomas), ADR-025 (sensado), ADR-026
  (`audio-core` y `train-audio`, snapshot de entrada), ADR-028 (packs/N3/LeCript). No afecta a la
  simulación.

## Alternativas consideradas

- **Mantener el pitch-shift de `cruise`**: lo más barato, pero no puede representar el pitch real ni
  las transiciones ordenadas/esfuerzo; desaprovecha el material licenciado.
- **Un WAV por locomotora con labels que incluyan las transiciones** (extender el esquema Audacity):
  sencillo y compatible con `AudacityLabelParser`, pero carga el fichero entero en RAM, no escala a
  decenas de materiales, los labels son frágiles ante ediciones/renombrados y no encaja con los
  packs por material de ADR-028.
- **Síntesis procedural en runtime** (`TrainSynth`): control total y sin assets, pero no usa el
  material licenciado, suena sintético y es un desarrollo mayor; descartada como fuente principal
  (puede quedar como emergencia si no hay material).
- **Pre-render offline vs time-stretch en runtime**: pre-render; el material real ya viene grabado y
  el time-stretch de calidad en runtime es caro y no garantiza el timbre.
- **Nativo / streaming desde servidor**: ya descartados en ADR-028 (JNI y multiplicación de
  plataformas; dependencia de red).
- **Reproducción granular con `GrainEngine` para las transiciones** (time-stretch sin cambiar
  pitch): aplazada; añade color al timbre sin que el material lo pida (ver preguntas abiertas).

## Plan por fases

1. **Este PR: solo ADR-029** (documentación; sin código ni assets).
2. **ADR-026 fases 1–2** (`audio-core`): inventario y extracción del núcleo compartido (salida,
   mezclador, voces, carga/loop, secuenciador de labels). Prerrequisito de todo lo demás.
3. **Forja + pack de referencia**: herramienta dev, WAVs sintéticos por marcha/transición,
   manifiesto y `.ltsp` con clave de test; fixtures de BICHO y escucha del diseño.
4. **`train-audio` con el modelo**: perfil/taxonomía, metadata, selección event-driven con
   coalescing, fallbacks y modo legado; tests con el pack sintético y regresión del comportamiento
   actual.
5. **`soundpack` + `lecript`** (ADR-028): provider y validación; `train-audio` resuelve desde pack
   con fallback a classpath.
6. **Swap al pack real de Carlos**: firmado el acuerdo, empaquetar, `lecript validate` y publicar
   fuera del repo. Sin cambios de algoritmo.

## Preguntas abiertas

- **Umbral de coalescing**: ¿ventana temporal de asentamiento de la palanca (propuesta, 300–500 ms),
  ritmo de pasos físicos u otro criterio? ¿Global o por perfil?
- **Política exacta duración/crossfade**: longitudes de fade al cortar y empalmar, ¿política global
  o declarada por material (`cutPolicy`)? ¿La transición se deja terminar pisando el loop o se corta
  siempre que la física llegue antes?
- **Formato de metadatos de perfil**: ¿DSL de texto tipo `.sound` (propuesto), JSON/YAML? ¿Campos
  mínimos, validaciones y valores de `effort`/`torque`?
- **Preload vs demanda**: cuántos materiales precargar (¿actual ±1?), tamaño y política de la caché
  LRU, y cuánto tarda el descifrado antes de que se note.
- **Tamaño máximo de pack por locomotora** (y total): ¿caben todas las parejas ordenadas o solo
  adyacentes + compuestas aprobadas?
- **Motor de reproducción**: ¿reutilizar `GrainEngine` (granos, ping-pong, reverse) o reproducción
  directa con loops + crossfade para el material real? Lo segundo es más fiel; lo primero ya existe.
- **Relación con [[ADR-027-Clima-Y-Estaciones]]**: ¿el clima afecta al sonido del tren (lluvia sobre
  el techo, motor en frío, bocina con niebla)? Fuera de alcance hoy; decidir si entra en la taxonomía
  o se queda en el decorado.
- **Bocina y frenos**: disparo (tecla/comando), variantes múltiples y si entran en la taxonomía ya en
  la fase 4 o después.
- **Aleatoriedad de tomas**: ¿varias tomas por material (estilo ADR-024) o una por material en la
  primera fase?
