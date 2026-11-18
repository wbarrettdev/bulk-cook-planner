package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.application.NutritionLookup;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.application.NutritionLookup.Addition;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FakeNutrition;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FakeSource;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodRecord;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodSource;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.Source;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.mcp.NutritionTools.FoundRecords;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NutritionToolsTest {

    private final FakeNutrition nutrition = new FakeNutrition();
    private final NutritionTools tools =
            new NutritionTools(new NutritionLookup(List.of(FakeSource.cofid()), nutrition));

    @Test
    void listsWhatASearchFoundAsTextAndAsData() {
        CallToolResult result = tools.searchNutrition("cofid", "kale");

        assertThat(text(result)).isEqualTo("""
                        CoFID records for "kale", per 100g:
                        cofid:13-234  Curly kale, raw
                            33 kcal, protein 3.4g, fat 1.6g, carbs 1.3g, fibre 4.1g, salt 0.11g
                            five-a-day: veg (from its food group; check it)""");
        assertThat(result.structuredContent()).isEqualTo(new FoundRecords("cofid", List.of(FakeSource.KALE)));
    }

    @Test
    void marksARecordThatCannotBeAdded() {
        assertThat(text(tools.searchNutrition("cofid", "powder")))
                .contains("350 kcal, protein 10g, fat 2g, carbs 70g, fibre not given, salt not given")
                .contains("MISSING salt: this record can't be added");
    }

    @Test
    void saysWhenNothingMatches() {
        CallToolResult result = tools.searchNutrition("cofid", "dragon fruit");

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(result))
                .isEqualTo("No CoFID records match \"dragon fruit\". Try fewer or plainer words, or another source.");
    }

    @Test
    void namesTheSourcesWhenAskedForOneItDoesNotKnow() {
        CallToolResult result = tools.searchNutrition("tesco", "kale");

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo("\"tesco\" isn't a source. Use \"cofid\", \"usda\" or \"off\".");
    }

    @Test
    void addsAnIngredientAndSaysWhatWasWritten() {
        CallToolResult result = tools.addNutritionRow("cofid:13-234", "Kale", "g", 1.0, "veg");

        assertThat(text(result)).isEqualTo("""
                        Added "kale" (g, 1g) from cofid:13-234 Curly kale, raw:
                          macros: written (33 kcal, protein 3.4g, fat 1.6g, carbs 1.3g per 100g)
                          salt: written (0.11g per 100g)
                          fibre: written (4.1g per 100g)
                          five-a-day: written (veg)""");
        assertThat(result.structuredContent()).isInstanceOf(Addition.class);
        assertThat(nutrition.table().categories()).containsEntry("kale", "veg");
    }

    @Test
    void saysWhenThereWasNothingLeftToWrite() {
        tools.addNutritionRow("cofid:13-234", "kale", "g", 1.0, "none");

        CallToolResult again = tools.addNutritionRow("cofid:13-234", "kale", "g", 1.0, "none");

        assertThat(again.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(again))
                .startsWith("\"kale\" (g, 1g) already has all its rows, so nothing was written.")
                .contains("macros: already there, left as it was\n")
                .as("a row left alone may hold other values, so this record's are not shown beside it")
                .doesNotContain("kcal")
                .contains("five-a-day: doesn't count");
    }

    @Test
    void refusesACategoryFiveADayDoesNotHave() {
        CallToolResult result = tools.addNutritionRow("cofid:13-234", "kale", "g", 1.0, "greens");

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).startsWith("\"greens\" isn't a five-a-day category.");
        assertThat(nutrition.table().rows()).isEmpty();
    }

    @Test
    void passesOnWhyARecordCannotBeAdded() {
        CallToolResult result = tools.addNutritionRow("cofid:99-001", "powder", "g", 1.0, "none");

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo("cofid:99-001 Powder, mystery has no salt. Pick another record.");
    }

    @Test
    void saysWhenASourceCannotBeReached() {
        FoodSource down = new FoodSource() {
            @Override
            public Source source() {
                return Source.USDA;
            }

            @Override
            public List<FoodRecord> search(String query) {
                throw new Unavailable(Source.USDA, new IllegalStateException("no network"));
            }

            @Override
            public Optional<FoodRecord> find(String id) {
                throw new Unavailable(Source.USDA, new IllegalStateException("no network"));
            }
        };
        NutritionTools cutOff = new NutritionTools(new NutritionLookup(List.of(down), nutrition));

        CallToolResult result = cutOff.searchNutrition("usda", "kale");

        assertThat(result.isError()).isTrue();
        assertThat(text(result))
                .isEqualTo("USDA FoodData Central can't be reached right now. Try again later, or try another"
                        + " source.");
    }

    @Test
    void saysWhenTheNutritionTablesCannotBeReached() {
        NutritionTools cutOff = new NutritionTools(
                new NutritionLookup(List.of(FakeSource.cofid()), FakeNutrition.thatCannotBeReached()));

        CallToolResult result = cutOff.addNutritionRow("cofid:13-234", "kale", "g", 1.0, "veg");

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo("The nutrition tables can't be reached right now.");
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

    @Test
    void showsWhatTheTableHoldsForAnIngredient() {
        tools.addNutritionRow("cofid:13-234", "kale", "g", 1.0, "veg");
        tools.addNutritionRow("cofid:13-234", "kale", "handful", 30.0, "veg");

        CallToolResult result = tools.findIngredients("kale");

        assertThat(text(result)).isEqualTo("""
                        Ingredients in the nutrition table named with "kale", per 100g:
                        kale (g, 1g)
                            33 kcal, protein 3.4g, fat 1.6g, carbs 1.3g, fibre 4.1g, salt 0.11g
                            five-a-day: veg
                            source: cofid:13-234 Curly kale, raw
                        kale (handful, 30g)
                            33 kcal, protein 3.4g, fat 1.6g, carbs 1.3g, fibre 4.1g, salt 0.11g
                            five-a-day: veg
                            source: cofid:13-234 Curly kale, raw""");
    }

    @Test
    void pointsToTheLookupWhenAnIngredientIsNotStored() {
        CallToolResult result = tools.findIngredients("dragon fruit");

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(result))
                .isEqualTo("No ingredient in the nutrition table is named with \"dragon fruit\". Look it up with"
                        + " search_nutrition and add it with add_nutrition_row.");
    }
}
