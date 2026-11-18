package io.github.wbarrettdev.bulkcook.optimiser.week.domain;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.Nutrient;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.PreparedWeek.Line;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week.Day;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week.Recipe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The solved week: every amount, each day's totals, the shopping list, and the checks it passed or failed. */
public record WeekReport(
        Map<String, RecipeResult> recipes,
        List<DayResult> days,
        List<ShoppingLine> shopping,
        List<Check> checks,
        boolean pass) {

    private static final List<String> PORTION_KEYS =
            List.of("kcal", "protein", "fat", "carbs", "salt", "fibre", "p_vf", "p_pulse", "p_juice");
    private static final List<String> TWO_PLACES = List.of("salt", "p_vf", "p_pulse", "p_juice");

    public record RecipeResult(
            String title,
            boolean frozen,
            int portions,
            int cooks,
            int servingsPerCook,
            int platesEaten,
            int toFreeze,
            long gramsPerCook,
            Map<String, Double> perPortion,
            List<IngredientResult> ingredients) {}

    public record IngredientResult(
            String name,
            String unit,
            String note,
            double total,
            double perCook,
            Double perPlate,
            Double timesBook,
            boolean fibreUnknown,
            String table) {}

    public record DayResult(String day, Map<String, Double> total, Map<String, Map<String, Double>> meals) {}

    public record ShoppingLine(String name, String unit, double total) {}

    public record Check(boolean ok, String what) {}

    public static WeekReport of(PreparedWeek prepared, long[] steps, Targets targets) {
        List<Check> checks = new ArrayList<>();
        Map<String, RecipeResult> recipes = new LinkedHashMap<>();
        prepared.week()
                .recipes()
                .forEach((id, recipe) -> recipes.put(
                        id,
                        recipe.isFrozen()
                                ? frozen(recipe, prepared.plates().get(id))
                                : cooked(id, recipe, prepared, steps, checks)));
        List<DayResult> days = prepared.week().days().stream()
                .map(day -> day(day, recipes, targets, checks))
                .toList();
        boolean pass = checks.stream().allMatch(Check::ok);
        return new WeekReport(recipes, days, shopping(recipes), List.copyOf(checks), pass);
    }

    private static RecipeResult frozen(Recipe recipe, int plates) {
        Map<String, Double> perPortion = new LinkedHashMap<>();
        perPortion.put("kcal", recipe.frozen().kcal());
        perPortion.put("protein", recipe.frozen().protein());
        perPortion.put("fat", recipe.frozen().fat());
        perPortion.put("carbs", recipe.frozen().carbs());
        perPortion.put("salt", recipe.frozen().salt());
        perPortion.put("fibre", recipe.frozen().fibre());
        perPortion.put("p_vf", recipe.frozen().portions());
        perPortion.put("p_pulse", 0.0);
        perPortion.put("p_juice", 0.0);
        return new RecipeResult(recipe.title(), true, recipe.portions(), 0, 0, plates, 0, 0, perPortion, List.of());
    }

    private static RecipeResult cooked(
            String id, Recipe recipe, PreparedWeek prepared, long[] steps, List<Check> checks) {
        List<Line> lines = prepared.linesByRecipe().get(id);
        Line anchor = prepared.anchorOf(id);
        double anchorPerCook = anchor == null ? 0 : perCook(anchor, steps);
        Map<String, Double> totals = new LinkedHashMap<>();
        PORTION_KEYS.forEach(key -> totals.put(key, 0.0));
        totals.put("grams", 0.0);
        List<IngredientResult> ingredients = new ArrayList<>();
        for (Line line : lines) {
            double total = total(line, steps);
            for (Nutrient nutrient : Nutrient.values()) {
                totals.merge(portionKey(nutrient), total * line.per().of(nutrient), Double::sum);
            }
            totals.merge("grams", total * line.per().grams(), Double::sum);
            ingredients.add(ingredient(id, line, total, anchor, anchorPerCook, steps, checks));
        }
        int cooks = lines.getFirst().cooks();
        double gramsPerCook = totals.get("grams") / cooks;
        if (recipe.maxBatchG() != null && recipe.maxBatchG() != 0) {
            checks.add(new Check(
                    gramsPerCook <= recipe.maxBatchG() + 1,
                    id + ": " + Numbers.round(gramsPerCook) + "g per cook fits the pot (max "
                            + Numbers.plain(recipe.maxBatchG()) + "g)"));
        }
        Map<String, Double> perPortion = new LinkedHashMap<>();
        totals.forEach((key, value) ->
                perPortion.put(key, Numbers.round(value / recipe.portions(), TWO_PLACES.contains(key) ? 2 : 1)));
        int plates = prepared.plates().get(id);
        return new RecipeResult(
                recipe.title(),
                false,
                recipe.portions(),
                cooks,
                recipe.portions() / cooks,
                plates,
                recipe.portions() - plates,
                Numbers.round(gramsPerCook),
                perPortion,
                ingredients);
    }

    private static IngredientResult ingredient(
            String id, Line line, double total, Line anchor, double anchorPerCook, long[] steps, List<Check> checks) {
        String name = line.ingredient().name();
        double perCook = perCook(line, steps);
        double gramsPerPlate = total * line.per().grams() / line.portions();
        if (!line.per().fibreKnown()) {
            checks.add(new Check(
                    gramsPerPlate < 20,
                    id + ": no fibre value for " + name + " (" + Numbers.round(gramsPerPlate)
                            + "g a plate): look it up and add it"));
        }
        Double timesBook = null;
        if (line.followsTheAnchor()) {
            double ratio = line.ratioTo(anchor);
            double slackDown = line.step() / 2;
            double slackUp = line.slackUp();
            timesBook = anchorPerCook != 0 ? Numbers.round((perCook / anchorPerCook) / ratio, 2) : 0.0;
            double lowest = anchorPerCook != 0
                    ? line.band().lo() - slackDown / (ratio * anchorPerCook)
                    : line.band().lo();
            double highest = anchorPerCook != 0
                    ? line.band().hi() + slackUp / (ratio * anchorPerCook)
                    : line.band().hi();
            checks.add(new Check(
                    lowest - 0.005 <= timesBook && timesBook <= highest + 0.005,
                    id + ": " + name + " at " + timesBook + "x the book ratio (allowed " + Numbers.round(lowest, 2)
                            + "-" + Numbers.round(highest, 2) + "x after rounding to whole steps)"));
        }
        return new IngredientResult(
                name,
                line.ingredient().unit(),
                line.ingredient().note(),
                total,
                perCook,
                line.ingredient().isPerPortion() ? Numbers.round(line.step() * steps[line.index()], 4) : null,
                timesBook,
                !line.per().fibreKnown(),
                line.per().table());
    }

    private static double perCook(Line line, long[] steps) {
        return Numbers.round(line.step() * line.unitsPerCook() * steps[line.index()], 4);
    }

    private static double total(Line line, long[] steps) {
        return Numbers.round(perCook(line, steps) * line.cooks(), 4);
    }

    private static String portionKey(Nutrient nutrient) {
        return switch (nutrient) {
            case VEG_AND_FRUIT -> "p_vf";
            case PULSE -> "p_pulse";
            case JUICE -> "p_juice";
            default -> nutrient.key();
        };
    }

    private static DayResult day(Day day, Map<String, RecipeResult> recipes, Targets targets, List<Check> checks) {
        Map<String, Map<String, Double>> meals = new LinkedHashMap<>();
        day.meals().forEach((meal, ids) -> meals.put(meal, eaten(ids, recipes)));
        Map<String, Double> total =
                eaten(day.meals().values().stream().flatMap(List::stream).toList(), recipes);
        String name = day.day();
        double kcalTarget = targets.targetsPerPersonDay().get("kcal");
        double proteinTarget = targets.targetsPerPersonDay().get("protein");
        double fatTarget = targets.targetsPerPersonDay().get("fat");
        Targets.Tolerances tolerances = targets.tolerances();
        checks.add(new Check(
                Math.abs(total.get("kcal") - kcalTarget) <= kcalTarget * tolerances.kcalPct() / 100,
                name + ": " + Numbers.round(total.get("kcal")) + " kcal (target " + Numbers.plain(kcalTarget) + " ±"
                        + Numbers.plain(tolerances.kcalPct()) + "%)"));
        checks.add(new Check(
                total.get("protein") >= proteinTarget - tolerances.proteinBelowG(),
                name + ": " + Numbers.round(total.get("protein")) + "g protein (target " + Numbers.plain(proteinTarget)
                        + "g, at most " + Numbers.plain(tolerances.proteinBelowG()) + "g under)"));
        checks.add(new Check(
                total.get("fat") <= fatTarget + tolerances.fatOverG(),
                name + ": " + Numbers.round(total.get("fat")) + "g fat (target " + Numbers.plain(fatTarget)
                        + "g, at most " + Numbers.plain(tolerances.fatOverG()) + "g over)"));
        checks.add(new Check(
                total.get("salt") <= targets.saltMaxGPerPersonDay() + 0.05,
                name + ": " + Numbers.fixed(total.get("salt"), 1) + "g salt (max "
                        + Numbers.plain(targets.saltMaxGPerPersonDay()) + "g)"));
        checks.add(new Check(
                total.get("fibre") >= targets.fibreMinGPerPersonDay() - 0.05,
                name + ": " + Numbers.fixed(total.get("fibre"), 0) + "g fibre (at least "
                        + Numbers.plain(targets.fibreMinGPerPersonDay()) + "g)"));
        checks.add(new Check(
                total.get("five_a_day") >= targets.fiveADay().minPerDay() - 0.05,
                name + ": " + total.get("five_a_day") + " portions of veg and fruit (at least "
                        + Numbers.plain(targets.fiveADay().minPerDay()) + ")"));
        meals.forEach((meal, eaten) -> {
            List<Double> allowed = targets.mealKcal().get(meal);
            checks.add(new Check(
                    allowed.get(0) - 1 <= eaten.get("kcal") && eaten.get("kcal") <= allowed.get(1) + 1,
                    name + " " + meal + ": " + Numbers.round(eaten.get("kcal")) + " kcal (allowed "
                            + Numbers.plain(allowed.get(0)) + "-" + Numbers.plain(allowed.get(1)) + ")"));
        });
        return new DayResult(name, total, meals);
    }

    /** One person's intake from these recipes, a plate of each. Pulses and juice count once at most. */
    private static Map<String, Double> eaten(List<String> ids, Map<String, RecipeResult> recipes) {
        Map<String, Double> sum = new LinkedHashMap<>();
        PORTION_KEYS.forEach(key -> sum.put(key, 0.0));
        for (String id : ids) {
            for (String key : PORTION_KEYS) {
                sum.merge(key, recipes.get(id).perPortion().getOrDefault(key, 0.0), Double::sum);
            }
        }
        double fiveADay = sum.remove("p_vf") + Math.min(sum.remove("p_pulse"), 1) + Math.min(sum.remove("p_juice"), 1);
        sum.put("five_a_day", Numbers.round(fiveADay, 1));
        return sum;
    }

    /** The week's total of each ingredient, for the shopping list. Frozen food is already bought. */
    private static List<ShoppingLine> shopping(Map<String, RecipeResult> recipes) {
        Map<List<String>, Double> totals = new TreeMap<>((a, b) -> {
            int byName = a.get(0).compareTo(b.get(0));
            return byName != 0 ? byName : a.get(1).compareTo(b.get(1));
        });
        recipes.values()
                .forEach(recipe -> recipe.ingredients()
                        .forEach(i -> totals.merge(
                                List.of(i.name(), i.unit()), i.total(), (a, b) -> Numbers.round(a + b, 2))));
        return totals.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new ShoppingLine(
                        entry.getKey().get(0), entry.getKey().get(1), Numbers.round(entry.getValue(), 2)))
                .toList();
    }
}
