# Agent module source attribution

Java adaptations made 2026-09-30. Upstream code was read and selected algorithms
and control structures were adapted; these are not drop-in upstream packages.
Exact revisions and source paths are recorded in agent-port-sources.json.
Upstream license texts are preserved in the adjacent project directories.

| Upstream and pinned revision | Source | Java adaptation | Intentional changes |
| --- | --- | --- | --- |
| mindcraft-bots/mindcraft, 5f3acc87b479864124173de444f31fa5538f94a6 | src/agent/commands/index.js, commands/actions.js | tools/GameToolRegistry, ToolDefinition, ToolParameter | Registry and schema validation translated to Java; regex command parsing replaced with structured inputs; policy and session-lifetime receipts added |
| mindcraft-bots/mindcraft, same revision | src/agent/action_manager.js, self_prompter.js | action/ActionResult, runtime/AgentLoop | Outcome separation, retained action continuation, pause/resume/cancel, immutable decisions and bounded automatic loop; no generated code, process killing or endless self-prompting |
| a16z-infra/ai-town, 8e05997f2409275669c8344b84a51692e83f3f33 | convex/aiTown/agent.ts, agentInputs.ts | runtime/AgentTask and AgentRunner | In-progress operation identity, timeout and late-completion checks; Convex replaced by caller-owned Java executors and explicit task transitions |
| a16z-infra/ai-town, same revision | convex/aiTown/movement.ts | navigation/NavigationService | Priority-queue search and predecessor reconstruction; real eight-way/custom exits; zero heuristic to remain optimal with arbitrary shortcuts; no partial-route success |
| joonspk-research/generative_agents, fe05a71d3e4ed7d10bf68aa4eda6dd995ec070f4 | persona/cognitive_modules/perceive.py, persona/memory_structures/associative_memory.py under reverie/backend_server | memory/NpcMemory, MemoryService, MemoryStore, tools/MemoryTools | Event records, visible-event filtering, nearest-first attention and dedup; immutable Java values, world/NPC/source scopes, occurrence-time retrieval and versioned event save/load; no embeddings/reflection |

Mindcraft: Copyright (c) 2024 Kolby Nottingham, MIT.
AI Town: Copyright (c) 2023 a16z-infra, MIT.
Generative Agents: original source author Joon Sung Park, Apache License 2.0.
All Java files listed above were modified/reimplemented for this project.
No upstream game art, Minecraft components, ROMs, Convex runtime or model
credentials are included. The existing FIT2099 source attribution is unchanged.

Source URLs use https://github.com/OWNER/REPO/blob/COMMIT/PATH with the revisions
above. Full license terms, including warranty disclaimers, are in:

- mindcraft/LICENSE
- ai-town/LICENSE
- generative-agents/LICENSE
