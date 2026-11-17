package io.github.wbarrettdev.bulkcook.knowledge.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DishResolverTest {

    @Test
    void normalisesCaseAndSpacing() {
        assertThat(DishResolver.normalised("  Chicken   Thighs ")).isEqualTo("chicken thighs");
    }

    @Test
    void offersThePluralOfASingular() {
        assertThat(DishResolver.spellingsOf("lemon")).containsExactly("lemon", "lemons");
    }

    @Test
    void offersTheSingularOfAPlural() {
        assertThat(DishResolver.spellingsOf("lemons")).contains("lemon");
        assertThat(DishResolver.spellingsOf("cherries")).contains("cherry");
        assertThat(DishResolver.spellingsOf("tomatoes")).contains("tomato");
    }
}
