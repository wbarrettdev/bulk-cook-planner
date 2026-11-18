package io.github.wbarrettdev.bulkcook.optimiser.week.application;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.PreparedWeek;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Targets;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekReport;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekSolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Scales a week's recipes so every day lands on its targets, and reports what it found. */
@Service
@RequiredArgsConstructor
public class WeekOptimiser {

    private final WeekSolver solver;
    private final NutritionRepository nutrition;

    public WeekReport optimise(Week week, Targets targets) {
        targets.checked();
        NutritionTable table =
                nutrition.table().withPortionsOf(targets.fiveADay().portionG());
        PreparedWeek prepared = PreparedWeek.of(week, targets, table);
        long[] steps = solver.solve(prepared, targets);
        return WeekReport.of(prepared, steps, targets);
    }

    public static boolean cannotBeSolved(RuntimeException problem) {
        return problem instanceof WeekSolver.NoSolution;
    }
}
