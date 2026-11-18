package io.github.wbarrettdev.bulkcook.optimiser.week.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** What a person should eat in a day, and how far a recipe may drift from its source. */
public record Targets(
        Integer people,
        Map<String, Double> targetsPerPersonDay,
        Tolerances tolerances,
        Double saltMaxGPerPersonDay,
        Map<String, List<Double>> mealKcal,
        Map<String, Double> weights,
        Map<String, List<Double>> ratioBounds,
        Double fibreMinGPerPersonDay,
        FiveADay fiveADay) {

    /** A day is always held to these. Carbs are held to a target only when one is given. */
    private static final List<String> ALWAYS_TARGETED = List.of("kcal", "protein", "fat");

    private static final String DEFAULT_ROLE = "default";
    private static final String STAY_NEAR_BOOK = "stay_near_book";
    private static final List<String> PORTION_KINDS = List.of("veg", "fruit", "dried", "pulse", "juice");

    public Integer people() {
        return people == null ? 2 : people;
    }

    public record Tolerances(Double kcalPct, Double proteinBelowG, Double fatOverG) {}

    /** How many portions of veg and fruit a day, and the grams that make one portion of each kind. */
    public record FiveADay(Double minPerDay, Map<String, Double> portionG) {}

    /** These targets, once nothing a solve relies on is missing. Every gap is named, not only the first. */
    public Targets checked() {
        List<String> gaps = new ArrayList<>();
        if (people() < 1) {
            gaps.add("people must be at least 1");
        }
        Map<String, Double> perDay = orEmpty(targetsPerPersonDay);
        Map<String, Double> weight = orEmpty(weights);
        ALWAYS_TARGETED.stream()
                .filter(nutrient -> perDay.get(nutrient) == null)
                .forEach(nutrient -> gaps.add("targets_per_person_day needs " + nutrient));
        perDay.keySet().stream()
                .filter(nutrient -> weight.get(nutrient) == null)
                .forEach(nutrient -> gaps.add("weights needs " + nutrient));
        if (weight.get(STAY_NEAR_BOOK) == null) {
            gaps.add("weights needs " + STAY_NEAR_BOOK);
        }
        if (tolerances == null
                || tolerances.kcalPct() == null
                || tolerances.proteinBelowG() == null
                || tolerances.fatOverG() == null) {
            gaps.add("tolerances needs kcal_pct, protein_below_g and fat_over_g");
        }
        if (saltMaxGPerPersonDay == null) {
            gaps.add("salt_max_g_per_person_day is missing");
        }
        if (fibreMinGPerPersonDay == null) {
            gaps.add("fibre_min_g_per_person_day is missing");
        }
        if (orEmpty(mealKcal).isEmpty()) {
            gaps.add("meal_kcal needs each meal with its lowest and highest calories");
        }
        orEmpty(mealKcal).forEach((meal, allowed) -> {
            if (isNotAPair(allowed)) {
                gaps.add("meal_kcal " + meal + " needs two numbers: lowest and highest");
            }
        });
        if (!orEmpty(ratioBounds).containsKey(DEFAULT_ROLE)) {
            gaps.add("ratio_bounds needs " + DEFAULT_ROLE);
        }
        orEmpty(ratioBounds).forEach((role, band) -> {
            if (isNotAPair(band)) {
                gaps.add("ratio_bounds " + role + " needs two numbers: lowest and highest");
            }
        });
        if (fiveADay == null || fiveADay.minPerDay() == null) {
            gaps.add("five_a_day needs min_per_day");
        }
        Map<String, Double> portionGrams = fiveADay == null ? Map.of() : orEmpty(fiveADay.portionG());
        PORTION_KINDS.stream()
                .filter(kind -> !(portionGrams.get(kind) != null && portionGrams.get(kind) > 0))
                .forEach(kind -> gaps.add("five_a_day portion_g needs the grams in a portion of " + kind));
        if (!gaps.isEmpty()) {
            throw new InvalidWeek("The targets need fixing:\n  " + String.join("\n  ", gaps));
        }
        return this;
    }

    private static boolean isNotAPair(List<Double> numbers) {
        return numbers == null || numbers.size() != 2 || numbers.stream().anyMatch(Objects::isNull);
    }

    private static <V> Map<String, V> orEmpty(Map<String, V> map) {
        return map == null ? Map.of() : map;
    }
}
