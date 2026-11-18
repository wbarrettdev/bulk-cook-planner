package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.mcp;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.application.NutritionLookup;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.application.NutritionLookup.Addition;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.application.NutritionLookup.NewRow;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FiveADayCategory;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodRecord;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodReference;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodSource;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.LookupProblem;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.Entry;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.Source;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** Each tool answers twice over: as text for a model to read, and as structured content for code to check. */
@Component
@RequiredArgsConstructor
class NutritionTools {

    private static final String DOES_NOT_COUNT = "none";

    private final NutritionLookup lookup;

    @McpTool(
            name = "search_nutrition",
            title = "Search a reference source for a food",
            description = "Search a reference source for a food's nutrition values per 100g. Three sources:"
                    + " cofid (the UK's reference foods; first choice for everything it covers), usda (US"
                    + " reference foods; only for whole foods that are the same anywhere, such as meat, fish,"
                    + " vegetables, pulses, grains and single spices, when cofid has no good match) and off (Open"
                    + " Food Facts; for a packaged food the other two don't have, such as bread, stock or a"
                    + " sauce). Returns a few records, each with its id. Pick the one that matches what goes in"
                    + " the pan: the same food, cut and state (raw, dry, drained). Then store it with"
                    + " add_nutrition_row. Nutrition values come only from these records, never from memory.",
            annotations =
                    @McpAnnotations(
                            title = "Search a reference source for a food",
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = true))
    CallToolResult searchNutrition(
            @McpToolParam(description = "The source to search: \"cofid\", \"usda\" or \"off\"", required = true)
                    String source,
            @McpToolParam(
                            description = "The food and its state in plain words, e.g. \"chicken thigh raw\"."
                                    + " For off, a barcode also works.",
                            required = true)
                    String query) {
        return answer(() -> {
            Source asked = sourceCalled(source);
            List<FoodRecord> records = lookup.search(asked, query);
            return CallToolResult.builder()
                    .addTextContent(NutritionText.of(asked, query, records))
                    .structuredContent(new FoundRecords(asked.key(), records))
                    .build();
        });
    }

    @McpTool(
            name = "find_ingredients",
            title = "Find ingredients in the nutrition table",
            description = "Look in the nutrition table for ingredients that are already stored, by words in"
                    + " their name. Use it before writing a week, to get an ingredient's exact name, the units"
                    + " it is stored in, and the state its values are for (raw, dry, drained, cooked), which"
                    + " its source says. Use it before adding a row, to see whether one is already there. A"
                    + " name written \"name|text\" is a second set of values, used when a recipe's note for"
                    + " the ingredient contains the text.",
            annotations =
                    @McpAnnotations(
                            title = "Find ingredients in the nutrition table",
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    CallToolResult findIngredients(
            @McpToolParam(
                            description = "Words in the ingredient's name, e.g. \"rice\" or \"chicken thigh\"",
                            required = true)
                    String query) {
        return answer(() -> {
            List<Entry> entries = lookup.storedIngredients(query);
            return CallToolResult.builder()
                    .addTextContent(NutritionText.of(query, entries))
                    .structuredContent(new StoredIngredients(entries))
                    .build();
        });
    }

    @McpTool(
            name = "add_nutrition_row",
            title = "Add an ingredient to the nutrition table",
            description = "Store an ingredient's nutrition rows, taking every value from one record that"
                    + " search_nutrition found: its macros, salt, fibre and five-a-day category. A row that is"
                    + " already there is never changed, so this is safe to repeat; the answer says which rows"
                    + " were written and which were already there. To keep a second set of values for the same"
                    + " ingredient in another state, name it \"name|state\", such as \"white rice|dry\": that"
                    + " row is used when a recipe's note for the ingredient contains the state.",
            annotations =
                    @McpAnnotations(
                            title = "Add an ingredient to the nutrition table",
                            readOnlyHint = false,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = true))
    CallToolResult addNutritionRow(
            @McpToolParam(
                            description = "The record to take the values from, as the search gave it, e.g."
                                    + " \"cofid:13-234\"",
                            required = true)
                    String record,
            @McpToolParam(
                            description = "The ingredient's name as recipes write it, e.g. \"kale\" or \"white"
                                    + " rice|dry\"",
                            required = true)
                    String ingredient,
            @McpToolParam(
                            description = "The unit recipes measure it in: \"g\", \"ml\", \"tsp\", \"Tbsp\","
                                    + " \"slice\" and so on, or \"each\" for a counted item such as an egg",
                            required = true)
                    String unit,
            @McpToolParam(description = "What one unit weighs in grams: 1 for g or ml, 58 for an egg", required = true)
                    Double grams,
            @McpToolParam(
                            description = "Its five-a-day category: \"veg\", \"fruit\", \"dried\" (dried fruit),"
                                    + " \"pulse\", \"juice\", or \"none\". The search suggests one, but check it:"
                                    + " potatoes, herbs, garlic, spices, purées and sauces don't count.",
                            required = true)
                    String category) {
        return answer(() -> {
            Addition addition = lookup.add(new NewRow(
                    FoodReference.parse(record),
                    ingredient,
                    unit,
                    grams == null ? 0 : grams,
                    categoryCalled(category)));
            return CallToolResult.builder()
                    .addTextContent(NutritionText.of(addition))
                    .structuredContent(addition)
                    .build();
        });
    }

    /** A problem the asker can act on comes back as the tool's answer, marked as an error, not as a failed call. */
    private static CallToolResult answer(Supplier<CallToolResult> work) {
        try {
            return work.get();
        } catch (LookupProblem | FoodSource.Unavailable | NutritionRepository.Unavailable problem) {
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent(problem.getMessage())
                    .build();
        }
    }

    private static Source sourceCalled(String name) {
        return Source.withKey(lowerCase(name))
                .orElseThrow(() ->
                        new LookupProblem("\"" + name + "\" isn't a source. Use \"cofid\", \"usda\" or \"off\"."));
    }

    private static FiveADayCategory categoryCalled(String name) {
        String asked = lowerCase(name);
        if (asked.equals(DOES_NOT_COUNT)) {
            return null;
        }
        return FiveADayCategory.withKey(asked)
                .orElseThrow(() -> new LookupProblem("\"" + name + "\" isn't a five-a-day category. Use \"veg\","
                        + " \"fruit\", \"dried\", \"pulse\", \"juice\" or \"none\"."));
    }

    private static String lowerCase(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }

    /** What the table holds, as structured content. */
    record StoredIngredients(List<Entry> ingredients) {}

    /** A search's answer as structured content. */
    record FoundRecords(String source, List<FoodRecord> records) {}
}
