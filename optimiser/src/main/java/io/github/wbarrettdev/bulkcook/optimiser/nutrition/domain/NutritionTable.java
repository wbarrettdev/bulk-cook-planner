package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The nutrition tables: what one unit of each ingredient holds. */
public record NutritionTable(
        List<Row> rows,
        Map<String, Double> saltPer100g,
        Map<String, Double> fibrePer100g,
        Map<String, String> categories,
        Map<String, Double> portionGrams) {

    private static final Set<String> COUNTED_BY_WEIGHT = Set.of("veg", "fruit", "dried");

    /** The same tables, counting veg and fruit at these grams a portion for each category. */
    public NutritionTable withPortionsOf(Map<String, Double> grams) {
        return new NutritionTable(rows, saltPer100g, fibrePer100g, categories, grams);
    }

    /**
     * What the table holds for one ingredient in one unit, put together from its four tables. Salt
     * and fibre are null when the table has no value, and the category when it doesn't count.
     */
    public record Entry(
            String ingredient,
            String unit,
            double gramsPerUnit,
            double kcal,
            double protein,
            double fat,
            double carbs,
            Double salt,
            Double fibre,
            String fiveADay,
            String source) {}

    /** The entries whose name has every word asked for, the plainest name first. */
    public List<Entry> entriesNamedWith(String words) {
        List<String> wanted = Arrays.stream(words.toLowerCase(Locale.ROOT).split("[\\s,]+"))
                .filter(word -> !word.isEmpty())
                .toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(row -> wanted.stream().allMatch(row.ingredient()::contains))
                .sorted(Comparator.comparingInt((Row row) -> row.ingredient().length())
                        .thenComparing(Row::ingredient)
                        .thenComparing(Row::unit))
                .map(this::entry)
                .toList();
    }

    /** A "name|text" row uses the plain name's salt, fibre and category when it has none of its own. */
    private Entry entry(Row row) {
        int bar = row.ingredient().indexOf('|');
        String plainName = bar < 0 ? row.ingredient() : row.ingredient().substring(0, bar);
        return new Entry(
                row.ingredient(),
                row.unit(),
                row.gramsPerUnit(),
                row.kcal(),
                row.protein(),
                row.fat(),
                row.carbs(),
                underEitherName(saltPer100g, row.ingredient(), plainName),
                underEitherName(fibrePer100g, row.ingredient(), plainName),
                underEitherName(categories, row.ingredient(), plainName),
                row.source());
    }

    /** One line of the macros table, for one ingredient in one unit. */
    public record Row(
            String ingredient,
            String unit,
            double gramsPerUnit,
            double kcal,
            double protein,
            double fat,
            double carbs,
            String source) {}

    /** The answer to a lookup: what one unit holds, or why it could not be read. */
    public sealed interface Lookup permits PerUnit, Missing {}

    /** The things a day is measured in. */
    public enum Nutrient {
        KCAL,
        PROTEIN,
        FAT,
        CARBS,
        SALT,
        FIBRE,
        VEG_AND_FRUIT,
        PULSE,
        JUICE;

        /** The nutrient's name in the targets: kcal, protein and so on. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What one unit of an ingredient holds, and the table row it was read from. */
    public record PerUnit(
            double kcal,
            double protein,
            double fat,
            double carbs,
            double salt,
            double grams,
            double fibre,
            boolean fibreKnown,
            double vegAndFruitPortions,
            double pulsePortions,
            double juicePortions,
            String table)
            implements Lookup {

        public double of(Nutrient nutrient) {
            return switch (nutrient) {
                case KCAL -> kcal;
                case PROTEIN -> protein;
                case FAT -> fat;
                case CARBS -> carbs;
                case SALT -> salt;
                case FIBRE -> fibre;
                case VEG_AND_FRUIT -> vegAndFruitPortions;
                case PULSE -> pulsePortions;
                case JUICE -> juicePortions;
            };
        }
    }

    /** Why an ingredient could not be read from the tables. */
    public record Missing(String what) implements Lookup {}

    /**
     * Looks an ingredient up. A "name|text" row wins when the
     * ingredient's note contains that text, then name and unit, then grams for millilitres.
     */
    public Lookup perUnit(String where, String name, String unit, String note) {
        String n = name.toLowerCase(Locale.ROOT);
        String u = Units.asStored(unit);
        String noteLower = note.toLowerCase(Locale.ROOT);
        Optional<Row> row = taggedRow(n, u, noteLower)
                .or(() -> rowFor(n, u))
                .or(() -> u.equals("ml") ? rowFor(n, "g") : u.equals("g") ? rowFor(n, "ml") : Optional.empty());
        if (row.isEmpty()) {
            return new Missing(where + ": \"" + name + "\" (" + (unit.isEmpty() ? "each" : unit)
                    + ") isn't in the nutrition table. Look it up and add it.");
        }
        Row found = row.get();
        Double salt = underEitherName(saltPer100g, found.ingredient(), n);
        if (salt == null) {
            return new Missing(where + ": \"" + name + "\" has no salt value. Look it up and add it.");
        }
        double grams = found.gramsPerUnit();
        Double fibre = underEitherName(fibrePer100g, found.ingredient(), n);
        String category = underEitherName(categories, found.ingredient(), n);
        return new PerUnit(
                grams * found.kcal() / 100,
                grams * found.protein() / 100,
                grams * found.fat() / 100,
                grams * found.carbs() / 100,
                grams * salt / 100,
                grams,
                fibre == null ? 0.0 : grams * fibre / 100,
                fibre != null,
                category != null && COUNTED_BY_WEIGHT.contains(category) ? grams / portionGrams.get(category) : 0.0,
                "pulse".equals(category) ? grams / portionGrams.get("pulse") : 0.0,
                "juice".equals(category) ? grams / portionGrams.get("juice") : 0.0,
                found.ingredient() + " (" + (found.unit().isEmpty() ? "each" : found.unit()) + "): " + found.source());
    }

    private Optional<Row> taggedRow(String name, String unit, String note) {
        return rows.stream()
                .filter(row -> {
                    int bar = row.ingredient().indexOf('|');
                    if (bar < 0) {
                        return false;
                    }
                    String tag = row.ingredient().substring(bar + 1);
                    return row.ingredient().substring(0, bar).equals(name)
                            && !tag.isEmpty()
                            && note.contains(tag)
                            && (row.unit().equals(unit) || (row.unit().equals("ml") && unit.equals("g")));
                })
                .findFirst();
    }

    private Optional<Row> rowFor(String name, String unit) {
        return rows.stream()
                .filter(row -> row.ingredient().equals(name) && row.unit().equals(unit))
                .findFirst();
    }

    private static <T> T underEitherName(Map<String, T> table, String rowName, String name) {
        T value = table.get(rowName);
        return value != null ? value : table.get(name);
    }
}
