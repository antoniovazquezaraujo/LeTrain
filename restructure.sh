#!/bin/bash
set -e

# Core
mkdir -p core/src/main/java/letrain core/src/test/java/letrain core/src/main/resources core/src/main/antlr4
# Copy all of letrain to core, then remove the UI packages from core
cp -a src/main/java/letrain/* core/src/main/java/letrain/
rm -rf core/src/main/java/letrain/mvp/impl/graphic
rm -rf core/src/main/java/letrain/mvp/impl/terminal
rm -rf core/src/main/java/letrain/visitor/gdx3d
rm -rf core/src/main/java/letrain/visitor/terminal

# Core tests
cp -a src/test/java/letrain/* core/src/test/java/letrain/ 2>/dev/null || true
rm -rf core/src/test/java/letrain/mvp/impl/graphic 2>/dev/null || true
rm -rf core/src/test/java/letrain/mvp/impl/terminal 2>/dev/null || true
rm -rf core/src/test/java/letrain/visitor/gdx3d 2>/dev/null || true
rm -rf core/src/test/java/letrain/visitor/terminal 2>/dev/null || true

# Core resources & antlr
cp -a src/main/resources/* core/src/main/resources/
cp -a src/main/antlr4/* core/src/main/antlr4/

# Terminal UI
mkdir -p ui-terminal/src/main/java/letrain/mvp/impl/terminal ui-terminal/src/main/java/letrain/visitor/terminal
cp -a src/main/java/letrain/mvp/impl/terminal/* ui-terminal/src/main/java/letrain/mvp/impl/terminal/
cp -a src/main/java/letrain/visitor/terminal/* ui-terminal/src/main/java/letrain/visitor/terminal/

mkdir -p ui-terminal/src/test/java/letrain/mvp/impl/terminal ui-terminal/src/test/java/letrain/visitor/terminal
cp -a src/test/java/letrain/mvp/impl/terminal/* ui-terminal/src/test/java/letrain/mvp/impl/terminal/ 2>/dev/null || true
cp -a src/test/java/letrain/visitor/terminal/* ui-terminal/src/test/java/letrain/visitor/terminal/ 2>/dev/null || true

# Graphic UI
mkdir -p ui-graphic/src/main/java/letrain/mvp/impl/graphic ui-graphic/src/main/java/letrain/visitor/gdx3d
cp -a src/main/java/letrain/mvp/impl/graphic/* ui-graphic/src/main/java/letrain/mvp/impl/graphic/
cp -a src/main/java/letrain/visitor/gdx3d/* ui-graphic/src/main/java/letrain/visitor/gdx3d/

mkdir -p ui-graphic/src/test/java/letrain/mvp/impl/graphic ui-graphic/src/test/java/letrain/visitor/gdx3d
cp -a src/test/java/letrain/mvp/impl/graphic/* ui-graphic/src/test/java/letrain/mvp/impl/graphic/ 2>/dev/null || true
cp -a src/test/java/letrain/visitor/gdx3d/* ui-graphic/src/test/java/letrain/visitor/gdx3d/ 2>/dev/null || true

# Launchers
mkdir -p launcher-terminal/src/main/java/letrain
cat << 'MAINEOF' > launcher-terminal/src/main/java/letrain/LeTrainTerminal.java
package letrain;

import letrain.mvp.impl.Model;
import letrain.mvp.impl.terminal.TerminalPresenter;

public class LeTrainTerminal {
    public static void main(String[] args) {
        Model model = new Model();
        TerminalPresenter presenter = new TerminalPresenter(model);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (presenter != null) {
                presenter.stop();
            }
        }));
        presenter.start();
        presenter.stop();
        System.exit(0);
    }
}
MAINEOF

mkdir -p launcher-graphic/src/main/java/letrain
cat << 'MAINEOF' > launcher-graphic/src/main/java/letrain/LeTrainGraphic.java
package letrain;

import letrain.mvp.impl.Model;
import letrain.mvp.impl.graphic.GraphicPresenter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;

public class LeTrainGraphic {
    public static void main(String[] args) {
        Model model = new Model();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("LeTrain 3D - Wooden Edition");
        config.setMaximized(true);
        GraphicPresenter view3D = new GraphicPresenter(model);
        new Lwjgl3Application(view3D, config);
    }
}
MAINEOF

# Remove LeTrain.java from core since it's split
rm -f core/src/main/java/letrain/LeTrain.java

# Remove old src directory to track progress (git rm)
git rm -r src
