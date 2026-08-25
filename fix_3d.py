import glob

for filename in glob.glob("src/main/java/letrain/visitor/gdx3d/*Renderer.java"):
    with open(filename, 'r') as f:
        content = f.read()
    
    if "visitSpeedSignal" not in content:
        # insert before the last brace
        last_brace = content.rfind('}')
        if last_brace != -1:
            method = "\n    @Override\n    public void visitSpeedSignal(letrain.track.SpeedSignal speedSignal) {}\n"
            content = content[:last_brace] + method + content[last_brace:]
            with open(filename, 'w') as f:
                f.write(content)
