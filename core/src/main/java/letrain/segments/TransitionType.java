package letrain.segments;

public enum TransitionType {
    DIVERGING, // Entrada por tronco (TRUNK) hacia una de las ramas (A o B)
    CONVERGING, // Entrada por rama (A o B) saliendo hacia el tronco (TRUNK)
    BLOCKED // Paso inválido (ej: rama A a rama B, o cualquier paso en DeadEnd)
}
