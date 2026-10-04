package game.agent.llm;

import game.agent.runtime.AgentLoop;

/** Provider-neutral decision boundary, receives no mutable game objects. */
public interface LlmGateway extends AgentLoop.DecisionProvider { }
