package game.agent.web;
import java.util.Map;
interface WebRoom {
    String getId();
    AgentRoom.Reply command(Map<String,Object> input,long now);
    AgentRoom.Reply command(Map<String,Object> input,long now,boolean otherModeActive);
    AgentRoom.Reply command(Map<String,Object> input,long now,String controlBlock);
    void tick(long now);
    Map<String,Object> snapshot();
    void close();
}
