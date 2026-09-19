# ADR-026: Dominios de audio y módulos (decorado, tren, SFX, núcleo compartido)

## Estado: PROPUESTO (borrador para revisión)

## Contexto

Hoy conviven **dos sistemas de audio completos y solapados**:

- `soundscape` (módulo aislado, sin dependencias del juego): decorado por zonas/clima/hora.
  Con `AmbientPlayer`, `SampleLoader`, `SampleResolver`, `AmbientVoice`.
- `core` (`letrain.audio`): el sistema legacy, con `AudioController`, `AudioMixer`,
  `DistanceAttenuator`, `sources/` y `synth/`:
  - **Ambiente**: `sound/birds.wav` y `sound/wind.wav` vía `SequencedAmbientSource`, dirigidos
    por `updateAmbient(...)` con la **posición de la cámara** — duplica lo que ya hace
    `soundscape` y usa la referencia equivocada (ver ADR-025).
  - **Tren**: `TrainSynth`, `TrainSynthesizer`, `SpeedNotch`, `GrainEngine`, `Oscillator`, más
    `sound/train-sound.wav` secuenciado con **labels** (`AudacityLabelParser`).
  - **SFX de obra**: `hammer.wav` (jackhammer), `caterpillar.wav`, `fork.wav`, `load-unload.wav`.
  - **Eventos**: `train-link`, `train-contact`, `train-brakes`, `train-explosion`.
- Consecuencias del estado actual: dos mezcladores, dos cargadores de samples, dos
  espacializaciones; el ambiente legacy sigue a la cámara; `core` depende de Java Sound y sus
  tests se cargan de audio; el secuenciador por labels vive escondido en `core` cuando hace falta
  también en `soundscape`.

## Decisión (propuesta)

1. **Cuatro piezas con responsabilidades separadas**:
   - **`soundscape`** (ya existe): decorado por zonas, clima, hora y espacio. Aislado
     (ADR-023); el sensado de zonas vive fuera (ADR-025).
   - **`train-audio`** (módulo nuevo): rodadura, tracción, frenos, enganches, contacto y
     explosión. Entrada = **snapshot de estado** (velocidad, notch, freno, posición, rumbo…), no
     el `Model` entero; así se testea con snapshots sintéticos y la simulación no se acopla al
     audio. Reutiliza el synth existente (`TrainSynth`, `SpeedNotch`, `GrainEngine`).
   - **`gameplay-sfx`** (capa fina, módulo propio solo si crece): martillo, caterpillar,
     carga/descarga y sonidos de UI. Disparos puntuales y posicionales; los emite el presenter.
   - **`audio-core`** (núcleo compartido, extraído de lo que ya existe): dispositivo de salida,
     mezclador, voces, carga/loop, espacialización (`DistanceAttenuator`), **secuenciador por
     labels** (`AudacityLabelParser`), **etapa de espacio (reverb/damping)** y limitador.
2. **Dependencias**: `audio-core` ← (`soundscape`, `train-audio`, sfx). `train-audio` depende de
   interfaces/snapshot de `core`, no de sus clases internas. `soundscape` no depende de nadie del
   juego.
3. **El foco de escucha y los cortes de escena** (ADR-025) son responsabilidad de quien produce
   el estado (el presenter); los reproductores solo reciben objetivos.
4. **Se comparten el secuenciador por labels y la etapa de espacio**: los labels sirven al tren
   (ya los usa) y a la capa de eventos del decorado (truenos, fauna a intervalos). La etapa de
   espacio, alimentada por el eje `enclosure` de ADR-025, aplica la misma reverb a **todas las
   fuentes que estén dentro** de un túnel o recinto: ambiente (que además duckea las zonas
   exteriores), sonidos del tren y SFX como el martillo al perforar.
5. **Migración por fases**, una PR por paso:
   1. Inventario y clasificación de fuentes y assets legacy (ambiente / tren / SFX / UI).
   2. Extraer `audio-core` (sin mover aún a nadie).
   3. Pasar el ambiente legacy a `soundscape` y borrarlo de `core`; conectar el sensor (ADR-025).
   4. Crear `train-audio` con el snapshot y mover synth + assets de tren.
   5. Pasar SFX de obra al presenter y eventos puntuales.
   6. Borrar `AudioController` y el `synth/`/`sources/` sobrantes.

## Consecuencias

- Positivas: un solo motor de mezcla; el ambiente referencia al tren/cursor (no a la cámara);
  simulación y audio desacoplados; tests de audio con snapshots; synth y labels reutilizados; el
  panel de calibración del `soundscape` sirve para todo.
- Costes y riesgos: refactor amplio en varias PRs; la espacialización del tren exige estado
  continuo a alta cadencia (no vale el modelo de composición por bandas del decorado); hay que
  revisar licencias de los assets legacy que se muevan (`birds`, `wind` figuran "license to
  confirm" en `CREDITS.md`).
- Dependencias: ADR-021 (descomposición del modelo), ADR-023 (aislamiento del decorado),
  ADR-024 (materiales), ADR-025 (sensado de zonas).

## Alternativas consideradas

- **Dejar todo en `core` y mover solo el ambiente**: más barato hoy, pero mantiene la duplicación
  y acopla la simulación a Java Sound.
- **Un único módulo de audio con todo**: descartado; mezcla dominios con entradas y cadencias
  distintas (ambiente por bandas vs rodadura por tick) y complica el aislamiento.
- **Meter el tren dentro de `soundscape`**: descartado; rompe el aislamiento de ADR-023.

## Preguntas abiertas

- ¿`gameplay-sfx` como módulo propio o como capa del presenter?
- ¿Qué campos mínimos y qué cadencia tiene el snapshot del tren?
- ¿El cliente 2D usa el mismo núcleo sin espacialización o su propio camino?
- ¿Qué hacemos con las licencias de los wav legacy que se muevan?
