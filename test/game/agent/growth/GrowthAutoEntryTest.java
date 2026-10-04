package game.agent.growth;

import game.agent.action.ActionResult;
import game.agent.llm.Json;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrowthAutoEntryTest {
 private GrowthWorld fresh(boolean adventure){GrowthWorld w=new GrowthWorld(()->0);if(adventure)w.startAdventure();w.starter("TREECKO");return w;}
 private void go(GrowthWorld w,String id){for(int n=0;n<160&&!id.equals(w.region());n++)assertNotEquals(ActionResult.Status.REJECTED,w.execute("go_to_region",Json.object("regionId",id)).getStatus());assertEquals(id,w.region());}
 private GrowthWorld at(GrowthWorld w,int x,int y){Map<String,Object> save=w.export();save.put("x",x);save.put("y",y);return GrowthWorld.restore(save,()->0);}
 private void position(GrowthWorld w,String region,int x,int y){assertEquals(region,w.region());assertEquals(x,w.view().get("x"));assertEquals(y,w.view().get("y"));}

 @Test void cardinalMovementThroughEveryRealExitEntersOneConnectedRegionInOneTurn(){
  Object[][] cases={{"lab",5,6,"S","forest",9,9},{"forest",9,9,"S","lab",5,6},{"forest",12,5,"E","river",3,10},{"river",3,10,"W","forest",12,5},{"river",16,3,"E","mountain",3,10},{"mountain",3,10,"W","river",16,3}};
  for(Object[] c:cases){GrowthWorld w=fresh(false);go(w,(String)c[0]);w=at(w,(Integer)c[1],(Integer)c[2]);int turn=w.turn();ActionResult result=w.move((String)c[3]);assertEquals("REGION_ENTERED",result.getCode(),Arrays.toString(c));assertEquals(ActionResult.Status.SUCCESS,result.getStatus());position(w,(String)c[4],(Integer)c[5],(Integer)c[6]);assertEquals(turn+1,w.turn());}
 }
 @Test void enteringLockedExitRejectsBeforeMovingOrChangingAnyWorldState(){GrowthWorld w=fresh(true);go(w,"forest");w=at(w,12,5);String before=Json.write(w.export());assertEquals("REGION_LOCKED",w.move("E").getCode());assertEquals(before,Json.write(w.export()));}
 @Test void clearingForestAllowsManualEntryIntoRiver(){GrowthWorld w=fresh(true);go(w,"forest");for(int i=0;i<3;i++){String id="forest-1-"+i;for(int n=0;n<80;n++){ActionResult result=w.execute("move_to",Json.object("targetId",id));assertNotEquals(ActionResult.Status.REJECTED,result.getStatus());if(result.getStatus()==ActionResult.Status.SUCCESS)break;}assertEquals("GROWTH_CAPTURED",w.execute("capture",Json.object("targetId",id)).getCode());}assertTrue(w.regionUnlocked("river"));w=at(w,12,5);assertEquals("REGION_ENTERED",w.move("E").getCode());position(w,"river",3,10);assertEquals("river",w.expeditionRegion());}
 @Test void arrivalDoesNotBounceAndReenteringTheReturnExitRequiresAnotherMove(){GrowthWorld w=fresh(false);assertEquals("REGION_ENTERED",w.move("S").getCode());position(w,"forest",9,9);w=GrowthWorld.restore(w.export(),()->0);position(w,"forest",9,9);assertEquals("GROWTH_OBSERVED",w.execute("observe",Collections.emptyMap()).getCode());position(w,"forest",9,9);assertEquals("REGION_ENTERED",w.move("S").getCode());position(w,"lab",5,6);assertEquals(2,w.turn());}
 @Test void transitionsKeepCapturedIndividualsHpSkillsAndCheckpointState(){GrowthWorld w=fresh(false);go(w,"forest");for(int n=0;n<80;n++){if(w.execute("move_to",Json.object("targetId","forest-1-0")).getStatus()==ActionResult.Status.SUCCESS)break;}assertEquals("GROWTH_CAPTURED",w.execute("capture",Json.object("targetId","forest-1-0")).getCode());w.partner().hurt(7);w.partner().pp.put("POUND",3);w=at(w,12,5);String team=Json.write(w.export().get("team"));assertEquals("REGION_ENTERED",w.move("E").getCode());assertEquals(team,Json.write(w.export().get("team")));assertEquals("CAPTURED",Json.asObject(Json.asArray(w.export().get("encounters")).get(0)).get("state"));String checkpoint=Json.write(w.export());GrowthWorld restored=GrowthWorld.restore(w.export(),()->0);assertEquals(checkpoint,Json.write(restored.export()));position(restored,"river",3,10);}
 @Test void ordinaryTilesAndBlockedMovesDoNotTransition(){GrowthWorld w=fresh(false);assertEquals("ARRIVED",w.move("N").getCode());position(w,"lab",5,5);assertEquals(1,w.turn());assertEquals("ARRIVED",w.move("S").getCode());position(w,"lab",5,6);w=at(w,5,7);String before=Json.write(w.export());assertEquals("NO_PATH",w.move("S").getCode());assertEquals(before,Json.write(w.export()));}
}
