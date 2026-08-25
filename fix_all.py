import sys

# Fix Gdx3DInputHandler
with open('src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java', 'r') as f:
    text = f.read()

text = text.replace('handleSpeedSignalsInput(stroke);', 'handleSpeedSignalsInput(stroke);')
text = text.replace('private void handleSpeedSignalsInput(KeyStroke stroke)', 'private void handleSpeedSignalsInput(KeyStroke stroke)')

# wait, the error was: "method handleSpeedSignalsInput(KeyStroke) cannot find symbol", and "variable stroke cannot find symbol" at 376. 
# let's write exactly what it should be.
