# Technologies

A compilation of references relevant to the agentic coding harness — some we'll use directly, others we'll draw on as inspiration.

## Fabro
- [fabro-sh/fabro](https://github.com/fabro-sh/fabro) (~960 stars, MIT)
- Open-source platform that orchestrates AI coding agents through defined workflow graphs — a middle path between micromanaging agents and reviewing massive unvetted diffs
- Deterministic workflow graphs defined in Graphviz DOT, with branching, loops, and human-in-the-loop approval gates
- Multi-model routing via CSS-like stylesheets to optimize cost and performance
- Cloud sandbox execution with network isolation; git-based checkpointing for resumable runs
- REST API with web UI and SSE streaming
- Single compiled Rust binary, no runtime dependencies (deployable via Docker or standalone CLI)

## Factory.ai
- [factory.ai](https://factory.ai/) (commercial product)
- Agent-native software development platform — closest commercial analog to what we're building; useful as a reference for product direction
- Powered by "Droids" — autonomous AI agents that handle code generation and engineering tasks
- "Mission"-based development workflow
- Desktop app + CLI (curl-installed)
- Enterprise tier with security compliance (SLA, DPA, BAA)
- Talk: ["The Multi-Agent Architecture That Actually Ships"](https://www.youtube.com/watch?v=ow1we5PzK-o) by Luke Alvoeiro (Factory) — presentation on their workflow approach
