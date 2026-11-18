package io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.mcp;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository;
import io.github.wbarrettdev.bulkcook.optimiser.week.application.WeekOptimiser;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.InvalidWeek;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Targets;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OptimiserTools {

    private final WeekOptimiser optimiser;

    @McpTool(
            name = "solve_week",
            title = "Scale the week's recipes to the targets",
            description = "Scale a week's recipes so every day hits its calorie and macro targets, while each"
                    + " recipe stays in proportion to its source. Send the week and the targets. Returns every"
                    + " amount, each day's totals, the shopping totals and the checks. The report ends with"
                    + " ALL CHECKS PASS, or FAILED with the checks to fix. If the week is wrong, the error says"
                    + " what to fix. If no amounts can meet the bounds, the error says to change the plan."
                    + " Every quantity in a plan comes from here.",
            annotations =
                    @McpAnnotations(
                            title = "Scale the week's recipes to the targets",
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    CallToolResult solveWeek(
            @McpToolParam(
                            description = "The week: {\"recipes\": {id: {title, portions, cooks, anchor, max_batch_g,"
                                    + " ingredients: [{name, unit, note, book, min, max, step, role, lo, hi,"
                                    + " per_portion}], frozen}}, \"days\": [{day, meals: {breakfast: [ids], lunch:"
                                    + " [ids], dinner: [ids]}}]}",
                            required = true)
                    Map<String, Object> week,
            @McpToolParam(
                            description = "The targets: {people, targets_per_person_day, tolerances,"
                                    + " salt_max_g_per_person_day, meal_kcal, weights, ratio_bounds,"
                                    + " fibre_min_g_per_person_day, five_a_day}",
                            required = true)
                    Map<String, Object> targets) {
        try {
            Targets theTargets = RequestJson.targets(targets);
            Week theWeek = RequestJson.week(week);
            WeekReport report = optimiser.optimise(theWeek, theTargets);
            return CallToolResult.builder()
                    .addTextContent(WeekReportText.of(report))
                    .structuredContent(report)
                    .build();
        } catch (InvalidWeek | NutritionRepository.Unavailable problem) {
            return failure(problem);
        } catch (RuntimeException problem) {
            if (WeekOptimiser.cannotBeSolved(problem)) {
                return failure(problem);
            }
            throw problem;
        }
    }

    private static CallToolResult failure(RuntimeException problem) {
        return CallToolResult.builder()
                .isError(true)
                .addTextContent(problem.getMessage())
                .build();
    }
}
