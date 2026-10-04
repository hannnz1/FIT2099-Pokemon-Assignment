package game.agent.llm;

/** Structured interpretation only; the player confirms it before task start. */
public interface TaskInterpreter {
    TaskIntent parse(String text,String trustedQuestId);
}
