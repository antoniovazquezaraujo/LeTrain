package letrain.vehicle.rail;

import letrain.segments.BlockManager;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interfaz para el gestor de seguridad y control de cantones (bloques) del tren.
 * Define el contrato para la reserva exclusiva de segmentos de vía, paradas preventivas y frenado de emergencia.
 */
public interface TrainSafetyManager {
    Logger log = LoggerFactory.getLogger(letrain.vehicle.rail.impl.TrainSafetyManager.class);

    /**
     * Determina si el tren tiene autorización de seguridad para moverse.
     * Un tren en modo manual siempre tiene permiso, mientras que en modo automático depende de la reserva del bloque.
     *
     * @return true si el tren puede avanzar, false en caso contrario.
     */
    boolean hasPermissionToMove();

    /**
     * Obtiene el segmento o cantón actual donde está ubicada la cabeza del tren.
     *
     * @return el Segment actual o null si no está asignado.
     */
    Segment getCurrentSegment();

    /**
     * Obtiene el siguiente segmento de vía que el tren pretende reservar y ocupar.
     *
     * @return el Segment siguiente o null si no se ha determinado o no se necesita reservar.
     */
    Segment getNextSegment();

    /**
     * Comprueba si el tren está detenido esperando a que se libere el siguiente segmento.
     *
     * @return true si está en espera preventiva de bloque, false en caso contrario.
     */
    boolean isWaitingForBlock();

    /**
     * Fuerza el restablecimiento y limpieza de las referencias a los segmentos actual y siguiente.
     */
    void forceSegmentReset();

    /**
     * Restablece el estado de seguridad de emergencia interno del gestor
     * en caso de invasión de vía o conflicto.
     */
    void onEmergencyStop();

    /**
     * Notificación cuando se inicia el frenado preventivo por bloque ocupado.
     * Guarda la velocidad objetivo que tenía el tren antes de frenar.
     *
     * @param speedToSave velocidad objetivo previa al frenado.
     */
    void onBrakingInitiated(int speedToSave);

    /**
     * Reclama y reserva todos los cantones físicamente ocupados por los vagones y locomotoras del tren.
     * Se utiliza típicamente en la inicialización o al cargar una partida.
     */
    void claimOccupiedSegments();

    /**
     * Intenta bloquear y reservar los cantones iniciales (actual y el siguiente) para iniciar la marcha de forma segura.
     */
    void acquireInitialLocks();

    /**
     * Notifica que la cabeza del tren ha entrado en una vía.
     * El SafetyManager determina si esto implica un cambio de segmento
     * y actúa en consecuencia (bloqueos, notificaciones).
     *
     * @param track la vía física en la que acaba de entrar la cabeza.
     */
    void onTrackEntered(Track track);

    /**
     * Evento reactivo que se dispara cuando el tren entra físicamente en un nuevo segmento.
     * Asegura la posesión del nuevo segmento y solicita la reserva del cantón posterior.
     *
     * @param newSegment el nuevo segmento al que se ha accedido.
     */
    void onSegmentEntered(Segment newSegment);

    /**
     * Reevalúa la disponibilidad del siguiente bloque cuando se libera un cantón y reanuda la marcha si es posible.
     */
    void onBlockReleased();

    /**
     * Evento reactivo que se dispara al cambiar el sentido de la marcha.
     * Libera el segmento objetivo anterior y calcula/reserva el nuevo segmento frontal.
     */
    void onReverse();

    /**
     * Determina cuál es el siguiente segmento al que se dirige el tren.
     * Considera la ruta planificada del piloto automático si está activa, o calcula el siguiente segmento topológico.
     *
     * @param head el vehículo en cabeza del tren.
     * @param graph el grafo ferroviario.
     * @return el siguiente segmento objetivo.
     */
    Segment findNextSegment(Linker head, RailwayGraph graph);

    /**
     * Libera del gestor de bloques todos aquellos segmentos que la cola del tren ya ha abandonado físicamente.
     *
     * @param bm el gestor de bloques (BlockManager).
     * @param graph el grafo ferroviario.
     */
    void releaseOldSegments(BlockManager bm, RailwayGraph graph);

    /**
     * Determina el siguiente segmento del tren basándose únicamente en la topología física de las vías,
     * siguiendo las agujas del desvío actual.
     *
     * @param head el vehículo en cabeza del tren.
     * @param graph el grafo ferroviario.
     * @return el segmento topológicamente adyacente en la dirección de la marcha.
     */
    Segment findNextSegmentTopological(Linker head, RailwayGraph graph);
}
