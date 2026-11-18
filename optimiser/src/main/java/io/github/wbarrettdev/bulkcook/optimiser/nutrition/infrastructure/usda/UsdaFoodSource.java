package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.usda;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodRecord;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodReference;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodSource;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodValues;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.Source;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.config.FoodSourcesProperties;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.http.RetryWhenBusy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * The United States' reference foods, from USDA FoodData Central. For whole foods that are the same
 * anywhere, when CoFID has no good match. Only its analysed records are searched: Foundation, the
 * recent laboratory work, and SR Legacy, the older.
 */
@Component
class UsdaFoodSource implements FoodSource {

    private static final int MOST = 6;
    private static final String ANALYSED_FOODS = "Foundation,SR Legacy";

    /** USDA's nutrient numbers. Energy is given in one of three ways, depending on the record's age. */
    private static final List<String> KCAL = List.of("208", "958", "957");

    private static final String PROTEIN = "203";
    private static final String FAT = "204";
    private static final String CARBOHYDRATE_BY_DIFFERENCE = "205";
    private static final String FIBRE = "291";
    private static final String SODIUM_MG = "307";

    private final RestClient http;
    private final String apiKey;

    UsdaFoodSource(RestClient.Builder http, RetryWhenBusy retry, FoodSourcesProperties sources) {
        this.http = http.baseUrl(sources.usda().baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, sources.userAgent())
                .requestInterceptor(retry)
                .build();
        this.apiKey = sources.usda().apiKey();
    }

    @Override
    public Source source() {
        return Source.USDA;
    }

    @Override
    public List<FoodRecord> search(String query) {
        return get(
                        "/foods/search?query={query}&dataType={kinds}&pageSize={most}&api_key={key}",
                        query,
                        ANALYSED_FOODS,
                        MOST,
                        apiKey)
                .map(found -> found.path("foods").values().stream()
                        .map(UsdaFoodSource::record)
                        .toList())
                .orElse(List.of());
    }

    @Override
    public Optional<FoodRecord> find(String id) {
        if (!id.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        return get("/food/{id}?api_key={key}", id, apiKey).map(UsdaFoodSource::record);
    }

    private Optional<JsonNode> get(String uri, Object... values) {
        try {
            return Optional.ofNullable(http.get().uri(uri, values).retrieve().body(JsonNode.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException.Forbidden e) {
            throw new Unavailable("USDA refused the API key. Set USDA_API_KEY to your own key.", e);
        } catch (RestClientException e) {
            throw new Unavailable(Source.USDA, e);
        }
    }

    private static FoodRecord record(JsonNode food) {
        return new FoodRecord(
                new FoodReference(Source.USDA, food.path("fdcId").asString()),
                food.path("description").asString(""),
                food.path("dataType").asString(""),
                labelled(food),
                null,
                "");
    }

    /**
     * USDA's figures as a label would give them. Its carbohydrate is what is left after everything
     * else is weighed, fibre included, so the fibre comes off. Sodium becomes salt.
     */
    static FoodValues labelled(JsonNode food) {
        Map<String, Double> amounts = amountsByNutrientNumber(food);
        Double carbohydrate = amounts.get(CARBOHYDRATE_BY_DIFFERENCE);
        Double fibre = amounts.get(FIBRE);
        Double sodiumMg = amounts.get(SODIUM_MG);
        return FoodValues.rounded(
                KCAL.stream()
                        .map(amounts::get)
                        .flatMap(Stream::ofNullable)
                        .findFirst()
                        .orElse(null),
                amounts.get(PROTEIN),
                amounts.get(FAT),
                carbohydrate == null ? null : Math.max(carbohydrate - (fibre == null ? 0 : fibre), 0),
                fibre,
                sodiumMg == null ? null : FoodValues.saltFromSodiumMg(sodiumMg));
    }

    /** A search result names a nutrient and its amount one way, and a full record another. */
    private static Map<String, Double> amountsByNutrientNumber(JsonNode food) {
        Map<String, Double> amounts = new HashMap<>();
        for (JsonNode nutrient : food.path("foodNutrients").values()) {
            String number = nutrient.path("nutrientNumber")
                    .asString(nutrient.path("nutrient").path("number").asString(""));
            JsonNode amount = nutrient.has("value") ? nutrient.path("value") : nutrient.path("amount");
            if (!number.isEmpty() && amount.isNumber()) {
                amounts.put(number, amount.asDouble());
            }
        }
        return amounts;
    }
}
