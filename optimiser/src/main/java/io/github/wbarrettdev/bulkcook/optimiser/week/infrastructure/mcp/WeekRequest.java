package io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A week as a request sends it. This record is the contract: the tool's published schema is
 * generated from it, so each field's name, description and limits are written once, here. The
 * limits are standard validation annotations, which the schema states and the server enforces.
 */
public record WeekRequest(
        @NotEmpty
        @JsonPropertyDescription(
                "The recipes to scale, each under an id of your choosing. Days name recipes by these ids.")
        Map<String, Recipe> recipes,

        @NotEmpty @JsonPropertyDescription("The days to plan, in order.")
        List<Day> days) {

    Week toDomain() {
        Map<String, Week.Recipe> scaled = new LinkedHashMap<>();
        if (recipes != null) {
            recipes.forEach((id, recipe) -> scaled.put(id, recipe == null ? null : recipe.toDomain()));
        }
        return new Week(
                recipes == null ? null : scaled,
                days == null
                        ? null
                        : days.stream()
                                .map(day -> day == null ? null : new Week.Day(day.day(), day.meals()))
                                .toList());
    }

    /** A recipe to cook, or a batch to take from the freezer. */
    public record Recipe(
            @JsonPropertyDescription("The dish's name, for the report.")
            String title,

            @JsonPropertyDescription("Where the recipe is written. Carried for the caller; the solver ignores it.")
            String file,

            @NotNull
            @Positive
            @JsonPropertyDescription("How many plates the recipe makes in all. It must cover every meal"
                    + " that eats it, for every person. Plates left over are reported as spare.")
            Integer portions,

            @Positive
            @JsonPropertyDescription("How many times it is cooked. 1, the default, for a batch cooked"
                    + " once. A dish made fresh each time has one cook for each meal that eats it. Must"
                    + " divide portions.")
            Integer cooks,

            @JsonPropertyDescription("The name of the main ingredient. Every other ingredient with a book amount"
                    + " stays in proportion to it. Needed when any ingredient has a book amount.")
            String anchor,

            @PositiveOrZero
            @JsonProperty("max_batch_g")
            @JsonPropertyDescription("The most food, in grams, that one cook may hold: the pot's limit.")
            Double maxBatchG,

            @JsonPropertyDescription("What goes into it. Leave out for a frozen recipe.")
            List<Ingredient> ingredients,

            @JsonPropertyDescription("Set this, and no ingredients, for plates taken from the freezer. Nothing is"
                    + " cooked or bought for them.")
            Frozen frozen) {

        Week.Recipe toDomain() {
            return new Week.Recipe(
                    title,
                    file,
                    portions,
                    cooks,
                    anchor,
                    maxBatchG,
                    ingredients == null
                            ? null
                            : ingredients.stream()
                                    .map(ingredient -> ingredient == null ? null : ingredient.toDomain())
                                    .toList(),
                    frozen == null ? null : frozen.toDomain());
        }
    }

    /** One ingredient of a recipe. Amounts are for one cook unless per_portion says otherwise. */
    public record Ingredient(
            @NotBlank @JsonPropertyDescription("The ingredient's name, as the nutrition table has it.")
            String name,

            @JsonPropertyDescription("The unit its amounts are in: g, ml, tsp, tbsp and so on. Leave out, or write"
                    + " each, for a counted item such as an egg.")
            String unit,

            @JsonPropertyDescription("Picks a second set of values in the nutrition table: rice with the note"
                    + " \"dry weight\" uses the row named rice|dry.")
            String note,

            @Positive
            @JsonPropertyDescription("The amount the source recipe uses. With it, the ingredient follows"
                    + " the anchor. Without it, the ingredient is sized freely between min and max.")
            Double book,

            @PositiveOrZero
            @JsonPropertyDescription("The least of it to use. Needed, with max, for the anchor and for any"
                    + " ingredient without a book amount.")
            Double min,

            @PositiveOrZero @JsonPropertyDescription("The most of it to use.")
            Double max,

            @Positive
            @JsonPropertyDescription("The smallest sensible change in its amount: 10 for 10g of meat, 1"
                    + " for an egg. Left out, it is 5 for g and ml, 0.25 for tsp, 0.5 for tbsp, and 1 for"
                    + " anything else.")
            Double step,

            @JsonPropertyDescription("Which band it may move in: a key of the targets' ratio_bounds, such as veg,"
                    + " liquid, fat, salty or fixed. Left out, it is default. Only with a book amount.")
            String role,

            @PositiveOrZero
            @JsonPropertyDescription("Replaces the low end of its role's band, as a multiple of its book"
                    + " ratio to the anchor. Only with a book amount.")
            Double lo,

            @PositiveOrZero
            @JsonPropertyDescription("Replaces the high end of its role's band. Only with a book amount.")
            Double hi,

            @JsonProperty("per_portion")
            @JsonPropertyDescription("True when min and max are for one plate, not one cook: a side, or"
                    + " something made to order. Not with a book amount.")
            Boolean perPortion) {

        Week.Ingredient toDomain() {
            return new Week.Ingredient(name, unit, note, book, min, max, step, role, lo, hi, perPortion);
        }
    }

    /** What one plate from the freezer holds. Every value is for one plate. */
    public record Frozen(
            @NotNull @PositiveOrZero @JsonPropertyDescription("Calories.")
            Double kcal,

            @NotNull @PositiveOrZero @JsonPropertyDescription("Protein, in grams.")
            Double protein,

            @NotNull @PositiveOrZero @JsonPropertyDescription("Fat, in grams.")
            Double fat,

            @NotNull @PositiveOrZero @JsonPropertyDescription("Carbohydrate, in grams.")
            Double carbs,

            @NotNull @PositiveOrZero @JsonPropertyDescription("Salt, in grams.")
            Double salt,

            @NotNull @PositiveOrZero @JsonPropertyDescription("Fibre, in grams.")
            Double fibre,

            @NotNull @PositiveOrZero @JsonPropertyDescription("Portions of veg and fruit, towards five a day.")
            Double portions) {

        Week.Frozen toDomain() {
            return new Week.Frozen(kcal, protein, fat, carbs, salt, fibre, portions);
        }
    }

    /** One day and what it eats. */
    public record Day(
            @NotBlank @JsonPropertyDescription("The day's name, such as Mon 2026-11-09.")
            String day,

            @NotEmpty
            @JsonPropertyDescription("The recipe ids each meal eats, under the meal's name. The meals are"
                    + " the ones the targets' meal_kcal names, and a day needs every one. A recipe named"
                    + " twice is eaten twice.")
            Map<String, List<String>> meals) {}
}
