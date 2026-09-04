import re

with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/Gdx3DRenderer.java', 'r') as f:
    content = f.read()

content = re.sub(r'\s*@Override\s*public void visitBridgeGateRailTrack\(BridgeGateRailTrack track\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitBridgeRailTrack\(BridgeRailTrack track\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitTunnelGateRailTrack\(TunnelGateRailTrack track\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitTunnelRailTrack\(TunnelRailTrack track\) \{.*?\}', '', content, flags=re.DOTALL)

# In Gdx3DRenderer around line 177:
# if (track.getClass().equals(TunnelGateRailTrack.class))
content = content.replace('track.getClass().equals(TunnelGateRailTrack.class)', 'track.getVisualType() == letrain.track.rail.RailTrack.VisualType.TUNNEL_GATE')

with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/Gdx3DRenderer.java', 'w') as f:
    f.write(content)

with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/BaseSubRenderer.java', 'r') as f:
    content = f.read()

content = re.sub(r'\s*@Override\s*public void visitBridgeGateRailTrack\(letrain\.track\.rail\.BridgeGateRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitBridgeRailTrack\(letrain\.track\.rail\.BridgeRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitTunnelGateRailTrack\(letrain\.track\.rail\.TunnelGateRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitTunnelRailTrack\(letrain\.track\.rail\.TunnelRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)

with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/BaseSubRenderer.java', 'w') as f:
    f.write(content)
