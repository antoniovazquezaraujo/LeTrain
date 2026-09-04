with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/Gdx3DRenderer.java', 'r') as f:
    content = f.read()

content = content.replace('rt instanceof TunnelGateRailTrack', 'rt != null && rt.getVisualType() == letrain.track.rail.RailTrack.VisualType.TUNNEL_GATE')

with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/Gdx3DRenderer.java', 'w') as f:
    f.write(content)
