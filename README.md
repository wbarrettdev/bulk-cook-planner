# bulk-cook-planner

Plans a week of meals around one big cook, on a local model.

It picks the dishes, scales each recipe so every day hits its calorie and macro targets, and writes out the recipes, a menu and a shopping list. You can also chat to it about cooking and nutrition.

I'm building the tools first and the agents last. Two tool servers run today. The planner and the agents aren't built yet. See [ARCHITECTURE.md](ARCHITECTURE.md) for the design.

## What runs today

| Service | What it is | Where |
|---|---|---|
| `knowledge` | MCP server. Flavour pairings, from a graph | http://localhost:8082/mcp |
| `optimiser` | MCP server. Scales a week to its targets and looks up nutrition values | http://localhost:8083/mcp |
| `graphdb` | Neo4j, for the pairing graph | http://localhost:7474 |
| `db` | PostgreSQL with pgvector, for the nutrition data | localhost:5432 |
| `llm` | gpt-oss-20b on llama.cpp | http://localhost:8081 |

## Tools

| Tool | Server | What it does |
|---|---|---|
| `suggest_additions` | `knowledge` | Takes a dish's main ingredients and suggests what to add, with the reason for each |
| `check_pairings` | `knowledge` | Says whether one ingredient pairs with each of several others |
| `solve_week` | `optimiser` | Scales a week's recipes so each day hits its targets, keeping each recipe in proportion |
| `search_nutrition` | `optimiser` | Searches CoFID, USDA or Open Food Facts for a food's values per 100g |
| `add_nutrition_row` | `optimiser` | Stores an ingredient's nutrition, taken from one of those records |
| `find_ingredients` | `optimiser` | Shows what the nutrition table already holds for an ingredient |

Every quantity in a plan comes from the solver, and every nutrition value from a named record. The model never types a number.

## Run it

Needs Docker. The model also needs an NVIDIA card with 16GB of memory.

```sh
cp .env.example .env                                  # optional settings, all can stay empty
mkdir -p volumes/graphdb/import
cp samples/pairings/*.csv volumes/graphdb/import/     # the sample pairings
docker compose up -d
docker compose exec -T graphdb cypher-shell < samples/pairings/load.cypher
```

## Build and test

Needs Java 21, Maven and Docker.

```sh
mvn verify
```

The database tests start their own PostgreSQL and Neo4j. They're skipped if Docker isn't running.
