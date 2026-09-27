package game.web;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.*;
import game.runtime.*;
class HttpServerTest {
    GameHttpServer server;String base,cookie;ObjectMapper json=new ObjectMapper();
    @BeforeEach void start() throws Exception {server=new GameHttpServer(0,new SessionStore(DemoMap::create),false,null);server.start();base="http://localhost:"+server.port();}
    @AfterEach void stop(){server.close();}
    static class Reply {int status;String body,cookie;}
    Reply call(String method,String path,String body,String identity,String origin) throws Exception {
        java.net.http.HttpRequest.Builder req=java.net.http.HttpRequest.newBuilder(URI.create(base+path));
        if(identity!=null)req.header("Cookie",identity);if(origin!=null)req.header("Origin",origin);
        if(body!=null)req.header("Content-Type","application/json");
        req.method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(body));
        java.net.http.HttpResponse<String> response=java.net.http.HttpClient.newHttpClient().send(req.build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        Reply r=new Reply();r.status=response.statusCode();r.cookie=response.headers().firstValue("Set-Cookie").orElse(null);r.body=response.body();return r;
    }
    @Test void realHttpCreateWaitReplayAndOwnership() throws Exception {
        Reply created=call("POST","/api/games","{}",null,base);assertEquals(201,created.status);assertTrue(created.cookie.contains("HttpOnly"));cookie=created.cookie.split(";")[0];
        JsonNode s=json.readTree(created.body);String id=s.get("gameId").asText(),action=null;for(JsonNode a:s.get("availableActions"))if(a.get("kind").asText().equals("WAIT"))action=a.get("id").asText();
        String req="{\"requestId\":\"r1\",\"expectedRevision\":0,\"actionId\":\""+action+"\"}";
        Reply applied=call("POST","/api/games/"+id+"/actions",req,cookie,base);assertEquals(200,applied.status);assertEquals(1,json.readTree(applied.body).at("/snapshot/turn").asInt());
        assertTrue(json.readTree(call("POST","/api/games/"+id+"/actions",req,cookie,base).body).get("replayed").asBoolean());
        assertEquals(404,call("GET","/api/games/"+id,null,null,null).status);assertEquals(200,call("GET","/api/games/current",null,cookie,null).status);
        assertEquals(204,call("DELETE","/api/games/"+id,null,cookie,base).status);assertEquals(204,call("DELETE","/api/games/"+id,null,cookie,base).status);
    }
    @Test void malformedCrossOriginAndLargeRequestsAreRejected() throws Exception {
        assertEquals(403,call("POST","/api/games","{}",null,"https://attacker.invalid").status);
        assertEquals(400,call("POST","/api/games","{",null,base).status);
        assertEquals(413,call("POST","/api/games","{\"x\":\""+new String(new char[17000]).replace('\0','x')+"\"}",null,base).status);
        assertEquals(404,call("GET","/../pom.xml",null,null,null).status);
    }
}
