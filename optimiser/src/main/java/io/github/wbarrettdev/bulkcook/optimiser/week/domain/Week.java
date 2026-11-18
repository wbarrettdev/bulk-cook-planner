package io.github.wbarrettdev.bulkcook.optimiser.week.domain;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.Nutrient;
import java.util.List;
import java.util.Map;

/** A week as it is planned: the recipes to scale, and which of them each day's meals eat. */
public record Week(Map<String, Recipe> recipes, List<Day> days) {

    /**
     * A recipe to cook, or a batch to take from the freezer. Amounts are per cook. A cooked recipe
     * keeps its other ingredients in proportion to its anchor, the main ingredient.
     */
    public record Recipe(
            String title,
            String file,
            Integer portions,
            Integer cooks,
            String anchor,
            Double maxBatchG,
            List<Ingredient> ingredients,
            Frozen frozen) {

        public boolean isFrozen() {
            return frozen != null;
        }

        public List<Ingredient> ingredients() {
            return ingredients == null ? List.of() : ingredients;
        }
    }

    /**
     * One ingredient of a recipe. With a book amount it follows the anchor, within its role's band.
     * Without one it is a side or made to order, sized freely between min and max.
     */
    public record Ingredient(
            String name,
            String unit,
            String note,
            Double book,
            Double min,
            Double max,
            Double step,
            String role,
            Double lo,
            Double hi,
            Boolean perPortion) {

        public String unit() {
            return unit == null ? "" : unit;
        }

        public String note() {
            return note == null ? "" : note;
        }

        public boolean isPerPortion() {
            return Boolean.TRUE.equals(perPortion);
        }
    }

    /** A plate from the freezer: its nutrition is already known and nothing is cooked for it. */
    public record Frozen(
            Double kcal, Double protein, Double fat, Double carbs, Double salt, Double fibre, Double portions) {

        /** What one plate holds of a nutrient. A frozen plate's pulses and juice aren't recorded. */
        public double of(Nutrient nutrient) {
            return switch (nutrient) {
                case KCAL -> kcal;
                case PROTEIN -> protein;
                case FAT -> fat;
                case CARBS -> carbs;
                case SALT -> salt;
                case FIBRE -> fibre;
                case VEG_AND_FRUIT -> portions;
                case PULSE, JUICE -> 0.0;
            };
        }
    }

    public record Day(String day, Map<String, List<String>> meals) {}
}
