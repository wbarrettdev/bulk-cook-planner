package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.postgres;

import io.github.wbarrettdev.bulkcook.optimiser.Fixtures;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Puts the made-up nutrition tables kept with the tests into a database, replacing what is there. */
public final class StoredSample {

    private StoredSample() {}

    public static NutritionTable into(JdbcClient jdbc) {
        NutritionTable sample = Fixtures.storedTable();
        jdbc.sql("TRUNCATE ingredient_macros, ingredient_salt, ingredient_fibre, ingredient_five_a_day")
                .update();
        sample.rows()
                .forEach(row -> jdbc.sql("INSERT INTO ingredient_macros VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                        .params(
                                row.ingredient(),
                                row.unit(),
                                row.gramsPerUnit(),
                                row.kcal(),
                                row.protein(),
                                row.fat(),
                                row.carbs(),
                                row.source())
                        .update());
        sample.saltPer100g()
                .forEach((ingredient, salt) -> jdbc.sql(
                                "INSERT INTO ingredient_salt (ingredient, salt_g_per_100g) VALUES (?, ?)")
                        .params(ingredient, salt)
                        .update());
        sample.fibrePer100g()
                .forEach((ingredient, fibre) -> jdbc.sql(
                                "INSERT INTO ingredient_fibre (ingredient, fibre_g_per_100g) VALUES (?, ?)")
                        .params(ingredient, fibre)
                        .update());
        sample.categories()
                .forEach((ingredient, category) -> jdbc.sql("INSERT INTO ingredient_five_a_day VALUES (?, ?)")
                        .params(ingredient, category)
                        .update());
        return sample;
    }
}
