# Inspiration

A collection of products, stories, and patterns that inform the direction of this project. Nothing prescriptive — just useful reference points.

---

## Tempo.io — The Jira Extension That Became a Platform

**Source:** https://www.tempo.io/

### What it is
Tempo is a third-party app built on top of Atlassian Jira. It adds time tracking, capacity planning, financial management, and strategic roadmapping — features Jira doesn't offer natively. Used by 30,000+ organizations including Fortune 500 companies.

### Why it's relevant
This project follows the exact same architectural pattern:

| | Tempo | This Project |
|---|---|---|
| Parent platform | Jira | Follow Up Boss (FUB) |
| Integration method | Jira REST API | FUB API |
| Core value | Adds missing intelligence to project data | Adds missing automation to lead data |
| Target user | Project managers / CFOs | Real estate agents / brokers |

### The origin story
Tempo started as an internal tool built by an IT consulting firm in Iceland (TM Software) because Jira had no built-in time tracking. It solved a real, personal pain point. It got popular. They spun it off as a company in 2015, joined the Atlassian Marketplace, and it became a major enterprise product.

### Key takeaways for this project

1. **Start with a real gap** — Tempo didn't invent a problem. FUB doesn't natively handle the level of lead automation serious agents need. That gap is the product.

2. **The API extension model scales** — Tempo proved that "parent platform + smart extension layer" is a valid and valuable architecture. Not a workaround — a product category.

3. **Less competition here** — The Jira marketplace is saturated. FUB's automation ecosystem is far less built out, which means less noise to cut through.

4. **The moat is reliability + breadth** — Other agents could build their own scripts. The reason they'd pay for this instead is that it works reliably, covers more automation scenarios, and doesn't require them to write code.

5. **Monetization path exists** — Tempo monetizes via the Atlassian Marketplace. FUB has its own integrations/partner ecosystem. This project could follow that same route.

---
