package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.cofid;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FiveADayCategory;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodRecord;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodReference;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodValues;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.Source;

/**
 * One CoFID food as the dataset publishes it, for 100g of the edible part. CoFID measures some
 * things differently from a food label, so this is where its figures are turned into a label's.
 */
record CofidFood(
        String code,
        String name,
        String group,
        Double kcal,
        Double protein,
        Double fat,
        Double carbohydrate,
        Double starch,
        Double totalSugars,
        Double aoacFibre,
        Double nsp,
        Double sodiumMg) {

    /** A gram of starch breaks down into 1.10g of simple sugar, and a gram of sugars into about 1.05g. */
    private static final double STARCH_AS_MONOSACCHARIDE = 1.10;

    private static final double SUGARS_AS_MONOSACCHARIDE = 1.05;
    private static final double MIXED_AS_MONOSACCHARIDE = 1.08;

    /** The AOAC method finds about a third more fibre than the older NSP measure (SACN's conversion). */
    private static final double AOAC_PER_NSP = 1.33;

    FoodRecord asLabelled() {
        return new FoodRecord(
                new FoodReference(Source.COFID, code),
                name,
                "",
                FoodValues.rounded(
                        kcal,
                        protein,
                        fat,
                        carbsByWeight(),
                        fibre(),
                        sodiumMg == null ? null : FoodValues.saltFromSodiumMg(sodiumMg)),
                suggestedFiveADay(),
                fibreIsEstimated() ? "fibre from NSP x1.33" : "");
    }

    /**
     * CoFID gives carbohydrate as the simple sugars it would break down into, which weigh more than
     * the carbohydrate itself. A label gives its own weight.
     */
    Double carbsByWeight() {
        if (starch != null && totalSugars != null) {
            return starch / STARCH_AS_MONOSACCHARIDE + totalSugars / SUGARS_AS_MONOSACCHARIDE;
        }
        return carbohydrate == null ? null : carbohydrate / MIXED_AS_MONOSACCHARIDE;
    }

    /** Fibre as labels measure it (AOAC), worked out from the older NSP figure where that is all there is. */
    Double fibre() {
        if (aoacFibre != null) {
            return aoacFibre;
        }
        return nsp == null ? null : nsp * AOAC_PER_NSP;
    }

    boolean fibreIsEstimated() {
        return aoacFibre == null && nsp != null;
    }

    /** CoFID's food groups: DB beans and lentils, DF and DG vegetables, F fruit, FB dried, FC juices. */
    FiveADayCategory suggestedFiveADay() {
        if (group.startsWith("DB")) {
            return FiveADayCategory.PULSE;
        }
        if (group.startsWith("DG") || group.startsWith("DF")) {
            return FiveADayCategory.VEG;
        }
        if (group.startsWith("FC")) {
            return FiveADayCategory.JUICE;
        }
        if (group.startsWith("FB")) {
            return FiveADayCategory.DRIED;
        }
        return group.startsWith("F") ? FiveADayCategory.FRUIT : null;
    }
}
