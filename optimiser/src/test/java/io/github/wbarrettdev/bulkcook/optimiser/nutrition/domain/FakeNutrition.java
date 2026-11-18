package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.Row;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Nutrition tables held in memory, which keep the repository's promise never to change a row. */
public final class FakeNutrition implements NutritionRepository {

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Double> salt = new LinkedHashMap<>();
    private final Map<String, Double> fibre = new LinkedHashMap<>();
    private final Map<String, String> categories = new LinkedHashMap<>();
    private boolean reachable = true;

    public FakeNutrition() {}

    public FakeNutrition(NutritionTable holding) {
        rows.addAll(holding.rows());
        salt.putAll(holding.saltPer100g());
        fibre.putAll(holding.fibrePer100g());
        categories.putAll(holding.categories());
    }

    public static FakeNutrition thatCannotBeReached() {
        FakeNutrition nutrition = new FakeNutrition();
        nutrition.reachable = false;
        return nutrition;
    }

    @Override
    public NutritionTable table() {
        failIfUnreachable();
        return new NutritionTable(
                List.copyOf(rows), Map.copyOf(salt), Map.copyOf(fibre), Map.copyOf(categories), Map.of());
    }

    @Override
    public Stored add(IngredientNutrition added) {
        failIfUnreachable();
        FoodValues per100g = added.per100g();
        boolean hasMacros = rows.stream()
                .anyMatch(row -> row.ingredient().equals(added.ingredient())
                        && row.unit().equals(added.unit()));
        if (!hasMacros) {
            rows.add(new Row(
                    added.ingredient(),
                    added.unit(),
                    added.gramsPerUnit(),
                    per100g.kcal(),
                    per100g.protein(),
                    per100g.fat(),
                    per100g.carbs(),
                    added.record().citation()));
        }
        return new Stored(
                hasMacros ? Outcome.ALREADY_THERE : Outcome.WRITTEN,
                written(salt.putIfAbsent(added.ingredient(), per100g.salt())),
                added.hasFibre() ? written(fibre.putIfAbsent(added.ingredient(), per100g.fibre())) : Outcome.NOT_GIVEN,
                added.countsTowardsFiveADay()
                        ? written(categories.putIfAbsent(
                                added.ingredient(), added.fiveADay().key()))
                        : Outcome.NOT_GIVEN);
    }

    private static Outcome written(Object whatWasThere) {
        return whatWasThere == null ? Outcome.WRITTEN : Outcome.ALREADY_THERE;
    }

    private void failIfUnreachable() {
        if (!reachable) {
            throw new Unavailable(new IllegalStateException("no database"));
        }
    }
}
