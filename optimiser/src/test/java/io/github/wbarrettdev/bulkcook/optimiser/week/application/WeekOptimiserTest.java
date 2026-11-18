package io.github.wbarrettdev.bulkcook.optimiser.week.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.wbarrettdev.bulkcook.optimiser.Fixtures;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.InvalidWeek;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Targets;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week.Day;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week.Frozen;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week.Ingredient;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week.Recipe;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport.Check;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport.DayResult;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport.IngredientResult;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport.RecipeResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Solves a small made-up week kept with the tests. */
class WeekOptimiserTest {

    private final Targets targets = Fixtures.targets();
    private final Week week = Fixtures.week();
    private final WeekOptimiser optimiser = Fixtures.optimiser();

    @Test
    void solvesTheWeekWithEveryCheckPassing() {
        WeekReport report = optimiser.optimise(week, targets);

        assertThat(report.checks()).isNotEmpty().allMatch(Check::ok);
        assertThat(report.pass()).isTrue();
    }

    @Test
    void landsEveryDayWithinFivePercentOfItsCalories() {
        WeekReport report = optimiser.optimise(week, targets);

        assertThat(report.days()).extracting(DayResult::day).containsExactly("Mon", "Tue");
        assertThat(report.days())
                .allSatisfy(day -> assertThat(day.total().get("kcal")).isBetween(1900.0, 2100.0));
    }

    @Test
    void sizesEveryIngredientInWholeSteps() {
        RecipeResult stew = optimiser.optimise(week, targets).recipes().get("chicken-stew");

        assertThat(amountOf(stew, "chicken thigh") % 10).isZero();
        assertThat(amountOf(stew, "onion") % 5).isZero();
        assertThat(amountOf(stew, "cumin") % 0.25).isZero();
    }

    @Test
    void keepsARecipeInProportionToItsSource() {
        RecipeResult stew = optimiser.optimise(week, targets).recipes().get("chicken-stew");

        assertThat(stew.ingredients())
                .filteredOn(ingredient -> ingredient.timesBook() != null)
                .isNotEmpty()
                .allSatisfy(ingredient -> assertThat(ingredient.timesBook()).isBetween(0.8, 1.65));
        assertThat(timesBookOf(stew, "olive oil")).isLessThanOrEqualTo(1.0);
    }

    @Test
    void saysHowManyPortionsAreLeftToFreeze() {
        Map<String, RecipeResult> recipes = optimiser.optimise(week, targets).recipes();

        assertThat(recipes.get("chicken-stew").toFreeze()).isEqualTo(2);
        assertThat(recipes.get("lentil-soup").toFreeze()).isEqualTo(1);
        assertThat(recipes.get("porridge").toFreeze()).isZero();
    }

    @Test
    void addsUpTheWeeksShopping() {
        WeekReport report = optimiser.optimise(week, targets);

        assertThat(report.shopping()).extracting(WeekReport.ShoppingLine::name).contains("chicken thigh", "oats");
        assertThat(report.shopping()).isSortedAccordingTo((a, b) -> a.name().compareTo(b.name()));
    }

    @Test
    void namesARecipeADayEatsButTheWeekDoesNotHave() {
        Week broken = new Week(week.recipes(), List.of(new Day("Mon", Map.of("dinner", List.of("nope")))));

        assertThatThrownBy(() -> optimiser.optimise(broken, targets))
                .isInstanceOf(InvalidWeek.class)
                .hasMessageContaining("Mon dinner: unknown recipe \"nope\"")
                .hasMessageContaining("Mon: no breakfast");
    }

    @Test
    void namesAnIngredientTheTablesDoNotHave() {
        Map<String, Recipe> recipes = new LinkedHashMap<>(week.recipes());
        Ingredient unknown = new Ingredient("dragon fruit", "g", null, null, 50.0, 100.0, null, null, null, null, true);
        recipes.put("porridge", new Recipe("Porridge", null, 2, 2, null, null, List.of(unknown), null));

        assertThatThrownBy(() -> optimiser.optimise(new Week(recipes, week.days()), targets))
                .isInstanceOf(InvalidWeek.class)
                .hasMessageContaining("\"dragon fruit\" (g) isn't in the nutrition table. Look it up and add it.");
    }

    @Test
    void saysSoWhenNoAmountsCanMeetTheBounds() {
        Map<String, Double> impossible = new LinkedHashMap<>(targets.targetsPerPersonDay());
        Map<String, List<Double>> tinyMeals =
                Map.of("breakfast", List.of(10.0, 20.0), "lunch", List.of(10.0, 20.0), "dinner", List.of(10.0, 20.0));
        Targets starving = new Targets(
                targets.people(),
                impossible,
                targets.tolerances(),
                targets.saltMaxGPerPersonDay(),
                tinyMeals,
                targets.weights(),
                targets.ratioBounds(),
                targets.fibreMinGPerPersonDay(),
                targets.fiveADay());

        assertThatThrownBy(() -> optimiser.optimise(week, starving))
                .matches(problem -> WeekOptimiser.cannotBeSolved((RuntimeException) problem))
                .hasMessageContaining("Change the plan");
    }

    private static double amountOf(RecipeResult recipe, String ingredient) {
        return ingredientOf(recipe, ingredient).perCook();
    }

    private static double timesBookOf(RecipeResult recipe, String ingredient) {
        return ingredientOf(recipe, ingredient).timesBook();
    }

    private static IngredientResult ingredientOf(RecipeResult recipe, String name) {
        return recipe.ingredients().stream()
                .filter(ingredient -> ingredient.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void namesWhatIsMalformedInAWeekWithoutFallingOver() {
        Map<String, Recipe> recipes = new LinkedHashMap<>(week.recipes());
        Ingredient nameless = new Ingredient(null, "g", null, null, 50.0, 100.0, null, null, null, null, true);
        Ingredient noStep = new Ingredient("oats", "g", null, null, 50.0, 100.0, 0.0, null, null, null, true);
        recipes.put("porridge", new Recipe("Porridge", null, 2, 2, null, null, List.of(nameless, noStep), null));
        Map<String, List<String>> noLunch =
                new LinkedHashMap<>(week.days().getFirst().meals());
        noLunch.put("lunch", null);
        List<Day> days = List.of(new Day("Mon", noLunch), week.days().getLast());

        assertThatThrownBy(() -> optimiser.optimise(new Week(recipes, days), targets))
                .isInstanceOf(InvalidWeek.class)
                .hasMessageContaining("recipe \"porridge\": every ingredient needs a name")
                .hasMessageContaining("recipe \"porridge\", oats: step must be above 0")
                .hasMessageContaining("Mon: no lunch");
    }

    @Test
    void refusesABookAmountOfNothing() {
        Map<String, Recipe> recipes = new LinkedHashMap<>(week.recipes());
        Recipe stew = recipes.get("chicken-stew");
        List<Ingredient> ingredients = stew.ingredients().stream()
                .map(i -> i.name().equals("onion")
                        ? new Ingredient(i.name(), i.unit(), null, 0.0, null, null, null, i.role(), null, null, null)
                        : i)
                .toList();
        recipes.put(
                "chicken-stew",
                new Recipe(stew.title(), null, stew.portions(), stew.cooks(), stew.anchor(), null, ingredients, null));

        assertThatThrownBy(() -> optimiser.optimise(new Week(recipes, week.days()), targets))
                .isInstanceOf(InvalidWeek.class)
                .hasMessageContaining("onion: a book amount must be above 0");
    }

    @Test
    void namesEveryGapInTheTargetsBeforeSolvingAnything() {
        Targets gappy = new Targets(
                1, Map.of("kcal", 2000.0), null, null, targets.mealKcal(), Map.of(), targets.ratioBounds(), 25.0, null);

        assertThatThrownBy(() -> optimiser.optimise(week, gappy))
                .isInstanceOf(InvalidWeek.class)
                .hasMessageStartingWith("The targets need fixing:")
                .hasMessageContaining("targets_per_person_day needs protein")
                .hasMessageContaining("weights needs kcal")
                .hasMessageContaining("weights needs stay_near_book")
                .hasMessageContaining("tolerances needs kcal_pct, protein_below_g and fat_over_g")
                .hasMessageContaining("salt_max_g_per_person_day is missing")
                .hasMessageContaining("five_a_day needs min_per_day")
                .hasMessageContaining("five_a_day portion_g needs the grams in a portion of dried");
    }

    @Test
    void reportsADayFromTheFreezerThatFallsShortInsteadOfGivingUpOnTheWeek() {
        Map<String, Recipe> recipes = new LinkedHashMap<>(week.recipes());
        recipes.put(
                "freezer-stew",
                new Recipe(
                        "Stew from the freezer",
                        null,
                        3,
                        null,
                        null,
                        null,
                        null,
                        new Frozen(650.0, 37.0, 21.0, 75.0, 1.2, 9.0, 1.0)));
        List<Day> days = new ArrayList<>(week.days());
        days.add(new Day(
                "Wed",
                Map.of(
                        "breakfast", List.of("freezer-stew"),
                        "lunch", List.of("freezer-stew"),
                        "dinner", List.of("freezer-stew"))));

        WeekReport report = optimiser.optimise(new Week(recipes, days), targets);

        assertThat(report.pass()).isFalse();
        assertThat(report.checks())
                .contains(new Check(false, "Wed: 3.0 portions of veg and fruit (at least 5)"))
                .filteredOn(check -> !check.ok())
                .allMatch(check -> check.what().startsWith("Wed"));
    }
}
