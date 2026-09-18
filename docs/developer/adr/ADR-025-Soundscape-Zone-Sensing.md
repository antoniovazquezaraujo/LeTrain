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
   traduce una posición (tren o cámara, a decidir) a pesos 0.0–1.0 por zona y construye el
   `CompositionInput`. El `soundscape` sigue sin conocer el mapa (aislamiento ADR-023).
2. **Zonas naturales por densidad en un radio**: muestrear una rejilla gruesa alrededor de la
   posición y calcular la fracción de tiles `WATER` / `ROCK` / `GROUND`; el peso cae con la
   distancia (p. ej. `w = clamp((1 - d/R) * densidad)`). Varias zonas activas a la vez.
3. **Zonas industriales por densidad de industria**: `countIndustryDensity` para productoras
   (minas) y consumidoras (tiendas/central); mapear `gold/coal/ruby-factory` del estilo a las
   consumidoras reales (`JEWELRY_STORE`, `POWER_PLANT`, `RUBY_STORE`) o renombrar en el estilo.
4. **Suavizado temporal**: ataque/release exponencial (2–5 s) por zona para que cruzar una
   frontera no salte; el sensor muestrea a cadencia baja (p. ej. 4 Hz), no en cada tick.
5. **Límite de voz**: quedarse con las 2–3 zonas de mayor peso (y normalizar si hace falta) para
   no disparar el número de sonidos simultáneos.
6. **Determinismo**: misma posición + mismo mapa ⇒ mismos pesos; el suavizado depende solo del
   tiempo simulado, no del reloj real.

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

- ¿El sensor sigue al **tren** (posición del jugador) o a la **cámara** (zoom/altura)?
- Radio de muestreo y curva de caída por zona: ¿fijos por zona o por terreno?
- ¿Normalizamos los pesos o dejamos que sumen?
- ¿Dónde vive el sensor: `core`, un módulo bridge nuevo, o el launcher que ya conoce ambos?
- ¿Renombramos `gold/coal/ruby-factory` en el estilo o mapeamos a tiendas/central?
