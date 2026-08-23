package letrain.visitor.terminal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import letrain.economy.EconomyManager;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.impl.terminal.TerminalView;
import letrain.vehicle.Cursor;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InfoVisitorTest {

    @Test
    @DisplayName("visitModel should render Notch and Speed on left, and Finances right-aligned in English")
    void visitModel_shouldRenderNotchSpeedLeft_andFinancesRight() {
        TerminalView view = mock(TerminalView.class);
        when(view.getCols()).thenReturn(100);
        when(view.getMapScrollPage()).thenReturn(new Point(0, 0));

        Model model = mock(Model.class);
        when(model.getMode()).thenReturn(Model.GameMode.DRIVE);
        when(model.getMenuModel()).thenReturn(Collections.emptyList());

        Cursor cursor = mock(Cursor.class);
        when(cursor.getPosition()).thenReturn(new Point(10, 20));
        when(model.getCursor()).thenReturn(cursor);
        when(model.getQuantifierSteps()).thenReturn(1);
        when(model.getQuantifier()).thenReturn(1);

        EconomyManager economy = mock(EconomyManager.class);
        when(economy.getBalance()).thenReturn(1500.50f);
        when(economy.getTotalIncome()).thenReturn(200.0f);
        when(economy.getTotalExpenses()).thenReturn(50.0f);
        when(model.getEconomyManager()).thenReturn(economy);

        Locomotive loco = new Locomotive(1, "A");
        loco.setCurrentSpeed(3);
        loco.setTargetSpeed(5);
        Train train = new Train(1);
        train.pushBack(loco);
        when(model.getSelectedLocomotive()).thenReturn(loco);

        InfoVisitor visitor = new InfoVisitor(view);
        visitor.visitModel(model);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(view).setInfoBarText(captor.capture());

        String text = captor.getValue();
        String[] lines = text.split("\n");
        assertTrue(lines.length >= 3);

        String firstLine = lines[0];
        assertTrue(firstLine.contains("Train: 1"), "Should contain Train ID");
        assertTrue(firstLine.contains("Notch:"), "Should contain Notch bar");
        assertTrue(firstLine.contains("Speed: 3->5"), "Should contain speed progression");
        assertTrue(firstLine.contains("Wagons: 0"), "Should contain wagon count in English");
        assertTrue(firstLine.contains("$: 1500.50 | Income(+): 200.00 | Expenses(-): 50.00"), "Should contain financial info in English");
        assertTrue(firstLine.endsWith("$: 1500.50 | Income(+): 200.00 | Expenses(-): 50.00"), "Financial info must be aligned to the right");

        // Verify translations on other lines
        assertTrue(lines[1].contains("| Page 0,0 | Cursor 10,20"), "Page info should be in English");
        assertTrue(lines[2].contains("[PgUp/Dn]: Scroll | [c/C]: Camera | [r/d/f/s/t/l/u/p/n]: Modes | [Esc]: Exit"), "Global help should be in English");
    }
}
