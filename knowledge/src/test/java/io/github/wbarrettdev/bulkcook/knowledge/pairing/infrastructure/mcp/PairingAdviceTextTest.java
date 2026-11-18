package io.github.wbarrettdev.bulkcook.knowledge.pairing.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingAdvice;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingAdvice.Dish;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingAdvice.Suggestion;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingAdvice.UnknownIngredient;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.Avoidance;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.Combination;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.IngredientNotes;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.Pairing;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PairingAdviceTextTest {

    @Test
    void writesTheSetsTheSuggestionsAndTheReasons() {
        PairingAdvice advice = new PairingAdvice(
                new Dish(List.of("salmon", "potatoes"), List.of("salmon", "potatoes"), List.of()),
                List.of(new Combination(List.of("salmon", "potatoes"), List.of("chives"), Set.of())),
                1,
                List.of(new Suggestion(
                        "dill",
                        6,
                        false,
                        List.of(new Pairing("salmon", "dill", 4, "fresh"), new Pairing("potatoes", "dill", 2, "")))),
                List.of("bacon"),
                List.of(new Avoidance("salmon", "cinnamon")),
                List.of(new IngredientNotes("salmon", "poach, grill", "spring", "", "")));

        String text = PairingAdviceText.of(advice);

        assertThat(text)
                .contains("Pairings for: salmon, potatoes")
                .contains("has potatoes + salmon  ->  add chives")
                .contains("(1 more left out by the household rules)")
                .contains("  6  dill   <- potatoes 2, salmon 4 [fresh]")
                .contains("Left out by the household rules: bacon")
                .contains("Avoid: cinnamon with salmon")
                .contains("salmon: techniques: poach, grill; season: spring");
    }

    @Test
    void saysWhenThereIsNothingToLookUp() {
        Dish nothing =
                new Dish(List.of(), List.of(), List.of(new UnknownIngredient("xyz", List.of(), List.of("xyzzy"))));
        PairingAdvice advice = new PairingAdvice(nothing, List.of(), 0, List.of(), List.of(), List.of(), List.of());

        assertThat(PairingAdviceText.of(advice))
                .contains("Not in the pairing graph: xyz. Close names: xyzzy.")
                .contains("Nothing to look up");
    }
}
