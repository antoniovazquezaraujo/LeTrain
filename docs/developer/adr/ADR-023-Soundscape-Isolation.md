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
2. **Frontera por situación y tiempo**: el juego describe **dónde** está el punto de escucha
   (distancia/peso a tipos de zona) y **cuándo** (hora exacta del día, p. ej. 03:40), más el preset
   de velocidad. La lib responde con audio ambiental. Nunca se le manda "noche" o "mañana": las
   franjas son solo etiquetas de autoría.
   - Zonas de cualquier tipo, todas de la misma naturaleza: geografía (mar, montaña, río, bosque,
     llanura) y actividad (mina, fábrica, pueblo, estación).
   - Peso continuo 0.0–1.0 por zona según cercanía; varias zonas activas a la vez.
3. **Modelo único de sonido componente**: cada sonido ambiental = **material** (una o varias tomas)
   + **modo de reproducción** + parámetros (presencia, cercanía, variación).
   - **Bucle con salto**: material continuo (mar, viento, maquinaria). Se reproduce en bucle
     saltando aleatoriamente entre tramos de la grabación para que no se detecte la repetición.
   - **Discontinuo**: material de eventos (ladridos, gallos, gaviotas). Se eligen tomas de la
     grabación y se disparan con pausas aleatorias entre ellas.
4. **Composición = situación × tiempo**: cada sonido declara una **curva de presencia**
   anclada en las franjas del día (amanecer, mañana, mediodía, tarde, anochecer, noche, madrugada),
   y en ejecución se evalúa con la **hora exacta**: a las 03:40 el motor mira dónde cae esa hora entre
   noche y madrugada y mezcla los valores de ambas. En un punto, el resultado es la **suma ponderada
   de las zonas cercanas**.
   - La mezcla **persigue** ese objetivo suavizada en tiempo real (fundido con suelo), así que pasar
     de noche a mañana nunca es un salto: los perros bajan y las cigarras suben gradualmente.
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
   con peso, velocidad) antes de integrarlo en los clientes.
10. **Determinismo y carga**: el paisaje es función determinista de (hora, situación, clima,
    semilla del mundo). Al cargar una partida a las 04:39 se recalcula el objetivo de esa hora y se
    arranca ahí, sin arrastrar estado oculto de la sesión anterior. El azar de los eventos se deriva
    de la semilla (misma semilla + misma hora = misma programación), así que no hace falta guardar
    estado interno del azar.

## Detalle de composición (resumen)

**Entradas** (lo único que la lib necesita saber):

| Entrada | Ejemplo |
|---|---|
| Hora exacta + velocidad | 03:40, LENTA |
| Velocidad | LENTA / NORMAL / RÁPIDA (60 / 40 / 20 min de día, a validar de oído) |
| Situación: zonas con peso 0.0–1.0 | `mar 0.8`, `llanura 0.3`, `mina 0.5` |
| Clima global (fase posterior) | `lluvia 0.4` |

- **Zonas iniciales propuestas**: mar, llanura, bosque, montaña, río, mina, fábrica, pueblo,
  estación.
- Cada **zona** es un conjunto de **sonidos componentes**; cada sonido declara:
  - **material**: una o varias tomas/segmentos;
  - **modo**: *bucle con salto* (continuos) o *discontinuo* (eventos);
  - **presencia por franjas** como puntos de control de una curva (las franjas sin valor se interpolan o quedan a cero);
  - **parámetros**: presencia base, cercanía, densidad (en discontinuos) y variación.
- **Volumen final** de un sonido ≈ peso de su zona × presencia en la franja (interpolada) ×
  parámetros propios. Se suman todas las zonas activas.
- Ejemplo de tabla de la zona **llanura**:

| Sonido | Amanecer | Mañana | Mediodía | Tarde | Anochecer | Noche | Madrugada |
|---|---|---|---|---|---|---|---|
| gallos | 0.8 | 0.2 | 0 | 0 | 0 | 0 | 0.1 |
| perros | 0.3 | 0.2 | 0.5 | 0.2 | 0.2 | 0.1 | 0 |
| grillos | 0.1 | 0 | 0 | 0 | 0.4 | 0.7 | 0.8 |
| halcones | 0 | 0.1 | 0.1 | 0.4 | 0.1 | 0 | 0 |

- **Anti-repetición**: saltos aleatorios entre tramos con micro-fundido para evitar clics, pools de
  tomas, pausas aleatorias, variación de tono/volumen y modulación lenta; sonidos desfasados entre
  sí.
- **Clima**: global (estado + intensidad), determinista desde la semilla, compuesto con la franja
  (la lluvia enmascara grillos, por ejemplo).
- **Reproductor de pruebas**: hora exacta (con modo "día completo"), zonas con peso, preset de
  velocidad y recarga de catálogo/zonas sin reiniciar.

Ejemplo de composición:

```
anochecer junto a la costa
  hora: 20:45 · clima: lluvia 0.4 · situación: mar 0.8 · pueblo 0.2
  resultado: olas (bucle con salto, 0.6) · lluvia-suave (bucle, 0.4)
             gaviotas (discontinuo, 0.1) · perro-lejano (discontinuo, 0.15, lejano)
             grillos de pueblo (0.2, enmascarados por la lluvia)
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
- **Zoom/altura de escucha (perspectiva por sonido)**: aplazado; se simplificó la primera versión a
  situación × tiempo.
- **Librería externa con versionado/paquete publicado**: descartado por ahora; el repo ya es
  multi-módulo y un módulo interno da la misma frontera sin coste de publicación.

## Preguntas abiertas

- ¿Las 7 franjas son fijas o configurables por escenario? (propuesta: fijas, como puntos de control
  de las curvas de presencia).
- Lista definitiva de zonas y límite de sonidos simultáneos (6–8 como punto de partida).
- Presets de velocidad definitivos (se deciden oyendo en el reproductor).
- Catálogo y licencias de sonido; ¿assets en el repo o descarga aparte?
- ¿Módulo propio del repo o paquete aislado promovible?
