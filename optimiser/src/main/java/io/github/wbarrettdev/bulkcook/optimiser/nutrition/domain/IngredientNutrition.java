package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import java.util.List;
import java.util.Locale;

/**
 * The nutrition rows for one ingredient measured in one unit, every value taken from one food
 * record. No value is ever typed by hand.
 *
 * @param ingredient the name as recipes write it, lower case. "white rice|dry" is the row used when
 *     an ingredient's note says dry.
 * @param unit g, ml, tsp and so on. Empty means one of the thing, such as one egg.
 * @param fiveADay the category it counts under, or null when it doesn't count towards five a day.
 */
public record IngredientNutrition(
        String ingredient, String unit, double gramsPerUnit, FoodRecord record, FiveADayCategory fiveADay) {

    public IngredientNutrition {
        ingredient = normalised(ingredient);
        unit = Units.asStored(unit);
        if (ingredient.isEmpty()) {
            throw new LookupProblem("The ingredient needs a name, written as recipes write it.");
        }
        if (!(gramsPerUnit > 0)) {
            throw new LookupProblem("One " + (unit.isEmpty() ? ingredient : unit)
                    + " has to weigh more than 0g. Give its weight in grams (1 for g or ml).");
        }
        List<String> missing = record.per100g().missing();
        if (!missing.isEmpty()) {
            throw new LookupProblem(
                    record.citation() + " has no " + String.join(", ", missing) + ". Pick another record.");
        }
    }

    public FoodValues per100g() {
        return record.per100g();
    }

    public boolean hasFibre() {
        return per100g().fibre() != null;
    }

    public boolean countsTowardsFiveADay() {
        return fiveADay != null;
    }

    private static String normalised(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }
}
