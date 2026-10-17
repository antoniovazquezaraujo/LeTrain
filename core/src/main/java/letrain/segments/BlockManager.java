package letrain.segments;

import java.util.List;
import java.util.OptionalLong;
import letrain.vehicle.rail.impl.Train;

/** Gestor de seguridad y bloqueos por segmentos. Implementa las reglas definidas en el ADR-005. */
public interface BlockManager {
    /**
     * Intenta bloquear un segmento de forma exclusiva para un tren. Falla si el segmento ya tiene
     * dueño(s).
     *
     * @return true si se obtuvo el bloqueo, false en caso contrario.
     */
    boolean tryLock(Train train, Segment segment);

    /**
     * Registra la presencia física de un tren en un segmento sin comprobar exclusividad (ADR-022
     * phase 2f): al dividir un tren, las dos partes comparten cantón hasta que una lo abandone. El
     * segmento sigue ocupado para el resto de trenes.
     */
    void addOwner(Train train, Segment segment);

    /** Libera la propiedad de un segmento para un tren específico. */
    void release(Train train, Segment segment);

    /** Libera todos los segmentos que posee un tren. Útil en caso de destrucción total del tren. */
    void releaseAll(Train train);

    /** Devuelve la lista de trenes que poseen actualmente el segmento. */
    List<Train> getOwners(Segment segment);

    /** Limpia todos los bloqueos registrados (Protocolo Tabula Rasa). */
    void clearAll();

    /** Devuelve la lista de segmentos que posee actualmente un tren. */
    List<Segment> getOwnedSegments(Train train);

    /** Devuelve todos los segmentos que tienen algún bloqueo activo. */
    java.util.Set<Segment> getAllLockedSegments();

    /**
     * Pide el turno FIFO de un tren que empieza a esperar por un cantón (ADR-022 fase 2d). Los
     * turnos se asignan de forma monótona en orden de petición (determinista, sin reloj de pared),
     * de modo que ordenar a los que esperan por turno reproduce el orden de llegada. La petición es
     * idempotente mientras el tren sigue esperando: conserva el turno que ya tenía.
     *
     * @return el turno del tren (el recién asignado o el que ya tenía).
     */
    long requestWaitTurn(Train train);

    /** Devuelve el turno FIFO que tiene un tren, o vacío si no está esperando por un cantón. */
    OptionalLong getWaitTurn(Train train);

    /**
     * Limpia el turno FIFO de un tren que deja de esperar por un cantón (se resolvió o canceló la
     * espera, o el tren se destruyó). La siguiente espera pide un turno nuevo, posterior a todos
     * los anteriores.
     */
    void clearWaitTurn(Train train);
}
