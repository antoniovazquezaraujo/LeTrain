import sys

with open('src/main/java/letrain/visitor/gdx3d/InfrastructureRenderer.java', 'r') as f:
    lines = f.readlines()

out = []
for line in lines:
    if 'angle = (float) Math.atan2(dx, dz) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;' in line:
        out.append('            angle = (float) Math.atan2(dx, dz) * com.badlogic.gdx.math.MathUtils.radiansToDegrees + 180f;\n')
    elif 'float labelOffsetX = PathGeometry.getDirX(creationDir) * 0.051f;' in line:
        out.append('        float labelOffsetX = -PathGeometry.getDirX(creationDir) * 0.051f;\n')
    elif 'float labelOffsetZ = PathGeometry.getDirZ(creationDir) * 0.051f;' in line:
        out.append('        float labelOffsetZ = -PathGeometry.getDirZ(creationDir) * 0.051f;\n')
    elif 'Vector3 labelNormal = new Vector3(PathGeometry.getDirX(creationDir), 0, PathGeometry.getDirZ(creationDir));' in line:
        out.append('        Vector3 labelNormal = new Vector3(-PathGeometry.getDirX(creationDir), 0, -PathGeometry.getDirZ(creationDir));\n')
    elif 'Color textColor = speedSignal.isMax() ? Color.WHITE : Color.WHITE;' in line:
        out.append('        Color textColor = com.badlogic.gdx.graphics.Color.BLACK;\n')
    else:
        out.append(line)

with open('src/main/java/letrain/visitor/gdx3d/InfrastructureRenderer.java', 'w') as f:
    f.writelines(out)
