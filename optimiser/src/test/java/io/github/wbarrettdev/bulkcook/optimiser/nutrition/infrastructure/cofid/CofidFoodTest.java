package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.cofid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FiveADayCategory;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodRecord;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CofidFoodTest {

    @Test
    void givesCarbohydrateByItsOwnWeightFromStarchAndSugars() {
        CofidFood rice = food("A", 77.0, 75.9, 0.0, null, null, null);

        assertThat(rice.carbsByWeight()).isCloseTo(69.0, within(1e-9));
    }

    @Test
    void fallsBackOnTheTotalWhenStarchAndSugarsAreNotGivenApart() {
        CofidFood mixed = food("A", 54.0, null, 20.0, null, null, null);

        assertThat(mixed.carbsByWeight()).isCloseTo(50.0, within(1e-9));
    }

    @Test
    void prefersFibreMeasuredTheWayLabelsMeasureIt() {
        CofidFood both = food("DG", 1.0, 0.0, 1.0, 3.5, 2.8, null);

        assertThat(both.fibre()).isEqualTo(3.5);
        assertThat(both.asLabelled().fibreNote()).isEmpty();
    }

    @Test
    void worksFibreOutFromTheOlderMeasureAndSaysSo() {
        CofidFood older = food("DG", 1.0, 0.0, 1.0, null, 3.0, null);

        FoodRecord labelled = older.asLabelled();

        assertThat(labelled.per100g().fibre()).isEqualTo(4.0);
        assertThat(labelled.fibreCitation()).isEqualTo("cofid:00-001 Test food (fibre from NSP x1.33)");
    }

    @Test
    void turnsSodiumIntoSaltAndLeavesAnUnknownValueUnknown() {
        assertThat(food("A", 1.0, 0.0, 1.0, null, null, 240.0)
                        .asLabelled()
                        .per100g()
                        .salt())
                .isEqualTo(0.6);
        FoodValues unknown =
                food("A", null, null, null, null, null, null).asLabelled().per100g();
        assertThat(unknown.salt()).isNull();
        assertThat(unknown.carbs()).isNull();
        assertThat(unknown.fibre()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"DB,PULSE", "DG,VEG", "DF,VEG", "FC,JUICE", "FB,DRIED", "FA,FRUIT", "F,FRUIT"})
    void suggestsAFiveADayCategoryFromTheFoodGroup(String group, FiveADayCategory category) {
        assertThat(food(group, 1.0, 0.0, 1.0, null, null, null).suggestedFiveADay())
                .isEqualTo(category);
    }

    @ParameterizedTest
    @CsvSource({"DA", "MAC", "A", "PE"})
    void suggestsNothingForAGroupThatDoesNotCount(String group) {
        assertThat(food(group, 1.0, 0.0, 1.0, null, null, null).suggestedFiveADay())
                .isNull();
    }

    private static CofidFood food(
            String group, Double carbohydrate, Double starch, Double sugars, Double aoac, Double nsp, Double sodium) {
        return new CofidFood(
                "00-001", "Test food", group, 100.0, 1.0, 1.0, carbohydrate, starch, sugars, aoac, nsp, sodium);
    }
}
