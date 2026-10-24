package letrain.track.rail;

/**
 * Legacy track type kept for savegame and scenario compatibility: old worlds can deserialize a
 * track whose {@code type} is {@code TunnelGateRailTrack} (registered in {@code Track}'s
 * {@code @JsonSubTypes}). New code should use {@link RailTrack} with
 * {@link VisualType#TUNNEL_GATE}; do not remove while old savegames/`.ltr` scenarios must keep
 * loading (#823).
 *
 * @deprecated use {@link RailTrack} with the visual type instead; kept for compatibility.
 */
@Deprecated
public class TunnelGateRailTrack extends RailTrack {
    public TunnelGateRailTrack() {
        setVisualType(VisualType.TUNNEL_GATE);
    }
}
