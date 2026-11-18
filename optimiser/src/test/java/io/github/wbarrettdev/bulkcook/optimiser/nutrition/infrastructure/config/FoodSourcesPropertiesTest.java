package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.config.FoodSourcesProperties.OpenFoodFacts;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.config.FoodSourcesProperties.Usda;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class FoodSourcesPropertiesTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void usesTheSharedDemoKeyWhenNoKeyIsSet(String unset) {
        assertThat(new Usda("https://usda.test", unset).apiKey()).isEqualTo("DEMO_KEY");
    }

    @Test
    void usesTheCallersOwnKeyWhenThereIsOne() {
        assertThat(new Usda("https://usda.test", " my-key ").apiKey()).isEqualTo("my-key");
    }

    @Test
    void prefersNoCountryUnlessToldTo() {
        assertThat(new OpenFoodFacts("https://off.test", "https://search.off.test", null).preferredCountries())
                .isEmpty();
    }
}
