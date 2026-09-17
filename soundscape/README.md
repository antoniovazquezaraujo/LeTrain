# soundscape

Decorado sonoro de LeTrain, como **módulo independiente**: no depende de `core` ni sabe de trenes,
vías ni horarios. Recibe el estado del ambiente (hora exacta, peso de las zonas, altura, clima) y
devuelve la mezcla objetivo de cada sonido.

Diseño y decisiones: `docs/developer/adr/ADR-023-Soundscape-Isolation.md`.

## Estado

Módulo funcional: formato de estilo, motor de composición, catálogo con 12 sonidos ambientales
normalizados, **reproducción en vivo** (cada material suena entero en bucle; los saltos aleatorios
con crossfade están aparcados hasta cortar los sonidos), reproductor CLI y taller Swing. Las minas y
fábricas aún no tienen material.

## Uso

```bash
mvn -pl soundscape test

# Mezcla en un momento dado
mvn -pl soundscape exec:java@cli \
  -Dexec.args="--time 23:30 --zones sea=0.5,fields=0.7 --height 0.2 --weather drizzle"

# Recorrido de un día completo
mvn -pl soundscape exec:java@cli \
  -Dexec.args="--day --zones gold-mine=0.7,fields=0.4 --weather clear"
```

Opciones: `--file`, `--time HH:mm`, `--zones zona=peso,...`, `--height 0..1`, `--weather preset`,
`--rain/--wind/--storm 0..1`, `--day`, `--check-assets`, `--help`.

`--check-assets` resuelve los materiales del estilo bajo `sounds/` y avisa de los que falten.

## Interfaz gráfica de pruebas

`SoundscapePlayer` es un taller Swing: sliders de hora, altura, clima y zonas, selector de velocidad
(lenta/normal/rápida, día de 60/40/20 min) con multiplicador de vista previa (x1/x10/x60), barras de
volumen por sonido en vivo, botón de día completo, **botón 🔊 Escuchar** (reproduce la mezcla en
vivo; ganancia maestra con limitador suave) y carga de estilos (`*.sound`).

```bash
mvn -pl soundscape exec:java@gui

# con un estilo concreto
mvn -pl soundscape exec:java@gui -Dexec.args="mi-estilo.sound"
```

## Formato del estilo

Texto plano con secciones y `clave = valor`; los comentarios empiezan por `#`. El ejemplo completo
está en `src/main/resources/styles/valle-norte.sound`:

| Sección | Contenido |
|---|---|
| `[climate]` | presets de clima: `rain`, `wind`, `storm` (0.0–1.0) |
| `[sounds]` | catálogo: `nombre = material(es)` (se admiten comodines) |
| `[zones]` | `zona = sonido, sonido…` |
| `[presence]` | 7 valores por sonido: dawn, morning, noon, afternoon, dusk, night, predawn |
| `[height-by-sound]` | sensibilidad a la altura (halcones +, cigarras −) |
| `[climate-by-sound]` | sensibilidad a lluvia, viento y tormenta |
| `[weather]` | sonidos que aporta el propio clima (volumen = intensidad) |

La composición de cada sonido es `peso de zona × presencia (hora exacta) × clima × altura`; los
sonidos del clima se suman aparte con su intensidad. Todo es determinista: mismas entradas, misma
mezcla.
