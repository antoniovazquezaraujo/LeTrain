package letrain.audio.synth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import letrain.audio.material.MaterialId;
import letrain.audio.synth.TransitionPlanner.Kind;
import letrain.audio.synth.TransitionPlanner.Step;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("TransitionPlanner - ADR-029 §3 incremental fallback chain")
class TransitionPlannerTest {

    private static final Set<MaterialId> ADJACENT_1_TO_4 = Set.of(MaterialId.transition(1, 2),
            MaterialId.transition(2, 3), MaterialId.transition(3, 4), MaterialId.transition(4, 3),
            MaterialId.transition(3, 2), MaterialId.transition(2, 1));

    private static TransitionPlanner.Plan plan(int from, int to, Set<MaterialId> available,
            boolean legacyRampAvailable) {
        return TransitionPlanner.plan(from, to, available::contains, legacyRampAvailable);
    }

    @Test
    @DisplayName("uses the exact pair when the material is available")
    void should_UseExactPair_When_MaterialAvailable() {
        TransitionPlanner.Plan result = plan(2, 5, Set.of(MaterialId.transition(2, 5)), false);

        assertEquals(1, result.steps().size());
        assertEquals(new Step(2, 5, Kind.MATERIAL), result.first());
    }

    @Test
    @DisplayName("decomposes into adjacent material steps when the exact pair is missing")
    void should_DecomposeIntoAdjacentChain_When_ExactPairMissing() {
        TransitionPlanner.Plan result = plan(1, 4, ADJACENT_1_TO_4, false);

        assertEquals(List.of(new Step(1, 2, Kind.MATERIAL), new Step(2, 3, Kind.MATERIAL),
                new Step(3, 4, Kind.MATERIAL)), result.steps());
    }

    @Test
    @DisplayName("decomposes downward chains with the reverse adjacent pairs")
    void should_DecomposeDownwardChain_When_ExactPairMissing() {
        TransitionPlanner.Plan result = plan(4, 1, ADJACENT_1_TO_4, false);

        assertEquals(List.of(new Step(4, 3, Kind.MATERIAL), new Step(3, 2, Kind.MATERIAL),
                new Step(2, 1, Kind.MATERIAL)), result.steps());
    }

    @Test
    @DisplayName("falls back to the legacy ramp for a stage without material")
    void should_FallBackToLegacyRamp_When_StageHasNoMaterial() {
        TransitionPlanner.Plan result = plan(1, 3, Set.of(MaterialId.transition(1, 2)), true);

        assertEquals(List.of(new Step(1, 2, Kind.MATERIAL), new Step(2, 3, Kind.LEGACY_RAMP)),
                result.steps());
    }

    @Test
    @DisplayName("collapses the rest of the ride into a loop crossfade without material or legacy")
    void should_FallBackToLoopCrossfade_When_NoMaterialAndNoLegacyRamp() {
        TransitionPlanner.Plan result = plan(1, 4, Set.of(MaterialId.transition(1, 2)), false);

        assertEquals(List.of(new Step(1, 2, Kind.MATERIAL), new Step(2, 4, Kind.LOOP_CROSSFADE)),
                result.steps());
    }

    @Test
    @DisplayName("level 4 keeps a full-ride loop crossfade when nothing else exists")
    void should_PlanLoopCrossfade_When_NothingAvailable() {
        TransitionPlanner.Plan result = plan(2, 5, Set.of(), false);

        assertEquals(List.of(new Step(2, 5, Kind.LOOP_CROSSFADE)), result.steps());
    }

    @Test
    @DisplayName("never returns an empty plan for any real ride")
    void should_NeverReturnEmptyPlan_When_TransitionRequested() {
        for (int from = TransitionPlanner.MIN_NOTCH; from <= TransitionPlanner.MAX_NOTCH; from++) {
            for (int to = TransitionPlanner.MIN_NOTCH; to <= TransitionPlanner.MAX_NOTCH; to++) {
                if (from == to) {
                    continue;
                }
                for (boolean legacy : new boolean[] {true, false}) {
                    TransitionPlanner.Plan result = plan(from, to, Set.of(), legacy);

                    assertFalse(result.steps().isEmpty(),
                            from + " -> " + to + " (legacy=" + legacy + ") must not be mute");
                    assertEquals(to, result.steps().get(result.steps().size() - 1).to(),
                            "the last stage must land on the requested destination");
                }
            }
        }
    }

    @Test
    @DisplayName("returns an empty plan when both notches are equal")
    void should_ReturnEmptyPlan_When_NotchesEqual() {
        assertTrue(plan(3, 3, ADJACENT_1_TO_4, true).isEmpty());
        assertTrue(plan(0, 0, Set.of(), false).isEmpty());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"-1, 3", "0, 11", "11, 0"})
    @DisplayName("rejects out-of-range notches")
    void should_Throw_When_NotchOutOfRange(int from, int to) {
        assertThrows(IllegalArgumentException.class, () -> plan(from, to, Set.of(), true));
    }

    @Test
    @DisplayName("rejects a stage with equal endpoints")
    void should_Throw_When_StepEndpointsEqual() {
        assertThrows(IllegalArgumentException.class, () -> new Step(4, 4, Kind.MATERIAL));
    }

    @Test
    @DisplayName("rejects a stage without kind")
    void should_Throw_When_StepKindMissing() {
        assertThrows(NullPointerException.class, () -> new Step(4, 5, null));
    }
}
