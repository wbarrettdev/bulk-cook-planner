package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.Missing;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.Nutrient;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.PerUnit;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.Row;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NutritionTableTest {

    private final NutritionTable table = new NutritionTable(
            List.of(
                    new Row("white rice", "g", 1, 130, 2.7, 0.3, 28, "cooked"),
                    new Row("white rice|dry", "g", 1, 355, 7, 0.6, 79, "dry"),
                    new Row("milk", "ml", 1.03, 47, 3.4, 1.7, 4.8, "semi-skimmed"),
                    new Row("carrot", "g", 1, 40, 0.9, 0.2, 8, "raw"),
                    new Row("mystery", "g", 1, 100, 1, 1, 1, "no salt row")),
            Map.of("white rice", 0.0, "milk", 0.1, "carrot", 0.1),
            Map.of("carrot", 2.8),
            Map.of("carrot", "veg"),
            Map.of("veg", 80.0, "fruit", 80.0, "dried", 30.0, "pulse", 80.0, "juice", 150.0));

    @Test
    void readsWhatOneUnitHolds() {
        PerUnit carrot = (PerUnit) table.perUnit("recipe", "carrot", "g", "");

        assertThat(carrot.of(Nutrient.KCAL)).isCloseTo(0.4, within(1e-9));
        assertThat(carrot.of(Nutrient.FIBRE)).isCloseTo(0.028, within(1e-9));
        assertThat(carrot.fibreKnown()).isTrue();
        assertThat(carrot.of(Nutrient.VEG_AND_FRUIT)).isCloseTo(1 / 80.0, within(1e-9));
    }

    @Test
    void prefersTheRowWhoseStateTheNoteNames() {
        PerUnit dry = (PerUnit) table.perUnit("recipe", "white rice", "g", "dry weight");
        PerUnit cooked = (PerUnit) table.perUnit("recipe", "white rice", "g", "");

        assertThat(dry.of(Nutrient.KCAL)).isCloseTo(3.55, within(1e-9));
        assertThat(cooked.of(Nutrient.KCAL)).isCloseTo(1.3, within(1e-9));
    }

    @Test
    void readsAMillilitreRowForGrams() {
        assertThat(table.perUnit("recipe", "milk", "g", "")).isInstanceOf(PerUnit.class);
    }

    @Test
    void saysAnIngredientIsNotInTheTable() {
        assertThat(table.perUnit("recipe \"soup\"", "Dragon Fruit", "", ""))
                .isEqualTo(
                        new Missing(
                                "recipe \"soup\": \"Dragon Fruit\" (each) isn't in the nutrition table. Look it up and add it."));
    }

    @Test
    void saysAnIngredientHasNoSaltValue() {
        Missing missing = (Missing) table.perUnit("recipe \"soup\"", "mystery", "g", "");

        assertThat(missing.what()).contains("\"mystery\" has no salt value");
    }

    @Test
    void treatsAMissingFibreValueAsUnknownNotZero() {
        PerUnit milk = (PerUnit) table.perUnit("recipe", "milk", "ml", "");

        assertThat(milk.fibreKnown()).isFalse();
    }

    @Test
    void readsACountedItemWhetherOrNotItIsWrittenAsEach() {
        NutritionTable eggs = new NutritionTable(
                List.of(new Row("egg", "", 58, 131, 12.6, 9, 0, "raw")),
                Map.of("egg", 0.4),
                Map.of(),
                Map.of(),
                Map.of());

        assertThat(eggs.perUnit("recipe", "egg", "each", "")).isEqualTo(eggs.perUnit("recipe", "egg", "", ""));
        assertThat(eggs.perUnit("recipe", "egg", "Each", "")).isInstanceOf(PerUnit.class);
    }
}
