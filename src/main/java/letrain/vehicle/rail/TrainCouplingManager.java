package letrain.vehicle.rail;

import letrain.map.Dir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

public interface TrainCouplingManager {
    Logger log = LoggerFactory.getLogger(letrain.vehicle.rail.impl.TrainCouplingManager.class);

    int getNumLinkersToJoin();

    int getNumLinkersToRemove();


    List<Linker> getSelectedLinkersToJoin()/*
     * - Vaciamos los linkersToJoin
     * - Si solicitan forwardDirection, lastLinker es getFirst(), si no es
     * getLast(), es decir, que vamos agregar linkers en ese sentido seleccionado.
     * - En dir ponemos la dirección de "salida" del tren, es decir, la que apuntará
     * a despegarse del tren. Pero ahí necesitamos saber si el tren está invertido o
     * no.
     * - Si el tren no está invertido, la dirección de salida del primer linker es
     * la correcta, pero la del último será la inversa de su track.
     * - Si el tren está invertido es lo contrario, la que hay que invertir es la
     * primera.
     */;


    void updateLinkersToJoin(boolean forwardDirection);

    void joinLinkers();

    void prepareLink(boolean forward, int count);

    void prepareUnlink(boolean forward, int count);

    void setFrontDivisionSense();

    void setBackDivisionSense();

    void resetUnlinkState();

    void resetLinkState();

    void selectNextDivisionLink();

    void selectPrevDivisionLink();

    void updateLinkersToRemove();

    void divideTrain(Supplier<Integer> nextTrainIdSupplier);

    List<Linker> destroyLinkers(Supplier<Integer> nextTrainIdSupplier);

    Dir getLinkDir(Linker linker);

    Linker getAdjacentLinker(Linker linker, Dir dir);
}
