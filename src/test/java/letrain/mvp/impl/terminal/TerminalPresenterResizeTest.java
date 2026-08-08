package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import letrain.map.Page;
import letrain.map.Point;
import letrain.mvp.Model.GameMode;
import letrain.mvp.impl.Model;
import letrain.track.RailSemaphore;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TerminalPresenterResizeTest {

    @Test
    @DisplayName("onScreenResized should center view on cursor position in RAILS mode")
    void should_UpdateMapPageToContainCursor_When_ScreenResized() {
        Model model = new Model();
        model.setMode(GameMode.RAILS);
        model.getCursor().setPosition(new Point(150, 60));

        TerminalPresenter presenter = new TerminalPresenter(model);
        TerminalView view = mock(TerminalView.class);
        when(view.getScrollOffset()).thenReturn(new Point(70, 40));
        presenter.view = view;

        Page.setWidth(160);
        Page.setHeight(40);

        presenter.onScreenResized(160, 40);

        verify(view).centerOn(150, 60);
    }

    @Test
    @DisplayName("onScreenResized should center view on selected locomotive in DRIVE mode")
    void should_UpdateMapPageToContainSelectedLocomotive_When_ScreenResizedInDriveMode() {
        Model model = new Model();
        model.setMode(GameMode.DRIVE);
        Locomotive loco = mock(Locomotive.class);
        when(loco.getPosition()).thenReturn(new Point(250, 180));
        model.getLocomotives().add(loco);
        model.setSelectedLocomotive(loco);

        TerminalPresenter presenter = new TerminalPresenter(model);
        TerminalView view = mock(TerminalView.class);
        when(view.getScrollOffset()).thenReturn(new Point(200, 165));
        presenter.view = view;

        Page.setWidth(100);
        Page.setHeight(30);

        presenter.onScreenResized(100, 30);

        verify(view).centerOn(250, 180);
    }

    @Test
    @DisplayName("getActiveFocusPoint should return the correct point for each mode")
    void should_ReturnActiveFocusPoint_AccordingToGameMode() {
        Model model = new Model();
        TerminalPresenter presenter = new TerminalPresenter(model);

        // 1. DRIVE mode with selected locomotive
        Locomotive loco = mock(Locomotive.class);
        when(loco.getPosition()).thenReturn(new Point(10, 20));
        model.setSelectedLocomotive(loco);
        model.setMode(GameMode.DRIVE);
        assertEquals(new Point(10, 20), presenter.getActiveFocusPoint());

        // 2. FORKS mode with selected fork
        ForkRailTrack fork = mock(ForkRailTrack.class);
        when(fork.getPosition()).thenReturn(new Point(30, 40));
        model.setSelectedFork(fork);
        model.setMode(GameMode.FORKS);
        assertEquals(new Point(30, 40), presenter.getActiveFocusPoint());

        // 3. SEMAPHORES mode with selected semaphore
        RailSemaphore semaphore = mock(RailSemaphore.class);
        when(semaphore.getPosition()).thenReturn(new Point(50, 60));
        model.setSelectedSemaphore(semaphore);
        model.setMode(GameMode.SEMAPHORES);
        assertEquals(new Point(50, 60), presenter.getActiveFocusPoint());

        // 4. STATIONS mode with selected station
        Station station = mock(Station.class);
        when(station.getPosition()).thenReturn(new Point(70, 80));
        model.setSelectedStation(station);
        model.setMode(GameMode.STATIONS);
        assertEquals(new Point(70, 80), presenter.getActiveFocusPoint());

        // 5. RAILS mode with cursor
        model.getCursor().setPosition(new Point(90, 100));
        model.setMode(GameMode.RAILS);
        assertEquals(new Point(90, 100), presenter.getActiveFocusPoint());
    }
}
