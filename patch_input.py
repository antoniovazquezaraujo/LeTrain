import sys

with open('src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java', 'r') as f:
    lines = f.readlines()

out = []
for i, line in enumerate(lines):
    if 'private int stationId = 0;' in line:
        out.append(line)
        out.append('    private int speedSignalId = 0;\n')
        out.append('    private long speedSignalInputTimeout = 0;\n')
    elif "case 's':" in line and 'if (!model.getSemaphores().isEmpty())' in lines[i+1]:
        out.append("                    case 'g':\n")
        out.append("                        if (!model.getSpeedSignals().isEmpty())\n")
        out.append("                            model.setMode(Model.GameMode.SPEED_SIGNALS);\n")
        out.append("                        return;\n")
        out.append(line)
    elif 'case SEMAPHORES:' in line:
        out.append(line)
        out.append('                handleSemaphoreInput(stroke);\n')
        out.append('                break;\n')
        out.append('            case SPEED_SIGNALS:\n')
        out.append('                handleSpeedSignalsInput(stroke);\n')
        out.append('                break;\n')
    elif 'handleSemaphoreInput(stroke);' in line and 'case SEMAPHORES:' in lines[i-1]:
        continue # handled above
    elif 'break;' in line and 'case SEMAPHORES:' in lines[i-2]:
        continue # handled above
    elif 'private void handleSemaphoreInput(KeyStroke keyEvent) {' in line:
        with open('patch_speed_signal_input.txt', 'r') as p:
            out.extend(p.readlines())
        out.append('\n')
        out.append(line)
    else:
        out.append(line)

with open('src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java', 'w') as f:
    f.writelines(out)
