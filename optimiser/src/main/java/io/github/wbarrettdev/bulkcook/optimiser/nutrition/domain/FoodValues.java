package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * What 100g of a food holds, stated the way a European food label states it: carbohydrate by its
 * own weight with fibre counted apart, and salt in place of sodium. A value the source doesn't give
 * is null.
 */
public record FoodValues(Double kcal, Double protein, Double fat, Double carbs, Double fibre, Double salt) {

    private static final double SALT_PER_SODIUM = 2.5;

    /** The values to the precision a label gives: one decimal place, and two for salt. */
    public static FoodValues rounded(Double kcal, Double protein, Double fat, Double carbs, Double fibre, Double salt) {
        return new FoodValues(
                round(kcal, 1), round(protein, 1), round(fat, 1), round(carbs, 1), round(fibre, 1), round(salt, 2));
    }

    /** Salt is sodium and chloride together, and weighs two and a half times its sodium. */
    public static double saltFromSodiumMg(double sodiumMg) {
        return sodiumMg * SALT_PER_SODIUM / 1000;
    }

    /** The values a nutrition row can't do without and this food lacks. Fibre is not one of them. */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        addIfNull(missing, "kcal", kcal);
        addIfNull(missing, "protein", protein);
        addIfNull(missing, "fat", fat);
        addIfNull(missing, "carbs", carbs);
        addIfNull(missing, "salt", salt);
        return List.copyOf(missing);
    }

    private static void addIfNull(List<String> missing, String name, Double value) {
        if (value == null) {
            missing.add(name);
        }
    }

    /** Rounds half to even on the number's exact value. */
    private static Double round(Double value, int places) {
        return value == null
                ? null
                : new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN).doubleValue();
    }
}
