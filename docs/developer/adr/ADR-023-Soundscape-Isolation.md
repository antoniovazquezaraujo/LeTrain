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
   ella no conoce el juego. No sabe de trenes, vías, horarios ni entidades.
2. **Frontera por sitios**: el juego traduce su mundo a **sitios** con peso (mar, montaña, llanura,
   mina, pueblo…) y aporta la hora y el preset de velocidad. La lib responde con audio ambiental.
   - Peso continuo 0.0–1.0; varios sitios activos a la vez según la cercanía.
   - Todos los sitios son de la misma naturaleza: un conjunto de sonidos. No hay trato especial
     para lo "natural" frente a lo "humano".
3. **Modelo único de sonido componente**: cada sonido ambiental = **material** (una o varias tomas)
   + **modo de reproducción** + parámetros (presencia, cercanía, variación).
   - **Bucle con salto**: material continuo (mar, viento, maquinaria). Se reproduce en bucle
     saltando aleatoriamente entre tramos de la grabación para que no se detecte la repetición.
   - **Discontinuo**: material de eventos (ladridos, gallos, gaviotas). Se eligen tomas de la
     grabación y se disparan con pausas aleatorias entre ellas.
   Un sitio es, precisamente, un conjunto de sonidos componentes.
4. **Sin ducking**: el tren se suma al decorado; no lo silencia. Un limitador maestro evita
   saturación. Los sonidos de tren siguen siendo responsabilidad del juego.
5. **Clima global** (estado + intensidad), determinista desde la semilla, compuesto con la hora. Sin
   clima posicional.
6. **Velocidad LENTA / NORMAL / RÁPIDA** con duraciones configurables (definidas en ADR-022). Los
   sitios siguen al reloj de juego, pero la **textura se ancla al tiempo real**: densidad
   de eventos y fundidos (con suelo en segundos reales y techo como fracción de la fase).
7. **Todo es data**: catálogo de sonidos, sitios y presets viven en ficheros de texto legibles y
   recargables.
8. **Primero el reproductor de pruebas**: se valida el paisaje sonoro en aislamiento (hora, sitios
   con peso, velocidad) antes de integrarlo en los clientes.

## Detalle de composición (resumen)

**Entradas** (lo único que la lib necesita saber):

| Entrada | Ejemplo |
|---|---|
| Hora del día | 22:30 → noche |
| Velocidad | LENTA / NORMAL / RÁPIDA (60 / 40 / 20 min de día, a validar de oído) |
| Sitios con peso 0.0–1.0 | `mar 0.8`, `llanura 0.3`, `mina 0.5` |
| Clima global (fase posterior) | `lluvia 0.4` |

- **Sitios iniciales propuestos**: mar, llanura, bosque, montaña, río, mina, fábrica, pueblo,
  estación. Todos de la misma naturaleza.
- Cada **sitio** es un conjunto de **sonidos componentes**; cada sonido declara:
  - **material**: una o varias tomas/segmentos;
  - **modo**: *bucle con salto* (continuos: mar, viento, maquinaria) o *discontinuo* (eventos:
    ladridos, gallos, gaviotas);
  - **parámetros**: presencia, cercanía, densidad (en discontinuos) y variación.
- Un **punto del mapa** se compone de los sitios que lo rodean, ponderados por cercanía; la hora
  modula qué suena y con qué presencia (de noche entran los grillos y los perros se alejan).
- **Anti-repetición**: saltos aleatorios entre tramos con micro-fundido para evitar clics, pools de
  tomas, pausas aleatorias, variación de tono/volumen y modulación lenta; sonidos desfasados entre
  sí.
- **Tiempo**: los sitios siguen al reloj, pero densidad de eventos y fundidos se miden en tiempo
  real (suelo de ~20–30 s por fundido, techo como fracción de la fase). A más velocidad, menos
  variedad de sitios activos.
- **Clima**: global (estado + intensidad), determinista desde la semilla, compuesto con la hora.
- **Reproductor de pruebas**: hora, sitios con peso, preset de velocidad, modo "día completo" y
  recarga de catálogo/sitios sin reiniciar.

Ejemplo de composición:

```
noche-de-lluvia-en-la-costa
  hora: 21:00–05:00 · clima: lluvia 0.5 · sitios: mar 0.8 · pueblo 0.2
  sonidos: olas (bucle con salto, 0.6) · lluvia-suave (bucle, 0.5)
           gaviotas (discontinuo, 0.1) · perro-lejano (discontinuo, 0.15, lejano)
```

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
- **Una escena completa por cada combinación de hora/sitio**: descartado por combinatoria y
  mantenimiento; los sitios se componen de sonidos reutilizables y un punto combina los sitios
  que lo rodean.
- **Ducking del ambiente al pasar el tren**: descartado; no es sonido realista (el tren se suma y
  enmascara) y añade dependencia de los trenes dentro de la lib.
- **Clima posicional por zonas**: aplazado; encarece mucho (mapa, bordes, transiciones al moverse)
  para el beneficio actual.
- **Librería externa con versionado/paquete publicado**: descartado por ahora; el repo ya es
  multi-módulo y un módulo interno da la misma frontera sin coste de publicación.

## Preguntas abiertas

- ¿Módulo propio del repo o paquete aislado promovible? (se decide al empezar la implementación).
- Presets de velocidad definitivos, lista de sitios y límite de sonidos simultáneos.
- Catálogo y licencias de sonido; ¿assets en el repo o descarga aparte?
- Herramienta del reproductor de pruebas.
