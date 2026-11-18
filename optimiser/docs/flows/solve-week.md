# Solving a week

What happens when a client calls `solve_week`.

```mermaid
sequenceDiagram
    participant C as MCP client
    participant T as solve_week
    participant W as WeekOptimiser
    participant D as nutrition database
    participant S as OR-Tools

    C->>T: week, targets
    T->>T: read the request
    T->>W: optimise
    W->>W: check the targets are complete
    W->>D: read the nutrition table
    D-->>W: macros, salt, fibre, five-a-day
    W->>W: check the week, look up every ingredient
    W->>S: build the programme and solve
    S-->>W: whole steps for each ingredient
    W->>W: work out amounts, day totals, shopping, checks
    W-->>T: report
    T-->>C: report as text and as structured content
```

## When it stops early

Each of these comes back as the tool's answer, marked as an error, with what to change:

| Where | Why | The answer says |
|---|---|---|
| Reading the request | A value has the wrong shape, such as 4.7 portions | Which value, by its path in the request |
| Checking the targets | Something the solve relies on is missing | Every gap, not only the first |
| Checking the week | An unknown recipe, a meal with nothing in it, an ingredient that isn't in the table | Every problem found |
| Reading the table | The database can't be reached | That the nutrition tables can't be reached |
| Solving | No amounts can meet every bound | To change the plan, with suggestions |

A week that solves but misses a target is not an error. The report comes back with the failed checks listed.
