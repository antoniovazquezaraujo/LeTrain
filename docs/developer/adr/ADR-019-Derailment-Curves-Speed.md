# ADR-019: Descarrilamiento por curvas demasiado seguidas (issue #350)

## Estado: APROBADO

## Contexto
Las señales de velocidad frenan al tren, pero nada castiga al jugador que vuelve a acelerar en conducción manual. Queremos que las curvas sean peligrosas a alta velocidad para dar propósito a las señales.

## Decisión
La magnitud relevante es el **tiempo que transcurre entre una curva y otra** (la velocidad ya queda codificada en ese tiempo). Regla:

- El tren anota el **instante en que atraviesa una curva**.
- Al entrar en la siguiente curva, si ha pasado **menos de `derail.minCurveInterval`** desde la anterior, **descarrila** (el tren entero, igual que un accidente).
- Las rectas entre curvas "dan tiempo"; la velocidad lo consume. No hace falta ninguna ventana ni factor artificial.

Detalles:
- **Solo se evalúa al entrar en una curva**, y con el **tiempo efectivo transcurrido** (ya incluye frenadas y aceleraciones de señales y del jugador). Por eso el caso "la señal me frena y luego re-acelero" queda cubierto de forma natural.
- **Velocidad mínima**: por debajo de `derail.minSpeed` (configurable) un tren **nunca descarrila**, aunque encadene curvas (a baja velocidad no se descarrila).
- Qué es una curva: una pieza cuyo paso cambia el rumbo (en este motor no existe un tipo `Curve`; se detecta porque el rumbo de salida difiere del de entrada, con los dirs que ya se calculan al mover el tren). Los **desvíos no son un caso especial**: un `ForkRailTrack` recorrido en recto es una recta más y un desvío desviado es una curva normal. El peligro es el cambio de rumbo, no el objeto "desvío".
- El único estado es el instante de la última curva; se resetea al parar o invertir la marcha. Marcha atrás usa la misma regla.
- Parámetros simbólicos en `economy.properties` (`derail.minCurveInterval`, `derail.minSpeed`), para afinar el balance sin recompilar.

## Consecuencias
- Las señales de velocidad ganan propósito mecánico: anticipar el frenado antes de tramos sinuosos o desvíos.
- Nuevo modo de destruir el tren (descarrilamiento), con su limpieza/economía.
- Sin cambios en la lógica de bloques/cantones ni loops de reserva.
- Tests deterministas: dos curvas muy seguidas en el tiempo (alta velocidad) → descarrila; la misma geometría a baja velocidad (≥ `minCurveInterval` entre curvas) → no; encadenar curvas por debajo de `minSpeed` → nunca; frenar tras una señal y no re-acelerar → nunca; re-acelerar tras la señal → descarrila; desvío en recto → no cuenta como curva; desvío desviado → cuenta como curva normal.

## Fuera de alcance (ideas futuras)
- Feedback visual/HUD y ayudas al construir tramos sinuosos: features independientes, se tratarían aparte.
