package io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.mcp;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository;
import io.github.wbarrettdev.bulkcook.optimiser.week.application.WeekOptimiser;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.InvalidWeek;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What the solve_week tool does when it is called. {@link SolveWeekToolConfiguration} declares it. */
@Component
@RequiredArgsConstructor
class SolveWeekTool {

    private final WeekOptimiser optimiser;

    /** Answers twice over: as text for a model to read, and as structured content for code to check. */
    CallToolResult solveWeek(Map<String, Object> arguments) {
        try {
            SolveWeekInput input = SolveWeekInput.read(arguments);
            WeekReport report = optimiser.optimise(input.toWeek(), input.toTargets());
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
