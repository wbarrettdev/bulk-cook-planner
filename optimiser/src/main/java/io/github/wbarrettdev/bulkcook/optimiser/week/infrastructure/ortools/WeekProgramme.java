package io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.ortools;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.Nutrient;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.InvalidWeek;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Numbers;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.PreparedWeek;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.PreparedWeek.Line;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Targets;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week.Day;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week.Recipe;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.WeekSolver.NoSolution;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One week as a mixed-integer programme: how many whole steps of each ingredient make every day land
 * on its targets, while each recipe stays in proportion to its source. Solved by SCIP.
 */
final class WeekProgramme {

    private static final List<Nutrient> TARGETED =
            List.of(Nutrient.KCAL, Nutrient.PROTEIN, Nutrient.FAT, Nutrient.CARBS);
    private static final int TIME_LIMIT_MS = 60_000;
    private static final double INFINITY = Double.POSITIVE_INFINITY;

    static {
        Loader.loadNativeLibraries();
    }

    private final PreparedWeek prepared;
    private final Targets targets;
    private final MPSolver solver;
    private final MPObjective objective;
    private final MPVariable[] steps;

    private WeekProgramme(PreparedWeek prepared, Targets targets) {
        this.prepared = prepared;
        this.targets = targets;
        this.solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            throw new IllegalStateException("OR-Tools has no SCIP solver");
        }
        this.objective = solver.objective();
        this.steps = new MPVariable[prepared.lines().size()];
    }

    /** The whole steps chosen for each line, in the order of the prepared week's lines. */
    static long[] solve(PreparedWeek prepared, Targets targets) {
        WeekProgramme model = new WeekProgramme(prepared, targets);
        try {
            model.sizeEveryLine();
            model.keepRecipesInProportion();
            prepared.week().days().forEach(model::holdTheDayToItsTargets);
            return model.solved();
        } finally {
            model.solver.delete(); // the model lives outside the Java heap, where the garbage collector can't see it
        }
    }

    private void sizeEveryLine() {
        for (Line line : prepared.lines()) {
            Double min = line.ingredient().min();
            Double max = line.ingredient().max();
            double lowest = min != null ? Math.ceil(min / line.step() - 1e-9) : 0;
            double highest = max != null ? Math.floor(max / line.step() + 1e-9) : INFINITY;
            if (highest < lowest) {
                throw new InvalidWeek(
                        "recipe \"" + line.recipe() + "\", " + line.ingredient().name()
                                + ": min and max don't contain a whole step of " + Numbers.plain(line.step()));
            }
            steps[line.index()] = solver.makeIntVar(lowest, highest, "n" + line.index());
        }
    }

    private void keepRecipesInProportion() {
        prepared.week().recipes().forEach((id, recipe) -> {
            List<Line> lines = prepared.linesByRecipe().get(id);
            Line anchor = prepared.anchorOf(id);
            lines.stream().filter(Line::followsTheAnchor).forEach(line -> followTheAnchor(line, anchor));
            if (recipe.maxBatchG() != null && recipe.maxBatchG() != 0 && !lines.isEmpty()) {
                Sum batch = new Sum();
                lines.forEach(line -> batch.add(
                        line, line.step() * line.unitsPerCook() * line.per().grams()));
                atMost(batch, recipe.maxBatchG());
            }
        });
    }

    /** Per cook: lo x ratio x anchor - step/2 <= amount <= hi x ratio x anchor (+ step/2, unless salty or fat). */
    private void followTheAnchor(Line line, Line anchor) {
        double ratio = line.ratioTo(anchor);
        double anchorStep = anchor.step();
        double slackDown = line.step() / 2;
        double slackUp = line.slackUp();
        atMost(new Sum().add(line, line.step()).add(anchor, -line.band().hi() * ratio * anchorStep), slackUp);
        atLeast(new Sum().add(line, line.step()).add(anchor, -line.band().lo() * ratio * anchorStep), -slackDown);
        double weight = targets.weights().get("stay_near_book")
                / Math.max(line.ingredient().book(), 1);
        equalTo(withDeviation(new Sum().add(line, line.step()).add(anchor, -ratio * anchorStep), weight), 0);
    }

    private void holdTheDayToItsTargets(Day day) {
        List<String> everything =
                day.meals().values().stream().flatMap(List::stream).toList();
        for (Nutrient nutrient : TARGETED) {
            Double target = targets.targetsPerPersonDay().get(nutrient.key());
            if (target != null) {
                double weight = targets.weights().get(nutrient.key()) / target;
                equalTo(withDeviation(intake(everything, nutrient), weight), target);
            }
        }
        Sum salt = intake(everything, Nutrient.SALT);
        if (!salt.isConstant()) {
            atMost(salt, targets.saltMaxGPerPersonDay());
        }
        Sum fibre = intake(everything, Nutrient.FIBRE);
        if (!fibre.isConstant()) {
            atLeast(fibre, targets.fibreMinGPerPersonDay());
        }
        fiveADay(everything);
        day.meals().forEach((meal, ids) -> {
            Sum kcal = intake(ids, Nutrient.KCAL);
            if (!kcal.isConstant()) {
                List<Double> allowed = targets.mealKcal().get(meal);
                atLeast(kcal, allowed.get(0));
                atMost(kcal, allowed.get(1));
            }
        });
    }

    /**
     * Veg and fruit count by weight. Pulses and juice count once a day at most, however much is eaten.
     * A day with nothing to scale, eaten wholly from the freezer, is left alone like its salt and
     * fibre: no amounts could change it, so the report's check says if it falls short.
     */
    private void fiveADay(List<String> everything) {
        Sum vegAndFruit = intake(everything, Nutrient.VEG_AND_FRUIT);
        Sum pulses = intake(everything, Nutrient.PULSE);
        Sum juice = intake(everything, Nutrient.JUICE);
        if (vegAndFruit.isConstant() && pulses.isConstant() && juice.isConstant()) {
            return;
        }
        MPVariable pulsePortion = solver.makeNumVar(0, 1, "");
        MPVariable juicePortion = solver.makeNumVar(0, 1, "");
        atLeast(pulses.add(pulsePortion, -1), 0);
        atLeast(juice.add(juicePortion, -1), 0);
        atLeast(
                vegAndFruit.add(pulsePortion, 1).add(juicePortion, 1),
                targets.fiveADay().minPerDay());
    }

    /** One person's intake from these recipes. A recipe named twice is eaten twice. */
    private Sum intake(List<String> recipeIds, Nutrient nutrient) {
        Sum sum = new Sum();
        for (String id : recipeIds) {
            Recipe recipe = prepared.week().recipes().get(id);
            if (recipe.isFrozen()) {
                sum.constant += recipe.frozen().of(nutrient);
            }
            for (Line line : prepared.linesByRecipe().get(id)) {
                sum.add(
                        line,
                        line.step()
                                * line.unitsPerCook()
                                * line.cooks()
                                * line.per().of(nutrient)
                                / line.portions());
            }
        }
        return sum;
    }

    private long[] solved() {
        if (prepared.lines().isEmpty()) {
            return new long[0];
        }
        objective.setMinimization();
        solver.setTimeLimit(TIME_LIMIT_MS);
        MPSolver.ResultStatus status = solver.solve();
        if (status != MPSolver.ResultStatus.OPTIMAL && status != MPSolver.ResultStatus.FEASIBLE) {
            throw new NoSolution();
        }
        long[] chosen = new long[steps.length];
        for (int i = 0; i < steps.length; i++) {
            chosen[i] = Math.round(steps[i].solutionValue());
        }
        return chosen;
    }

    /** Adds how far the sum is above and below its mark as two costs, so the solver keeps both small. */
    private Sum withDeviation(Sum sum, double weight) {
        MPVariable above = solver.makeNumVar(0, INFINITY, "");
        MPVariable below = solver.makeNumVar(0, INFINITY, "");
        objective.setCoefficient(above, weight);
        objective.setCoefficient(below, weight);
        return sum.add(above, -1).add(below, 1);
    }

    private void atMost(Sum sum, double limit) {
        constrain(sum, -INFINITY, limit);
    }

    private void atLeast(Sum sum, double limit) {
        constrain(sum, limit, INFINITY);
    }

    private void equalTo(Sum sum, double value) {
        constrain(sum, value, value);
    }

    private void constrain(Sum sum, double lowest, double highest) {
        MPConstraint constraint = solver.makeConstraint(
                lowest == -INFINITY ? lowest : lowest - sum.constant,
                highest == INFINITY ? highest : highest - sum.constant);
        sum.terms.forEach(constraint::setCoefficient);
    }

    /** A weighted sum of variables plus a constant. */
    private final class Sum {
        private final Map<MPVariable, Double> terms = new LinkedHashMap<>();
        private double constant;

        Sum add(Line line, double coefficient) {
            return add(steps[line.index()], coefficient);
        }

        Sum add(MPVariable variable, double coefficient) {
            terms.merge(variable, coefficient, Double::sum);
            return this;
        }

        boolean isConstant() {
            return terms.isEmpty();
        }
    }
}
