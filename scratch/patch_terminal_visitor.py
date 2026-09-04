with open('ui-terminal/src/main/java/letrain/visitor/terminal/RenderVisitor.java', 'r') as f:
    content = f.read()

import re

# Remove imports
content = re.sub(r'import letrain\.track\.rail\.BridgeGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.BridgeRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelGateRailTrack;\n', '', content)
content = re.sub(r'import letrain\.track\.rail\.TunnelRailTrack;\n', '', content)

# Remove the specific methods manually by knowing their signatures and structure
# or just splitting by lines. It's safer to split by lines or use a better regex!

# The methods are:
# public void visitBridgeRailTrack(BridgeRailTrack track)
# public void visitTunnelGateRailTrack(TunnelGateRailTrack track)
# public void visitTunnelRailTrack(TunnelRailTrack track)
# public void visitBridgeGateRailTrack(BridgeGateRailTrack track)

# They don't have nested braces (actually let's check).
