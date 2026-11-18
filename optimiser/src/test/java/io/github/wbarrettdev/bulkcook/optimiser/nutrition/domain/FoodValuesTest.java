package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class FoodValuesTest {

    @Test
    void roundsToWhatALabelGives() {
        FoodValues values = FoodValues.rounded(123.44, 20.06, 4.449, 0.04, 2.25, 0.2349);

        assertThat(values).isEqualTo(new FoodValues(123.4, 20.1, 4.4, 0.0, 2.2, 0.23));
    }

    @Test
    void keepsAValueTheSourceDoesNotGiveAsMissingNotAsZero() {
        FoodValues values = FoodValues.rounded(100.0, null, 1.0, 2.0, null, null);

        assertThat(values.protein()).isNull();
        assertThat(values.missing()).containsExactly("protein", "salt");
    }

    @Test
    void doesNotCountFibreAmongTheValuesARowNeeds() {
        assertThat(new FoodValues(100.0, 1.0, 1.0, 1.0, null, 0.0).missing()).isEmpty();
    }

    @Test
    void weighsSaltAtTwoAndAHalfTimesItsSodium() {
        assertThat(FoodValues.saltFromSodiumMg(400)).isCloseTo(1.0, within(1e-12));
    }
}
