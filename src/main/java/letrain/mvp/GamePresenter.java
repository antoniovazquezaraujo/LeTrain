package letrain.mvp;

public interface GamePresenter extends GameViewListener {

    enum GameMode {
        NAVIGATE_MAP_COMMAND("Navigate map"),
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

        GameMode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    GameMode getMode();

    void setMode(GameMode mode);

    GameView getView();

    GameModel getModel();
}
