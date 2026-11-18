package io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.ortools;

import io.github.wbarrettdev.bulkcook.optimiser.week.domain.PreparedWeek;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Targets;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekSolver;
import org.springframework.stereotype.Component;

/** Solves the week with Google's OR-Tools. */
@Component
public class OrToolsWeekSolver implements WeekSolver {

    @Override
    public long[] solve(PreparedWeek prepared, Targets targets) {
        return WeekProgramme.solve(prepared, targets);
    }
}
