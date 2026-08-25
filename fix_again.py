import sys

with open('src/main/java/letrain/visitor/gdx3d/InfrastructureRenderer.java', 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if 'angle = (float) Math.atan2(dx, dz) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;' in line:
        if 'visitSemaphore' in ''.join(lines[i-20:i]):
            lines[i] = '            angle = (float) Math.atan2(dx, dz) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;\n'
        else:
            lines[i] = '        float angle = (float) Math.atan2(dx, dz) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;\n'

with open('src/main/java/letrain/visitor/gdx3d/InfrastructureRenderer.java', 'w') as f:
    f.writelines(lines)
