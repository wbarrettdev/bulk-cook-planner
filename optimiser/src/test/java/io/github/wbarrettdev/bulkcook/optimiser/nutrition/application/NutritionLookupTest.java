package io.github.wbarrettdev.bulkcook.optimiser.nutrition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.application.NutritionLookup.Addition;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.application.NutritionLookup.NewRow;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FakeNutrition;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FakeSource;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FiveADayCategory;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodReference;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.LookupProblem;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository.Outcome;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository.Stored;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.PerUnit;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.Source;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NutritionLookupTest {

    private static final NewRow KALE_IN_GRAMS =
            new NewRow(FoodReference.parse("cofid:13-234"), "kale", "g", 1, FiveADayCategory.VEG);

    private final FakeNutrition nutrition = new FakeNutrition();
    private final NutritionLookup lookup = new NutritionLookup(List.of(FakeSource.cofid()), nutrition);

    @Test
    void searchesTheSourceThatWasAsked() {
        assertThat(lookup.search(Source.COFID, " kale ")).containsExactly(FakeSource.KALE);
    }

    @Test
    void asksForAFoodToSearchFor() {
        assertThatThrownBy(() -> lookup.search(Source.COFID, " ")).isInstanceOf(LookupProblem.class);
    }

    @Test
    void saysWhenASourceIsNotSetUp() {
        assertThatThrownBy(() -> lookup.search(Source.USDA, "kale"))
                .isInstanceOf(LookupProblem.class)
                .hasMessage("USDA FoodData Central isn't set up in this service.");
    }

    @Test
    void storesARecordAsAnIngredientTheSolverCanRead() {
        Addition addition = lookup.add(KALE_IN_GRAMS);

        assertThat(addition.stored())
                .isEqualTo(new Stored(Outcome.WRITTEN, Outcome.WRITTEN, Outcome.WRITTEN, Outcome.WRITTEN));
        PerUnit kale =
                (PerUnit) nutrition.table().withPortionsOf(Map.of("veg", 80.0)).perUnit("recipe", "kale", "g", "");
        assertThat(kale.kcal()).isEqualTo(0.33);
        assertThat(kale.table()).isEqualTo("kale (g): cofid:13-234 Curly kale, raw");
    }

    @Test
    void changesNothingWhenTheSameIngredientIsAddedAgain() {
        lookup.add(KALE_IN_GRAMS);

        Addition again = lookup.add(KALE_IN_GRAMS);

        assertThat(again.stored().wroteNothing()).isTrue();
        assertThat(nutrition.table().rows()).hasSize(1);
    }

    @Test
    void addsASecondUnitWithoutTouchingTheSaltThatIsThere() {
        lookup.add(KALE_IN_GRAMS);

        Addition byTheHandful = lookup.add(
                new NewRow(FoodReference.parse("cofid:13-234"), "kale", "handful", 30, FiveADayCategory.VEG));

        assertThat(byTheHandful.stored())
                .isEqualTo(new Stored(
                        Outcome.WRITTEN, Outcome.ALREADY_THERE, Outcome.ALREADY_THERE, Outcome.ALREADY_THERE));
    }

    @Test
    void saysWhenTheSourceHasNoSuchRecord() {
        NewRow unknown = new NewRow(FoodReference.parse("cofid:00-000"), "kale", "g", 1, null);

        assertThatThrownBy(() -> lookup.add(unknown))
                .isInstanceOf(LookupProblem.class)
                .hasMessageStartingWith("CoFID has no record cofid:00-000.");
    }

    @Test
    void storesNothingFromARecordThatLacksAValue() {
        NewRow incomplete = new NewRow(FoodReference.parse("cofid:99-001"), "powder", "g", 1, null);

        assertThatThrownBy(() -> lookup.add(incomplete)).isInstanceOf(LookupProblem.class);
        assertThat(nutrition.table().rows()).isEmpty();
    }
}
