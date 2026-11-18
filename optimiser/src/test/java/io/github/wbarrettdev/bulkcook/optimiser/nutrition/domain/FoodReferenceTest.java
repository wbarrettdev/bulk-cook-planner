package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FoodReferenceTest {

    @Test
    void readsARecordAsTheSearchWritesIt() {
        assertThat(FoodReference.parse("cofid:13-234")).isEqualTo(new FoodReference(Source.COFID, "13-234"));
        assertThat(FoodReference.parse(" OFF: 5000169005408 "))
                .isEqualTo(new FoodReference(Source.OPEN_FOOD_FACTS, "5000169005408"));
    }

    @Test
    void writesItselfTheWayARowCitesIt() {
        assertThat(new FoodReference(Source.USDA, "171455")).hasToString("usda:171455");
    }

    @ParameterizedTest
    @ValueSource(strings = {"13-234", "tesco:13-234", "cofid:", ""})
    void refusesWhatDoesNotNameARecord(String written) {
        assertThatThrownBy(() -> FoodReference.parse(written))
                .isInstanceOf(LookupProblem.class)
                .hasMessageContaining("doesn't name a record");
    }
}
