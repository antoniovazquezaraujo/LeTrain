# ADR-019: Descarrilamiento por curvas a alta velocidad (issue #350)

## Estado: PROPUESTO

## Contexto
Las señales de velocidad frenan al tren, pero nada castiga al jugador que vuelve a acelerar. Queremos que las curvas sean peligrosas a alta velocidad para dar propósito a las señales.

## Decisión
Regla simbólica, sin física fina:

- Un tren **descarrila al entrar en una curva** si a esa velocidad lleva demasiadas curvas seguidas: recuerda las últimas `N` piezas recorridas (`N` proporcional a su velocidad) y, si las curvas recientes en esa ventana superan lo tolerable a esa velocidad, descarrila.
- **A más velocidad, menos curvas seguidas tolera.** Por debajo de una velocidad mínima, nunca descarrila.
- Cruzar un **desvío** (`ForkRailTrack`) por encima de su velocidad límite descarrila siempre, aunque el recorrido sea recto.
- Unos pocos parámetros en `economy.properties` (velocidad mínima, ventana `N`, curvas toleradas, límite de desvío).

Notas: se evalúa solo al entrar en curvas y con la **velocidad efectiva** de ese instante (la señal frena; re-acelerar es lo que mata). El historial vive en el `SafetyManager` y se limpia al parar o invertir. Los valores se ajustan en balance con una pequeña tabla de casos de prueba (velocidad + curvas → ¿descarrila?).

## Fuera de alcance
Feedback visual/HUD y ayudas al construir tramos sinuosos: features independientes, se tratarían aparte.
