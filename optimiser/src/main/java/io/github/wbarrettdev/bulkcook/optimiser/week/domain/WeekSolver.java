package io.github.wbarrettdev.bulkcook.optimiser.week.domain;

/** Chooses how much of each ingredient the week needs. How it's worked out is not the domain's concern. */
public interface WeekSolver {

    /** The whole steps chosen for each line, in the order of the prepared week's lines. */
    long[] solve(PreparedWeek prepared, Targets targets);

    /** No amounts can meet every bound. The plan has to change. */
    final class NoSolution extends RuntimeException {
        public NoSolution() {
            super("No solution: the bounds can't all be met. Change the plan: a different side, a leaner or bigger"
                    + " anchor range, more veg, fruit or beans (fibre and 5-a-day), or a recipe that fits the"
                    + " meal's calories.");
        }
    }
}
