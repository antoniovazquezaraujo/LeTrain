# 🚉 Informe de Viabilidad y Diseño: Trenes de Pasajeros en LeTrain

El ecosistema actual de LeTrain está fuertemente centrado en la minería, la logística de mercancías pesadas (carbón, oro, rubíes) y la gestión de recursos estáticos. 

Introducir **trenes de pasajeros** no es solo añadir un tipo de vagón; es un cambio de paradigma que abriría la puerta a un bucle de jugabilidad completamente nuevo, pero que mantendrá la elegancia y simplicidad del modelo de mercancías gracias a las siguientes mecánicas clave ("Antonicismos").

---

## 1. El Paradigma del Pasajero (Filosofía de Diseño)

En lugar de simular miles de pasajeros individuales con rutas calculadas por A* (lo cual hundiría el rendimiento del motor), el sistema tratará a las poblaciones como nodos de intercambio dinámico.

*   **Generación de Ciudades:** Al igual que las zonas de producción, el mapa generará ciudades procedurales compuestas por bloques de edificios. El tamaño de la ciudad determinará su capacidad de generar ingresos.
*   **El Intercambio (Carga/Descarga abstracta):** Cuando un tren para en una ciudad, no contabilizamos "quién" sube o baja. Simplemente se produce un "intercambio de pasajeros" que inyecta dinero en la cuenta del jugador en función del volumen de la ciudad.
*   **La Mecánica Estrella: PUNTUALIDAD:** La rentabilidad del intercambio se multiplica por la **puntualidad** del tren. 
    *   *¿Cómo se mide?* Registrando la **varianza del tiempo (delta)** entre las visitas de un tren a la misma estación.
    *   Si un tren pasa cada 46 minutos constantes, su puntualidad (y multiplicador de ingresos) será máxima. 
    *   Si el tren se atasca en la red logística y los tiempos fluctúan (10 min, luego 30, luego 14), la puntualidad se desploma. Esto obliga al jugador a diseñar circuitos limpios y eficientes.

---

## 2. Modelos, Estética y Sonido

Para diferenciar inmediatamente el tráfico pesado del transporte de personas:

*   **Identidad Visual:** Los trenes de pasajeros serán **blancos**.
*   **Locomotoras Aerodinámicas:** El frontal del tren bala tendrá un **biselado** para dar sensación de alta velocidad.
*   **Vagones Dinámicos (3D):** Los vagones serán tan altos como la locomotora. En su franja superior tendrán una fila de ventanitas que se verán blancas cuando el tren vaya vacío, y se llenarán de **colores** cuando lleve pasajeros (indicador visual inmediato de carga).
*   **Representación en 2D (Terminal):** Las letras de los vagones irán **tachadas** o con el color invertido para indicar que están cargados.
*   **Audio Inmersivo:** Se añadirá al `AudioMixer` un sonido ambiente específico para las estaciones y un murmullo de gente al efectuar el intercambio de pasajeros.

---

## 3. Composición de Trenes y Físicas

Los trenes de pasajeros introducen nuevos retos de acople y velocidad:

*   **Doble Composición (Formación AVE):** Para solucionar el problema de invertir la marcha con un morro biselado, los trenes de pasajeros se construirán con **dos locomotoras**, una en cada extremo (cabeza y cola).
*   **Restricciones de Acople Físico:** Las locomotoras de alta velocidad tendrán su enganche "escondido" en el morro. En el juego, **solo podrán engancharse a vagones por su parte plana trasera**. Sin embargo, se permitirá enganchar dos "morros" entre sí para crear dobles composiciones realistas.
*   **Trenes Mixtos y Velocidad:** ¿Se puede enganchar un vagón de carbón a un tren bala? Sí, hay libertad. Pero el tren pasará a tener la **velocidad máxima del eslabón más lento**. Mezclar mercancías con pasajeros hundirá la puntualidad del tren, penalizando la práctica de forma orgánica.

---

## 4. UX y el Ilusionismo de la Inversión de Marcha

Cuando un tren con dos locomotoras invierte su marcha, la locomotora de "cola" pasa a ser la cabeza. Esto genera un desafío de usabilidad (UX).

*   **El Problema:** Si el jugador tiene seleccionada la locomotora delantera (ej. ID 2) y da la vuelta, la cámara y la línea verde se quedarían en la parte trasera del tren.
*   **La Solución Visual:** 
    1. Unificaremos los IDs visibles. El renderizador 3D y la UI dejarán de mostrar el ID de la locomotora concreta y **mostrarán el ID del Tren completo** (ej. "Tren 5") para todos sus eslabones.
    2. Al invertir la marcha (`train.reverse()`), si el jugador tenía seleccionada la locomotora de cabeza, el juego **saltará la selección automáticamente a la nueva locomotora de cabeza**.
    3. Como ambas locomotoras muestran "Tren 5" y la línea verde salta instantáneamente al nuevo frente, el jugador sentirá que simplemente ha cambiado de cabina, manteniendo intacta la ilusión de conducir un único vehículo coordinado.

---

## Hoja de Ruta Propuesta (Cuándo empecemos)

1. **Fase 1 (El Mundo):** Renderizado de Ciudades procedurales, modelos 3D del Tren Bala (bisel y ventanas). Restricciones de velocidad según el tren.
2. **Fase 2 (UX & Lógica):** Solucionar el problema de selección (Train ID vs Loco ID) al invertir trenes de doble composición. Añadir sonidos.
3. **Fase 3 (Gameplay):** La mecánica de "Intercambio de Pasajeros" y el cálculo del multiplicador de "Puntualidad" midiendo deltas de tiempo.
