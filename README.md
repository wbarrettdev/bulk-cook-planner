# bulk-cook-planner

Plans a week of meals around one big cook, on a local model.

It picks the dishes, scales each recipe so every day hits its calorie and macro targets, and writes out the recipes, a menu and a shopping list. You can also chat to it about cooking and nutrition.

Most of it isn't built yet. See [ARCHITECTURE.md](ARCHITECTURE.md) for the design.

## What runs today

| Service | What it is |
|---|---|
| `llm` | gpt-oss-20b on llama.cpp |
| `graphdb` | Neo4j, for the flavour pairing graph |

## Run it

Needs Docker and an NVIDIA card with 16GB of memory.

```sh
docker compose up -d
```

The model is about 12GB and downloads on first start, into `/models` on the host.

- Model API: http://localhost:8081
- Neo4j browser: http://localhost:7474

Both are on localhost only, with no login. 

## Data

The full pairing graph is built from a book I own, so that data isn't in the repo. `samples/pairings/` has a small hand-written set to use instead.
