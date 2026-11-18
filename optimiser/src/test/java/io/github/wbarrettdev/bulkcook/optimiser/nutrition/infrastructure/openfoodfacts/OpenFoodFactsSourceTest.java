package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.openfoodfacts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodRecord;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodSource;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodValues;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.SourceSettings;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Answers Open Food Facts' requests with made-up products. */
class OpenFoodFactsSourceTest {

    private static final String PRODUCTS = """
            [{"code": "111", "brands": "Abroad Co", "product_name": "Test stock", "quantity": "8 cubes",
              "countries_tags": ["en:france"],
              "nutriments": {"energy_100g": 418.4, "proteins_100g": "1.5", "fat_100g": 0.5,
                             "carbohydrates_100g": 2, "salt_100g": 0.127}},
             {"code": "222", "brands": "Local Co", "product_name": "Test stock", "quantity": "6 cubes",
              "countries_tags": ["en:france", "en:spain"],
              "nutriments": {"energy-kcal_100g": 12, "energy_100g": 50, "proteins_100g": 0.5, "fat_100g": 0.2,
                             "carbohydrates_100g": 1.1, "fiber_100g": 0.3, "salt_100g": 0.1}}]
            """;

    private static final String MAIN_SEARCH = "https://off.test/cgi/search.pl?";
    private static final String NEWER_SEARCH = "https://search.off.test/search?";

    private final RestClient.Builder http = RestClient.builder();
    private final MockRestServiceServer off = MockRestServiceServer.bindTo(http).build();
    private final OpenFoodFactsSource source =
            new OpenFoodFactsSource(http, SourceSettings.RETRY, SourceSettings.FOR_TESTS);

    @Test
    void listsProductsSoldInThePreferredCountriesFirst() {
        off.expect(requestTo(startsWith(MAIN_SEARCH)))
                .andExpect(queryParam("search_terms", "test%20stock"))
                .andRespond(withSuccess("{\"products\": " + PRODUCTS + "}", MediaType.APPLICATION_JSON));

        List<FoodRecord> found = source.search("test stock");

        assertThat(found).extracting(FoodRecord::name).containsExactly("Local Co Test stock", "Abroad Co Test stock");
        assertThat(found.getFirst().detail()).isEqualTo("6 cubes");
        assertThat(found.getFirst().reference()).hasToString("off:222");
    }

    @Test
    void readsLabelFiguresWhetherWrittenAsNumbersOrText() {
        off.expect(requestTo(startsWith(MAIN_SEARCH)))
                .andRespond(withSuccess("{\"products\": " + PRODUCTS + "}", MediaType.APPLICATION_JSON));

        List<FoodRecord> found = source.search("test stock");

        assertThat(found.getFirst().per100g()).isEqualTo(new FoodValues(12.0, 0.5, 0.2, 1.1, 0.3, 0.1));
        assertThat(found.getLast().per100g())
                .as("energy from kilojoules, protein from text, no fibre given")
                .isEqualTo(new FoodValues(100.0, 1.5, 0.5, 2.0, null, 0.13));
    }

    @Test
    void turnsToTheNewerSearchWhenTheMainOneIsDown() {
        off.expect(times(3), requestTo(startsWith(MAIN_SEARCH))).andRespond(withServerError());
        off.expect(requestTo(startsWith(NEWER_SEARCH)))
                .andExpect(queryParam("q", "test%20stock"))
                .andRespond(withSuccess("{\"hits\": " + PRODUCTS + "}", MediaType.APPLICATION_JSON));

        assertThat(source.search("test stock")).hasSize(2);
        off.verify();
    }

    @Test
    void saysWhenBothSearchesAreDown() {
        off.expect(times(3), requestTo(startsWith(MAIN_SEARCH))).andRespond(withServerError());
        off.expect(times(3), requestTo(startsWith(NEWER_SEARCH))).andRespond(withServerError());

        assertThatThrownBy(() -> source.search("test stock"))
                .isInstanceOf(FoodSource.Unavailable.class)
                .hasMessageStartingWith("Open Food Facts search is down.");
    }

    @Test
    void findsOneProductByItsBarcode() {
        off.expect(requestTo(startsWith("https://off.test/api/v2/product/222.json?")))
                .andRespond(withSuccess("""
                        {"status": 1, "product": {"code": "222", "brands": "Local Co", "product_name": "Test stock",
                         "quantity": "6 cubes", "nutriments": {"energy-kcal_100g": 12}}}
                        """, MediaType.APPLICATION_JSON));

        FoodRecord product = source.find("222").orElseThrow();

        assertThat(product.citation()).isEqualTo("off:222 Local Co Test stock");
        assertThat(product.per100g().missing()).containsExactly("protein", "fat", "carbs", "salt");
    }

    @Test
    void findsNothingForABarcodeItDoesNotHave() {
        off.expect(requestTo(startsWith("https://off.test/api/v2/product/000.json?")))
                .andRespond(withSuccess("{\"status\": 0}", MediaType.APPLICATION_JSON));

        assertThat(source.find("000")).isEmpty();
    }

    @Test
    void looksABarcodeUpAsAProductNotAsWords() {
        off.expect(requestTo(startsWith("https://off.test/api/v2/product/5000000000222.json?")))
                .andRespond(withSuccess("""
                        {"status": 1, "product": {"code": "5000000000222", "brands": "Local Co",
                         "product_name": "Test stock", "nutriments": {"energy-kcal_100g": 12}}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(source.search("5000000000222"))
                .extracting(FoodRecord::citation)
                .containsExactly("off:5000000000222 Local Co Test stock");
        off.verify();
    }
}
