# Prototipos sintetizados — meteo (viento, lluvia, olas)

Audio generado por el proyecto: ruido filtrado, envolventes y redes de delays. Sin muestras de
terceros y sin licencias externas; mismo licenciamiento que el repositorio (Apache-2.0).

Formato: WAV 48 kHz, 16 bits, estéreo, normalizado a pico -3 dBFS.

| Carpeta | Fichero | Contenido |
|---|---|---|
| `wind/` | `brisa-suave.wav` | brisa grave con subidas y bajadas lentas |
| `wind/` | `viento-medio.wav` | capas grave + media con movimiento asíncrono |
| `wind/` | `vendaval.wav` | retumbo grave + silbido en banda estrecha |
| `wind/` | `rachas.wav` | golpes de viento irregulares |
| `rain/` | `lluvia-suave.wav` | siseo fino con gotas dispersas |
| `rain/` | `lluvia-media.wav` | densidad media con cuerpo grave |
| `rain/` | `lluvia-fuerte.wav` | cortina densa con siseo alto |
| `rain/` | `lluvia-cristal.wav` | gotas resonantes (cristal/tejado) |
| `rain/` | `lluvia-cercana.wav` | gotas presentes, siseo brillante, poca masa |
| `rain/` | `lluvia-lejana.wav` | sin gotas definidas, masa grave en movimiento |
| `waves/` | `olas-suaves-v2.wav` | mar de fondo continuo + oleaje corto |
| `waves/` | `olas-fuertes-v2.wav` | mar de fondo continuo + rompiente |

El trueno sintético se descartó (no convencía). Para trueno se usarán grabaciones CC0 de
Freesound, pendientes de selección.

## Siguiente paso

Seleccionar los mejores, convertir a 44.1 kHz (formato de `sounds/`), añadirlos al catálogo con
su entrada en `src/main/resources/sounds/CREDITS.md` y referenciarlos en los estilos `*.sound`.
