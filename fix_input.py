with open('src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java', 'r') as f:
    lines = f.readlines()

out = []
skip = False
for line in lines:
    if 'handleSemaphoreInput(stroke);' in line and 'case SEMAPHORES:' not in ''.join(out[-2:]):
        # We are in handleSnapCursor, wait, handleSnapCursor doesn't have handleSemaphoreInput(stroke)
        pass
    if 'case SEMAPHORES:' in line and 'handleSnapCursor()' in ''.join(out[-15:]):
        out.append(line)
        skip = True # skip the next few lines that were added
    elif skip:
        if 'if (model.getSelectedSemaphore() != null) {' in line:
            skip = False
            out.append(line)
    else:
        out.append(line)

with open('src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java', 'w') as f:
    f.writelines(out)
