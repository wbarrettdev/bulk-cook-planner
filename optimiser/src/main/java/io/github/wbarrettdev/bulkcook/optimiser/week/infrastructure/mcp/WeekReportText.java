package io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.mcp;

import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Numbers;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport.Check;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport.DayResult;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport.IngredientResult;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport.RecipeResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Writes the solved week as the plain report a person or a model reads. Pure functions. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WeekReportText {

    static String of(WeekReport report) {
        List<String> lines = new ArrayList<>();
        report.recipes().forEach((id, recipe) -> lines.addAll(recipe(id, recipe)));
        lines.add("");
        lines.add("Per person per day");
        report.days().stream().map(WeekReportText::day).forEach(lines::add);
        lines.add("");
        lines.add("Week totals for shopping");
        report.shopping().forEach(line -> lines.add(amount(line.total(), line.unit()) + " " + line.name()));
        lines.add("");
        lines.add(verdict(report.checks()));
        return String.join("\n", lines);
    }

    private static List<String> recipe(String id, RecipeResult recipe) {
        Map<String, Double> p = recipe.perPortion();
        double vegAndFruit =
                p.getOrDefault("p_vf", 0.0) + p.getOrDefault("p_pulse", 0.0) + p.getOrDefault("p_juice", 0.0);
        String macros = "  per portion: " + Numbers.round(p.get("kcal")) + " kcal  P " + p.get("protein") + "  F "
                + p.get("fat") + "  C " + p.get("carbs") + "  salt " + p.get("salt") + "g  fibre "
                + p.getOrDefault("fibre", 0.0) + "g  veg/fruit " + Numbers.round(vegAndFruit, 1);
        String title = recipe.title() == null || recipe.title().isEmpty() ? id : recipe.title();
        List<String> lines = new ArrayList<>();
        lines.add("");
        if (recipe.frozen()) {
            lines.add(title + "  [" + id + "]  from the freezer: " + recipe.platesEaten() + " of " + recipe.portions()
                    + " portions");
            lines.add(macros);
            return lines;
        }
        String cooks = recipe.cooks() > 1
                ? ", cooked " + recipe.cooks() + " times (" + recipe.servingsPerCook() + " serving"
                        + (recipe.servingsPerCook() != 1 ? "s" : "") + " each)"
                : "";
        String spare = recipe.toFreeze() != 0 ? ", " + recipe.toFreeze() + " spare portion(s) to freeze" : "";
        lines.add(title + "  [" + id + "]  " + recipe.portions() + " portions" + cooks + ", " + recipe.gramsPerCook()
                + "g per cook" + spare);
        lines.add(macros);
        lines.add(String.format("  %9s %-5s ingredient", "per cook", ""));
        recipe.ingredients().forEach(i -> lines.add(ingredient(i, recipe.cooks() > 1)));
        return lines;
    }

    private static String ingredient(IngredientResult i, boolean cookedMoreThanOnce) {
        String extra = Stream.of(
                        i.perPlate() != null ? Numbers.plain(i.perPlate()) + " " + i.unit() + " per plate" : "",
                        i.timesBook() != null ? i.timesBook() + "x book" : "",
                        cookedMoreThanOnce ? Numbers.plain(i.total()) + " in total" : "",
                        "table: " + i.table())
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(", "));
        return amount(i.perCook(), i.unit()) + " " + i.name() + "  (" + extra + ")";
    }

    private static String amount(double quantity, String unit) {
        return String.format("  %9s %-5s", Numbers.plain(quantity), unit.isEmpty() ? "x" : unit);
    }

    private static String day(DayResult day) {
        Map<String, Double> t = day.total();
        String meals = day.meals().entrySet().stream()
                .map(meal -> meal.getKey() + " " + Numbers.round(meal.getValue().get("kcal")))
                .collect(Collectors.joining(", "));
        return String.format(
                "  %-16s %d kcal  P %d  F %d  C %d  salt %sg  fibre %sg  5-a-day %s   %s",
                day.day(),
                Numbers.round(t.get("kcal")),
                Numbers.round(t.get("protein")),
                Numbers.round(t.get("fat")),
                Numbers.round(t.get("carbs")),
                Numbers.fixed(t.get("salt"), 1),
                Numbers.fixed(t.get("fibre"), 0),
                t.get("five_a_day"),
                meals);
    }

    private static String verdict(List<Check> checks) {
        List<String> failed =
                checks.stream().filter(check -> !check.ok()).map(Check::what).toList();
        if (failed.isEmpty()) {
            return "ALL " + checks.size() + " CHECKS PASS";
        }
        return "FAILED " + failed.size() + " of " + checks.size() + " checks:\n  " + String.join("\n  ", failed);
    }
}
