package letrain.construction;

import java.util.List;
import java.util.Set;
import letrain.construction.ConstructionCompany.Capability;
import letrain.construction.ConstructionCompany.Quality;

/**
 * Initial phase-2 contractor catalogue.
 *
 * <p>
 * Four deliberately extreme profiles establish the trade-offs before exact balancing: very
 * cheap/slow/low, cheap/fast/low, expensive/slow/high and very expensive/fast/high. The first two
 * cannot quote bridges or tunnels. Names and factors are provisional and can be tuned without
 * changing the quote model.
 */
public final class ConstructionCompanyCatalog {

    public static final ConstructionCompany CHAPUCERT =
            new ConstructionCompany("chapucert", "CHAPUCERT", 0.70, 1.80, Quality.LOW, Set.of());
    public static final ConstructionCompany RAPIDEX =
            new ConstructionCompany("rapidex", "RAPIDEX", 0.95, 0.70, Quality.LOW, Set.of());
    public static final ConstructionCompany ARTESANA = new ConstructionCompany("artesana",
            "ARTESANA", 1.60, 1.60, Quality.HIGH, Set.of(Capability.BRIDGE, Capability.TUNNEL));
    public static final ConstructionCompany IMPERIAL = new ConstructionCompany("imperial",
            "IMPERIAL", 2.50, 0.55, Quality.HIGH, Set.of(Capability.BRIDGE, Capability.TUNNEL));

    private static final List<ConstructionCompany> ALL =
            List.of(CHAPUCERT, RAPIDEX, ARTESANA, IMPERIAL);

    private ConstructionCompanyCatalog() {}

    /** Catalogue order is stable: it is the order shown in the quote dialog. */
    public static List<ConstructionCompany> all() {
        return ALL;
    }
}
