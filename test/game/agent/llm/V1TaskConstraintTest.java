package game.agent.llm;
import edu.monash.fit2099.engine.positions.*;
import game.environments.Dirt;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class V1TaskConstraintTest {
    private Map<String,Object> output() { return Json.object("status","SUPPORTED","goal","COMPLETE_QUEST","questId","quest","constraints",Arrays.asList("AREA_RESTRICTED","DEADLINE"),"unsupportedClauses",Collections.emptyList(),"areaId","QUEST_AREA","deadlineTurn",5); }
    @Test void typedParamsDecodeAndSchemaRequiresNullableFields() throws Exception {
        TaskIntent intent=TaskIntentCodec.decode(Json.write(output()),"quest");
        assertEquals("QUEST_AREA",intent.getAreaId());
        assertEquals(Long.valueOf(5),intent.getDeadlineTurn());
        assertTrue(Json.asArray(TaskIntentCodec.schema("quest").get("required")).containsAll(Arrays.asList("areaId","deadlineTurn")));
    }
    @Test void malformedAndMismatchedTypedParamsFailClosed() {
        for(Object bad:Arrays.asList("5",5.5,0,-1)) { Map<String,Object> v=output(); v.put("deadlineTurn",bad); assertThrows(ProviderException.class,()->TaskIntentCodec.decode(Json.write(v),"quest")); }
        for(Object bad:Arrays.asList("", "../lab", 3)) { Map<String,Object> v=output(); v.put("areaId",bad); assertThrows(ProviderException.class,()->TaskIntentCodec.decode(Json.write(v),"quest")); }
        Map<String,Object> v=output(); v.put("constraints",Collections.emptyList()); assertThrows(ProviderException.class,()->TaskIntentCodec.decode(Json.write(v),"quest"));
    }
    @Test void oldTwoConstraintOutputStillDecodes() { Map<String,Object> v=output(); v.remove("areaId"); v.remove("deadlineTurn"); v.put("constraints",Arrays.asList("NO_SPENDING","NO_ACTIVE_BATTLE")); assertTrue(TaskIntentCodec.decode(Json.write(v),"quest").has(TaskIntent.Constraint.NO_SPENDING)); }
    @Test void policyRejectsIntermediateTilesUnknownAreaAndExtendedDeadline() throws Exception {
        GameMap map=new GameMap(new FancyGroundFactory(new Dirt()),Arrays.asList("..."));
        Set<Location> allowed=new HashSet<>(Arrays.asList(map.at(0,0),map.at(2,0))); Map<String,Set<Location>> catalog=new HashMap<>(); catalog.put("QUEST_AREA",allowed);
        TaskIntent intent=TaskIntentCodec.decode(Json.write(output()),"quest"); ConstraintPolicy policy=new ConstraintPolicy(intent,catalog,10L);
        assertTrue(policy.allows(map.at(0,0))); assertFalse(policy.allows(map.at(1,0))); assertTrue(policy.allows(map.at(2,0)));
        new edu.monash.fit2099.engine.positions.World(new edu.monash.fit2099.engine.displays.Display()).addGameMap(map);
        game.actors.Player actor=new game.actors.Player("Ash",'@',100); map.addActor(actor,map.at(0,0));
        game.agent.navigation.NavigationService nav=new game.agent.navigation.NavigationService();
        assertEquals("NO_PATH",nav.step(actor,map,map.at(2,0),policy::allows,()->true).getCode());
        assertSame(map.at(0,0),map.locationOf(actor));
        allowed.add(map.at(1,0)); assertFalse(policy.allows(map.at(1,0))); assertEquals(5L,policy.deadlineTurn());
        assertThrows(IllegalArgumentException.class,()->new ConstraintPolicy(intent,Collections.emptyMap(),10L));
        assertThrows(IllegalArgumentException.class,()->new ConstraintPolicy(intent,catalog,4L));
    }
}
