package letrain.segments;

import java.util.List;

import letrain.vehicle.impl.rail.Train;

/**
 * Gestor de seguridad y bloqueos por segmentos.
 * Implementa las reglas definidas en el ADR-005.
 */
public interface BlockManager {
    /**
     * Intenta bloquear un segmento de forma exclusiva para un tren.
     * Falla si el segmento ya tiene dueño(s).
     * @return true si se obtuvo el bloqueo, false en caso contrario.
     */
    boolean tryLock(Train train, Segment segment);

    /**
     * Intenta bloquear un segmento en modo Maniobra (Shunting).
     * Permite la propiedad compartida si el segmento ya está ocupado.
     * @return true si se permitió el acceso, false si hay impedimentos físicos (movimiento).
     */
    boolean tryShuntingLock(Train train, Segment segment);

    /**
     * Libera la propiedad de un segmento para un tren específico.
     */
    void release(Train train, Segment segment);

    /**
     * Libera todos los segmentos que posee un tren.
     * Útil en caso de destrucción total del tren.
     */
    void releaseAll(Train train);

    /**
     * Devuelve la lista de trenes que poseen actualmente el segmento.
     */
    List<Train> getOwners(Segment segment);

    /**
     * Valida si un tren puede abandonar el modo Shunting.
     * Según ADR-005: solo si es el único dueño de todos sus segmentos ocupados.
     */
    boolean canExitShunting(Train train);

    /**
     * Limpia todos los bloqueos registrados (Protocolo Tabula Rasa).
     */
    void clearAll();

    /**
     * Devuelve la lista de segmentos que posee actualmente un tren.
     */
    List<Segment> getOwnedSegments(Train train);

    /**
     * Devuelve todos los segmentos que tienen algún bloqueo activo.
     */
    java.util.Set<Segment> getAllLockedSegments();
}
