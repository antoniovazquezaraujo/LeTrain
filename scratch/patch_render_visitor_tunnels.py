with open('ui-terminal/src/main/java/letrain/visitor/terminal/RenderVisitor.java', 'r') as f:
    content = f.read()

import re

old_loco = '''        if ((locomotive.getTrack().getClass().equals(TunnelRailTrack.class)
                || locomotive.getTrack().getClass().equals(TunnelGateRailTrack.class))
                && this.mode != GameMode.RAILS) {'''
new_loco = '''        if (locomotive.getTrack() != null && (locomotive.getTrack().getVisualType() == RailTrack.VisualType.TUNNEL
                || locomotive.getTrack().getVisualType() == RailTrack.VisualType.TUNNEL_GATE)
                && this.mode != GameMode.RAILS) {'''

content = content.replace(old_loco, new_loco)

old_wagon = '''        if ((wagon.getTrack().getClass().equals(TunnelRailTrack.class)
                || wagon.getTrack().getClass().equals(TunnelGateRailTrack.class))
                && this.mode != GameMode.RAILS) {'''
new_wagon = '''        if (wagon.getTrack() != null && (wagon.getTrack().getVisualType() == RailTrack.VisualType.TUNNEL
                || wagon.getTrack().getVisualType() == RailTrack.VisualType.TUNNEL_GATE)
                && this.mode != GameMode.RAILS) {'''

content = content.replace(old_wagon, new_wagon)

with open('ui-terminal/src/main/java/letrain/visitor/terminal/RenderVisitor.java', 'w') as f:
    f.write(content)
