package io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.InvalidWeek;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Targets;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Targets.FiveADay;
import io.github.wbarrettdev.bulkcook.optimiser.week.domain.Week;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

/** Reads the week and the targets from a request, where names are written like_this. */
public final class RequestJson {

    private static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .addMixIn(Targets.class, TargetsNames.class)
            .build();

    private RequestJson() {}

    /** Where a value sits in the request, written as its names from the top down: recipes.stew.portions. */
    private static String whereIn(JacksonException problem) {
        return problem.getPath().stream()
                .map(step ->
                        step.getPropertyName() != null ? step.getPropertyName() : String.valueOf(step.getIndex() + 1))
                .collect(Collectors.joining("."));
    }

    private static boolean isAWholeNumber(Class<?> type) {
        return type == Integer.class || type == int.class || type == Long.class || type == long.class;
    }

    /** The request's names for the targets that like_this naming can't work out from the Java names. */
    private interface TargetsNames {
        @JsonProperty("salt_max_g_per_person_day")
        Double saltMaxGPerPersonDay();

        @JsonProperty("fibre_min_g_per_person_day")
        Double fibreMinGPerPersonDay();

        @JsonProperty("five_a_day")
        FiveADay fiveADay();
    }

    public static Week week(Map<String, Object> week) {
        return read("week", week, Week.class);
    }

    public static Targets targets(Map<String, Object> targets) {
        return read("targets", targets, Targets.class);
    }

    private static <T> T read(String what, Map<String, Object> value, Class<T> type) {
        if (value == null || value.isEmpty()) {
            throw new InvalidWeek("The request has no " + what + ".");
        }
        try {
            return JSON.convertValue(value, type);
        } catch (MismatchedInputException e) {
            throw new InvalidWeek("The " + what + " can't be read: " + whereIn(e)
                    + (isAWholeNumber(e.getTargetType())
                            ? " must be a whole number."
                            : " isn't the right kind of value."));
        } catch (JacksonException | IllegalArgumentException e) {
            throw new InvalidWeek("The " + what + " can't be read: " + e.getMessage());
        }
    }
}
