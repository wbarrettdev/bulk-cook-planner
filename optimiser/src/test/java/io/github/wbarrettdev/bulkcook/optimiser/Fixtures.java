package io.github.wbarrettdev.bulkcook.optimiser;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FakeNutrition;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.Row;
import io.github.wbarrettdev.bulkcook.optimiser.week.application.WeekOptimiser;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Targets;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week;
import io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.mcp.RequestJson;
import io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.ortools.OrToolsWeekSolver;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** A small made-up week, its targets and nutrition tables, kept with the tests. */
public final class Fixtures {

    private static final Path RESOURCES = Path.of("src", "test", "resources");
    private static final Path NUTRITION = RESOURCES.resolve("nutrition");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final CSVFormat WITH_HEADER =
            CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();

    private Fixtures() {}

    public static Map<String, Object> weekRequest() {
        return request("week.json");
    }

    public static Map<String, Object> targetsRequest() {
        return request("targets.json");
    }

    public static Week week() {
        return RequestJson.week(weekRequest());
    }

    public static Targets targets() {
        return RequestJson.targets(targetsRequest());
    }

    /** The made-up nutrition tables as a repository would hand them over, before portions are counted. */
    public static NutritionTable storedTable() {
        return new NutritionTable(
                rows("ingredients.csv").stream().map(Fixtures::macros).toList(),
                byIngredient("salt.csv", row -> Double.parseDouble(row.get("salt_g_per_100g"))),
                byIngredient("fibre.csv", row -> Double.parseDouble(row.get("fibre_g_per_100g"))),
                byIngredient("portions.csv", row -> row.get("category")),
                Map.of());
    }

    public static NutritionRepository nutrition() {
        return new FakeNutrition(storedTable());
    }

    public static NutritionTable table() {
        return storedTable().withPortionsOf(targets().fiveADay().portionG());
    }

    public static WeekOptimiser optimiser() {
        return new WeekOptimiser(new OrToolsWeekSolver(), nutrition());
    }

    private static Row macros(CSVRecord row) {
        return new Row(
                row.get("ingredient"),
                row.get("unit"),
                Double.parseDouble(row.get("grams_per_unit")),
                Double.parseDouble(row.get("kcal_per_100g")),
                Double.parseDouble(row.get("protein_per_100g")),
                Double.parseDouble(row.get("fat_per_100g")),
                Double.parseDouble(row.get("carbs_per_100g")),
                row.get("source"));
    }

    private static <T> Map<String, T> byIngredient(String file, Function<CSVRecord, T> value) {
        Map<String, T> table = new LinkedHashMap<>();
        rows(file).forEach(row -> table.put(row.get("ingredient"), value.apply(row)));
        return table;
    }

    private static List<CSVRecord> rows(String file) {
        try (Reader reader = Files.newBufferedReader(NUTRITION.resolve(file))) {
            return WITH_HEADER.parse(reader).getRecords();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Object> request(String file) {
        return JSON.readValue(RESOURCES.resolve(file).toFile(), new TypeReference<>() {});
    }
}
