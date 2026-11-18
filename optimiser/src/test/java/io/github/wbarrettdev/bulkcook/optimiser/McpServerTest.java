package io.github.wbarrettdev.bulkcook.optimiser;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.postgres.StoredSample;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Calls the tools the way a client does: over MCP, through the running server and a real database. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class McpServerTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbc;

    private McpSyncClient client;

    @BeforeEach
    void connect() {
        StoredSample.into(jdbc);
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .endpoint("/mcp")
                        .build())
                .requestTimeout(Duration.ofSeconds(90))
                .build();
        client.initialize();
    }

    @AfterEach
    void disconnect() {
        client.closeGracefully();
    }

    @Test
    void offersEveryTool() {
        assertThat(client.listTools().tools())
                .extracting(Tool::name)
                .containsExactlyInAnyOrder("solve_week", "search_nutrition", "add_nutrition_row", "find_ingredients");
    }

    @Test
    void publishesWhatSolveWeekTakes() {
        Tool solveWeek = client.listTools().tools().stream()
                .filter(tool -> tool.name().equals("solve_week"))
                .findFirst()
                .orElseThrow();

        assertThat(solveWeek.inputSchema()).containsEntry("required", List.of("week", "targets"));
        assertThat(solveWeek.inputSchema().toString())
                .contains("per_portion")
                .contains("max_batch_g")
                .contains("stay_near_book");
    }

    @Test
    void solvesAWeekSentOverTheWire() {
        CallToolResult result = client.callTool(new CallToolRequest(
                "solve_week", Map.of("week", Fixtures.weekRequest(), "targets", Fixtures.targetsRequest())));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(result)).endsWith("CHECKS PASS");
    }

    @Test
    void refusesACallThatDoesNotFitAndSaysEveryWayItDoesNot() {
        Map<String, Object> week = new HashMap<>(Fixtures.weekRequest());
        Map<String, Object> recipes = new HashMap<>(mapAt(week, "recipes"));
        Map<String, Object> stew = new HashMap<>(mapAt(recipes, "chicken-stew"));
        stew.put("portions", 4.7);
        stew.put("portion", 4);
        recipes.put("chicken-stew", stew);
        week.put("recipes", recipes);
        week.put("days", new ArrayList<>());
        Map<String, Object> targets = new HashMap<>(Fixtures.targetsRequest());
        targets.remove("five_a_day");
        targets.put("salt_max_g_per_person_day", "six");

        CallToolResult result =
                client.callTool(new CallToolRequest("solve_week", Map.of("week", week, "targets", targets)));

        assertThat(result.isError()).isTrue();
        assertThat(text(result))
                .as("part of a portion")
                .contains("/week/recipes/chicken-stew/portions")
                .as("a misspelt field")
                .contains("'portion'")
                .as("no days")
                .contains("/week/days")
                .as("a missing field")
                .contains("'five_a_day'")
                .as("text where a number belongs")
                .contains("/targets/salt_max_g_per_person_day");
    }

    @Test
    void holdsACallThatFitsTheSchemaToTheRulesASchemaCannotState() {
        Map<String, Object> week = new HashMap<>(Fixtures.weekRequest());
        Map<String, Object> monday =
                new HashMap<>(mapAt(Map.of("day", castList(week.get("days")).getFirst()), "day"));
        Map<String, Object> meals = new HashMap<>(mapAt(monday, "meals"));
        meals.put("lunch", List.of("a-recipe-the-week-does-not-have"));
        monday.put("meals", meals);
        week.put("days", List.of(monday, castList(week.get("days")).getLast()));

        CallToolResult result = client.callTool(
                new CallToolRequest("solve_week", Map.of("week", week, "targets", Fixtures.targetsRequest())));

        assertThat(result.isError()).isTrue();
        assertThat(text(result))
                .startsWith("The week needs fixing:")
                .contains("unknown recipe \"a-recipe-the-week-does-not-have\"");
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapAt(Map<String, Object> holder, String name) {
        return (Map<String, Object>) holder.get(name);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object value) {
        return (List<Object>) value;
    }
}
