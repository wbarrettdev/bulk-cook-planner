package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.openfoodfacts;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodRecord;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodReference;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodSource;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodValues;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.Source;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.config.FoodSourcesProperties;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.http.RetryWhenBusy;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Packaged products, from Open Food Facts: the labels of things sold in shops, entered by
 * volunteers. For a processed food the reference tables don't have, such as a stock cube or a
 * bread. A record's id is the product's barcode.
 */
@Component
class OpenFoodFactsSource implements FoodSource {

    private static final int MOST = 6;
    private static final int ASKED_FOR = MOST * 2;
    private static final String FIELDS = "code,product_name,brands,countries_tags,nutriments,quantity";
    private static final double KJ_PER_KCAL = 4.184;
    private static final int FOUND = 1;
    private static final int SHORTEST_BARCODE = 8;

    private final RestClient http;
    private final FoodSourcesProperties.OpenFoodFacts site;

    OpenFoodFactsSource(RestClient.Builder http, RetryWhenBusy retry, FoodSourcesProperties sources) {
        this.http = http.defaultHeader(HttpHeaders.USER_AGENT, sources.userAgent())
                .requestInterceptor(retry)
                .build();
        this.site = sources.openFoodFacts();
    }

    @Override
    public Source source() {
        return Source.OPEN_FOOD_FACTS;
    }

    /** Products sold in the preferred countries come first, since a recipe changes from country to country. */
    @Override
    public List<FoodRecord> search(String query) {
        if (isABarcode(query)) {
            return find(query).stream().toList();
        }
        return products(query)
                .sorted(Comparator.comparing(product -> !isSoldLocally(product)))
                .limit(MOST)
                .map(product -> record(product, product.path("quantity").asString("")))
                .toList();
    }

    @Override
    public Optional<FoodRecord> find(String id) {
        try {
            JsonNode answer = get(site.baseUrl() + "/api/v2/product/{code}.json?fields={fields}", id, FIELDS);
            return answer.path("status").asInt(0) == FOUND
                    ? Optional.of(record(answer.path("product"), ""))
                    : Optional.empty();
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new Unavailable(Source.OPEN_FOOD_FACTS, e);
        }
    }

    private static boolean isABarcode(String query) {
        return query.length() >= SHORTEST_BARCODE && query.chars().allMatch(Character::isDigit);
    }

    /** The main site's search is often overloaded, so the newer search service stands behind it. */
    private Stream<JsonNode> products(String query) {
        try {
            return get(
                    site.baseUrl() + "/cgi/search.pl?search_terms={query}&json=1&page_size={size}&fields={fields}",
                    query,
                    ASKED_FOR,
                    FIELDS)
                    .path("products")
                    .values()
                    .stream();
        } catch (RestClientException mainSearchFailed) {
            try {
                JsonNode answer = get(
                        site.searchUrl() + "/search?q={query}&page_size={size}&fields={fields}",
                        query,
                        ASKED_FOR,
                        FIELDS);
                return (answer.has("hits") ? answer.path("hits") : answer.path("products")).values().stream();
            } catch (RestClientException e) {
                throw new Unavailable(
                        "Open Food Facts search is down. Try again later, or add a product by its barcode.", e);
            }
        }
    }

    private JsonNode get(String uri, Object... values) {
        JsonNode answer = http.get().uri(uri, values).retrieve().body(JsonNode.class);
        if (answer == null) {
            throw new RestClientException("Open Food Facts sent an empty answer");
        }
        return answer;
    }

    private boolean isSoldLocally(JsonNode product) {
        return product.path("countries_tags").values().stream()
                .anyMatch(country -> site.preferredCountries().contains(country.asString("")));
    }

    private static FoodRecord record(JsonNode product, String packSize) {
        String name = Stream.of(
                        product.path("brands").asString(""),
                        product.path("product_name").asString(""))
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .reduce((brand, productName) -> brand + " " + productName)
                .orElse("");
        return new FoodRecord(
                new FoodReference(Source.OPEN_FOOD_FACTS, product.path("code").asString("")),
                name,
                packSize.strip(),
                labelled(product.path("nutriments")),
                null,
                "");
    }

    /** These are label figures already. Energy is sometimes given only in kilojoules. */
    static FoodValues labelled(JsonNode per100g) {
        Double kcal = amount(per100g, "energy-kcal_100g");
        Double kilojoules = amount(per100g, "energy_100g");
        return FoodValues.rounded(
                kcal != null ? kcal : kilojoules == null ? null : Double.valueOf(kilojoules / KJ_PER_KCAL),
                amount(per100g, "proteins_100g"),
                amount(per100g, "fat_100g"),
                amount(per100g, "carbohydrates_100g"),
                amount(per100g, "fiber_100g"),
                amount(per100g, "salt_100g"));
    }

    /** Volunteers' entries hold numbers as numbers or as text, and sometimes nothing. */
    private static Double amount(JsonNode per100g, String field) {
        JsonNode value = per100g.path(field);
        if (value.isNumber()) {
            return value.asDouble();
        }
        try {
            return value.isString() ? Double.valueOf(value.asString().strip()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
