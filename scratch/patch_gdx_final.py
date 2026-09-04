import re

with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/Gdx3DRenderer.java', 'r') as f:
    content = f.read()

old_visit = '''    @Override
    public void visitRailTrack(RailTrack track) {
        trackRenderer.visitRailTrack(track);
    }'''
new_visit = '''    @Override
    public void visitRailTrack(RailTrack track) {
        if (track.getVisualType() == letrain.track.rail.RailTrack.VisualType.TUNNEL_GATE) {
            infrastructureRenderer.renderTunnelGateTrack(track);
        }
        trackRenderer.visitRailTrack(track);
    }'''
content = content.replace(old_visit, new_visit)

with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/Gdx3DRenderer.java', 'w') as f:
    f.write(content)


with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/InfrastructureRenderer.java', 'r') as f:
    content = f.read()

content = content.replace('import letrain.track.rail.TunnelGateRailTrack;', '')
content = content.replace('''    @Override
    public void visitTunnelGateRailTrack(
            letrain.track.rail.TunnelGateRailTrack tunnelGateRailTrack) {''', '''    public void renderTunnelGateTrack(
            letrain.track.rail.RailTrack tunnelGateRailTrack) {''')
content = content.replace('trackRenderer.visitRailTrack(tunnelGateRailTrack);', '')

with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/InfrastructureRenderer.java', 'w') as f:
    f.write(content)

