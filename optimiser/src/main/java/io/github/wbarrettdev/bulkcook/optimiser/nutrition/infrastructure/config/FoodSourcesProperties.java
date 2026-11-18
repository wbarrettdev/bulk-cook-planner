package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How to reach the reference sources that are on the internet. Read from the nutrition.sources
 * settings.
 *
 * @param userAgent how this service names itself to the sources
 * @param retryDelay how long to wait before asking a busy source again; doubled, then tripled
 */
@ConfigurationProperties(prefix = "nutrition.sources")
public record FoodSourcesProperties(String userAgent, Duration retryDelay, Usda usda, OpenFoodFacts openFoodFacts) {

    /** @param apiKey the caller's own key. Left empty, USDA's shared demonstration key is used. */
    public record Usda(String baseUrl, String apiKey) {

        private static final String SHARED_DEMO_KEY = "DEMO_KEY";

        public Usda {
            apiKey = apiKey == null || apiKey.isBlank() ? SHARED_DEMO_KEY : apiKey.strip();
        }
    }

    /**
     * @param searchUrl the newer search service, used when the main site's search is overloaded
     * @param preferredCountries country tags, such as en:spain, whose products are listed first
     */
    public record OpenFoodFacts(String baseUrl, String searchUrl, List<String> preferredCountries) {

        public OpenFoodFacts {
            preferredCountries = preferredCountries == null ? List.of() : List.copyOf(preferredCountries);
        }
    }
}
