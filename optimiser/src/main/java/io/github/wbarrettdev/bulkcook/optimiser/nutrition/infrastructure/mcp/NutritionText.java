package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.mcp;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.application.NutritionLookup.Addition;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodRecord;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodValues;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.IngredientNutrition;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository.Outcome;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository.Stored;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.Source;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Writes the nutrition tools' answers as text for a model to read. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NutritionText {

    static String of(Source source, String query, List<FoodRecord> records) {
        if (records.isEmpty()) {
            return "No " + source.title() + " records match \"" + query
                    + "\". Try fewer or plainer words, or another source.";
        }
        List<String> lines = new ArrayList<>();
        lines.add(source.title() + " records for \"" + query + "\", per 100g:");
        records.forEach(record -> lines.addAll(describe(record)));
        return String.join("\n", lines);
    }

    static String of(Addition addition) {
        IngredientNutrition rows = addition.ingredient();
        Stored stored = addition.stored();
        FoodValues per100g = rows.per100g();
        String named = "\"" + rows.ingredient() + "\" (" + (rows.unit().isEmpty() ? "each" : rows.unit()) + ", "
                + plain(rows.gramsPerUnit()) + "g)";
        List<String> lines = new ArrayList<>();
        lines.add(
                stored.wroteNothing()
                        ? named + " already has all its rows, so nothing was written. To keep these values as"
                                + " well, add them under a \"name|state\" name."
                        : "Added " + named + " from " + rows.record().citation() + ":");
        lines.add("  macros: " + said(stored.macros(), macros(per100g) + " per 100g"));
        lines.add("  salt: " + said(stored.salt(), plain(per100g.salt()) + "g per 100g"));
        lines.add("  fibre: "
                + (stored.fibre() == Outcome.NOT_GIVEN
                        ? "this record has no fibre value. Add it from another record, unless the food has none"
                        : said(stored.fibre(), plain(per100g.fibre()) + "g per 100g")));
        lines.add("  five-a-day: "
                + (stored.fiveADay() == Outcome.NOT_GIVEN
                        ? "doesn't count"
                        : said(stored.fiveADay(), rows.fiveADay().key())));
        return String.join("\n", lines);
    }

    private static List<String> describe(FoodRecord record) {
        List<String> lines = new ArrayList<>();
        lines.add(record.reference() + "  " + record.name()
                + (record.detail().isEmpty() ? "" : " [" + record.detail() + "]"));
        FoodValues per100g = record.per100g();
        lines.add("    " + macros(per100g) + ", fibre " + grams(per100g.fibre()) + ", salt " + grams(per100g.salt()));
        if (!per100g.missing().isEmpty()) {
            lines.add("    MISSING " + String.join(", ", per100g.missing()) + ": this record can't be added");
        }
        if (record.suggestedFiveADay() != null) {
            lines.add("    five-a-day: " + record.suggestedFiveADay().key() + " (from its food group; check it)");
        }
        return lines;
    }

    private static String macros(FoodValues per100g) {
        return (per100g.kcal() == null ? "no" : plain(per100g.kcal())) + " kcal, protein " + grams(per100g.protein())
                + ", fat " + grams(per100g.fat()) + ", carbs " + grams(per100g.carbs());
    }

    /** Only a row that was written shows its values: a row left alone holds its own, not this record's. */
    private static String said(Outcome outcome, String whatWasWritten) {
        return outcome == Outcome.WRITTEN ? "written (" + whatWasWritten + ")" : "already there, left as it was";
    }

    private static String grams(Double value) {
        return value == null ? "not given" : plain(value) + "g";
    }

    /** A number without a needless ".0". */
    private static String plain(double value) {
        BigDecimal exact = BigDecimal.valueOf(value).stripTrailingZeros();
        return exact.scale() < 0 ? exact.setScale(0).toPlainString() : exact.toPlainString();
    }
}
