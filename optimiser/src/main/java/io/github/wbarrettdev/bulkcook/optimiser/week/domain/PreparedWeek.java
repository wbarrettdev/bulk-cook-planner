package io.github.wbarrettdev.bulkcook.optimiser.week.domain;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.Lookup;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.Missing;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.PerUnit;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week.Day;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week.Ingredient;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week.Recipe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A week that has been checked and is ready to solve: every ingredient of every cooked recipe is
 * a line with its nutrition, its step and the band it may move in.
 */
public record PreparedWeek(
        Week week, List<Line> lines, Map<String, List<Line>> linesByRecipe, Map<String, Integer> plates) {

    private static final Map<String, Double> DEFAULT_STEP =
            Map.of("g", 5.0, "ml", 5.0, "tsp", 0.25, "tbsp", 0.5, "clove", 1.0, "", 1.0);

    /** How far an ingredient may drift from its book ratio to the anchor. */
    public record Band(double lo, double hi) {}

    /** One ingredient of one cooked recipe, sized in whole steps. */
    public record Line(
            int index,
            String recipe,
            Ingredient ingredient,
            double step,
            PerUnit per,
            Band band,
            boolean anchor,
            int unitsPerCook,
            int cooks,
            int portions) {

        private static final Set<String> NEVER_ABOVE_THE_BOOK = Set.of("salty", "fat");

        public boolean followsTheAnchor() {
            return band != null;
        }

        /** Rounding to a whole step may take an ingredient half a step over its band, but never fat or salt. */
        public double slackUp() {
            String role = ingredient.role();
            return role != null && NEVER_ABOVE_THE_BOOK.contains(role) ? 0 : step / 2;
        }

        /** How much of this ingredient the source uses for each unit of the anchor. */
        public double ratioTo(Line anchor) {
            return ingredient.book() / anchor.ingredient().book();
        }
    }

    public Line anchorOf(String recipe) {
        return linesByRecipe.get(recipe).stream()
                .filter(Line::anchor)
                .findFirst()
                .orElse(null);
    }

    public static PreparedWeek of(Week week, Targets targets, NutritionTable table) {
        if (week.recipes() == null || week.recipes().isEmpty()) {
            throw new InvalidWeek("The week has no recipes.");
        }
        if (week.days() == null || week.days().isEmpty()) {
            throw new InvalidWeek("The week has no days.");
        }
        Set<String> errors = new LinkedHashSet<>();
        List<Line> lines = new ArrayList<>();
        Map<String, List<Line>> byRecipe = new LinkedHashMap<>();
        week.recipes().forEach((id, recipe) -> byRecipe.put(id, linesOf(id, recipe, targets, table, lines, errors)));
        Map<String, Integer> plates = plates(week, targets, errors);
        if (!errors.isEmpty()) {
            throw new InvalidWeek("The week needs fixing:\n  " + String.join("\n  ", errors));
        }
        return new PreparedWeek(week, List.copyOf(lines), byRecipe, plates);
    }

    private static List<Line> linesOf(
            String id, Recipe recipe, Targets targets, NutritionTable table, List<Line> all, Set<String> errors) {
        String where = "recipe \"" + id + "\"";
        List<Line> lines = new ArrayList<>();
        if (recipe == null) {
            errors.add(where + ": is empty");
            return lines;
        }
        boolean portionsAreValid = recipe.portions() != null && recipe.portions() > 0;
        if (!portionsAreValid) {
            errors.add(where + ": portions must be a whole number above 0");
        }
        if (recipe.isFrozen()) {
            frozenProblems(where, recipe, errors);
            return lines;
        }
        int cooks = recipe.cooks() == null ? 1 : recipe.cooks();
        if (!(cooks > 0 && portionsAreValid && recipe.portions() % cooks == 0)) {
            errors.add(where + ": cooks must be a whole number that divides portions");
            return lines;
        }
        if (recipe.ingredients().isEmpty()) {
            errors.add(where + ": no ingredients");
            return lines;
        }
        boolean hasAnchor = recipe.anchor() != null && !recipe.anchor().isEmpty();
        if (recipe.ingredients().stream().anyMatch(i -> i.book() != null) && !hasAnchor) {
            errors.add(where + ": uses book amounts but has no anchor");
        }
        for (Ingredient ingredient : recipe.ingredients()) {
            if (ingredient == null
                    || ingredient.name() == null
                    || ingredient.name().isBlank()) {
                errors.add(where + ": every ingredient needs a name");
                continue;
            }
            Line line = lineOf(all.size(), id, where, recipe, cooks, ingredient, targets, table, errors);
            all.add(line);
            lines.add(line);
        }
        if (hasAnchor && lines.stream().noneMatch(Line::anchor)) {
            errors.add(where + ": anchor \"" + recipe.anchor() + "\" isn't one of its ingredients");
        }
        return lines;
    }

    private static void frozenProblems(String where, Recipe recipe, Set<String> errors) {
        Week.Frozen frozen = recipe.frozen();
        Map<String, Double> needed = new LinkedHashMap<>();
        needed.put("kcal", frozen.kcal());
        needed.put("protein", frozen.protein());
        needed.put("fat", frozen.fat());
        needed.put("carbs", frozen.carbs());
        needed.put("salt", frozen.salt());
        needed.put("fibre", frozen.fibre());
        needed.put("portions", frozen.portions());
        needed.forEach((name, value) -> {
            if (value == null) {
                errors.add(where + ": frozen needs " + name + " per plate");
            }
        });
        if (!recipe.ingredients().isEmpty()) {
            errors.add(where + ": a frozen recipe has no ingredients");
        }
    }

    private static Line lineOf(
            int index,
            String id,
            String where,
            Recipe recipe,
            int cooks,
            Ingredient ingredient,
            Targets targets,
            NutritionTable table,
            Set<String> errors) {
        String ingredientWhere = where + ", " + ingredient.name();
        Lookup lookup = table.perUnit(where, ingredient.name(), ingredient.unit(), ingredient.note());
        if (lookup instanceof Missing missing) {
            errors.add(missing.what());
        }
        double step = ingredient.step() != null
                ? ingredient.step()
                : DEFAULT_STEP.getOrDefault(ingredient.unit().toLowerCase(Locale.ROOT), 1.0);
        if (!(step > 0)) {
            errors.add(ingredientWhere + ": step must be above 0");
        }
        boolean isAnchor = ingredient.name().equals(recipe.anchor());
        Double book = ingredient.book();
        if (book != null && !(book > 0)) {
            errors.add(ingredientWhere + ": a book amount must be above 0");
        }
        boolean hasRange = ingredient.min() != null && ingredient.max() != null;
        if (ingredient.isPerPortion() && book != null) {
            errors.add(ingredientWhere + ": can't be both per_portion and a book amount");
        }
        if (isAnchor && (book == null || !hasRange)) {
            errors.add(ingredientWhere + ": the anchor needs book, min and max (per cook)");
        }
        if (!isAnchor && book == null && !hasRange) {
            errors.add(ingredientWhere + ": needs a book amount, or min and max");
        }
        if (book == null && (ingredient.role() != null || ingredient.lo() != null || ingredient.hi() != null)) {
            errors.add(ingredientWhere + ": role, lo and hi only apply to book amounts");
        }
        Band band = book != null && !isAnchor ? bandOf(ingredientWhere, ingredient, targets, errors) : null;
        int unitsPerCook = ingredient.isPerPortion() ? recipe.portions() / cooks : 1;
        PerUnit per = lookup instanceof PerUnit found ? found : null;
        return new Line(index, id, ingredient, step, per, band, isAnchor, unitsPerCook, cooks, recipe.portions());
    }

    private static Band bandOf(String where, Ingredient ingredient, Targets targets, Set<String> errors) {
        List<Double> base = targets.ratioBounds().get(ingredient.role() == null ? "default" : ingredient.role());
        if (base == null) {
            errors.add(where + ": unknown role \"" + ingredient.role() + "\"");
            return null;
        }
        Band band = new Band(
                ingredient.lo() != null ? ingredient.lo() : base.get(0),
                ingredient.hi() != null ? ingredient.hi() : base.get(1));
        if (band.lo() > band.hi()) {
            errors.add(where + ": lo " + band.lo() + " is above hi " + band.hi());
        }
        return band;
    }

    /** How many plates of each recipe the week eats: once per person for every meal that names it. */
    private static Map<String, Integer> plates(Week week, Targets targets, Set<String> errors) {
        Set<String> meals = targets.mealKcal().keySet();
        Map<String, Integer> uses = new LinkedHashMap<>();
        week.recipes().keySet().forEach(id -> uses.put(id, 0));
        for (int i = 0; i < week.days().size(); i++) {
            Day day = week.days().get(i);
            if (day == null
                    || day.day() == null
                    || day.day().isEmpty()
                    || day.meals() == null
                    || day.meals().isEmpty()) {
                errors.add("day " + (i + 1) + ": needs day and meals");
                continue;
            }
            day.meals().forEach((meal, ids) -> {
                if (!meals.contains(meal)) {
                    errors.add(day.day() + ": unknown meal \"" + meal + "\" (use " + String.join(", ", meals) + ")");
                }
                for (String id : ids == null ? List.<String>of() : ids) {
                    if (uses.containsKey(id)) {
                        uses.merge(id, 1, Integer::sum);
                    } else {
                        errors.add(day.day() + " " + meal + ": unknown recipe \"" + id + "\"");
                    }
                }
            });
            for (String meal : meals) {
                List<String> ids = day.meals().get(meal);
                if (ids == null || ids.isEmpty()) {
                    errors.add(day.day() + ": no " + meal);
                }
            }
        }
        Map<String, Integer> plates = new LinkedHashMap<>();
        week.recipes().forEach((id, recipe) -> {
            int eaten = uses.get(id);
            plates.put(id, eaten * targets.people());
            if (eaten == 0) {
                errors.add("recipe \"" + id + "\": not eaten on any day");
            } else if (recipe != null && recipe.portions() != null && plates.get(id) > recipe.portions()) {
                errors.add("recipe \"" + id + "\": eaten " + eaten + " times (" + plates.get(id)
                        + " plates) but makes only " + recipe.portions() + " portions");
            }
        });
        return plates;
    }
}
