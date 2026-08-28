sed -i '44d' src/main/java/letrain/audio/core/AudioMixer.java
sed -i '83d' src/main/java/letrain/audio/core/AudioMixer.java
sed -i '34d' src/main/java/letrain/audio/synth/AudioSample.java
sed -i '13d' src/main/java/letrain/audio/synth/TrainSynth.java

sed -i '584,586d' src/main/java/letrain/audio/synth/TrainSynthesizer.java
sed -i '753,795d' src/main/java/letrain/audio/synth/TrainSynthesizer.java

sed -i '/private Segment lastSegment;/d' src/main/java/letrain/itinerary/impl/AutoPilotImpl.java
sed -i '/private TrainActionManager actionManager;/d' src/main/java/letrain/itinerary/impl/AutoPilotImpl.java
sed -i 's/this.actionManager = actionManager;//g' src/main/java/letrain/itinerary/impl/AutoPilotImpl.java
sed -i 's/this.actionManager = null;//g' src/main/java/letrain/itinerary/impl/AutoPilotImpl.java
sed -i 's/this.lastSegment = null;//g' src/main/java/letrain/itinerary/impl/AutoPilotImpl.java
sed -i '/lastSegment = null;/d' src/main/java/letrain/itinerary/impl/AutoPilotImpl.java
sed -i '/lastSegment = currentSeg;/d' src/main/java/letrain/itinerary/impl/AutoPilotImpl.java

sed -i '211,213d' src/main/java/letrain/mvp/impl/RailTrackMaker.java
sed -i '36d' src/main/java/letrain/mvp/impl/graphic/Gdx3DHud.java

sed -i '43d' src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java
sed -i '/speedSignalInputTimeout = 0;/d' src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java
sed -i '/speedSignalInputTimeout = System.currentTimeMillis/d' src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java

sed -i '255d' src/main/java/letrain/mvp/impl/terminal/TerminalPresenter.java
sed -i '81d' src/main/java/letrain/track/TrackDirector.java
sed -i '370d' src/main/java/letrain/vehicle/rail/impl/Locomotive.java

sed -i '140d;141d' src/main/java/letrain/vehicle/rail/impl/TrainLogisticsManager.java
sed -i '187,191d' src/main/java/letrain/vehicle/rail/impl/TrainLogisticsManager.java

