package io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Targets;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The targets as a request sends them. Like {@link WeekRequest}, this record is the contract the
 * published schema is generated from.
 */
public record TargetsRequest(
        @Positive @JsonPropertyDescription("How many people each meal feeds. Left out, it is 2.")
        Integer people,

        @NotNull
        @JsonProperty("targets_per_person_day")
        @JsonPropertyDescription("What one person should eat in a day.")
        PerDay targetsPerPersonDay,

        @NotNull @JsonPropertyDescription("How far a day may miss a target and still pass its check.")
        Tolerances tolerances,

        @NotNull
        @PositiveOrZero
        @JsonProperty("salt_max_g_per_person_day")
        @JsonPropertyDescription("The most salt one person may eat in a day, in grams.")
        Double saltMaxGPerPersonDay,

        @NotEmpty
        @JsonProperty("meal_kcal")
        @JsonPropertyDescription("Each meal's name, with the fewest and the most calories it may have as"
                + " [fewest, most]. These names are the meals a day has.")
        Map<String, List<Double>> mealKcal,

        @NotNull
        @JsonPropertyDescription("How much it costs to miss each target. A bigger number is held to more tightly.")
        Weights weights,

        @NotEmpty
        @JsonProperty("ratio_bounds")
        @JsonPropertyDescription("For each ingredient role, how far an ingredient may move from its book"
                + " ratio to the anchor, as [lowest, highest] multiples. Must have default. The roles fat"
                + " and salty never go above the book, whatever their band says.")
        Map<String, List<Double>> ratioBounds,

        @NotNull
        @PositiveOrZero
        @JsonProperty("fibre_min_g_per_person_day")
        @JsonPropertyDescription("The least fibre one person should eat in a day, in grams.")
        Double fibreMinGPerPersonDay,

        @NotNull
        @JsonProperty("five_a_day")
        @JsonPropertyDescription("Portions of veg and fruit: how many a day, and what a portion weighs.")
        FiveADay fiveADay) {

    Targets toDomain() {
        return new Targets(
                people,
                targetsPerPersonDay == null ? null : targetsPerPersonDay.toDomain(),
                tolerances == null
                        ? null
                        : new Targets.Tolerances(
                                tolerances.kcalPct(), tolerances.proteinBelowG(), tolerances.fatOverG()),
                saltMaxGPerPersonDay,
                mealKcal,
                weights == null ? null : weights.toDomain(),
                ratioBounds,
                fibreMinGPerPersonDay,
                fiveADay == null
                        ? null
                        : new Targets.FiveADay(
                                fiveADay.minPerDay(),
                                fiveADay.portionG() == null
                                        ? null
                                        : fiveADay.portionG().toDomain()));
    }

    /** A day's targets for one person. */
    public record PerDay(
            @NotNull @Positive @JsonPropertyDescription("Calories.")
            Double kcal,

            @NotNull @Positive @JsonPropertyDescription("Protein, in grams.")
            Double protein,

            @NotNull @Positive @JsonPropertyDescription("Fat, in grams.")
            Double fat,

            @Positive @JsonPropertyDescription("Carbohydrate, in grams. Left out, carbs are not held to a target.")
            Double carbs) {

        Map<String, Double> toDomain() {
            Map<String, Double> targets = new LinkedHashMap<>();
            targets.put("kcal", kcal);
            targets.put("protein", protein);
            targets.put("fat", fat);
            targets.put("carbs", carbs);
            return withoutGaps(targets);
        }
    }

    public record Tolerances(
            @NotNull
            @PositiveOrZero
            @JsonProperty("kcal_pct")
            @JsonPropertyDescription("How far a day's calories may be from the target, as a percentage either way.")
            Double kcalPct,

            @NotNull
            @PositiveOrZero
            @JsonProperty("protein_below_g")
            @JsonPropertyDescription("How many grams under its protein target a day may be.")
            Double proteinBelowG,

            @NotNull
            @PositiveOrZero
            @JsonProperty("fat_over_g")
            @JsonPropertyDescription("How many grams over its fat target a day may be.")
            Double fatOverG) {}

    /** The cost of missing each target. One is needed for each nutrient that has a target. */
    public record Weights(
            @NotNull @PositiveOrZero @JsonPropertyDescription("For missing the calories.")
            Double kcal,

            @NotNull @PositiveOrZero @JsonPropertyDescription("For missing the protein.")
            Double protein,

            @NotNull @PositiveOrZero @JsonPropertyDescription("For missing the fat.")
            Double fat,

            @PositiveOrZero @JsonPropertyDescription("For missing the carbs. Needed when carbs have a target.")
            Double carbs,

            @NotNull
            @PositiveOrZero
            @JsonProperty("stay_near_book")
            @JsonPropertyDescription("For an ingredient drifting from its amount in the source recipe.")
            Double stayNearBook) {

        Map<String, Double> toDomain() {
            Map<String, Double> weights = new LinkedHashMap<>();
            weights.put("kcal", kcal);
            weights.put("protein", protein);
            weights.put("fat", fat);
            weights.put("carbs", carbs);
            weights.put("stay_near_book", stayNearBook);
            return withoutGaps(weights);
        }
    }

    public record FiveADay(
            @NotNull
            @PositiveOrZero
            @JsonProperty("min_per_day")
            @JsonPropertyDescription("The fewest portions one person should eat in a day.")
            Double minPerDay,

            @NotNull
            @JsonProperty("portion_g")
            @JsonPropertyDescription("What one portion of each kind weighs, in grams.")
            PortionGrams portionG) {}

    public record PortionGrams(
            @NotNull @Positive @JsonPropertyDescription("Vegetables.")
            Double veg,

            @NotNull @Positive @JsonPropertyDescription("Fruit.")
            Double fruit,

            @NotNull @Positive @JsonPropertyDescription("Dried fruit.")
            Double dried,

            @NotNull
            @Positive
            @JsonPropertyDescription("Beans and lentils. They count once a day at most, however much is eaten.")
            Double pulse,

            @NotNull @Positive @JsonPropertyDescription("Juice, in grams. It counts once a day at most.")
            Double juice) {

        Map<String, Double> toDomain() {
            Map<String, Double> grams = new LinkedHashMap<>();
            grams.put("veg", veg);
            grams.put("fruit", fruit);
            grams.put("dried", dried);
            grams.put("pulse", pulse);
            grams.put("juice", juice);
            return withoutGaps(grams);
        }
    }

    /** The same values without the ones that weren't given, which the domain then notices are missing. */
    private static Map<String, Double> withoutGaps(Map<String, Double> values) {
        values.values().removeIf(Objects::isNull);
        return values;
    }
}
