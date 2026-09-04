with open('core/src/main/java/letrain/visitor/Visitor.java', 'r') as f:
    content = f.read()

import re

content = re.sub(r'import letrain\.track\.rail\.BridgeGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.BridgeRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelRailTrack;\n', '', content)

content = re.sub(r'\s*void visitBridgeGateRailTrack\(BridgeGateRailTrack \w+\);', '', content)
content = re.sub(r'\s*void visitBridgeRailTrack\(BridgeRailTrack \w+\);', '', content)
content = re.sub(r'\s*void visitTunnelGateRailTrack\(TunnelGateRailTrack \w+\);', '', content)
content = re.sub(r'\s*void visitTunnelRailTrack\(TunnelRailTrack \w+\);', '', content)

with open('core/src/main/java/letrain/visitor/Visitor.java', 'w') as f:
    f.write(content)
