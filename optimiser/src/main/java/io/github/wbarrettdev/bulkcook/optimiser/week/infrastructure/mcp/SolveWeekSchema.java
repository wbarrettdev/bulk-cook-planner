package io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.mcp;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.generator.TypeScope;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption;
import java.lang.reflect.Type;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The JSON Schema of what solve_week takes, generated from the request records so the two can't
 * drift apart. Names and descriptions come from their Jackson annotations, and what is required
 * and how small a number may be from their validation annotations.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SolveWeekSchema {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static ObjectNode asJson() {
        SchemaGeneratorConfigBuilder settings = new SchemaGeneratorConfigBuilder(
                        SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(new JacksonSchemaModule())
                .with(new JakartaValidationModule(JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED))
                .with(Option.INLINE_ALL_SCHEMAS);
        settings.forTypesInGeneral()
                .withPropertySorter((first, second) -> 0)
                .withAdditionalPropertiesResolver(SolveWeekSchema::whatElseItMayHold);
        return new SchemaGenerator(settings.build()).generateSchema(SolveWeekInput.class);
    }

    static Map<String, Object> asMap() {
        return JSON.convertValue(asJson(), new TypeReference<>() {});
    }

    static String asText() {
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(asJson());
    }

    /**
     * What an object may hold beyond the fields it declares. A map holds values of its value type
     * under any name. Anything else holds nothing more, so a misspelt field is refused.
     */
    private static Type whatElseItMayHold(TypeScope scope) {
        if (scope.getType().isInstanceOf(Map.class)) {
            return scope.getTypeParameterFor(Map.class, 1);
        }
        return scope.isContainerType() ? null : Void.class;
    }
}
