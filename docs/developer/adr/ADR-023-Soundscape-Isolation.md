# ADR-023: Decorado Sonoro como Componente Aislado

## Estado: PROPUESTO (borrador para revisión conjunta con el equipo `agy`)

## Contexto

- El juego ya tiene audio ambiental básico (pájaros/viento que se cruzan con el zoom) y un motor de
  síntesis para los trenes. No hay sonido ligado al paso del tiempo, al terreno ni al clima.
- ADR-022 introduce el tiempo de juego (reloj, día/noche, horarios). Sin una frontera clara, el
  audio ambiental, el clima y la simulación acabarían enredados: el tiempo sabría de sonidos y el
  audio sabría de trenes y de reglas de juego.
- Los sonidos ambientales requieren un tratamiento distinto al del tren: escenas, capas,
  aleatoriedad, licencias, catálogo. Es un problema propio.
- El proyecto valora el bajo acoplamiento (MVP) y ya es multi-módulo (core, clientes, launchers),
  por lo que existe un sitio natural para separar esta pieza.

## Decisión (propuesta)

1. **El decorado sonoro es una pieza aparte**, con dependencia unidireccional: el juego la alimenta,
   ella no conoce el juego. No sabe de trenes, vías, horarios ni entidades.
2. **Frontera por etiquetas**: el juego traduce su mundo a etiquetas de entorno con peso
   (terreno + actividad humana) y aporta la hora y el preset de velocidad. La lib responde con
   audio ambiental.
   - Terreno: mar, llanura, bosque, montaña, río.
   - Actividad humana: mina, fábrica, pueblo, estación.
   - Peso continuo 0.0–1.0; varias etiquetas activas a la vez.
3. **Escenas por capas** (base + acentos), no escenas completas por cada combinación del mundo.
   Tipos: colchón continuo, cuasi-continuo, eventos discretos y textura cercana.
4. **Sin ducking**: el tren se suma al decorado; no lo silencia. Un limitador maestro evita
   saturación. Los sonidos de tren siguen siendo responsabilidad del juego.
5. **Clima global** (estado + intensidad), determinista desde la semilla, compuesto con la hora. Sin
   clima posicional.
6. **Velocidad LENTA / NORMAL / RÁPIDA** con duraciones configurables (definidas en ADR-022). Las
   fases de escena siguen al reloj de juego, pero la **textura se ancla al tiempo real**: densidad
   de eventos y fundidos (con suelo en segundos reales y techo como fracción de la fase).
7. **Todo es data**: catálogo de sonidos, reglas de composición, escenas y presets viven en
   ficheros de texto legibles y recargables.
8. **Primero el reproductor de pruebas**: se valida el paisaje sonoro en aislamiento (hora, etiquetas
   con peso, velocidad) antes de integrarlo en los clientes.

El detalle (hoja de parámetros, ejemplos, escenas y preguntas abiertas) vive en
`docs/developer/systems/Soundscape_Design.md`.

## Consecuencias

- Positivas: el core queda limpio de lógica de audio ambiental; la pieza es probable sin arrancar
  el juego (tests deterministas); añadir fauna, industrias o clima es añadir datos, no código;
  evita que tiempo, clima y audio se acoplen entre sí.
- Costes y riesgos: mantener una pieza más y su catálogo de sonidos; disciplina de licencias
  (todo debe poder distribuirse con el juego); riesgo de repetición audible si la aleatoriedad no se
  trabaja; decidir módulo propio vs paquete aislado al empezar la implementación.
- Dependencias: consume el reloj y los presets de velocidad de ADR-022; no influye en la
  simulación.

## Alternativas consideradas

- **Meter el decorado dentro del core**: descartado; contamina la simulación con catálogo, mezcla y
  reglas de audio.
- **Una escena completa por cada combinación de hora/terreno/industria**: descartado por
  combinatoria y mantenimiento; se compone por capas (base + acentos).
- **Ducking del ambiente al pasar el tren**: descartado; no es sonido realista (el tren se suma y
  enmascara) y añade dependencia de los trenes dentro de la lib.
- **Clima posicional por zonas**: aplazado; encarece mucho (mapa, bordes, transiciones al moverse)
  para el beneficio actual.
- **Librería externa con versionado/paquete publicado**: descartado por ahora; el repo ya es
  multi-módulo y un módulo interno da la misma frontera sin coste de publicación.

## Preguntas abiertas

- ¿Módulo propio del repo o paquete aislado promovible? (se decide al empezar la implementación).
- Presets de velocidad definitivos, set de etiquetas y límite de capas simultáneas.
- Catálogo y licencias de sonido; ¿assets en el repo o descarga aparte?
- Herramienta del reproductor de pruebas.
