package io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wbarrettdev.bulkcook.optimiser.Fixtures;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FakeNutrition;
import io.github.wbarrettdev.bulkcook.optimiser.week.application.WeekOptimiser;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport;
import io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.ortools.OrToolsWeekSolver;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OptimiserToolsTest {

    private final OptimiserTools tools = new OptimiserTools(Fixtures.optimiser());

    @Test
    void solvesTheWeekItIsSentAndReportsItAsText() {
        CallToolResult result = tools.solveWeek(Fixtures.weekRequest(), Fixtures.targetsRequest());

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(result))
                .contains("Chicken and chickpea stew  [chicken-stew]  4 portions")
                .contains("Per person per day")
                .contains("Week totals for shopping")
                .endsWith("CHECKS PASS");
    }

    @Test
    void reportsTheSameWeekAsStructuredContent() {
        WeekReport report = (WeekReport) tools.solveWeek(Fixtures.weekRequest(), Fixtures.targetsRequest())
                .structuredContent();

        assertThat(report.pass()).isTrue();
        assertThat(report.recipes()).containsKeys("porridge", "chicken-stew", "lentil-soup");
    }

    @Test
    void saysWhatIsWrongWithTheWeek() {
        Map<String, Object> noDays = new HashMap<>(Fixtures.weekRequest());
        noDays.put("days", List.of());

        CallToolResult result = tools.solveWeek(noDays, Fixtures.targetsRequest());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo("The week has no days.");
    }

    @Test
    void saysWhenTheRequestHasNoWeek() {
        CallToolResult result = tools.solveWeek(Map.of(), Fixtures.targetsRequest());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo("The request has no week.");
    }

    @Test
    void saysWhenTheNutritionTablesCannotBeReached() {
        OptimiserTools cutOff =
                new OptimiserTools(new WeekOptimiser(new OrToolsWeekSolver(), FakeNutrition.thatCannotBeReached()));

        CallToolResult result = cutOff.solveWeek(Fixtures.weekRequest(), Fixtures.targetsRequest());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).startsWith("The nutrition tables can't be reached");
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

    @Test
    void refusesPartOfAPortionInsteadOfRoundingItAway() {
        Map<String, Object> week = new HashMap<>(Fixtures.weekRequest());
        Map<String, Object> recipes = new HashMap<>(castToMap(week.get("recipes")));
        Map<String, Object> stew = new HashMap<>(castToMap(recipes.get("chicken-stew")));
        stew.put("portions", 4.7);
        recipes.put("chicken-stew", stew);
        week.put("recipes", recipes);

        CallToolResult result = tools.solveWeek(week, Fixtures.targetsRequest());

        assertThat(result.isError()).isTrue();
        assertThat(text(result))
                .isEqualTo("The week can't be read: recipes.chicken-stew.portions must be a whole number.");
    }

    @Test
    void saysWhichTargetIsMissing() {
        Map<String, Object> targets = new HashMap<>(Fixtures.targetsRequest());
        targets.remove("five_a_day");

        CallToolResult result = tools.solveWeek(Fixtures.weekRequest(), targets);

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).startsWith("The targets need fixing:").contains("five_a_day needs min_per_day");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToMap(Object value) {
        return (Map<String, Object>) value;
    }
}
