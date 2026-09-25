package letrain.segments;

import java.util.List;
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
}
