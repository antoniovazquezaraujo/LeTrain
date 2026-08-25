with open('src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java', 'r') as f:
    text = f.read()

if 'private void handleSpeedSignalsInput' not in text:
    with open('patch_speed_signal_input.txt', 'r') as f2:
        method = f2.read()
    text = text.replace('private void handleSemaphoreInput(KeyStroke keyEvent) {', method + '\n    private void handleSemaphoreInput(KeyStroke keyEvent) {')
    
with open('src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java', 'w') as f:
    f.write(text)
