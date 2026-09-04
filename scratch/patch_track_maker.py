with open('core/src/main/java/letrain/mvp/impl/RailTrackMaker.java', 'r') as f:
    content = f.read()

old_if = '''                if (oldTrack.getClass().equals(letrain.track.rail.TunnelRailTrack.class)) {
                    convertOldTrackToGate(Presenter.TrackType.TUNNEL_GATE_TRACK);
                } else if (oldTrack.getClass().equals(letrain.track.rail.BridgeRailTrack.class)) {
                    convertOldTrackToGate(Presenter.TrackType.BRIDGE_GATE_TRACK);
                }'''
new_if = '''                if (oldTrack.getVisualType() == letrain.track.rail.RailTrack.VisualType.TUNNEL) {
                    convertOldTrackToGate(Presenter.TrackType.TUNNEL_GATE_TRACK);
                } else if (oldTrack.getVisualType() == letrain.track.rail.RailTrack.VisualType.BRIDGE) {
                    convertOldTrackToGate(Presenter.TrackType.BRIDGE_GATE_TRACK);
                }'''
content = content.replace(old_if, new_if)

old_convert = '''    private void convertOldTrackToGate(Presenter.TrackType gateType) {
        if (oldTrack == null) {
            return;
        }
        RailTrack newGate;
        if (gateType == Presenter.TrackType.TUNNEL_GATE_TRACK) {
            newGate = new letrain.track.rail.TunnelGateRailTrack();
        } else {
            newGate = new letrain.track.rail.BridgeGateRailTrack();
        }'''
new_convert = '''    private void convertOldTrackToGate(Presenter.TrackType gateType) {
        if (oldTrack == null) {
            return;
        }
        RailTrack newGate = new RailTrack();
        if (gateType == Presenter.TrackType.TUNNEL_GATE_TRACK) {
            newGate.setVisualType(letrain.track.rail.RailTrack.VisualType.TUNNEL_GATE);
        } else {
            newGate.setVisualType(letrain.track.rail.RailTrack.VisualType.BRIDGE_GATE);
        }'''
content = content.replace(old_convert, new_convert)

old_create = '''    public RailTrack createTrackOfSelectedType() {
        switch (newTrackType) {
            case STATION_TRACK:
                return new StationRailTrack();
            case TUNNEL_GATE_TRACK:
                return new TunnelGateRailTrack();
            case TUNNEL_TRACK:
                return new TunnelRailTrack();
            case BRIDGE_GATE_TRACK:
                return new BridgeGateRailTrack();
            case BRIDGE_TRACK:
                return new BridgeRailTrack();
            default:
                return new RailTrack();
        }
    }'''
new_create = '''    public RailTrack createTrackOfSelectedType() {
        RailTrack track = new RailTrack();
        switch (newTrackType) {
            case STATION_TRACK:
                track.setVisualType(letrain.track.rail.RailTrack.VisualType.STATION);
                break;
            case TUNNEL_GATE_TRACK:
                track.setVisualType(letrain.track.rail.RailTrack.VisualType.TUNNEL_GATE);
                break;
            case TUNNEL_TRACK:
                track.setVisualType(letrain.track.rail.RailTrack.VisualType.TUNNEL);
                break;
            case BRIDGE_GATE_TRACK:
                track.setVisualType(letrain.track.rail.RailTrack.VisualType.BRIDGE_GATE);
                break;
            case BRIDGE_TRACK:
                track.setVisualType(letrain.track.rail.RailTrack.VisualType.BRIDGE);
                break;
            default:
                break;
        }
        return track;
    }'''
content = content.replace(old_create, new_create)

import re
content = re.sub(r'import letrain\.track\.rail\.BridgeGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.BridgeRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.StationRailTrack;\n', '', content)

with open('core/src/main/java/letrain/mvp/impl/RailTrackMaker.java', 'w') as f:
    f.write(content)
