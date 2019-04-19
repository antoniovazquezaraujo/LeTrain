package letrain.mvp;

import javafx.scene.paint.Color;
import letrain.map.Point;

public interface GameView {
    enum GameMode {
        NAVIGATE_MAP_COMMAND ("Navigate map"),
        CREATE_NORMAL_TRACKS_COMMAND("Create normal tracks"),
        CREATE_LOAD_PLATFORM_COMMAND("Create load platform"),
        CREATE_FACTORY_PLATFORM_COMMAND("Create factory platform"),
        REMOVE_TRACKS_COMMAND("Remove tracks"),
        USE_TRAINS_COMMAND("Use trains"),
        USE_FORKS_COMMAND("Use forks"),
        USE_LOAD_PLATFORMS_COMMAND("Use load platforms"),
        USE_FACTORY_PLATFORMS_COMMAND("Use factory platforms"),
        QUIT_COMMAN("Quit");

        private String name;

        private GameMode(String name) {
            this.name = name;
        }

        public String getName(){
            return name;
        }
    }
    Point getMapScrollPage();

    void setMapScrollPage(Point pos);

    void paint();

    void clear();

    void set(int x, int y, String c);

    void setColor(Color color);

    void setPageOfPos(int x, int y);

    void clear(int x, int y);

    void fill(int x, int y, int width, int height, String c);

    void box(int x, int y, int width, int height);
}
