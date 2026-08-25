#!/bin/bash
sed -i '/private int stationId = 0;/a \    private int speedSignalId = 0;\n    private long speedSignalInputTimeout = 0;' src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java

sed -i "/case 's':/i \\                    case 'g':\n                        if (!model.getSpeedSignals().isEmpty())\n                            model.setMode(Model.GameMode.SPEED_SIGNALS);\n                        return;" src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java

sed -i '/case SEMAPHORES:/a \            case SPEED_SIGNALS:\n                handleSpeedSignalsInput(stroke);\n                break;' src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java

sed -i '/private void handleSemaphoreInput(KeyStroke keyEvent) {/r patch_speed_signal_input.txt' src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java
