package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.config.FoodSourcesProperties;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.config.FoodSourcesProperties.OpenFoodFacts;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.config.FoodSourcesProperties.Usda;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.http.RetryWhenBusy;
import java.time.Duration;
import java.util.List;

/** Settings that point the sources at addresses no test ever reaches, and never wait between tries. */
public final class SourceSettings {

    public static final FoodSourcesProperties FOR_TESTS = new FoodSourcesProperties(
            "bulk-cook-planner-tests",
            Duration.ZERO,
            new Usda("https://usda.test/fdc/v1", "test-key"),
            new OpenFoodFacts("https://off.test", "https://search.off.test", List.of("en:spain")));

    public static final RetryWhenBusy RETRY = new RetryWhenBusy(FOR_TESTS);

    private SourceSettings() {}
}
