package letrain.track.rail;

/**
 * Legacy track type kept for savegame and scenario compatibility: old worlds can deserialize a
 * track whose {@code type} is {@code BridgeRailTrack} (registered in {@code Track}'s
 * {@code @JsonSubTypes}) and {@code TestCircuit1Trait} still builds one. New code should use
 * {@link RailTrack} with {@link VisualType#BRIDGE}; do not remove while old savegames/`.ltr`
 * scenarios must keep loading (#823).
 *
 * @deprecated use {@link RailTrack} with the visual type instead; kept for compatibility.
 */
@Deprecated
public class BridgeRailTrack extends RailTrack {
    public BridgeRailTrack() {
        setVisualType(VisualType.BRIDGE);
    }
}
