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

    @Test
    @DisplayName("arbitrary charges and refunds update balance and expenses, and respect free mode")
    void chargeAndRefund_areAccounted() {
        Model model = new Model();
        EconomyManager economy = model.getEconomyManager();
        economy.refund(100f);
        float start = economy.getBalance();

        economy.charge(30f);
        assertEquals(start - 30f, economy.getBalance(), 0.0001f);
        assertEquals(30f, economy.getTotalExpenses(), 0.0001f);

        economy.refund(10f);
        assertEquals(start - 20f, economy.getBalance(), 0.0001f);
        assertEquals(20f, economy.getTotalExpenses(), 0.0001f);

        economy.setFreeConstruction(true);
        economy.charge(50f);
        economy.refund(50f);
        assertEquals(start - 20f, economy.getBalance(), 0.0001f);
    }
}
