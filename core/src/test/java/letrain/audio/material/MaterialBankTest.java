package letrain.audio.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Optional;
import letrain.audio.material.MaterialId.Role;
import letrain.audio.synth.AudioSample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MaterialBank - shared cache and typed API")
class MaterialBankTest {

    private static final String TEST_PROFILE = """
            profile = test
            packVersion = 1

            notch.1   = sound/train/generic/notch-1.wav loop=1.0,2.0
            notch.2   = sound/train/generic/notch-2.wav loop=1.0,2.0
            idle      = sound/train/generic/idle.wav    loop=0.5,1.0
            trans.1-2 = sound/train/generic/trans-1-2.wav
            trans.2-3 = sound/train/generic/trans-2-3.wav
            trans.3-4 = sound/train/generic/trans-3-4.wav
            """;

    @TempDir
    Path tempDir;

    private MaterialResolver resolver;
    private MaterialBank bank;
    private AudioSample sample;

    @BeforeEach
    void setUp() throws Exception {
        sample = WavFixture.sample(tempDir, "synthetic.wav");
        resolver = mock(MaterialResolver.class);
        bank = new MaterialBank(MaterialProfileParser.parse(TEST_PROFILE), resolver);
    }

    @Test
    @DisplayName("resolves a notch loop through the injected resolver")
    void should_ReturnResolvedSample_When_LoopRequested() {
        when(resolver.resolve(MaterialId.notch(1))).thenReturn(Optional.of(sample));

        assertSame(sample, bank.loop(1).orElseThrow());
        verify(resolver, times(1)).resolve(MaterialId.notch(1));
    }

    @Test
    @DisplayName("resolves and decodes each material only once")
    void should_ResolveOnlyOnce_When_LoopRequestedTwice() {
        when(resolver.resolve(MaterialId.notch(1))).thenReturn(Optional.of(sample));

        assertSame(bank.loop(1).orElseThrow(), bank.loop(1).orElseThrow());
        verify(resolver, times(1)).resolve(MaterialId.notch(1));
    }

    @Test
    @DisplayName("caches misses so a missing material is not retried")
    void should_CacheMiss_When_MaterialIsMissing() {
        when(resolver.resolve(MaterialId.notch(2))).thenReturn(Optional.empty());

        assertTrue(bank.loop(2).isEmpty());
        assertTrue(bank.loop(2).isEmpty());
        verify(resolver, times(1)).resolve(MaterialId.notch(2));
    }

    @Test
    @DisplayName("resolves a directional transition")
    void should_ReturnTransition_When_PairDeclared() {
        when(resolver.resolve(MaterialId.transition(1, 2))).thenReturn(Optional.of(sample));

        assertSame(sample, bank.transition(1, 2).orElseThrow());
        verify(resolver, times(1)).resolve(MaterialId.transition(1, 2));
    }

    @Test
    @DisplayName("prefetch warms the cache so a second prefetch does not resolve again")
    void should_WarmCache_When_PrefetchCalled() {
        when(resolver.resolve(MaterialId.of(Role.IDLE))).thenReturn(Optional.of(sample));

        bank.prefetch(MaterialId.of(Role.IDLE));
        bank.prefetch(MaterialId.of(Role.IDLE));

        verify(resolver, times(1)).resolve(MaterialId.of(Role.IDLE));
    }

    @Test
    @DisplayName("prefetch APIs are typed for loops and transitions")
    void should_WarmCache_When_TypedPrefetchCalled() {
        when(resolver.resolve(MaterialId.notch(1))).thenReturn(Optional.of(sample));
        when(resolver.resolve(MaterialId.transition(2, 3))).thenReturn(Optional.of(sample));

        bank.prefetchLoop(1);
        bank.prefetchTransition(2, 3);

        verify(resolver, times(1)).resolve(MaterialId.notch(1));
        verify(resolver, times(1)).resolve(MaterialId.transition(2, 3));
    }

    @Test
    @DisplayName("reports a complete adjacent chain in both directions")
    void should_ReportCompleteChain_When_AdjacentStepsDeclared() {
        assertTrue(bank.hasChain(1, 4), "1->2->3->4 is fully declared");
        assertTrue(bank.hasChain(1, 2));
        assertTrue(bank.hasChain(1, 1), "an empty chain is trivially complete");
        verifyNoInteractions(resolver);
    }

    @Test
    @DisplayName("reports a broken chain when a step or its direction is missing")
    void should_ReportBrokenChain_When_StepMissing() {
        assertFalse(bank.hasChain(1, 5), "trans.4-5 is not declared");
        assertFalse(bank.hasChain(4, 1), "no downward transitions are declared");
        assertFalse(bank.hasChain(0, 3), "trans.0-1 is not declared");
        verifyNoInteractions(resolver);
    }

    @Test
    @DisplayName("exposes profile metadata without decoding audio")
    void should_ExposeMetadata_WithoutResolving() {
        MaterialRef ref = bank.material(MaterialId.notch(1)).orElseThrow();

        assertEquals("sound/train/generic/notch-1.wav", ref.path());
        assertEquals(1.0, ref.loop().orElseThrow().startSeconds(), 1e-9);
        assertEquals(1, bank.profile().packVersion());
        verifyNoInteractions(resolver);
    }

    @ParameterizedTest(name = "rejects loop({0})")
    @ValueSource(ints = {0, 11})
    @DisplayName("throws when a notch index is out of range")
    void should_Throw_When_NotchIndexOutOfRange(int notch) {
        assertThrows(IllegalArgumentException.class, () -> bank.loop(notch));
    }

    @Test
    @DisplayName("throws when a transition is not directional")
    void should_Throw_When_TransitionIsNotDirectional() {
        assertThrows(IllegalArgumentException.class, () -> bank.transition(3, 3));
    }
}
