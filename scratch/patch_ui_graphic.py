import os, re

# For BaseSubRenderer
with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/BaseSubRenderer.java', 'r') as f:
    content = f.read()

content = re.sub(r'\s*@Override\s*public void visitBridgeGateRailTrack\(letrain\.track\.rail\.BridgeGateRailTrack \w+\) \{\}', '', content)
content = re.sub(r'\s*@Override\s*public void visitBridgeRailTrack\(letrain\.track\.rail\.BridgeRailTrack \w+\) \{\}', '', content)
content = re.sub(r'\s*@Override\s*public void visitTunnelGateRailTrack\(letrain\.track\.rail\.TunnelGateRailTrack \w+\) \{\}', '', content)
content = re.sub(r'\s*@Override\s*public void visitTunnelRailTrack\(letrain\.track\.rail\.TunnelRailTrack \w+\) \{\}', '', content)

with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/BaseSubRenderer.java', 'w') as f:
    f.write(content)


# For Gdx3DRenderer
with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/Gdx3DRenderer.java', 'r') as f:
    content = f.read()

content = re.sub(r'import letrain\.track\.rail\.BridgeGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.BridgeRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelRailTrack;\n', '', content)

content = re.sub(r'\s*@Override\s*public void visitBridgeGateRailTrack\(BridgeGateRailTrack track\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitBridgeRailTrack\(BridgeRailTrack track\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitTunnelGateRailTrack\(TunnelGateRailTrack track\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitTunnelRailTrack\(TunnelRailTrack track\) \{.*?\}', '', content, flags=re.DOTALL)

with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/Gdx3DRenderer.java', 'w') as f:
    f.write(content)


# For TrackRenderer
with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/TrackRenderer.java', 'r') as f:
    content = f.read()

content = re.sub(r'import letrain\.track\.rail\.BridgeGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.BridgeRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelRailTrack;\n', '', content)

content = re.sub(r'\s*@Override\s*public void visitBridgeGateRailTrack\(BridgeGateRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitBridgeRailTrack\(BridgeRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitTunnelGateRailTrack\(TunnelGateRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitTunnelRailTrack\(TunnelRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)

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
            // Graphic UI didn't render anything special for bridges, it just called visitRailTrack.
        } else if (track.getVisualType() == RailTrack.VisualType.TUNNEL || track.getVisualType() == RailTrack.VisualType.TUNNEL_GATE) {
            // Graphic UI didn't render anything special for tunnels, it just called visitRailTrack.
        }'''

content = content.replace(visit_rail_track_old, visit_rail_track_new)

with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/TrackRenderer.java', 'w') as f:
    f.write(content)


# For VehicleRenderer, InfrastructureRenderer, GroundRenderer
for renderer in ['VehicleRenderer.java', 'InfrastructureRenderer.java', 'GroundRenderer.java']:
    path = f'ui-graphic/src/main/java/letrain/visitor/gdx3d/{renderer}'
    with open(path, 'r') as f:
        content = f.read()
    
    content = re.sub(r'\s*@Override\s*public void visitBridgeGateRailTrack\(letrain\.track\.rail\.BridgeGateRailTrack \w+\) \{\}', '', content)
    content = re.sub(r'\s*@Override\s*public void visitBridgeRailTrack\(letrain\.track\.rail\.BridgeRailTrack \w+\) \{\}', '', content)
    content = re.sub(r'\s*@Override\s*public void visitTunnelGateRailTrack\(letrain\.track\.rail\.TunnelGateRailTrack \w+\) \{\}', '', content)
    content = re.sub(r'\s*@Override\s*public void visitTunnelRailTrack\(letrain\.track\.rail\.TunnelRailTrack \w+\) \{\}', '', content)
    
    with open(path, 'w') as f:
        f.write(content)

