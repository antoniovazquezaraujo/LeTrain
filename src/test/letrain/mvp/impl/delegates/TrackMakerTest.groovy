package letrain.mvp.impl.delegates

import letrain.map.Dir
import letrain.map.Point
import letrain.map.RailMap
import letrain.map.SimpleRouter
import letrain.mvp.GameModel
import letrain.mvp.GamePresenter
import letrain.mvp.GameView
import letrain.track.Track
import letrain.track.TrackDirector
import letrain.track.rail.RailTrack
import letrain.track.road.RoadTrack
import letrain.vehicle.impl.Cursor
import spock.lang.*
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import static org.mockito.Mockito.*

class TrackMakerTest extends Specification {
    //Field newTrackType of type NewTrackType - was not mocked since Mockito doesn't mock enums
    @Mock
    Track oldTrack
    //Field oldDir of type Dir - was not mocked since Mockito doesn't mock enums
    //Field dir of type Dir - was not mocked since Mockito doesn't mock enums
    @Mock
    GameModel model
    @Mock
    GameView view
    @InjectMocks
    TrackMaker trackMaker

    def setup() {
        MockitoAnnotations.initMocks(this)
    }

    def "test on Game Mode Selected"() {
        given:
        when(model.getRailMap()).thenReturn(new RailMap())
        when(model.getCursor()).thenReturn(new Cursor(dir: Dir.E))

        when:
        trackMaker.onGameModeSelected(GamePresenter.GameMode.NAVIGATE_MAP_COMMAND)

        then:
        false//todo - validate something
    }

    def "test on Up"() {
        given:
        when(oldTrack.getRouter()).thenReturn(new SimpleRouter())
        when(oldTrack.connect(any(), any())).thenReturn(true)
        when(oldTrack.getPosition()).thenReturn(new Point(0, 0))
        when(oldTrack.getNumRoutes()).thenReturn(0)
        when(model.getRailMap()).thenReturn(new RailMap())
        when(model.getCursor()).thenReturn(new Cursor(dir: Dir.E))

        when:
        trackMaker.onUp()

        then:
        false//todo - validate something
    }

    def "test on Down"() {
        when:
        trackMaker.onDown()

        then:
        false//todo - validate something
    }

    def "test on Left"() {
        given:
        when(model.getCursor()).thenReturn(new Cursor(dir: Dir.E))

        when:
        trackMaker.onLeft()

        then:
        false//todo - validate something
    }

    def "test on Right"() {
        given:
        when(model.getCursor()).thenReturn(new Cursor(dir: Dir.E))

        when:
        trackMaker.onRight()

        then:
        false//todo - validate something
    }

    def "test on Char"() {
        when:
        trackMaker.onChar("c")

        then:
        false//todo - validate something
    }

    def "test select New Track Type"() {
        when:
        trackMaker.selectNewTrackType(TrackMaker.NewTrackType.NORMAL_TRACK)

        then:
        false//todo - validate something
    }

    def "test create Track Of Selected Type"() {
        when:
        RailTrack result = trackMaker.createTrackOfSelectedType()

        then:
        result == new RailTrack(trackDirector: new TrackDirector())
    }

    def "test can Be A Fork"() {
        given:
        when(oldTrack.getRouter()).thenReturn(new SimpleRouter())
        when(oldTrack.getNumRoutes()).thenReturn(0)

        when:
        boolean result = trackMaker.canBeAFork(new RoadTrack(), Dir.E, Dir.E)

        then:
        result == true
    }
}

//Generated with love by TestMe :) Please report issues and submit feature requests at: http://weirddev.com/forum#!/testme