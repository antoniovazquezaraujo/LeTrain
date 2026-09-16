# Diseño: Decorado Sonoro (Soundscape) — borrador para revisión conjunta

## Estado: PROPUESTO (borrador para revisión con el equipo `agy`)

## 1. La idea

El juego no debería saber cómo suena cada sitio. Solo describe **el ambiente** (hora, terreno,
actividad humana, clima) y una pieza aparte —el **decorado sonoro**— compone el paisaje sonoro.

- Principio: **el juego pone los trenes; la lib pone el paisaje**.
- Objetivo: escenas ricas, variadas y no repetitivas, probables **en aislamiento**, sin lógica de
  juego dentro.

## 2. Frontera (qué sabe y qué no)

La lib **no conoce** trenes, vías, horarios ni entidades del juego. Recibe:

| Entrada | Rango | Ejemplo |
|---|---|---|
| Hora del día | 00:00–23:59 | 22:30 → noche |
| Velocidad | LENTA / NORMAL / RÁPIDA | NORMAL |
| Etiquetas de terreno | peso 0.0–1.0 | `mar 0.8`, `bosque 0.2` |
| Etiquetas de actividad humana | peso 0.0–1.0 | `mina 0.5`, `pueblo 0.3` |
| Clima (fase posterior) | estado + intensidad | `lluvia 0.4` |

Salida: audio ambiental. **El juego mezcla eso con sus trenes** (que ya se atenúan por distancia) y
un limitador maestro evita saturación. Aquí no hay "ducking": el tren no baja el decorado, se suma.

Todo se parametriza en **ficheros de texto legibles** y recargables en caliente.

## 3. Modelo de escena: capas, no pistas

Una escena es una lista de capas. Tipos de capa:

- **Colchón continuo**: viento, lluvia. Nunca "canta".
- **Cuasi-continuo**: grillos, cigarras. Fondo con densidad y volumen que respiran.
- **Eventos discretos**: perro lejano, gallo, avispas. Momentos aleatorios.
- **Textura cercana**: avispas al lado, pasos. Cercanía = menos filtro, más presencia.

Parámetros por capa: **presencia (volumen), densidad, distancia/cercanía, variación**.

Composición **base + acentos**:

- **Base**: cama natural por terreno y hora → mar = olas y gaviotas; tierra = estorninos e
  insectos; montaña = halcones, viento, eco.
- **Acentos**: actividad humana → mina = maquinaria extractora; fábrica/pueblo = producción y
  trasiego; estación = murmullo y maniobras.
- **La hora modula ambas**: de noche la base vira a grillos/búhos y los acentos se apagan o se
  vuelven tenues y lejanos.
- El **peso de la etiqueta** se traduce en volumen y cercanía: mina al lado = presencia fuerte; a
  dos valles = un zumbido.

## 4. Anti-repetición (el enemigo número uno)

- Pools de sonidos por familia (3–4 chirridos distintos, no uno repetido).
- Intervalos aleatorios entre eventos, con **silencio** como parte de la composición.
- Variación de tono y volumen en cada reproducción.
- Modulación lenta en los colchones (el viento sube y baja en minutos, no en segundos).
- Capas de distinta periodicidad desfasadas entre sí.

## 5. Tiempo y velocidad

- Tres presets: **LENTA / NORMAL / RÁPIDA**, con duración de día configurable. Propuesta inicial
  para validar de oído: 60 / 40 / 20 minutos reales por día de juego.
- **Regla de oro**: la escena sigue al reloj (las fases), pero la textura se mide en **tiempo
  real**: cada cuánto ladra el perro, cuánto dura un fundido, cuánto respira el viento.
- Fundidos con **suelo** (p. ej. nunca menos de 20–30 s reales) y **techo** (no comerse la fase).
- A más velocidad, **menos escenas**: si el día es corto, amanecer y mañana se funden en una sola.

## 6. Clima

- **Global**: "ahora llueve" / "después escampa" para todo el mundo. Sin posición, sin mapa de
  lluvia, sin bordes. En 2D y 3D es trivial mantenerlo coherente.
- Estados con intensidad, **deterministas desde la semilla** + hora, para que escenarios, guardados
  y pruebas sean reproducibles.
- Se compone con la hora: una noche de tormenta es más oscura y enmascara la fauna.
- Fuera de alcance: clima por zonas/regiones (se puede evolucionar después cambiando de dónde
  salen las etiquetas, no cómo suenan).

## 7. Etiquetas iniciales propuestas

- **Terreno (5)**: mar, llanura, bosque, montaña, río.
- **Actividad humana (4)**: mina, fábrica, pueblo, estación.
- Peso continuo 0.0–1.0; varias etiquetas activas a la vez.

## 8. Reglas de composición (ejemplos)

| Hora | Etiquetas dominantes | Capas resultantes |
|---|---|---|
| Amanecer | llanura | Gallos, coro de pájaros, viento suave |
| Mediodía | llanura, (calor) | Cigarras, pájaros espaciados, silencio de fondo |
| Tarde | mar 0.8 | Olas, gaviotas, viento |
| Noche | bosque | Grillos, búho, viento tenue |
| Cualquiera | mina 0.5 + hora laboral | Maquinaria extractora; de noche tenue y lejana |
| Cualquiera | estación 0.2 | Murmullo, maniobras, avisos |

## 9. Dos escenas de ejemplo (borrador)

```
Escena: amanecer-de-granja
  hora: 05:30–08:00 · velocidad: cualquiera
  capas:
    - gallo         eventos, densidad baja, distancia media
    - coro-pajaros  cuasi-continuo, presencia 0.6
    - viento-suave  colchón, presencia 0.2, modulación lenta
```

```
Escena: noche-de-lluvia-en-la-costa
  hora: 21:00–05:00
  clima: lluvia 0.5
  capas:
    - lluvia-suave  colchón, presencia 0.5
    - olas          colchón, presencia 0.6, modulación lenta
    - grillos       cuasi-continuo, presencia 0.2 (la lluvia los enmascara)
    - perro-lejano  eventos, densidad muy baja, distancia lejana
```

## 10. Reproductor de pruebas (primero, sin el juego)

- Deslizador de hora y modo "día completo" para sentir las transiciones.
- Interruptores **con peso** para las etiquetas (`mar 0.8`, `mina 0.3`…).
- Selector LENTA / NORMAL / RÁPIDA.
- Recarga de escenas y reglas sin reiniciar.

Con esto se prueba fauna, industrias y velocidades **antes de que existan en el juego**; cuando
llegue, el juego solo manda etiquetas.

## 11. Fuera de alcance (por ahora)

- Clima posicional y estaciones del año.
- Efectos de gameplay (retrasos, adherencia, producción).
- Ducking y cualquier lógica que mencione trenes dentro de la lib.
- Integración en los clientes.

## 12. Decisiones tomadas

1. El decorado vive en una pieza aparte, con frontera por **etiquetas**.
2. Escenas por **capas** (base + acentos), no escenas completas por combinación.
3. **Sin ducking**: el tren se suma; limitador de seguridad en la mezcla maestra.
4. Clima **global** con estados e intensidad, determinista desde la semilla.
5. Velocidad **LENTA / NORMAL / RÁPIDA** con duraciones configurables.
6. **Textura en tiempo real; fases al reloj del juego.** Fundidos con suelo y techo.
7. Todo el diseño es **data** (catálogo, reglas, escenas, presets) en ficheros de texto.
8. Se empieza por el **reproductor de pruebas**.

## 13. Preguntas abiertas

- Duración definitiva de los tres presets (se decide **oyendo** en el reproductor).
- Set de etiquetas definitivo y cuántas capas simultáneas como máximo (6–8 como punto de partida).
- Catálogo de sonidos: fuentes, licencias (todo debe poder distribuirse con el juego) y formato.
- ¿Los sonidos van al repo o se descargan aparte? (peso frente a simplicidad).
- Herramienta del reproductor: pendiente de decidir (reutilizar el audio actual del proyecto o una
  herramienta externa de pruebas).
