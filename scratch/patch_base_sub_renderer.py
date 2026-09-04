with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/BaseSubRenderer.java', 'r') as f:
    content = f.read()

import re

# Remove specific visits
content = re.sub(r'\s*@Override\s*public void visitBridgeGateRailTrack\(letrain\.track\.rail\.BridgeGateRailTrack \w+\) \{\}', '', content)
content = re.sub(r'\s*@Override\s*public void visitBridgeRailTrack\(letrain\.track\.rail\.BridgeRailTrack \w+\) \{\}', '', content)
content = re.sub(r'\s*@Override\s*public void visitTunnelGateRailTrack\(letrain\.track\.rail\.TunnelGateRailTrack \w+\) \{\}', '', content)
content = re.sub(r'\s*@Override\s*public void visitTunnelRailTrack\(letrain\.track\.rail\.TunnelRailTrack \w+\) \{\}', '', content)

with open('ui-graphic/src/main/java/letrain/visitor/gdx3d/BaseSubRenderer.java', 'w') as f:
    f.write(content)
