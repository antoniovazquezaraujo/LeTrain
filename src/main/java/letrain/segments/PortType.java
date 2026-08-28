package letrain.segments;

public enum PortType {
    TRUNK, // El tronco común / entrada principal (o el único extremo de un DeadEnd)
    A, // La rama A de un desvío (ruta por defecto)
    B // La rama B de un desvío (ruta alternativa)
}
