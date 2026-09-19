# ADR-025: Detección de zonas del decorado sonoro (posición → pesos)

## Estado: PROPUESTO (borrador para revisión)

## Contexto

- [[ADR-023-Soundscape-Isolation]] dejó el decorado aislado y definió que el motor recibe un
  **estado ambiental** (hora, pesos de zona, altura, clima). El módulo `soundscape` consume ese
  estado, pero **nadie lo produce**: fuera del módulo no hay ninguna referencia a `soundscape`, y
  `core` sigue usando su `AudioController` antiguo para efectos puntuales (martillo, etc.).
- Hoy no existe, por tanto, "las vías cerca del agua activan el mar". El único eje posicional
  implementado es la **altura/zoom** (atenuación + LP de aire), que no es distancia geográfica.
- El mapa **sí** tiene la materia prima:
  - Terrenos naturales: `GROUND` (campos), `WATER` (mar/agua), `ROCK` (montaña).
  - Industrias productoras: `GOLD_MINE`, `MINE`, `RUBY_MINE`.
  - Industrias consumidoras: `JEWELRY_STORE`, `POWER_PLANT`, `RUBY_STORE`.
  - Helpers: `GroundMap.getValueAt(pos)`, `getBackgroundTerrain(x, y)`,
    `findClosestIndustry(center, radius)` y `countIndustryDensity(center, radius, type)`.
  - `RailTrackMaker` ya muestrea el terreno en la posición de la vía.
- El estilo `valle-norte.sound` nombra las zonas consumidoras como `gold-factory`, `coal-factory`
  y `ruby-factory`, que **no existen como terreno**: el mapa tiene tiendas y central eléctrica.
  Hay que alinear nomenclatura o mapear.
- ADR-023 ya avisó de que el clima posicional por zonas es caro; la detección debe ser barata y
  determinista (tests con mapas fijos).

## Decisión (propuesta)

1. **Un sensor de zonas fuera del módulo `soundscape`** (en `core` o en un bridge dedicado) que
   traduce una posición a pesos 0.0–1.0 por zona y construye el `CompositionInput`. La referencia
   es el **elemento activo del modo**, no la cámara:
   - `drive`: **la locomotora** (si entra en un túnel, el decorado suena a túnel aunque la cámara
     esté por encima de la montaña);
   - `rails`: **el cursor**, se oye la zona por donde se construye;
   - `stations`, `load_trains`: **la estación** seleccionada;
   - `semaphores`, `sensors`, `speed_signals`, `forks`, `add`: **el elemento que se edita**
     (cursor u objeto seleccionado);
   - `trains`, `link`, `unlink`: el tren/locomotora implicados;
   - `MENU`, `COMMAND`, `PROGRAM` (sin elemento espacial): **se mantiene el último foco**.
   No existe el caso "sin herramienta": `GameMode` **siempre** tiene valor. La **cámara**
   (ORBIT/CAB/MAP) no cambia el foco de escucha: solo aporta altura/zoom (atenuación + LP de aire,
   ya existente). El cambio de foco entre modos es un
   **corte de escena inmediato, sin transición**: al pasar del tren al cursor, los pesos de la
   nueva posición se aplican de golpe. El `soundscape` sigue sin conocer el mapa (aislamiento
   ADR-023).
2. **Zonas naturales por densidad en un radio fijo**: muestrear una rejilla gruesa alrededor de
   la posición y calcular la fracción de tiles `WATER` / `ROCK` / `GROUND`; el peso cae con la
   distancia (p. ej. `w = clamp((1 - d/R) * densidad)`). El radio de muestreo es un valor **fijo**
   global (no por zona ni por terreno). Varias zonas activas a la vez.
3. **Zonas industriales por densidad de industria**: `countIndustryDensity` para productoras
   (minas) y consumidoras (tiendas/central). Nomenclatura del estilo: se conservan los nombres
   `x-mine` / `x-factory` y el sensor mapea los terrenos:
   `GOLD_MINE→gold-mine`, `MINE→coal-mine`, `RUBY_MINE→ruby-mine` y
   `JEWELRY_STORE→gold-factory`, `POWER_PLANT→coal-factory`, `RUBY_STORE→ruby-factory`.
4. **Mezcla progresiva dentro del foco, corte con micro-fade entre focos**: el movimiento continuo
   (el tren avanza, el cursor se desplaza) mezcla las zonas progresivamente con ataque/release
   exponencial (2–5 s) para no saltar al cruzar fronteras. Al **cambiar de foco** (tren ↔ cursor)
   la escena cambia de inmediato, con un **micro-fade anti-click (~10–20 ms)**, sin transición
   audible. El sensor muestrea a cadencia baja (p. ej. 4 Hz), no en cada tick. Implicación para el
   player: el ease de ganancia actual (0,8 s en `AmbientVoice`) debe poder saltarse en el cambio
   de foco.
5. **Pesos proporcionales a la cercanía**: el volumen de cada zona crece con la proximidad y los
   pesos **suman** (sin normalizar): una costa con montaña cerca suena a ambas. Queda por fijar
   si se recortan a las N zonas mayores para no disparar el número de sonidos simultáneos.
6. **Determinismo**: misma posición + mismo mapa ⇒ mismos pesos; el suavizado depende solo del
   tiempo simulado, no del reloj real.
7. **Espacio acústico (túnel/estación)**: el estado ambiental gana un eje `enclosure` (0 = cielo
   abierto, 1 = dentro). La señal de túnel ya existe en el modelo (`TunnelGateRailTrack`,
   `VisualType.TUNNEL`). Al entrar: bajar/duckear las zonas exteriores (montaña, campo) y activar
   una reverb corta sobre el mix (y sobre los sonidos del tren) — el eco cobra sentido aquí. Al
   salir, se invierte con el mismo suavizado. Opcional: capa propia de túnel (retumbo, viento
   canalizado). El render de CAB dentro del túnel (ver algo de vía y la salida al fondo) es una
   tarea de la capa gráfica, no del audio.

## Algoritmo propuesto (zona primaria + secundaria)

1. **Primaria = el tile sobre el que se está** (`getValueAt(pos)`):
   - `WATER → sea`, `ROCK → mountain`, `GROUND → fields`; industria (`10..29`) → `x-mine` /
     `x-factory` según el mapeo del punto 3.
   - Ejemplos: en un puente, el tile de debajo es agua → primaria `sea`; en una mina, primaria
     `x-mine` y el campo de alrededor aparecerá como secundaria de forma natural.
2. **Secundaria = la zona distinta más cercana** dentro de un radio fijo `R`, muestreando
   **anillos** (8 direcciones × 3–4 radios, 24–32 lecturas), no el cuadrado completo. Peso
   `(1 - d/R) × 0.45`, siempre por debajo de la primaria: en la costa la tierra gana pero el mar
   se oye; al alejarse, la secundaria cae a 0.
3. **Máximo 2 zonas** (primaria + mejor secundaria). No hacen falta `findClosestIndustry` ni
   `countIndustryDensity`: la industria ya viene en el valor del tile y en los anillos.
4. **Refresco**: al **cambiar de celda** + un tick lento (4 Hz) de seguridad + inmediato al
   cambiar de foco. Entre refrescos, mezcla progresiva (ataque/release), de modo que cruzar
   celdas no produzca saltos.
5. **Coste**: con tiles materializados, `getValueAt` es O(1) (HashMap) y 30 lecturas son
   microsegundos. Para tiles **no materializados**, `getValueAt` recalcula el terreno con Perlin
   en cada lectura y **no cachea**; con anillos y 4 Hz el coste es despreciable, y si aparecen
   picos se añade una **caché de terreno por bloque** (resultados de `computeTerrainValue`,
   tamaño acotado) en `GroundMap`; `setValueAt` sigue mandando porque escribe celdas
   materializadas.

## Consecuencias

- Positivas: el decorado reacciona al paisaje (mar al borde de la costa, montaña al subir, mina al
  acercarse a la industria); encaja con el modelo de familias de ADR-024.
- Costes y riesgos: muestreo espacial hay que medirlo (posible caché por bloques); decidir si el
  sensor sigue al tren o a la cámara; definir radios y curvas oyendo; riesgo de mezclas confusas
  si demasiadas zonas pesan a la vez.
- Dependencias: requiere decidir el punto de integración (¿`core` depende de `soundscape`? ¿bridge
  aparte?) y un estilo con zonas alineadas con el terreno.

## Alternativas consideradas

- **Raycast por tiles en cada tick**: descartado; caro y con saltos duros en las fronteras.
- **Mapa precomputado de distancias por zona**: descartado de momento; mucha memoria y hay que
  mantenerlo por seed/mapa.
- **Regiones predefinidas a mano por mapa**: descartado; poco flexible y no escala a mapas
  generados por ruido.
- **Pesos por altitud del terreno**: insuficiente; no distingue costa, industria ni agua.

## Plan por fases

1. Sensor mínimo: mar/montaña/campos por densidad + suavizado, con tests de mapa fijo.
2. Industrias productoras/consumidoras y alineación de nombres con el estilo.
3. Ajuste fino de radios, curvas y cadencia oyendo en el GUI.
4. (Futuro) espacios acústicos (túnel, estación cubierta) si el modelo de material lo permite.

## Preguntas abiertas

- ¿La reverb de túnel se implementa en el player (FDN) o se pre-renderiza en las tomas?
- Dentro de un túnel, ¿duck completo de zonas exteriores o solo atenuación + LP?
- ¿Dónde vive el sensor: `core`, un módulo bridge nuevo, o el launcher que ya conoce ambos?
- Curva de caída del peso con la distancia: ¿lineal, exponencial, por oído?
