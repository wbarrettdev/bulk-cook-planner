package io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.mcp;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.InvalidWeek;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Targets;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Everything one solve_week call sends. The published schema is generated from this record and a
 * call's arguments are read into it, so the contract and what is read are the same thing.
 */
public record SolveWeekInput(
        @NotNull @JsonPropertyDescription("The week to scale.")
        WeekRequest week,

        @NotNull
        @JsonPropertyDescription("What each day should come to, and how far a recipe may drift from its source.")
        TargetsRequest targets) {

    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    /**
     * Reads a call's arguments. The server has already held them to the schema, so what gets here
     * fits it. They are read strictly all the same, so that a misspelt field or part of a portion
     * can never be quietly ignored or rounded away.
     */
    public static SolveWeekInput read(Map<String, Object> arguments) {
        SolveWeekInput input;
        try {
            input = STRICT.convertValue(arguments == null ? Map.of() : arguments, SolveWeekInput.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new InvalidWeek("The request doesn't fit what solve_week takes: " + e.getMessage());
        }
        if (input.week() == null) {
            throw new InvalidWeek("The request has no week.");
        }
        if (input.targets() == null) {
            throw new InvalidWeek("The request has no targets.");
        }
        return input;
    }

    public Week toWeek() {
        return week.toDomain();
    }

    public Targets toTargets() {
        return targets.toDomain();
    }
}
