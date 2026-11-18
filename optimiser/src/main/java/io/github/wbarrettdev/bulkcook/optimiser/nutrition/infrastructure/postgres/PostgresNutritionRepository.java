package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.postgres;

import static java.util.stream.Collectors.toMap;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodValues;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.IngredientNutrition;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.Row;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionOperations;

/** Keeps the nutrition tables in PostgreSQL. */
@Repository
@RequiredArgsConstructor
class PostgresNutritionRepository implements NutritionRepository {

    private static final String MACROS = """
            SELECT ingredient, unit, grams_per_unit, kcal_per_100g, protein_per_100g, fat_per_100g,
                   carbs_per_100g, source
            FROM ingredient_macros
            ORDER BY ingredient, unit
            """;
    private static final String SALT = "SELECT ingredient, salt_g_per_100g AS value FROM ingredient_salt";
    private static final String FIBRE = "SELECT ingredient, fibre_g_per_100g AS value FROM ingredient_fibre";
    private static final String FIVE_A_DAY = "SELECT ingredient, category AS value FROM ingredient_five_a_day";

    /** ON CONFLICT DO NOTHING is what makes adding safe to repeat: a row that is there is never touched. */
    private static final String ADD_MACROS = """
            INSERT INTO ingredient_macros
                (ingredient, unit, grams_per_unit, kcal_per_100g, protein_per_100g, fat_per_100g, carbs_per_100g, source)
            VALUES (:ingredient, :unit, :grams, :kcal, :protein, :fat, :carbs, :source)
            ON CONFLICT DO NOTHING
            """;

    private static final String ADD_SALT = """
            INSERT INTO ingredient_salt (ingredient, salt_g_per_100g, source)
            VALUES (:ingredient, :salt, :source)
            ON CONFLICT DO NOTHING
            """;
    private static final String ADD_FIBRE = """
            INSERT INTO ingredient_fibre (ingredient, fibre_g_per_100g, source)
            VALUES (:ingredient, :fibre, :source)
            ON CONFLICT DO NOTHING
            """;
    private static final String ADD_FIVE_A_DAY = """
            INSERT INTO ingredient_five_a_day (ingredient, category)
            VALUES (:ingredient, :category)
            ON CONFLICT DO NOTHING
            """;

    private final JdbcClient jdbc;
    private final TransactionOperations transaction;

    @Override
    public NutritionTable table() {
        try {
            return new NutritionTable(
                    macros(),
                    byIngredient(SALT, (row, n) -> row.getDouble("value")),
                    byIngredient(FIBRE, (row, n) -> row.getDouble("value")),
                    byIngredient(FIVE_A_DAY, (row, n) -> row.getString("value")),
                    Map.of());
        } catch (DataAccessException e) {
            throw new Unavailable(e);
        }
    }

    /**
     * One transaction, so an ingredient never ends up with its macros stored and its salt not. It is
     * opened here, not by an annotation, so that failing to open it is caught like any other failure.
     */
    @Override
    public Stored add(IngredientNutrition rows) {
        try {
            return transaction.execute(status -> insert(rows));
        } catch (DataAccessException | TransactionException e) {
            throw new Unavailable(e);
        }
    }

    private Stored insert(IngredientNutrition rows) {
        FoodValues per100g = rows.per100g();
        return new Stored(
                outcome(jdbc.sql(ADD_MACROS)
                        .param("ingredient", rows.ingredient())
                        .param("unit", rows.unit())
                        .param("grams", rows.gramsPerUnit())
                        .param("kcal", per100g.kcal())
                        .param("protein", per100g.protein())
                        .param("fat", per100g.fat())
                        .param("carbs", per100g.carbs())
                        .param("source", rows.record().citation())
                        .update()),
                outcome(jdbc.sql(ADD_SALT)
                        .param("ingredient", rows.ingredient())
                        .param("salt", per100g.salt())
                        .param("source", rows.record().citation())
                        .update()),
                !rows.hasFibre()
                        ? Outcome.NOT_GIVEN
                        : outcome(jdbc.sql(ADD_FIBRE)
                                .param("ingredient", rows.ingredient())
                                .param("fibre", per100g.fibre())
                                .param("source", rows.record().fibreCitation())
                                .update()),
                !rows.countsTowardsFiveADay()
                        ? Outcome.NOT_GIVEN
                        : outcome(jdbc.sql(ADD_FIVE_A_DAY)
                                .param("ingredient", rows.ingredient())
                                .param("category", rows.fiveADay().key())
                                .update()));
    }

    private static Outcome outcome(int rowsWritten) {
        return rowsWritten == 0 ? Outcome.ALREADY_THERE : Outcome.WRITTEN;
    }

    private List<Row> macros() {
        return jdbc.sql(MACROS)
                .query((row, n) -> new Row(
                        row.getString("ingredient"),
                        row.getString("unit"),
                        row.getDouble("grams_per_unit"),
                        row.getDouble("kcal_per_100g"),
                        row.getDouble("protein_per_100g"),
                        row.getDouble("fat_per_100g"),
                        row.getDouble("carbs_per_100g"),
                        row.getString("source")))
                .list();
    }

    private <T> Map<String, T> byIngredient(String sql, RowMapper<T> value) {
        return jdbc
                .sql(sql)
                .query((row, n) -> Map.entry(row.getString("ingredient"), value.mapRow(row, n)))
                .list()
                .stream()
                .collect(toMap(Entry::getKey, Entry::getValue));
    }
}
