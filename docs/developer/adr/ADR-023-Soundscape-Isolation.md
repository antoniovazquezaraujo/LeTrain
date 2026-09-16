# ADR-023: Decorado Sonoro como Componente Aislado

## Estado: PROPUESTO (borrador para revisión conjunta con el equipo `agy`)

## Contexto

- El juego ya tiene audio ambiental básico (pájaros/viento que se cruzan con el zoom) y un motor de
  síntesis para los trenes. No hay sonido ligado al paso del tiempo, al terreno ni al clima.
- ADR-022 introduce el tiempo de juego (reloj, día/noche, horarios). Sin una frontera clara, el
  audio ambiental, el clima y la simulación acabarían enredados: el tiempo sabría de sonidos y el
  audio sabría de trenes y de reglas de juego.
- Los sonidos ambientales requieren un tratamiento distinto al del tren: catálogo, modos de
  reproducción, aleatoriedad y licencias. Es un problema propio.
- El proyecto valora el bajo acoplamiento (MVP) y ya es multi-módulo (core, clientes, launchers),
  por lo que existe un sitio natural para separar esta pieza.

## Decisión (propuesta)

1. **El decorado sonoro es una pieza aparte**, con dependencia unidireccional: el juego la alimenta,
   ella no conoce el juego. No sabe de trenes, vías, horarios ni entidades. Tampoco se suscribe a
   nada: **el juego la actualiza en cada tick** con el estado de ambiente. Es un mezclador pasivo y
   determinista (mismas entradas, mismo sonido), probable sin arrancar el juego.
2. **Frontera por situación, tiempo y escucha**: el juego describe **dónde** está el punto de
   escucha (distancia/peso a tipos de zona), **cuándo** (hora exacta del día, p. ej. 03:40) y **cómo
   se escucha** (zoom/altura), más el preset de velocidad. La lib responde con audio ambiental.
   Nunca se le manda "noche" o "mañana": las franjas son solo etiquetas de autoría.
   - Zonas de cualquier tipo, todas de la misma naturaleza: geografía (mar, montaña, río, bosque,
     llanura) y actividad (mina, fábrica, pueblo, estación).
   - Peso continuo 0.0–1.0 por zona según cercanía; varias zonas activas a la vez.
3. **Modelo único de sonido componente**: cada sonido ambiental = **material** (una o varias tomas)
   + **modo de reproducción** + parámetros (presencia, cercanía, variación).
   - **Bucle con salto**: material continuo (mar, viento, maquinaria). Se reproduce en bucle
     saltando aleatoriamente entre tramos de la grabación para que no se detecte la repetición.
   - **Discontinuo**: material de eventos (ladridos, gallos, gaviotas). Se eligen tomas de la
     grabación y se disparan con pausas aleatorias entre ellas.
4. **Composición = situación × tiempo × escucha**: cada sonido declara una **curva de presencia**
   anclada en las franjas del día (amanecer, mañana, mediodía, tarde, anochecer, noche, madrugada),
   y en ejecución se evalúa con la **hora exacta**: a las 03:40 el motor mira dónde cae esa hora entre
   noche y madrugada y mezcla los valores de ambas. En un punto, el resultado es la **suma ponderada
   de las zonas cercanas**.
   - La mezcla **persigue** ese objetivo suavizada en tiempo real (fundido con suelo), así que pasar
     de noche a mañana nunca es un salto: los perros bajan y las cigarras suben gradualmente.
   - La **escucha (zoom/altura)** entra en la mezcla: desde lo alto y a ras de suelo no se percibe lo
     mismo. Se descartó el "alcance" por sonido (una etiqueta más que mantener); **cómo se refleja el
     zoom queda como pregunta abierta**.
   - Esto permite lo que buscamos: en una misma zona, por la mañana grillos, a mediodía perros y por
     la tarde halcones, sin duplicar nada.
   - **Overrides por escenario**: un mapa o una época pueden ajustar la tabla de una zona ("aquí, por
     la mañana, grillos") sin tocar las reglas generales.
5. **Sin ducking**: el tren se suma al decorado; no lo silencia. Un limitador maestro evita
   saturación. Los sonidos de tren siguen siendo responsabilidad del juego.
6. **Clima global** (estado + intensidad), determinista desde la semilla, compuesto con la hora. Sin
   clima posicional. Alcance inicial: **lluvia, viento y tormenta eléctrica** (la niebla queda fuera
   por ahora).
7. **Velocidad LENTA / NORMAL / RÁPIDA** con duraciones configurables (definidas en ADR-022). Las
   franjas siguen al reloj de juego, pero la **textura se ancla al tiempo real**: densidad de eventos
   y fundidos (con suelo en segundos reales y techo como fracción de la franja).
8. **Todo es data**: catálogo de sonidos, zonas, tablas de presencia y presets viven en ficheros de
   texto legibles y recargables.
9. **Primero el reproductor de pruebas**: se valida el paisaje sonoro en aislamiento (hora, zonas
   con peso, zoom/escucha, velocidad) antes de integrarlo en los clientes.
10. **Determinismo y carga**: el paisaje es función determinista de (hora, situación, escucha, clima,
    semilla del mundo). Al cargar una partida a las 04:39 se recalcula el objetivo de esa hora y se
    arranca ahí, sin arrastrar estado oculto de la sesión anterior. El azar de los eventos se deriva
    de la semilla (misma semilla + misma hora = misma programación), así que no hace falta guardar
    estado interno del azar.

## Detalle de composición (resumen)

**Entradas** (lo único que la lib necesita saber):

| Entrada | Ejemplo |
|---|---|
| Hora exacta + velocidad | 03:40, `slow` |
| Velocidad | `slow` / `normal` / `fast` (60 / 40 / 20 min de día, a validar de oído) |
| Situación: zonas con peso 0.0–1.0 | `sea 0.8`, `plains 0.3`, `mine 0.5` |
| Escucha: zoom/altura 0.0–1.0 | `listening` 0.2 (a ras de suelo) · 0.9 (vista amplia) |
| Clima global (fase posterior) | `rain 0.4` |

- **Zonas iniciales propuestas**: `sea`, `plains`, `forest`, `mountain`, `river`, `mine`,
  `factory`, `town`, `station`.
- Cada **zona** es un conjunto de **sonidos componentes**; cada sonido declara:
  - **material**: una o varias tomas/segmentos;
  - **presencia por franjas** (`dawn`, `morning`, `noon`, `afternoon`, `dusk`, `night`, `predawn`)
    como puntos de control de una curva; las franjas sin valor se interpolan o quedan a cero;
  - **parámetros**: presencia base, cercanía y variación.
- **Volumen final** de un sonido ≈ peso de su zona × presencia en la franja (interpolada) ×
  parámetros propios. Se suman todas las zonas activas.
- Ejemplo de formato (claves en inglés, como el resto del código; comentarios en español):

```
# ============================================================
#  Estilo sonoro — Valle del Norte
#  Texto plano, comentarios con #, recarga en caliente.
# ============================================================

# --- Presets de clima (intensidades 0.0–1.0) ----------------
[climate]
clear   = rain 0.0  wind 0.1  storm 0.0
drizzle = rain 0.3  wind 0.1  storm 0.0
storm   = rain 0.8  wind 0.6  storm 0.9

# --- Sonidos: bucles con saltos indetectables ---------------
#  (gallos, perros…: tomas de 10-20 s que ya incluyen silencios)
[sounds]
waves      = sea/waves-*.wav
cicadas    = bugs/cicada-*.wav
seagulls   = sea/seagull-*.wav
crickets   = bugs/cricket-*.wav
dogs       = town/dog-*.wav
roosters   = farm/rooster-*.wav
machinery  = mine/machine-*.wav
thuds      = mine/thud-*.wav

# --- Zonas: solo listas de sonidos --------------------------
[zones]
sea     = waves, seagulls
plains  = cicadas, crickets, dogs, roosters
mine    = machinery, thuds

# --- Presencia por franja -----------------------------------
#               dawn  morning  noon  afternoon  dusk  night  predawn
[presence]
waves           0.5     0.5   0.5       0.5   0.6    0.6      0.5
cicadas         0.0     0.2   1.0       0.5   0.1    0.0      0.0
seagulls        0.1     0.3   0.2       0.4   0.3    0.1      0.0
crickets        0.1     0.0   0.0       0.0   0.5    0.8      0.9
dogs            0.3     0.2   0.4       0.2   0.2    0.1      0.0
roosters        0.9     0.2   0.0       0.0   0.0    0.0      0.1
machinery       0.0     0.5   0.5       0.5   0.0    0.0      0.0
thuds           0.0     0.4   0.4       0.4   0.0    0.0      0.0

# --- Clima por sonido (0 = igual) ---------------------------
#             rain  wind  storm
[climate-by-sound]
cicadas      -1.0  -0.4   -1.0
crickets     -0.8  -0.2   -1.0
seagulls     -0.7  +0.3   -1.0
dogs          0.0   0.0   +0.2
waves         0.0  +0.4    0.0
machinery     0.0   0.0   -0.3
```

Ejemplo de composición con ese fichero:

```
23:30 · NORMAL · sea 0.5 · plains 0.7 · listening 0.2 · drizzle
→ rain (del preset) 0.30 · crickets 0.41 · waves 0.31 · dogs 0.07 · seagulls 0.04
  cicadas 0.00 · machinery 0.00
```

## Consecuencias

- Positivas: el core queda limpio de lógica de audio ambiental; la pieza es probable sin arrancar
  el juego (tests deterministas); añadir fauna, zonas o clima es añadir datos, no código; evita que
  tiempo, clima y audio se acoplen entre sí.
- Costes y riesgos: mantener una pieza más y su catálogo de sonidos; disciplina de licencias (todo
  debe poder distribuirse con el juego); riesgo de repetición audible si la aleatoriedad no se
  trabaja; decidir módulo propio vs paquete aislado al empezar la implementación.
- Dependencias: consume el reloj y los presets de velocidad de ADR-022; no influye en la
  simulación.

## Alternativas consideradas

- **Meter el decorado dentro del core**: descartado; contamina la simulación con catálogo, mezcla y
  reglas de audio.
- **Una escena completa por cada combinación de franja/zona**: descartado por combinatoria y
  mantenimiento; se compone con sonidos reutilizables y pesos por cercanía.
- **Ducking del ambiente al pasar el tren**: descartado; no es sonido realista (el tren se suma y
  enmascara) y añade dependencia de los trenes dentro de la lib.
- **Clima posicional por zonas**: aplazado; encarece mucho (mapa, bordes, transiciones al moverse)
  para el beneficio actual.
- **"Alcance" por sonido (perspectiva suelo/área/altura)**: descartado por ser una taxonomía de
  más; el zoom/altura sigue en la composición, pendiente de definir su efecto.
- **Librería externa con versionado/paquete publicado**: descartado por ahora; el repo ya es
  multi-módulo y un módulo interno da la misma frontera sin coste de publicación.

## Preguntas abiertas

- ¿Las 7 franjas son fijas o configurables por escenario? (propuesta: fijas, como puntos de control
  de las curvas de presencia).
- Cómo se refleja la escucha (zoom/altura) en la mezcla, sin "alcance" por sonido.
- Lista definitiva de zonas y límite de sonidos simultáneos (6–8 como punto de partida).
- Presets de velocidad definitivos (se deciden oyendo en el reproductor).
- Catálogo y licencias de sonido; ¿assets en el repo o descarga aparte?
- ¿Módulo propio del repo o paquete aislado promovible?
