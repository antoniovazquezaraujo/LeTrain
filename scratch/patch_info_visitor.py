with open('ui-terminal/src/main/java/letrain/visitor/terminal/InfoVisitor.java', 'r') as f:
    content = f.read()

import re

# Remove imports
content = re.sub(r'import letrain\.track\.rail\.BridgeGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.BridgeRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelRailTrack;\n', '', content)

# Remove specific visits
content = re.sub(r'\s*@Override\s*public void visitBridgeGateRailTrack\(letrain\.track\.rail\.BridgeGateRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitBridgeRailTrack\(letrain\.track\.rail\.BridgeRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitTunnelGateRailTrack\(letrain\.track\.rail\.TunnelGateRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitTunnelRailTrack\(letrain\.track\.rail\.TunnelRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)

with open('ui-terminal/src/main/java/letrain/visitor/terminal/InfoVisitor.java', 'w') as f:
    f.write(content)
