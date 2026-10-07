package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import letrain.economy.EconomyManager;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.vehicle.Cursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 3D HUD status info: the notch row (Pos/Step, no Page) and the compact 2D finances. */
@DisplayName("3D HUD status info")
class Gdx3DHudStatusInfoTest {

    private static Model modelWithCursor(int x, int y, int steps, int quantifier) {
        Model model = mock(Model.class);
        Cursor cursor = mock(Cursor.class);
        when(cursor.getPosition()).thenReturn(new Point(x, y));
        when(model.getCursor()).thenReturn(cursor);
        when(model.getQuantifierSteps()).thenReturn(steps);
        when(model.getQuantifier()).thenReturn(quantifier);
        return model;
    }

    @Test
    @DisplayName("the notch row shows the 2D Pos/Step line without the Page part")
    void should_FormatSystemInfoWithoutPages() {
        String info = Gdx3DHud.systemInfoText(modelWithCursor(10, 20, 1, 1));

        assertEquals("|Pos:10,20|Step:1/1|", info);
        assertFalse(info.contains("Page"), info);
    }

    @Test
    @DisplayName("the save time is appended when the game was saved, as in 2D")
    void should_AppendSaveTime() {
        Model model = modelWithCursor(-3, 7, 2, 5);
        when(model.getLastSaveTime()).thenReturn(LocalDateTime.of(2026, 10, 7, 14, 5));

        assertEquals("|Pos:-3,7|Step:2/5|Saved:14:05|", Gdx3DHud.systemInfoText(model));
    }

    @Test
    @DisplayName("the finances line uses the compact 2D format and locale")
    void should_FormatFinancesLikeThe2dClient() {
        Model model = mock(Model.class);
        EconomyManager economy = mock(EconomyManager.class);
        when(economy.getTotalIncome()).thenReturn(200.0f);
        when(economy.getTotalExpenses()).thenReturn(50.0f);
        when(economy.getBalance()).thenReturn(1500.50f);
        when(model.getEconomyManager()).thenReturn(economy);

        assertEquals("|In:200.00|Out:50.00|$:1,500.50|", Gdx3DHud.financeText(model));
    }

    @Test
    @DisplayName("no economy manager means an empty finances line")
    void should_ReturnEmptyFinancesWithoutEconomy() {
        Model model = mock(Model.class);
        when(model.getEconomyManager()).thenReturn(null);

        assertEquals("", Gdx3DHud.financeText(model));
    }

    @Test
    @DisplayName("the big red finance block is gone from the HUD")
    void should_NotKeepTheBigFinanceBlock() {
        for (String field : List.of("balanceLabel", "incomeLabel", "expensesLabel")) {
            assertThrows(NoSuchFieldException.class, () -> Gdx3DHud.class.getDeclaredField(field),
                    "the big finance block field must stay removed: " + field);
        }
    }
}
