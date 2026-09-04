import re

# InfoVisitor
with open('ui-terminal/src/main/java/letrain/visitor/terminal/InfoVisitor.java', 'r') as f:
    content = f.read()

content = re.sub(r'\s*@Override\s*public void visitBridgeGateRailTrack\(BridgeGateRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitBridgeRailTrack\(BridgeRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitTunnelGateRailTrack\(TunnelGateRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Override\s*public void visitTunnelRailTrack\(TunnelRailTrack \w+\) \{.*?\}', '', content, flags=re.DOTALL)

with open('ui-terminal/src/main/java/letrain/visitor/terminal/InfoVisitor.java', 'w') as f:
    f.write(content)

# RenderVisitor
with open('ui-terminal/src/main/java/letrain/visitor/terminal/RenderVisitor.java', 'r') as f:
    content = f.read()

# loco
old_loco = '''        if (locomotive.getTrack() != null && (locomotive.getTrack().getVisualType() == RailTrack.VisualType.TUNNEL
                || locomotive.getTrack().getVisualType() == RailTrack.VisualType.TUNNEL_GATE)
                && this.mode != GameMode.RAILS) {'''
new_loco = '''        if (locomotive.getTrack() instanceof RailTrack && (((RailTrack)locomotive.getTrack()).getVisualType() == RailTrack.VisualType.TUNNEL
                || ((RailTrack)locomotive.getTrack()).getVisualType() == RailTrack.VisualType.TUNNEL_GATE)
                && this.mode != GameMode.RAILS) {'''
content = content.replace(old_loco, new_loco)

# wagon
old_wagon = '''        if (wagon.getTrack() != null && (wagon.getTrack().getVisualType() == RailTrack.VisualType.TUNNEL
                || wagon.getTrack().getVisualType() == RailTrack.VisualType.TUNNEL_GATE)
                && this.mode != GameMode.RAILS) {'''
new_wagon = '''        if (wagon.getTrack() instanceof RailTrack && (((RailTrack)wagon.getTrack()).getVisualType() == RailTrack.VisualType.TUNNEL
                || ((RailTrack)wagon.getTrack()).getVisualType() == RailTrack.VisualType.TUNNEL_GATE)
                && this.mode != GameMode.RAILS) {'''
content = content.replace(old_wagon, new_wagon)

with open('ui-terminal/src/main/java/letrain/visitor/terminal/RenderVisitor.java', 'w') as f:
    f.write(content)

