package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IngredientNutritionTest {

    @Test
    void writesTheNameAndUnitTheWayTheTableKeepsThem() {
        IngredientNutrition rows = new IngredientNutrition(" Kale ", "G", 1, FakeSource.KALE, FiveADayCategory.VEG);

        assertThat(rows.ingredient()).isEqualTo("kale");
        assertThat(rows.unit()).isEqualTo("g");
    }

    @Test
    void keepsACountedItemUnderAnEmptyUnit() {
        assertThat(new IngredientNutrition("kale leaf", "each", 12, FakeSource.KALE, null).unit())
                .isEmpty();
    }

    @Test
    void refusesARecordThatLacksAValueARowNeeds() {
        assertThatThrownBy(() -> new IngredientNutrition("powder", "g", 1, FakeSource.MYSTERY_POWDER, null))
                .isInstanceOf(LookupProblem.class)
                .hasMessage("cofid:99-001 Powder, mystery has no salt. Pick another record.");
    }

    @Test
    void refusesAUnitThatWeighsNothing() {
        assertThatThrownBy(() -> new IngredientNutrition("kale", "tsp", 0, FakeSource.KALE, null))
                .isInstanceOf(LookupProblem.class)
                .hasMessageStartingWith("One tsp has to weigh more than 0g.");
    }

    @Test
    void refusesAnIngredientWithNoName() {
        assertThatThrownBy(() -> new IngredientNutrition("  ", "g", 1, FakeSource.KALE, null))
                .isInstanceOf(LookupProblem.class);
    }
}
