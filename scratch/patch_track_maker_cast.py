with open('core/src/main/java/letrain/mvp/impl/RailTrackMaker.java', 'r') as f:
    content = f.read()

old_if = '''                if (oldTrack.getVisualType() == letrain.track.rail.RailTrack.VisualType.TUNNEL) {
                    convertOldTrackToGate(Presenter.TrackType.TUNNEL_GATE_TRACK);
                } else if (oldTrack.getVisualType() == letrain.track.rail.RailTrack.VisualType.BRIDGE) {
                    convertOldTrackToGate(Presenter.TrackType.BRIDGE_GATE_TRACK);
                }'''
new_if = '''                if (oldTrack instanceof letrain.track.rail.RailTrack && ((letrain.track.rail.RailTrack)oldTrack).getVisualType() == letrain.track.rail.RailTrack.VisualType.TUNNEL) {
                    convertOldTrackToGate(Presenter.TrackType.TUNNEL_GATE_TRACK);
                } else if (oldTrack instanceof letrain.track.rail.RailTrack && ((letrain.track.rail.RailTrack)oldTrack).getVisualType() == letrain.track.rail.RailTrack.VisualType.BRIDGE) {
                    convertOldTrackToGate(Presenter.TrackType.BRIDGE_GATE_TRACK);
                }'''
content = content.replace(old_if, new_if)

with open('core/src/main/java/letrain/mvp/impl/RailTrackMaker.java', 'w') as f:
    f.write(content)
