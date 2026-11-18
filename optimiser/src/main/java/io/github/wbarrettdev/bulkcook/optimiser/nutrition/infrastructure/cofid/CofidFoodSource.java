package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.cofid;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodRecord;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodSource;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.Source;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Searches the CoFID foods kept in the database, so the first-choice source needs no network. */
@Repository
@RequiredArgsConstructor
class CofidFoodSource implements FoodSource {

    private static final int MOST = 8;

    private static final String FOOD = """
            SELECT code, name, food_group, kcal, protein_g, fat_g, carbohydrate_g, starch_g, total_sugars_g,
                   aoac_fibre_g, nsp_g, sodium_mg
            FROM cofid_food
            """;

    /** A shorter name is usually the plainer food, so it comes first. */
    private static final String NAMED_WITH_EVERY_WORD = FOOD + """
            WHERE lower(name) LIKE ALL (ARRAY[:words])
            ORDER BY length(name), code COLLATE "C"
            LIMIT :most
            """;

    private static final String WITH_CODE = FOOD + "WHERE code = :code";

    private final JdbcClient jdbc;

    @Override
    public Source source() {
        return Source.COFID;
    }

    @Override
    public List<FoodRecord> search(String query) {
        List<String> words = Arrays.stream(
                        query.toLowerCase(Locale.ROOT).replace(',', ' ').split("\\s+"))
                .filter(word -> !word.isEmpty())
                .map(CofidFoodSource::containing)
                .toList();
        if (words.isEmpty()) {
            return List.of();
        }
        try {
            return jdbc.sql(NAMED_WITH_EVERY_WORD)
                    .param("words", words)
                    .param("most", MOST)
                    .query((row, n) -> food(row).asLabelled())
                    .list();
        } catch (DataAccessException e) {
            throw new Unavailable(Source.COFID, e);
        }
    }

    @Override
    public Optional<FoodRecord> find(String id) {
        try {
            return jdbc.sql(WITH_CODE)
                    .param("code", id)
                    .query((row, n) -> food(row).asLabelled())
                    .optional();
        } catch (DataAccessException e) {
            throw new Unavailable(Source.COFID, e);
        }
    }

    /** A LIKE pattern for names that contain the word, with the word's own % and _ taken literally. */
    private static String containing(String word) {
        return "%" + word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static CofidFood food(ResultSet row) throws SQLException {
        return new CofidFood(
                row.getString("code"),
                row.getString("name"),
                row.getString("food_group"),
                number(row, "kcal"),
                number(row, "protein_g"),
                number(row, "fat_g"),
                number(row, "carbohydrate_g"),
                number(row, "starch_g"),
                number(row, "total_sugars_g"),
                number(row, "aoac_fibre_g"),
                number(row, "nsp_g"),
                number(row, "sodium_mg"));
    }

    /** Null when CoFID gives no value, which is not the same as none of it. */
    private static Double number(ResultSet row, String column) throws SQLException {
        BigDecimal value = row.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }
}
