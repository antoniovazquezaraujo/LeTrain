package lab;

import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

public class View extends BorderPane {
    enum ViewCommand {
        NAVIGATE_MAP,
        CREATE_NORMAL_TRACKS,
        CREATE_LOAD_PLATFORM,
        CREATE_FACTORY_PLATFORM,
        REMOVE_TRACKS,
        USE_TRAINS,
        USE_FORKS,
        USE_LOAD_PLATFORMS,
        USE_FACTORY_PLATFORMS,
        QUIT
    }
    public View(){
        setRight(createMenu());
    }

    private Node createMenu() {
return null;
    }
}
