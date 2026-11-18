package io.github.wbarrettdev.bulkcook.optimiser.week.infrastructure.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the solve_week tool to the server: its name, what it takes, and what to call.
 *
 * <p>The other tools are declared by annotation. This one takes a whole week, a deep document, and
 * a tool declared by annotation gets a schema that can't say what is inside a map of recipes. So
 * it is declared by hand, with the schema generated from the request records. The server checks
 * each call against that schema and answers one that doesn't fit with every way it doesn't.
 */
@Configuration(proxyBeanMethods = false)
class SolveWeekToolConfiguration {

    private static final String NAME = "solve_week";
    private static final String TITLE = "Scale the week's recipes to the targets";
    private static final String DESCRIPTION = "Scale a week's recipes so every day hits its calorie and macro"
            + " targets, while each recipe stays in proportion to its source. Send the week and the targets."
            + " Returns every amount, each day's totals, the shopping totals and the checks. The report ends"
            + " with ALL CHECKS PASS, or FAILED with the checks to fix. If the week is wrong, the error says"
            + " what to fix. If no amounts can meet the bounds, the error says to change the plan. Every"
            + " quantity in a plan comes from here.";

    @Bean
    List<SyncToolSpecification> solveWeekSpecification(SolveWeekTool tool) {
        Tool declaration = Tool.builder()
                .name(NAME)
                .title(TITLE)
                .description(DESCRIPTION)
                .inputSchema(SolveWeekSchema.asMap())
                .annotations(new ToolAnnotations(TITLE, true, false, true, false, null))
                .build();
        return List.of(SyncToolSpecification.builder()
                .tool(declaration)
                .callHandler((exchange, call) -> tool.solveWeek(call.arguments()))
                .build());
    }
}
