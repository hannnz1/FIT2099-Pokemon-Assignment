package game.agent;
import game.agent.web.*;import game.agent.growth.GrowthRules;import java.net.*;import java.io.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class GrowthExpansionAssetsTest {
 @Test void allTwentyOneFormsHaveServedPortraitAndSpritePngs()throws Exception{try(AgentWebServer server=new AgentWebServer(0,new ProviderSelection("none","offline",1000,null,null),null)){server.start();for(String species:GrowthRules.speciesIds())for(String style:Arrays.asList("portraits","sprites")){HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+server.getPort()+"/rpg/assets/images/pokemon/"+style+"/"+species.toLowerCase(Locale.ROOT)+".png").openConnection();c.setReadTimeout(3000);assertEquals(200,c.getResponseCode(),species+"/"+style);try(DataInputStream in=new DataInputStream(c.getInputStream())){assertEquals(0x89504e47,in.readInt());assertEquals(0x0d0a1a0a,in.readInt());}c.disconnect();}}}
}
