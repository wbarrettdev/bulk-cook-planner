package io.github.wbarrettdev.bulkcook.knowledge.pairing.mcp;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingAdvice;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingAdvisor;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PairingTools {

    private final PairingAdvisor advisor;

    /** Answers twice over: as text for a model to read, and as structured content for code to check. */
    @McpTool(
            name = "suggest_additions",
            title = "Suggest additions for a dish",
            description = "Suggest what to add to a dish, from a graph of flavour pairings. Give the dish's main"
                    + " ingredients by their plain names. Returns the known combinations the dish"
                    + " already half-matches, then the top single additions with the reason for each."
                    + " The household's food rules are already applied.",
            annotations =
                    @McpAnnotations(
                            title = "Suggest additions for a dish",
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    CallToolResult suggestAdditions(
            @McpToolParam(
                            description = "The dish's main ingredients, e.g. [\"salmon\", \"potatoes\", \"leeks\"]",
                            required = true)
                    List<String> ingredients,
            @McpToolParam(description = "How many additions to list. Defaults to 10.", required = false) Integer limit,
            @McpToolParam(description = "Extra ingredients to leave out for this dish", required = false)
                    List<String> exclude) {
        PairingAdvice advice = advisor.adviseOn(new PairingAdvisor.Request(
                ingredients,
                limit == null ? PairingAdvisor.Request.DEFAULT_LIMIT : limit,
                exclude == null ? List.of() : exclude));
        return CallToolResult.builder()
                .addTextContent(PairingAdviceText.of(advice))
                .structuredContent(advice)
                .build();
    }

    @McpTool(
            name = "check_pairings",
            title = "Check whether ingredients pair",
            description = "Check whether one ingredient pairs with each of several others, in a graph of flavour"
                    + " pairings. Looks in both directions and gives the strength, 1 to 4. Use it to confirm a"
                    + " pairing before citing it. The answer is short; use suggest_additions to find new ideas.",
            annotations =
                    @McpAnnotations(
                            title = "Check whether ingredients pair",
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    CallToolResult checkPairings(
            @McpToolParam(description = "The ingredient to check, by its plain name, e.g. \"honey\"", required = true)
                    String ingredient,
            @McpToolParam(
                            description = "The ingredients it is said to pair with, e.g. [\"chicken\", \"ginger\"]",
                            required = true)
                    List<String> partners) {
        PairingAdvisor.Check check = advisor.check(ingredient, partners);
        return CallToolResult.builder()
                .addTextContent(PairingAdviceText.of(check))
                .structuredContent(check)
                .build();
    }
}
