with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/TrackRenderer.java', 'r') as f:
    content = f.read()

import re

# Remove imports
content = re.sub(r'import letrain\.track\.rail\.BridgeGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.BridgeRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelRailTrack;\n', '', content)

# Find and replace visitBridgeRailTrack and others, putting their logic into visitRailTrack
visit_rail_track_old = '''    @Override
    public void visitRailTrack(RailTrack track) {
        if (track == null || track.getPosition() == null) {
            return;
        }'''

visit_rail_track_new = '''    @Override
    public void visitRailTrack(RailTrack track) {
        if (track == null || track.getPosition() == null) {
            return;
        }
        if (track.getVisualType() == RailTrack.VisualType.BRIDGE || track.getVisualType() == RailTrack.VisualType.BRIDGE_GATE) {
            renderBridgeTrack(track);
            return;
        } else if (track.getVisualType() == RailTrack.VisualType.TUNNEL || track.getVisualType() == RailTrack.VisualType.TUNNEL_GATE) {
            renderTunnelTrack(track);
            return;
        }'''

content = content.replace(visit_rail_track_old, visit_rail_track_new)

# rename visitBridgeRailTrack to renderBridgeTrack
content = content.replace('public void visitBridgeRailTrack(letrain.track.rail.BridgeRailTrack bridgeRailTrack) {', 'private void renderBridgeTrack(RailTrack bridgeRailTrack) {')
content = content.replace('public void visitBridgeGateRailTrack(BridgeGateRailTrack bridgeGateRailTrack) {', 'private void renderBridgeGateTrack(RailTrack bridgeGateRailTrack) {')
content = content.replace('public void visitTunnelRailTrack(TunnelRailTrack tunnelRailTrack) {', 'private void renderTunnelTrack(RailTrack tunnelRailTrack) {')
content = content.replace('public void visitTunnelGateRailTrack(TunnelGateRailTrack tunnelGateRailTrack) {', 'private void renderTunnelGateTrack(RailTrack tunnelGateRailTrack) {')

# Also wait, did it have @Override annotations for them?
content = re.sub(r'@Override\s+private void renderBridgeTrack', 'private void renderBridgeTrack', content)
content = re.sub(r'@Override\s+private void renderBridgeGateTrack', 'private void renderBridgeGateTrack', content)
content = re.sub(r'@Override\s+private void renderTunnelTrack', 'private void renderTunnelTrack', content)
content = re.sub(r'@Override\s+private void renderTunnelGateTrack', 'private void renderTunnelGateTrack', content)

# Check if there are still any references
content = content.replace('BridgeRailTrack', 'RailTrack')
content = content.replace('BridgeGateRailTrack', 'RailTrack')
content = content.replace('TunnelRailTrack', 'RailTrack')
content = content.replace('TunnelGateRailTrack', 'RailTrack')

with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/TrackRenderer.java', 'w') as f:
    f.write(content)
