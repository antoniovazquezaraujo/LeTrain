# Índice de Clases de LeTrain

Esta lista se genera automáticamente a partir de los archivos fuente presentes en el repositorio.

## Audio y Síntesis
- `letrain.audio.AudioController`
- `letrain.audio.core.AudioMixer`
- `letrain.audio.core.AudioSource`
- `letrain.audio.core.DistanceAttenuator`
- `letrain.audio.sources.AmbientSource`
- `letrain.audio.sources.SequencedAmbientSource`
- `letrain.audio.sources.WavSource`
- `letrain.audio.synth.AudioGenerator`
- `letrain.audio.synth.AudioSample`
- `letrain.audio.synth.GrainEngine`
- `letrain.audio.synth.NoiseGenerator`
- `letrain.audio.synth.Oscillator`
- `letrain.audio.synth.SpeedNotch`
- `letrain.audio.synth.TrainSynth`
- `letrain.audio.synth.TrainSynthesizer`
- `letrain.audio.util.AudacityLabelParser`

## Comandos y Automatización (ANTLR)
- `letrain.command.CommandManager`
- `letrain.mvp.impl.services.AutomationEngine`

## Economía
- `letrain.economy.EconomyManager`
- `letrain.economy.impl.EconomyManager`

## Terreno y Mapa
- `letrain.ground.Ground`
- `letrain.ground.GroundMap`
- `letrain.ground.NoiseGenerator`
- `letrain.ground.PerlinNoise`
- `letrain.ground.impl.GroundMap`
- `letrain.ground.impl.Terrain2D`
- `letrain.map.Dir`
- `letrain.map.DynamicRouter`
- `letrain.map.Mappable`
- `letrain.map.Page`
- `letrain.map.Point`
- `letrain.map.RailMap`
- `letrain.map.Reversible`
- `letrain.map.Rotatable`
- `letrain.map.Router`
- `letrain.map.impl.ForkRouter`
- `letrain.map.impl.RailMap`
- `letrain.map.impl.SimpleRouter`

## MVP (Model-View-Presenter)
- `letrain.mvp.GameViewListener`
- `letrain.mvp.Model`
- `letrain.mvp.Presenter`
- `letrain.mvp.View`
- `letrain.mvp.impl.EventLogManager`
- `letrain.mvp.impl.GameSaveService`
- `letrain.mvp.impl.Model`
- `letrain.mvp.impl.ModelMixin`
- `letrain.mvp.impl.RailTrackMaker`
- `letrain.mvp.impl.SimulationController`
- `letrain.mvp.impl.delegates.TrainFactory`
- `letrain.mvp.impl.graphic.CameraController`
- `letrain.mvp.impl.graphic.Gdx3DHud`
- `letrain.mvp.impl.graphic.Gdx3DInputHandler`
- `letrain.mvp.impl.graphic.GraphicPresenter`
- `letrain.mvp.impl.services.SimulationService`
- `letrain.mvp.impl.terminal.TerminalPresenter`
- `letrain.mvp.impl.terminal.TerminalView`

## Infraestructura y Vías
- `letrain.track.CargoTypes`
- `letrain.track.Connectable`
- `letrain.track.ForkEventListener`
- `letrain.track.Gate`
- `letrain.track.LinkerCompartment`
- `letrain.track.LinkerCompartmentListener`
- `letrain.track.RailSemaphore`
- `letrain.track.SemaphoreEventListener`
- `letrain.track.Sensor`
- `letrain.track.SensorEventListener`
- `letrain.track.Station`
- `letrain.track.StationEventListener`
- `letrain.track.Track`
- `letrain.track.TrackDirector`
- `letrain.track.Trackable`
- `letrain.track.rail.BridgeGateRailTrack`
- `letrain.track.rail.BridgeRailTrack`
- `letrain.track.rail.ForkRailTrack`
- `letrain.track.rail.RailTrack`
- `letrain.track.rail.StationRailTrack`
- `letrain.track.rail.TunnelGateRailTrack`
- `letrain.track.rail.TunnelRailTrack`

## Vehículos
- `letrain.vehicle.Destructible`
- `letrain.vehicle.Linkable`
- `letrain.vehicle.Selectable`
- `letrain.vehicle.Transportable`
- `letrain.vehicle.Vehicle`
- `letrain.vehicle.Cursor`
- `letrain.vehicle.rail.Linker`
- `letrain.vehicle.rail.RailIterator`
- `letrain.vehicle.Tracker`
- `letrain.vehicle.Tractor`
- `letrain.vehicle.rail.Trailer`
- `letrain.vehicle.rail.impl.Trip`
- `letrain.vehicle.rail.impl.Locomotive`
- `letrain.vehicle.rail.impl.Stop`
- `letrain.vehicle.rail.impl.Train`
- `letrain.vehicle.rail.TrainEventListener`
- `letrain.vehicle.rail.impl.Wagon`

## Visualización (Visitor)
- `letrain.visitor.Renderable`
- `letrain.visitor.Visitor`
- `letrain.visitor.gdx3d.BaseSubRenderer`
- `letrain.visitor.gdx3d.Gdx3DRenderer`
- `letrain.visitor.gdx3d.Gdx3DResourceContext`
- `letrain.visitor.gdx3d.GroundRenderer`
- `letrain.visitor.gdx3d.InfrastructureRenderer`
- `letrain.visitor.gdx3d.TrackRenderer`
- `letrain.visitor.gdx3d.VehicleRenderer`
- `letrain.visitor.terminal.InfoVisitor`
- `letrain.visitor.terminal.RenderVisitor`

## Utilidades
- `letrain.utils.FontManager`
- `letrain.utils.Pair`
- `letrain.utils.PathGeometry`
- `letrain.utils.SerializationHelper`
- `letrain.utils.SplinePath`
- `letrain.utils.ValidationUtils`
