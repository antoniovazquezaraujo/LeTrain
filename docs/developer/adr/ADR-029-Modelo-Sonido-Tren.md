# ADR-029: Modelo de sonido del tren (materiales por marcha y transiciones)

## Estado: PROPUESTO (borrador para revisión)

> **Enmienda 1 — 2026-10-02.** Motivo: la **forja** de materiales queda descartada (§7) —el
> material por marcha y rampa ya existe, **generado a mano y commiteado en el repo**— y se fija
> la **sincronía audio↔física** (§2): el audio va esclavo de `Locomotive.currentSpeed` y el
> one-shot arranca al inicio del paso físico. El ADR sigue en **PROPUESTO**: la enmienda se aplica
> sobre el borrador antes de su merge a `develop`, por lo que no se abre ADR nuevo.

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

### Material: propio (ya en repo) y del licenciante (pendiente)

- **Material propio del proyecto (ya existe, generado a mano)**: 28 WAVs en
  `core/src/main/resources/sound/train/generic/` — `notch-1`…`notch-10` (bucles de marcha de
  **8,7307 s**, la misma longitud que el segmento `cruise` legacy) y las 18 parejas adyacentes
  `trans-1-2`…`trans-9-10` (**5,0 s** subiendo) y `trans-2-1`…`trans-10-9` (**2,0 s** bajando),
  PCM16 44,1 kHz estéreo. Entran en el repo como **assets propios**: son la fuente de verdad del
  algoritmo, de la escucha en desarrollo y del test contrato (§7).
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
- Cobertura actual del set propio: **10 bucles + 18 transiciones adyacentes**. Faltan `idle`,
  `start`, `stop`, `rodadura`, `frenos`, `bocina`, `trans-0-1` y todas las compuestas → caen al
  fallback del punto 3.
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
  por marcha al acelerar** y **≈50 ticks (2,25–2,50 s) al frenar**; el paso `0 → 1` es inmediato.
  Las grabaciones están **calibradas contra esa inercia**: la subida cabe entera en su paso, y la
  bajada termina 0,25–0,5 s antes (se espera que haga crossfade al loop y espere, §4).
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
queden materiales sin sustituir —hoy `idle` (ralenti), `start`, `stop` y `rodadura` (`wagons`), más
el nivel 3 del fallback— **y mientras sostengan los tests de regresión existentes**
(`AudioControllerTest` depende del segmento `stop`). La retirada es una **decisión futura
explícita**, condicionada a: (a) recibir materiales propios de `idle`/`start`/`stop`/`rodadura`,
(b) confirmar que el nivel 3 ya no se usa en ningún perfil soportado y (c) decidir si el pitch-shift
de emergencia sigue necesitando `cruise`. No se borra `train-sound-labels.txt` mientras exista el
nivel 3.

### 4. Política de duración real vs inercia del juego

- **La física manda**: la grabación dura lo que dura y la simulación no se toca.
- **El one-shot se deja terminar mientras cubra el estado físico.** Al terminar —o al completarse
  el último tramo de una cadena— se hace **crossfade de 200 ms** al loop de la marcha física
  alcanzada (`notch-currentNotch`). Si la subida continúa, el siguiente paso encadena su transición
  desde ahí, sin pasar por el loop intermedio.
- **Si el estado físico abandona el tramo** (cambio de destino, frenada, fin de vía, `forceIdle`) se
  **corta con fade de 100 ms** y se reevalúa; nunca se alarga ni se acorta la física para cuadrar el
  audio.
- **Valores por defecto fijados por la enmienda**: crossfade **200 ms** (rango 150–300 ms), corte
  **100 ms** (rango 50–150 ms). Son **política global**; declararlos por material (`cutPolicy`) queda
  abierto (ver preguntas abiertas).
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
  profile     = generic
  packVersion = 0

  # material               = ruta classpath                       metadatos
  notch.1   = sound/train/generic/notch-1.wav   loop=auto   gain=0.0
  notch.2   = sound/train/generic/notch-2.wav   loop=auto   gain=-0.5
  ...
  notch.10  = sound/train/generic/notch-10.wav  loop=auto   gain=0.0

  trans.1-2 = sound/train/generic/trans-1-2.wav              gain=0.0
  trans.2-5 = sound/train/generic/trans-2-5.wav  effort=high torque=loaded
  ...

  # FALTAN hoy (→ fallback §3): idle, start, stop, rodadura, frenos, bocina,
  #                             trans-0-1 y todas las parejas compuestas
  ```

  - **Campos**: `loop=<s>,<s>` con puntos de bucle en segundos; **`loop=auto`** = fichero completo
    con el crossfade de `GrainEngine` (**es lo que se usa por defecto**: los WAV aún no declaran
    puntos de bucle ni traen chunk `smpl`, y la costura real de cada bucle se está auditando);
    `gain=<dB>` ganancia por material; `effort` y `torque` etiquetas opcionales que se parsean y se
    ignoran hoy (contrato con el licenciante, §1); `packVersion` para validar que pack y perfil
    encajan.
  - **Ubicación**: el descriptor **vive con el código**, se versiona en el repo y mapea IDs lógicos
    (`train/<perfil>/<material>`) a rutas de classpath.
  - **Validación**: ID desconocido, ruta ausente o línea malformada → **`WARN` + fallback (§3)**,
    nunca excepción.
- **Resolución**: el *lookup* de un material pasa por una interfaz propia, **`MaterialResolver`**
  (`MaterialId → Optional<AudioSample>`), que en esta fase resuelve contra el classpath. Esa
  interfaz es el **seam hacia el `SoundProvider` de ADR-028**: cuando el pack exista se añade la
  implementación de pack y el orden *pack → classpath* pasa a ser suyo, **sin tocar el synth ni el
  descriptor**. Así el mismo perfil sirve para classpath y para pack sin ramas en el código.

### 6. Carga y memoria

Datos medidos del set de 28 ficheros (PCM16 44,1 kHz estéreo; `AudioSample` descarta el canal
derecho, de modo que la RAM en `float[]` ≈ tamaño del fichero):

| Conjunto | Ficheros | Duración | Tamaño |
|---|---|---|---|
| `notch-1`…`notch-10` | 10 | 8,7307 s | 14,7 MiB |
| `trans` de subida | 9 | 5,0 s | 7,6 MiB |
| `trans` de bajada | 9 | 2,0 s | 3,0 MiB |
| **Total** | **28** | — | **≈ 25,3 MiB** |

- **Carga `EAGER`, compartida y estática al primer arranque de motor**: los 28 se decodifican una
  sola vez en el constructor del **primer** `TrainSynthesizer` (no al abrir la aplicación), igual que
  hoy `sharedSample`. Estimación 150–300 ms de I/O + conversión; **se mide en la PR del banco de
  materiales** y, si resulta perceptible, se adelanta el precalentamiento a un hilo de fondo.
- **`ON_DEMAND` queda como política futura** (caché LRU + `prefetch` desde el cambio de palanca): la
  API del banco no cambia, solo la política, así que puede llegar en una PR aparte sin tocar la
  selección. Mientras no exista no hay que vigilar huecos de audio.
- **Sin material cargado no se dispara la transición**: se cae al fallback (§3), que ya está en
  memoria.
- **Coste aceptado y declarado**: el artefacto de release (`mvn clean package`) crece **≈ +26 MB** de
  WAVs en `core` (de ~76 MB a ~102 MB de recursos de sonido).
- El límite de **100 MB por payload de ADR-028** sigue vigente para el pack propietario; este set
  vive en el classpath y no entra en ese presupuesto, pero conviene mantenerlo vigilado si el perfil
  crece a más locomotoras.

### 7. Assets en repo + fixtures de test

- **Los 28 WAVs se commitean** en `core/src/main/resources/sound/train/generic/` como **assets
  propios** del proyecto (generados a mano; no derivan de `train-sound.wav` ni de material de
  terceros). Son la fuente de verdad del material que suena en desarrollo.
- **No existe ninguna forja**: quedan descartados el módulo `material-forge`, el manifiesto
  generado, el `.ltsp` de referencia y la clave de test de material.
- **Determinismo de los tests**, que ya no sale de una herramienta reproducible sino de dos fuentes:
  1. **Fixtures WAV sintéticos** generados en `src/test/java` (`WavFixture`: bucles sinusoidales
     cortos escritos en un `@TempDir`, sin binarios commiteados). Permiten construir escenarios
     imposibles con el set real (perfil sin `notch-3`, cadena incompleta, `gain` fuera de rango) y
     mantienen los tests unitarios **rápidos e independientes** de los 25,3 MiB.
  2. **Un test contrato sobre los assets reales**: presencia de los 28, duraciones esperadas
     (8,7307 / 5,0 / 2,0 s) y resolución de todas las parejas adyacentes. Congela el material ante
     un commit erróneo y va en la misma PR del banco de materiales.
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

- Positivas: pitch real por marcha y transiciones ordenadas con esfuerzo audible; el material entra
  en el repo y **el swap del pack licenciado no reescribe el algoritmo**; los fallbacks permiten
  entregas por fases; los **assets a mano + fixtures de test** hacen el modelo **testeable y
  escuchable desde el día uno**; los packs de ADR-028 sirven al tren sin tocar el formato; el
  classpath sigue siendo red de seguridad; el sonido queda alineado con la inercia real (§2), que es
  lo que el jugador percibe como esfuerzo.
- Costes y riesgos: más complejidad de estado (coalescing, cortes, crossfades) y riesgo de clics o
  costuras si el empalme no se trabaja; **+26 MB de WAVs en el artefacto de release** (§6);
  `train-sound.wav` se mantiene mientras dure la retirada condicionada (§3), con su procedencia aún
  sin auditar (ADR-026); autoría de metadatos por material (un punto de bucle malo se oye — por eso
  `loop=auto` hasta auditar la costura); si la inercia futura se acelera respecto a las grabaciones
  habrá cortes frecuentes y habrá que validar de oído; dependencia de las fases de ADR-026/028 para
  el enchufe definitivo.
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
2. **PR B — banco de materiales**: mover los 28 WAVs a `core/…/sound/train/generic/`, crear
   `profiles/generic.profile` y el paquete `letrain.audio.material` (`MaterialId`,
   `MaterialProfile` + parser, `MaterialResolver` con impl. de classpath, `MaterialBank` compartido
   estático); tests de parser, del banco y **test contrato de los assets reales**. Cero cambio de
   comportamiento. *Riesgo: bajo.*
3. **PR C — reproductor de dos voces y modos**: segunda voz de locomotora para los one-shots, con
   envolventes de crossfade/corte **muestra a muestra** en `read()`; modos `AUTO / MATERIAL /
   LEGACY` con `LEGACY` por defecto (sonido actual intacto); `WavFixture` y tests de máquina de
   estados. *Riesgo: medio (es camino caliente).*
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

**Aún abiertas**:

- **Costura de los bucles**: ¿basta `loop=auto` o hay que declarar `loop=<s>,<s>` por fichero tras
  auditar la costura real de `notch-1`…`notch-10`? (auditoría en curso)
- **`cutPolicy` por material**: ¿mover cortes y crossfades del descriptor en vez de política global?
- **Tamaño máximo de pack por locomotora** (y total): ¿caben todas las parejas ordenadas o solo
  adyacentes + compuestas aprobadas?
- **Relación con [[ADR-027-Clima-Y-Estaciones]]**: ¿el clima afecta al sonido del tren (lluvia sobre
  el techo, motor en frío, bocina con niebla)? Fuera de alcance hoy; decidir si entra en la taxonomía
  o se queda en el decorado.
- **Bocina y frenos**: disparo (tecla/comando), variantes múltiples y si entran en la taxonomía ya en
  la PR D o después.
- **Aleatoriedad de tomas**: ¿varias tomas por material (estilo [[ADR-024-Soundscape-Material-Model]])
  o una por material en la primera fase?
