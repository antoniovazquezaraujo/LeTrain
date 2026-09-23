package letrain.visitor.terminal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TextColor;
import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.impl.terminal.TerminalView;
import letrain.segments.BlockManager;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenderVisitorTest {

    @Test
    @DisplayName("visitRailTrack should paint rail with locomotive color when segment is locked by a train")
    void visitRailTrack_shouldPaintWithLocomotiveColor_whenSegmentIsLocked() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor = new RenderVisitor(view);

        Model model = mock(Model.class);
        RailwayGraph graph = mock(RailwayGraph.class);
        BlockManager blockManager = mock(BlockManager.class);
        Segment segment = mock(Segment.class);
        Train train = mock(Train.class);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        when(train.getDirectorLinker()).thenReturn(loco);

        RailTrack track = new RailTrack();
        track.setPosition(new Point(5, 5));

        when(model.getRailwayGraph()).thenReturn(graph);
        when(model.getBlockManager()).thenReturn(blockManager);
        when(graph.getSegment(track)).thenReturn(segment);
        when(blockManager.getOwners(segment)).thenReturn(List.of(train));

        // Inject model into visitor
        visitor.visitModel(model);

        // Render the track
        visitor.visitRailTrack(track);

        // Verify that view.setFgColor was called with GREEN_BRIGHT
        verify(view, atLeastOnce()).setFgColor(TextColor.ANSI.GREEN_BRIGHT);
    }

    @Test
    @DisplayName("visitRailTrack should paint rail with standard color when segment is not locked")
    void visitRailTrack_shouldPaintWithStandardColor_whenSegmentIsNotLocked() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor =
                new RenderVisitor(view, new TerminalPalette(TerminalPalette.Depth.TRUECOLOR));

        Model model = mock(Model.class);
        RailwayGraph graph = mock(RailwayGraph.class);
        BlockManager blockManager = mock(BlockManager.class);
        Segment segment = mock(Segment.class);

        RailTrack track = new RailTrack();
        track.setPosition(new Point(5, 5));

        when(model.getRailwayGraph()).thenReturn(graph);
        when(model.getBlockManager()).thenReturn(blockManager);
        when(graph.getSegment(track)).thenReturn(segment);
        when(blockManager.getOwners(segment)).thenReturn(List.of());

        visitor.visitModel(model);
        visitor.visitRailTrack(track);

        // day rail token of the light family
        verify(view, atLeastOnce()).setFgColor(new TextColor.RGB(50, 50, 55));
    }

    @Test
    @DisplayName("visitLocomotive should paint locomotive with its assigned color")
    void visitLocomotive_shouldPaintLocomotiveWithAssignedColor() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor = new RenderVisitor(view);

        Model model = mock(Model.class);
        Locomotive loco = new Locomotive(1, "A", "CYAN_BRIGHT");
        RailTrack track = new RailTrack();
        track.setPosition(new Point(10, 10));
        loco.setTrack(track);
        loco.setPosition(new Point(10, 10));

        visitor.visitModel(model);
        visitor.visitLocomotive(loco);

        verify(view, atLeastOnce()).setFgColor(TextColor.ANSI.CYAN_BRIGHT);
    }

    @Test
    @DisplayName("visitRailTrack does not draw tunnel rail outside Rails mode")
    void visitRailTrack_shouldNotDrawTunnelRail_whenNotInRailsMode() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor = new RenderVisitor(view);

        Model model = mock(Model.class);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.DRIVE);

        RailTrack track = new RailTrack();
        track.setPosition(new Point(3, 3));
        track.setVisualType(RailTrack.VisualType.TUNNEL);

        visitor.visitModel(model);
        visitor.visitRailTrack(track);

        verify(view, never()).set(anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("visitRailTrack draws tunnel rail in Rails mode")
    void visitRailTrack_shouldDrawTunnelRail_inRailsMode() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor = new RenderVisitor(view);

        Model model = mock(Model.class);
        RailwayGraph graph = mock(RailwayGraph.class);
        BlockManager blockManager = mock(BlockManager.class);
        Segment segment = mock(Segment.class);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.RAILS);
        when(model.getRailwayGraph()).thenReturn(graph);
        when(model.getBlockManager()).thenReturn(blockManager);

        RailTrack track = new RailTrack();
        track.setPosition(new Point(3, 3));
        track.setVisualType(RailTrack.VisualType.TUNNEL);
        when(graph.getSegment(track)).thenReturn(segment);
        when(blockManager.getOwners(segment)).thenReturn(List.of());

        visitor.visitModel(model);
        visitor.visitRailTrack(track);

        verify(view, atLeastOnce()).set(eq(3), eq(3), anyString());
    }

    @Test
    @DisplayName("visitRailTrack keeps tunnel gates visible outside Rails mode")
    void visitRailTrack_shouldDrawTunnelGate_whenNotInRailsMode() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor = new RenderVisitor(view);

        Model model = mock(Model.class);
        RailwayGraph graph = mock(RailwayGraph.class);
        BlockManager blockManager = mock(BlockManager.class);
        Segment segment = mock(Segment.class);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.DRIVE);
        when(model.getRailwayGraph()).thenReturn(graph);
        when(model.getBlockManager()).thenReturn(blockManager);

        RailTrack track = new RailTrack();
        track.setPosition(new Point(3, 3));
        track.setVisualType(RailTrack.VisualType.TUNNEL_GATE);
        when(graph.getSegment(track)).thenReturn(segment);
        when(blockManager.getOwners(segment)).thenReturn(List.of());

        visitor.visitModel(model);
        visitor.visitRailTrack(track);

        verify(view, atLeastOnce()).set(eq(3), eq(3), anyString());
    }

    @Test
    @DisplayName("the rail token follows the clock into the night palette")
    void visitRailTrack_shouldUseNightColor_whenClockIsNight() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor =
                new RenderVisitor(view, new TerminalPalette(TerminalPalette.Depth.TRUECOLOR));

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        RailwayGraph graph = mock(RailwayGraph.class);
        BlockManager blockManager = mock(BlockManager.class);
        Segment segment = mock(Segment.class);

        RailTrack track = new RailTrack();
        track.setPosition(new Point(5, 5));

        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(1f);
        when(model.getRailwayGraph()).thenReturn(graph);
        when(model.getBlockManager()).thenReturn(blockManager);
        when(graph.getSegment(track)).thenReturn(segment);
        when(blockManager.getOwners(segment)).thenReturn(List.of());

        visitor.visitModel(model);
        visitor.visitRailTrack(track);

        verify(view, atLeastOnce()).setFgColor(new TextColor.RGB(150, 150, 160));
    }

    @Test
    @DisplayName("the visitor resolves the banded palette, not the drifting clock ratio")
    void visitModel_shouldUseBandedPalette() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(0.30f);
        visitor.visitModel(model);

        // 0.30 belongs to the band the stepper picks
        float band = TerminalPalette.band(0.30f, -1f);
        TextColor expected =
                palette.colorOf(TerminalPalette.rgbFor(band).get(TerminalPalette.Token.RAIL));
        visitor.visitRailTrack(new RailTrack());
        verify(view, atLeastOnce()).setFgColor(expected);
    }

    @Test
    @DisplayName("at night a locomotive casts a headlight beam on the rail ahead")
    void visitModel_shouldCastHeadlightBeam_atNight() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(1f);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        RailTrack locoTrack = new RailTrack();
        locoTrack.setPosition(new Point(5, 5));
        loco.setTrack(locoTrack);
        loco.setPosition(new Point(5, 5));
        loco.setDir(Dir.E);
        loco.setEngineOn(true);
        when(model.getLocomotives()).thenReturn(List.of(loco));

        visitor.visitModel(model);

        RailTrack ahead = new RailTrack();
        ahead.setPosition(new Point(6, 5));
        visitor.visitRailTrack(ahead);

        verify(view, atLeastOnce()).setBgColor(litBoard(palette, 1, 0));
    }

    @Test
    @DisplayName("by day the headlight stays off")
    void visitModel_shouldNotCastHeadlightBeam_byDay() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(0f);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        RailTrack locoTrack = new RailTrack();
        locoTrack.setPosition(new Point(5, 5));
        loco.setTrack(locoTrack);
        loco.setPosition(new Point(5, 5));
        loco.setDir(Dir.E);
        loco.setEngineOn(true);
        when(model.getLocomotives()).thenReturn(List.of(loco));

        visitor.visitModel(model);

        RailTrack ahead = new RailTrack();
        ahead.setPosition(new Point(6, 5));
        visitor.visitRailTrack(ahead);

        verify(view, never()).setBgColor(litBoard(palette, 1, 0));
    }

    @Test
    @DisplayName("with the terminal band already off day, the beam still waits for the lights-on ratio")
    void visitModel_shouldNotCastHeadlightBeam_beforeDusk() {
        // 0.08 is a ratio where the terminal band left day (>0) but the shared lights-on threshold
        // (0.1) has not been crossed, so this pins the headlight gate, not the palette band.
        assertTrue(TerminalPalette.band(0.08f, -1f) > 0f);
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);
        // From here on only the frame's own drawing counts: the constructor paints with the day
        // palette before the visitor knows the clock.
        clearInvocations(view);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(0.08f);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        RailTrack locoTrack = new RailTrack();
        locoTrack.setPosition(new Point(5, 5));
        loco.setTrack(locoTrack);
        loco.setPosition(new Point(5, 5));
        loco.setDir(Dir.E);
        loco.setEngineOn(true);
        when(model.getLocomotives()).thenReturn(List.of(loco));

        visitor.visitModel(model);

        RailTrack ahead = new RailTrack();
        ahead.setPosition(new Point(6, 5));
        visitor.visitRailTrack(ahead);

        // Only the band's board colour may ever be set as background: nothing is lit.
        float band = TerminalPalette.band(0.08f, -1f);
        TextColor bandBoard =
                palette.colorOf(TerminalPalette.rgbFor(band).get(TerminalPalette.Token.BOARD));
        verify(view, never()).setBgColor(argThat(c -> c != null && !c.equals(bandBoard)));
    }

    @Test
    @DisplayName("a locomotive with the engine off casts no light even at night")
    void visitModel_shouldHideHeadlight_whenEngineIsOff() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(1f);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        RailTrack locoTrack = new RailTrack();
        locoTrack.setPosition(new Point(5, 5));
        loco.setTrack(locoTrack);
        loco.setPosition(new Point(5, 5));
        loco.setDir(Dir.E); // engine off by default: no headlight
        when(model.getLocomotives()).thenReturn(List.of(loco));

        visitor.visitModel(model);

        RailTrack ahead = new RailTrack();
        ahead.setPosition(new Point(6, 5));
        visitor.visitRailTrack(ahead);

        verify(view, never()).setBgColor(litBoard(palette, 1, 0));
    }

    @Test
    @DisplayName("a speed signal west of the track draws arrow and ID away from the rails")
    void visitSpeedSignal_shouldDrawLabelOutwards_whenSignalIsWestOfTrack() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor = new RenderVisitor(view);

        Model model = mock(Model.class);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.SPEED_SIGNALS);

        RailTrack track = new RailTrack();
        track.setPosition(new Point(5, 5));
        letrain.track.SpeedSignal signal = new letrain.track.SpeedSignal(7, Dir.S, 3, true);
        signal.setTrack(track);

        visitor.visitModel(model);
        visitor.visitSpeedSignal(signal);

        verify(view, atLeastOnce()).set(eq(4), eq(5), anyString()); // icon on the west side
        verify(view, atLeastOnce()).set(eq(3), eq(5), eq("↓")); // arrow further out
        verify(view, atLeastOnce()).set(eq(2), eq(5), eq("7")); // ID further out
        verify(view, never()).set(eq(5), eq(5), eq("7")); // never over the track
    }

    @Test
    @DisplayName("a station west of the track draws its ID outwards, not over the rails")
    void visitStation_shouldDrawIdOutwards_whenStationIsWestOfTrack() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor = new RenderVisitor(view);

        Model model = mock(Model.class);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.STATIONS);
        when(model.getLocomotives()).thenReturn(List.of());

        RailTrack track = new RailTrack();
        track.setPosition(new Point(5, 5));
        letrain.track.Station station = new letrain.track.Station(7);
        station.setTrack(track);
        station.setCreationDir(Dir.S); // right side of S is W: the station sits at (4,5)

        visitor.visitModel(model);
        visitor.visitStation(station);

        verify(view, atLeastOnce()).set(eq(4), eq(5), anyString()); // arrow/aspect on the west
        verify(view, atLeastOnce()).set(eq(3), eq(5), eq("7")); // ID further out, after the arrow
        verify(view, never()).set(eq(5), eq(5), eq("7")); // never over the track
    }

    @Test
    @DisplayName("only the head tractor lights up: a mid-train locomotive casts no beam")
    void visitModel_shouldLightOnlyTheHeadLocomotive() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(1f);

        Locomotive head = new Locomotive(1, "A");
        Locomotive mid = new Locomotive(2, "B");
        letrain.vehicle.rail.impl.Train train = new letrain.vehicle.rail.impl.Train(9);
        train.pushBack(head);
        train.pushBack(mid);
        train.setDirectorLinker(head);

        RailTrack headTrack = new RailTrack();
        headTrack.setPosition(new Point(5, 5));
        head.setTrack(headTrack);
        head.setPosition(new Point(5, 5));
        head.setDir(Dir.E);
        head.setEngineOn(true);

        RailTrack midTrack = new RailTrack();
        midTrack.setPosition(new Point(20, 5));
        mid.setTrack(midTrack);
        mid.setPosition(new Point(20, 5));
        mid.setDir(Dir.E);
        mid.setEngineOn(true);

        when(model.getLocomotives()).thenReturn(List.of(head, mid));

        visitor.visitModel(model);

        // The head lights the cell ahead...
        RailTrack aheadOfHead = new RailTrack();
        aheadOfHead.setPosition(new Point(6, 5));
        visitor.visitRailTrack(aheadOfHead);
        verify(view, atLeastOnce()).setBgColor(litBoard(palette, 1, 0));

        // ...and the mid-train locomotive lights nothing of its own.
        clearInvocations(view);
        RailTrack aheadOfMid = new RailTrack();
        aheadOfMid.setPosition(new Point(21, 5));
        visitor.visitRailTrack(aheadOfMid);
        verify(view, never()).setBgColor(litBoard(palette, 1, 0));
    }

    /** Fondo esperado de una celda iluminada con factor {@code (dx, dy)} respecto a la loco. */
    private static TextColor litBoard(TerminalPalette palette, int dx, int dy) {
        float lit = Headlight.factor(dx, dy, Dir.E) * Headlight.MAX_LIGHT;
        int nightBoard = TerminalPalette.rgbFor(1f).get(TerminalPalette.Token.BOARD);
        int dayBoard = TerminalPalette.rgbFor(0f).get(TerminalPalette.Token.BOARD);
        return palette.colorOf(TerminalPalette.mix(nightBoard, dayBoard, lit));
    }

    @Test
    @DisplayName("a locomotive hidden inside a tunnel casts no light outside Rails mode")
    void visitModel_shouldHideHeadlight_whenLocomotiveIsInsideTunnel() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(1f);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.DRIVE);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        RailTrack tunnel = new RailTrack();
        tunnel.setPosition(new Point(5, 5));
        tunnel.setVisualType(RailTrack.VisualType.TUNNEL);
        loco.setTrack(tunnel);
        loco.setPosition(new Point(5, 5));
        loco.setDir(Dir.E);
        loco.setEngineOn(true);
        when(model.getLocomotives()).thenReturn(List.of(loco));

        visitor.visitModel(model);

        RailTrack ahead = new RailTrack();
        ahead.setPosition(new Point(6, 5));
        visitor.visitRailTrack(ahead);

        verify(view, never()).setBgColor(litBoard(palette, 1, 0));
    }

    @Test
    @DisplayName("in Rails mode tunnels are visible, so the headlight is too")
    void visitModel_shouldCastHeadlight_insideTunnel_whileInRailsMode() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(1f);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.RAILS);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        RailTrack tunnel = new RailTrack();
        tunnel.setPosition(new Point(5, 5));
        tunnel.setVisualType(RailTrack.VisualType.TUNNEL);
        loco.setTrack(tunnel);
        loco.setPosition(new Point(5, 5));
        loco.setDir(Dir.E);
        loco.setEngineOn(true);
        when(model.getLocomotives()).thenReturn(List.of(loco));

        visitor.visitModel(model);

        RailTrack ahead = new RailTrack();
        ahead.setPosition(new Point(6, 5));
        visitor.visitRailTrack(ahead);

        verify(view, atLeastOnce()).setBgColor(litBoard(palette, 1, 0));
    }

    @Test
    @DisplayName("the beam stops at hidden tunnel cells instead of lighting through them")
    void visitModel_shouldNotLightHiddenTunnelCells() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(1f);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.DRIVE);

        letrain.map.impl.RailMap railMap = mock(letrain.map.impl.RailMap.class);
        RailTrack tunnel = new RailTrack();
        tunnel.setVisualType(RailTrack.VisualType.TUNNEL);
        when(railMap.getTrackAt(6, 5)).thenReturn(tunnel);
        when(model.getRailMap()).thenReturn(railMap);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        RailTrack locoTrack = new RailTrack();
        locoTrack.setPosition(new Point(5, 5));
        loco.setTrack(locoTrack);
        loco.setPosition(new Point(5, 5));
        loco.setDir(Dir.E);
        loco.setEngineOn(true);
        when(model.getLocomotives()).thenReturn(List.of(loco));

        visitor.visitModel(model);

        RailTrack throughTunnel = new RailTrack();
        throughTunnel.setPosition(new Point(6, 5));
        visitor.visitRailTrack(throughTunnel);
        RailTrack diagonal = new RailTrack();
        diagonal.setPosition(new Point(7, 6));
        visitor.visitRailTrack(diagonal);

        verify(view, never()).setBgColor(litBoard(palette, 1, 0));
        verify(view, atLeastOnce()).setBgColor(litBoard(palette, 2, 1));
    }
}
