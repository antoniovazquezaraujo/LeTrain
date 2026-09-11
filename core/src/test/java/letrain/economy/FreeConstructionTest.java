package letrain.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.economy.EconomyManager.ExpenseType;
import letrain.mvp.impl.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Free-construction mode (scenario constructor libre)")
class FreeConstructionTest {

    @Test
    @DisplayName("spending is ignored while free, and charged again when disabled")
    void freeConstruction_skipsSpending() {
        Model model = new Model();
        EconomyManager economy = model.getEconomyManager();
        float start = economy.getBalance();

        assertFalse(economy.isFreeConstruction());
        economy.setFreeConstruction(true);
        assertTrue(economy.isFreeConstruction());
        economy.spend(ExpenseType.CONSTRUCTED_NORMAL_RAIL_TRACK);
        assertEquals(start, economy.getBalance(), 0.0001f, "free mode must not change balance");

        economy.setFreeConstruction(false);
        economy.spend(ExpenseType.CONSTRUCTED_NORMAL_RAIL_TRACK);
        assertTrue(economy.getBalance() < start, "after free mode, spending must charge again");
    }
}
