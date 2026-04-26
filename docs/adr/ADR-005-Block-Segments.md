[ [Índice] ] [[docs/Index|⬅️ Volver al Índice]]

# ADR-005: Sistema de Seguridad por Segmentos (Block System v2)

## 1. El Problema del Modelo Actual (ADR-002)
El sistema actual protege la vía gestionando de forma independiente los "puertos" de entrada y salida de los nodos (`tryLockPort`).
*   **Problema:** Es topológicamente frágil. Permite bloqueos parciales (un tren reserva la salida de A pero no la entrada de B) y sufre de fallos graves en cruces y desvíos donde múltiples trenes pueden reservar diferentes "puertas" del mismo nodo, colisionando en el interior.

## 2. La Solución Propuesta: Segmentos Atómicos
La nueva arquitectura abandona los puertos individuales a favor de **Segmentos** (Blocks) como unidades atómicas de ocupación de la vía.

### 2.1 Definición de Segmento (`RailwaySegment`)
Un Segmento representa el tramo físico ininterrumpido de vía entre dos Nodos adyacentes (estaciones, semáforos, desvíos o topes).
*   **Composición:** Agrupa ambos sentidos de circulación (`RailNodeLink` A->B y `RailNodeLink` B->A).
*   **Estado:** Solo tiene un dueño físico (`owner: Train`).
*   **Atomicidad:** Si un tren entra en el segmento (desde cualquier dirección), **todo el segmento físico queda bloqueado** para cualquier otro tren en cualquier dirección.

### 2.2 Gestión de Ocupación (El Tren)
El `Train` o el `AutoPilot` ya no negocia puertos, sino que mantiene una **lista dinámica de Segmentos Ocupados**:
```java
// Estado interno del tren
List<RailwaySegment> occupiedSegments;
```
1.  **Entrada (Adquisición):** Cuando la locomotora (cabecera) avanza en dirección a un nuevo Segmento, este se añade a la lista y su `owner` pasa a ser el tren.
2.  **Salida (Liberación):** Cuando el último vagón (cola) abandona un Segmento completamente, este se elimina de la lista y su `owner` vuelve a `null`.
*   *Nota:* Esto soporta nativamente trenes más largos que un segmento, ya que la lista puede contener N segmentos simultáneamente.

### 2.3 Lógica de Movimiento (Look-Ahead Básico)
Para que un tren pueda avanzar dentro de su Segmento Actual ($S_{actual}$) hacia el Siguiente Segmento ($S_{siguiente}$) en su ruta:
1.  El `AutoPilot` consulta: `if (S_siguiente.isFree()) { ... }`.
2.  Si está libre, adquiere la ocupación de $S_{siguiente}$ y el tren avanza en ese sentido.
3.  **Regla de Alternativa (Dynamic Branching):** Si $S_{siguiente}$ está ocupado y el nodo actual es un desvío (Fork), el tren debe verificar si existe un segmento alternativo $S_{alt}$ que conecte **directamente** con el mismo nodo de destino que $S_{siguiente}$. Si $S_{alt}$ está libre, el tren cambiará su orientación hacia $S_{alt}$, lo bloqueará y avanzará por él.
4.  **Protocolo de Frenado y Reintento:** Si no hay vía libre (ni la principal ni la alternativa), el tren frena inmediatamente. Una vez detenido, el sistema reintentará la comprobación de bloqueo cada 15 segundos hasta que la vía quede libre.

## 3. Ventajas Arquitectónicas
1.  **Simplicidad:** Desaparece la micro-gestión de "puertos" y los rollbacks complejos de bloqueos parciales.
2.  **Seguridad Física Real:** Protege el espacio real de la vía de forma atómica (alcances traseros y choques frontales en el tramo son imposibles por definición).
3. En una vía de doble dirección, no se deben hacer varios segmentos seguidos sin apartadero, o los trenes se encontrarán frente a frente. Cada segmento largo debería estar flanqueado por apartaderos, de forma que los trenes nunca se bloqueen

## 4. Retos Abiertos (A Diseñar)
*   **Protección del Nodo (Cruces y Desvíos):** Si los segmentos acaban justo en el borde del nodo... ¿qué protege el interior físico de un cruce en X? (Quizás los Nodos complejos deban actuar como "Segmentos Especiales" que también se bloquean enteros al cruzarlos).

---
*Última actualización: 2026-04-22*