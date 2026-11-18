package io.github.wbarrettdev.bulkcook.knowledge.pairing.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.application.PairingAdvisor;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.FakeGraph;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.FoodRules;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingAdvice;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingAdvice.Suggestion;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PairingToolsTest {

    private final FakeGraph graph =
            new FakeGraph().entry("salmon").pairs("salmon", "dill", 4).pairs("salmon", "lemon", 3);
    private final FoodRules noRules = new FoodRules(Set.of(), Set.of(), Set.of(), 2);
    private final PairingTools tools = new PairingTools(new PairingAdvisor(graph, noRules));

    @Test
    void answersAsTextForAModelToRead() {
        CallToolResult result = tools.suggestAdditions(List.of("salmon"), null, null);

        assertThat(result.content()).hasSize(1);
        assertThat(((TextContent) result.content().getFirst()).text()).contains("Pairings for: salmon", "dill");
    }

    @Test
    void answersAsStructuredContentForCodeToCheck() {
        CallToolResult result = tools.suggestAdditions(List.of("salmon"), null, null);

        PairingAdvice advice = (PairingAdvice) result.structuredContent();
        assertThat(advice.suggestions()).extracting(Suggestion::name).containsExactly("dill", "lemon");
    }

    @Test
    void confirmsPairingsInAShortAnswer() {
        CallToolResult result = tools.checkPairings("salmon", List.of("dill", "cinnamon"));

        assertThat(((TextContent) result.content().getFirst()).text())
                .isEqualTo(
                        "Does salmon pair with:\n  dill: yes (salmon lists dill at 4)\n  cinnamon: not in the pairing graph");
    }

    @Test
    void usesTheLimitAndExclusionsItIsGiven() {
        CallToolResult result = tools.suggestAdditions(List.of("salmon"), 1, List.of("dill"));

        PairingAdvice advice = (PairingAdvice) result.structuredContent();
        assertThat(advice.suggestions()).extracting(Suggestion::name).containsExactly("lemon");
    }
}
