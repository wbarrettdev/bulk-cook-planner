package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.usda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodRecord;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodReference;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodSource;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodValues;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.Source;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.SourceSettings;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Answers USDA's requests with made-up records in the two shapes USDA uses. */
class UsdaFoodSourceTest {

    /** A search result: nutrients carry their number and value side by side. */
    private static final String SEARCH_ANSWER = """
            {"foods": [
              {"fdcId": 111, "description": "Bean, test, raw", "dataType": "Foundation", "foodNutrients": [
                {"nutrientNumber": "208", "value": 300.04},
                {"nutrientNumber": "203", "value": 20.0},
                {"nutrientNumber": "204", "value": 1.5},
                {"nutrientNumber": "205", "value": 60.0},
                {"nutrientNumber": "291", "value": 15.0},
                {"nutrientNumber": "307", "value": 12.0}]},
              {"fdcId": 222, "description": "Bean, test, older", "dataType": "SR Legacy", "foodNutrients": [
                {"nutrientNumber": "957", "value": 290.0},
                {"nutrientNumber": "203", "value": 19.0}]}
            ]}
            """;

    /** A full record: the number sits inside "nutrient" and the value is called "amount". */
    private static final String FOOD_ANSWER = """
            {"fdcId": 111, "description": "Bean, test, raw", "dataType": "Foundation", "foodNutrients": [
              {"nutrient": {"number": "958"}, "amount": 301.0},
              {"nutrient": {"number": "203"}, "amount": 20.0},
              {"nutrient": {"number": "204"}, "amount": 1.5},
              {"nutrient": {"number": "205"}, "amount": 10.0},
              {"nutrient": {"number": "291"}, "amount": 15.0},
              {"nutrient": {"number": "307"}, "amount": 0.0}]}
            """;

    private final RestClient.Builder http = RestClient.builder();
    private final MockRestServiceServer usda =
            MockRestServiceServer.bindTo(http).build();
    private final UsdaFoodSource source = new UsdaFoodSource(http, SourceSettings.RETRY, SourceSettings.FOR_TESTS);

    @Test
    void searchesOnlyTheAnalysedFoodsAndReadsThemAsALabelWould() {
        usda.expect(requestTo(startsWith("https://usda.test/fdc/v1/foods/search?")))
                .andExpect(queryParam("query", "test%20bean"))
                .andExpect(queryParam("dataType", "Foundation%2CSR%20Legacy"))
                .andExpect(queryParam("api_key", "test-key"))
                .andExpect(header("User-Agent", "bulk-cook-planner-tests"))
                .andRespond(withSuccess(SEARCH_ANSWER, MediaType.APPLICATION_JSON));

        List<FoodRecord> found = source.search("test bean");

        assertThat(found)
                .containsExactly(
                        new FoodRecord(
                                new FoodReference(Source.USDA, "111"),
                                "Bean, test, raw",
                                "Foundation",
                                new FoodValues(300.0, 20.0, 1.5, 45.0, 15.0, 0.03),
                                null,
                                ""),
                        new FoodRecord(
                                new FoodReference(Source.USDA, "222"),
                                "Bean, test, older",
                                "SR Legacy",
                                new FoodValues(290.0, 19.0, null, null, null, null),
                                null,
                                ""));
        usda.verify();
    }

    @Test
    void readsAFullRecordAndNeverGivesLessThanNoCarbohydrate() {
        usda.expect(requestTo("https://usda.test/fdc/v1/food/111?api_key=test-key"))
                .andRespond(withSuccess(FOOD_ANSWER, MediaType.APPLICATION_JSON));

        FoodRecord food = source.find("111").orElseThrow();

        assertThat(food.per100g()).isEqualTo(new FoodValues(301.0, 20.0, 1.5, 0.0, 15.0, 0.0));
        assertThat(food.citation()).isEqualTo("usda:111 Bean, test, raw");
    }

    @Test
    void findsNothingForAnIdUsdaDoesNotHave() {
        usda.expect(requestTo("https://usda.test/fdc/v1/food/999?api_key=test-key"))
                .andRespond(withResourceNotFound());

        assertThat(source.find("999")).isEmpty();
        assertThat(source.find("not-a-number")).isEmpty();
    }

    @Test
    void saysWhenTheKeyIsRefused() {
        usda.expect(requestTo(startsWith("https://usda.test/fdc/v1/foods/search?")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> source.search("bean"))
                .isInstanceOf(FoodSource.Unavailable.class)
                .hasMessage("USDA refused the API key. Set USDA_API_KEY to your own key.");
    }

    @Test
    void saysWhenUsdaCannotBeReached() {
        usda.expect(requestTo(startsWith("https://usda.test/fdc/v1/foods/search?")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> source.search("bean"))
                .isInstanceOf(FoodSource.Unavailable.class)
                .hasMessageStartingWith("USDA FoodData Central can't be reached right now.");
    }
}
