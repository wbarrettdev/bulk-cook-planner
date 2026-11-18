package io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wbarrettdev.bulkcook.optimiser.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class SolveWeekSchemaTest {

    private static final Path PUBLISHED = Path.of("docs", "solve-week.schema.json");
    private static final Path GENERATED = Path.of("target", "solve-week.schema.json");

    private final JsonNode schema = SolveWeekSchema.asJson();

    /** The copy in the docs is what people read, so it has to be the one the tool publishes. */
    @Test
    void isTheSchemaPublishedInTheDocs() throws IOException {
        Files.createDirectories(GENERATED.getParent());
        Files.writeString(GENERATED, SolveWeekSchema.asText() + System.lineSeparator());

        assertThat(Files.readString(PUBLISHED))
                .as("docs/solve-week.schema.json is out of date. If the change to the contract is meant, copy"
                        + " target/solve-week.schema.json over it.")
                .isEqualToNormalizingNewlines(Files.readString(GENERATED));
    }

    @Test
    void describesTheRecipesInsideTheMapOfRecipes() {
        JsonNode recipe = schema.at("/properties/week/properties/recipes/additionalProperties");

        assertThat(recipe.at("/properties/ingredients/items/properties/per_portion/type")
                        .asString())
                .isEqualTo("boolean");
        assertThat(recipe.at("/properties/max_batch_g/description").asString()).contains("grams");
        assertThat(recipe.at("/required").values())
                .extracting(JsonNode::asString)
                .containsExactly("portions");
    }

    @Test
    void listsFieldsInTheOrderARecipeIsWritten() {
        assertThat(schema.at("/properties/week/properties/recipes/additionalProperties/properties/ingredients"
                                + "/items/properties")
                        .propertyNames())
                .containsExactly(
                        "name", "unit", "note", "book", "min", "max", "step", "role", "lo", "hi", "per_portion");
    }

    @Test
    void forbidsFieldsItDoesNotName() {
        assertThat(schema.at("/additionalProperties").asBoolean(true)).isFalse();
        assertThat(schema.at("/properties/targets/additionalProperties").asBoolean(true))
                .isFalse();
        assertThat(schema.at("/properties/targets/properties/five_a_day/properties/portion_g/required")
                        .values())
                .extracting(JsonNode::asString)
                .containsExactly("veg", "fruit", "dried", "pulse", "juice");
    }

    @Test
    void statesTheLimitsTheRequestRecordsDeclare() {
        JsonNode week = schema.at("/properties/week/properties");
        JsonNode recipe = week.at("/recipes/additionalProperties/properties");

        assertThat(recipe.at("/portions/exclusiveMinimum").asInt(-1)).isZero();
        assertThat(recipe.at("/ingredients/items/properties/min/minimum").asInt(-1))
                .isZero();
        assertThat(recipe.at("/ingredients/items/properties/name/minLength").asInt(-1))
                .isOne();
        assertThat(week.at("/days/minItems").asInt(-1)).isOne();
    }

    @Test
    void readsTheWeekAndTargetsKeptWithTheTestsWithoutLosingAnything() {
        assertThat(Fixtures.week().recipes()).hasSize(5);
        assertThat(Fixtures.targets().weights()).containsOnlyKeys("kcal", "protein", "fat", "carbs", "stay_near_book");
    }
}
