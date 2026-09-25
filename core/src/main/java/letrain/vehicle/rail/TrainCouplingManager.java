package letrain.vehicle.rail;

import java.util.List;
import java.util.function.Supplier;
import letrain.map.Dir;
import letrain.vehicle.rail.impl.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service providing stateless operations for coupling and uncoupling trains. Transient state is
 * stored directly in the Train instances.
 */
public interface TrainCouplingManager {
    Logger log = LoggerFactory.getLogger(letrain.vehicle.rail.impl.TrainCouplingManager.class);

    /**
     * Count meaning "every vehicle on that side" ({@code uncouple backward all}). The prepare
     * methods clamp it to the removable/linkable vehicles, so it can be passed through as-is.
     */
    int ALL = Integer.MAX_VALUE;

    List<Linker> getSelectedLinkersToJoin(Train train);

    boolean hasLinkableVehicles(Train train);

    void updateLinkersToJoin(Train train, boolean forwardDirection);

    void joinLinkers(Train train);

    void prepareLink(Train train, boolean forward, int count);

    void prepareUnlink(Train train, boolean forward, int count);

    void setFrontDivisionSense(Train train);

    void setBackDivisionSense(Train train);

    void resetUnlinkState(Train train);

    void resetLinkState(Train train);

    void selectNextDivisionLink(Train train);

    void selectPrevDivisionLink(Train train);

    void updateLinkersToRemove(Train train);

    void divideTrain(Train train, Supplier<Integer> nextTrainIdSupplier);

    List<Linker> destroyLinkers(Train train, Supplier<Integer> nextTrainIdSupplier);

    Linker getAdjacentLinker(Linker linker, Dir dir);
}
