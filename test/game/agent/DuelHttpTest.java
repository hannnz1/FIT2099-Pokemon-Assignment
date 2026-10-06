package game.agent;
import game.agent.web.*;import game.agent.llm.Json;import java.nio.file.Paths;import java.net.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class DuelHttpTest {
 @Test void duelRoutesUseOwnerSessionCsrfAndPackagedAssets()throws Exception{
  try(AgentWebServer server=new AgentWebServer(0,new ProviderSelection("disabled","none",1000,null,null),Paths.get("client/agent"))){server.start();AgentWebServerTest.Browser a=new AgentWebServerTest.Browser(server.getPort()),b=new AgentWebServerTest.Browser(server.getPort());
   assertEquals(200,a.connection("/duel/","GET").getResponseCode());assertEquals(200,a.connection("/duel/duel.mjs","GET").getResponseCode());assertEquals(200,a.connection("/duel/client.mjs","GET").getResponseCode());
   Map<String,Object> room=a.post("/api/duel/rooms",Collections.emptyMap());String root="/api/duel/rooms/"+room.get("roomId");assertEquals(404,b.connection(root+"/snapshot","GET").getResponseCode());
   assertEquals(403,a.post("/api/duel/rooms","{}",a.base,"wrong").getResponseCode());Map<String,Object> view=AgentWebServerTest.read(a.connection(root+"/snapshot","GET"));
   Map<String,Object> start=Json.object("requestId",UUID.randomUUID().toString(),"taskId",view.get("taskId"),"expectedRevision",view.get("revision"),"command","START","params",Json.object("mode","auto","seed",11,"leftModel","baseline","rightModel","baseline","teamIds",Collections.emptyList()));assertEquals("ACCEPTED",a.post(root+"/commands",start).get("outcome"));
  }
 }
 @Test void duelOnlyCookieRestoresSameOwnerAfterServerRestart()throws Exception{
  java.nio.file.Path dir=java.nio.file.Files.createTempDirectory("duel-http-restart");String cookie,task;int port;
  ProviderSelection disabled=new ProviderSelection("disabled","none",1000,null,null);
  try(AgentWebServer server=new AgentWebServer(0,disabled,Paths.get("client/agent"),false,new game.agent.persistence.FileWorldStore(dir))){server.start();port=server.getPort();AgentWebServerTest.Browser browser=new AgentWebServerTest.Browser(port);cookie=browser.cookie;Map<String,Object> room=browser.post("/api/duel/rooms",Collections.emptyMap());task=(String)AgentWebServerTest.read(browser.connection("/api/duel/rooms/"+room.get("roomId")+"/snapshot","GET")).get("taskId");}
  try(AgentWebServer server=new AgentWebServer(port,disabled,Paths.get("client/agent"),false,new game.agent.persistence.FileWorldStore(dir))){server.start();AgentWebServerTest.Browser browser=new AgentWebServerTest.Browser(port);browser.cookie=cookie;Map<String,Object> session=AgentWebServerTest.read(browser.connection("/api/duel/session","GET"));browser.csrf=(String)session.get("csrfToken");Map<String,Object> restored=browser.post("/api/duel/rooms",Collections.emptyMap());assertEquals(task,AgentWebServerTest.read(browser.connection("/api/duel/rooms/"+restored.get("roomId")+"/snapshot","GET")).get("taskId"));}
 }
}
