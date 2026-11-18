# Checking pairings

What happens when a client calls `check_pairings` to confirm that one ingredient goes with each of several others. It's the short tool: use it to check a pairing before citing it, and `suggest_additions` to find new ideas.

```mermaid
sequenceDiagram
    participant C as MCP client
    participant T as check_pairings
    participant A as PairingAdvisor
    participant G as pairing graph

    C->>T: ingredient, partners
    T->>A: check
    A->>G: which names does the graph have for the ingredient?
    G-->>A: names
    loop each partner
        A->>G: which names does the graph have for this partner?
        G-->>A: names
        A->>G: any pairing between the two, in either direction?
        G-->>A: pairings, each with its strength
    end
    A-->>T: a verdict for each partner
    T-->>C: verdicts as text and as structured content
```

## What a verdict says

| For each partner | Meaning |
|---|---|
| Known | Whether the graph has a name for it |
| Paired | Whether any pairing links it to the ingredient |
| Pairings | The pairings that say so, with their strength from 1 to 4 |

A pairing counts in either direction. One ingredient's entry may list the other without the reverse being true.

The household's rules don't apply here. This tool reports what the graph holds.
