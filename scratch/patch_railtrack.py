with open('core/src/main/java/letrain/track/rail/RailTrack.java', 'r') as f:
    content = f.read()

import re

visual_type_code = '''
    public enum VisualType {
        NORMAL, STATION, TUNNEL, TUNNEL_GATE, BRIDGE, BRIDGE_GATE
    }
    
    private VisualType visualType = VisualType.NORMAL;
    private letrain.map.Dir creationDir = letrain.map.Dir.N;
    
    public VisualType getVisualType() { return visualType; }
    public void setVisualType(VisualType visualType) { this.visualType = visualType; }
    
    public letrain.map.Dir getCreationDir() { return creationDir; }
    public void setCreationDir(letrain.map.Dir creationDir) { this.creationDir = creationDir; }
'''

content = content.replace('public class RailTrack extends Track {', 'public class RailTrack extends Track {' + visual_type_code)

with open('core/src/main/java/letrain/track/rail/RailTrack.java', 'w') as f:
    f.write(content)
