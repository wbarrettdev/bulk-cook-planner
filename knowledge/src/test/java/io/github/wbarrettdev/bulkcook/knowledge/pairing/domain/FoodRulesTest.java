package io.github.wbarrettdev.bulkcook.knowledge.pairing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FoodRulesTest {

    private final FoodRules rules = new FoodRules(Set.of("Ham", "mushrooms"), Set.of("tomatoes"), Set.of("salt"), 2);

    @Test
    void forbidsANameThatContainsAForbiddenWord() {
        assertThat(rules.forbids("serrano ham", List.of())).isTrue();
    }

    @Test
    void doesNotForbidAWordThatOnlyContainsTheLetters() {
        assertThat(rules.forbids("champagne", List.of())).isFalse();
    }

    @Test
    void forbidsAKindOfSomethingForbidden() {
        assertThat(rules.forbids("morels", List.of("mushrooms"))).isTrue();
    }

    @Test
    void advisesGoingEasyOnlyOnWhatIsListed() {
        assertThat(rules.advisesGoingEasyOn("tomatoes", List.of())).isTrue();
        assertThat(rules.advisesGoingEasyOn("basil", List.of())).isFalse();
    }

    @Test
    void addsExtrasWithoutChangingTheOriginalRules() {
        FoodRules stricter = rules.alsoForbidding(List.of("dill"));

        assertThat(stricter.forbids("dill", List.of())).isTrue();
        assertThat(rules.forbids("dill", List.of())).isFalse();
    }
}
