package game.agent;

import game.agent.web.*;
import game.agent.llm.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentWebServerTest {
    static final class Browser {
        String cookie,csrf;final String base;
        Browser(int port) throws Exception {
            base="http://127.0.0.1:"+port;
            HttpURLConnection c=connection("/api/agent/session","GET");assertEquals(200,c.getResponseCode());
            cookie=c.getHeaderField("Set-Cookie").split(";",2)[0];
            assertTrue(c.getHeaderField("Set-Cookie").contains("HttpOnly"));assertTrue(c.getHeaderField("Set-Cookie").contains("SameSite=Strict"));
            csrf=(String)read(c).get("csrfToken");
        }
        HttpURLConnection connection(String path,String method) throws Exception {
            HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();c.setConnectTimeout(2000);c.setReadTimeout(3000);c.setRequestMethod(method);
            if(cookie!=null)c.setRequestProperty("Cookie",cookie);return c;
        }
        HttpURLConnection post(String path,String body,String origin,String token) throws Exception {
            // JDK HttpURLConnection silently filters Origin. Send the actual browser headers
            // on a loopback socket rather than weakening the production Origin check.
            return wire(path,body,origin,token,new URL(base).getAuthority());
        }
        HttpURLConnection wire(String path,String body,String origin,String token,String host) throws Exception {
            URL url=new URL(base);byte[] payload=body.getBytes(StandardCharsets.UTF_8);
            try(Socket socket=new Socket("127.0.0.1",url.getPort())) {
                socket.setSoTimeout(3000);OutputStream out=socket.getOutputStream();
                String headers="POST "+path+" HTTP/1.1\r\nHost: "+host+"\r\nOrigin: "+origin+"\r\nX-CSRF-Token: "+token+
                    "\r\nCookie: "+cookie+"\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: "+payload.length+"\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.US_ASCII));out.write(payload);out.flush();
                ByteArrayOutputStream received=new ByteArrayOutputStream();byte[] buf=new byte[4096];
                for(int n;(n=socket.getInputStream().read(buf))!=-1;)received.write(buf,0,n);
                return new WireConnection(url,received.toByteArray());
            }
        }
        Map<String,Object> post(String path,Map<String,Object> body) throws Exception {
            HttpURLConnection c=post(path,Json.write(body),base,csrf);
            int status=c.getResponseCode();
            if(status!=200) {try(InputStream in=c.getErrorStream()) {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[1024];for(int n;(n=in.read(bytes))!=-1;)out.write(bytes,0,n);fail("HTTP "+status+" "+new String(out.toByteArray(),StandardCharsets.UTF_8));}}
            return read(c);
        }
        Map<String,Object> snapshot(String id) throws Exception {HttpURLConnection c=connection("/api/agent/rooms/"+id+"/snapshot","GET");assertEquals(200,c.getResponseCode());return read(c);}
        Map<String,Object> cmd(String id,Map<String,Object> view,String kind,Map<String,Object> params) throws Exception {
            return post("/api/agent/rooms/"+id+"/commands",Json.object("requestId",UUID.randomUUID().toString(),"taskId",view.get("taskId"),"expectedRevision",view.get("revision"),"command",kind,"params",params));
        }
    }
    static final class WireConnection extends HttpURLConnection {
        final byte[] body;
        WireConnection(URL url,byte[] bytes) {
            super(url);String raw=new String(bytes,StandardCharsets.UTF_8);int split=raw.indexOf("\r\n\r\n");
            responseCode=Integer.parseInt(raw.substring(0,raw.indexOf("\r\n")).split(" ")[1]);
            body=raw.substring(split+4).getBytes(StandardCharsets.UTF_8);
        }
        @Override public int getResponseCode(){return responseCode;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(body);}
        @Override public InputStream getErrorStream(){return new ByteArrayInputStream(body);}
        @Override public void disconnect(){}
        @Override public boolean usingProxy(){return false;}
        @Override public void connect(){}
    }
    static Map<String,Object> read(HttpURLConnection c) throws Exception {
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[4096];for(int n;(n=in.read(buf))!=-1;)out.write(buf,0,n);return Json.asObject(Json.read(new String(out.toByteArray(),StandardCharsets.UTF_8)));
        } finally {c.disconnect();}
    }
    static Map<String,Object> await(Browser b,String room,String status) throws Exception {
        long end=System.nanoTime()+8000000000L;Map<String,Object> view;
        do {view=b.snapshot(room);if(status.equals(view.get("status")))return view;Thread.sleep(50);} while(System.nanoTime()<end);
        fail("Expected "+status+"; received "+Json.write(view));return view;
    }
    @Test void realHttpRunsConfirmedQuestAndEnforcesBrowserOwnership() throws Exception {
        JsonTransport fake=(m,k,body,t)->{
            if(body.contains("json_schema"))return new JsonTransport.Response(200,AgentRoomTest.parsed(body));
            Map<String,Object> obs=Json.asObject(Json.asObject(Json.read((String)Json.asObject(Json.read(body)).get("input"))).get("observation"));
            int carried=Integer.parseInt((String)obs.get("carriedBerry"));String x=(String)obs.get("x"),y=(String)obs.get("y");
            String fn,args;
            if(carried==0 && !(x.equals("2")&&y.equals("1"))) {fn="move_to";args="{\"locationId\":\"orchard\"}";}
            else if(carried==0) {fn="pickup";args="{\"itemId\":\"BERRY\",\"quantity\":2}";}
            else if(carried==2 && !(x.equals("6")&&y.equals("2"))) {fn="move_to";args="{\"locationId\":\"alternative\"}";}
            else if(carried==2) {fn="pickup";args="{\"itemId\":\"BERRY\",\"quantity\":1}";}
            else if(!(x.equals("7")&&y.equals("1"))) {fn="move_to";args="{\"locationId\":\"laboratory\"}";}
            else {fn="deliver";args=Json.write(Json.object("questId",obs.get("questId"),"targetNpcId","professor"));}
            return new JsonTransport.Response(200,OpenAiGatewayTest.function(fn,args));
        };
        try(AgentWebServer server=new AgentWebServer(0,AgentRoomTest.provider(fake),Paths.get("client/agent"))) {
            server.start();Browser a=new Browser(server.getPort()),b=new Browser(server.getPort());
            String id=(String)a.post("/api/agent/rooms",Collections.emptyMap()).get("roomId");
            assertEquals(404,b.connection("/api/agent/rooms/"+id+"/snapshot","GET").getResponseCode());
            assertEquals(403,a.post("/api/agent/rooms","{}","https://attacker.example",a.csrf).getResponseCode());
            assertEquals(403,a.post("/api/agent/rooms","{}",a.base,"wrong").getResponseCode());
            assertEquals(403,a.wire("/api/agent/rooms","{}",a.base,a.csrf,"attacker.example").getResponseCode());
            assertEquals(400,a.post("/api/agent/rooms","{\"ownerId\":\"other\"}",a.base,a.csrf).getResponseCode());
            assertEquals(413,a.post("/api/agent/rooms",String.join("",Collections.nCopies(17000,"x")),a.base,a.csrf).getResponseCode());
            Map<String,Object> initial=a.snapshot(id);a.cmd(id,initial,"PARSE",Json.object("text","完成树果任务，不花金币"));
            Map<String,Object> ready=await(a,id,"READY");assertEquals("0",Json.asObject(ready.get("world")).get("x"));
            a.cmd(id,ready,"CONFIRM",Collections.emptyMap());Map<String,Object> done=await(a,id,"COMPLETED");
            assertEquals("3",done.get("delivered").toString());assertEquals("5",Json.asObject(done.get("world")).get("coins"));
            assertFalse(Json.write(done).contains("approvalToken"));assertFalse(Json.write(done).contains("test-key"));
        }
    }
    @Test void localSessionsAndStaticAssetsAreBounded() throws Exception {
        try(AgentWebServer server=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),Paths.get("client/agent"))) {
            server.start();for(int i=0;i<8;i++)new Browser(server.getPort());
            HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+server.getPort()+"/api/agent/session").openConnection();assertEquals(429,c.getResponseCode());
            assertEquals(404,((HttpURLConnection)new URL("http://127.0.0.1:"+server.getPort()+"/../pom.xml").openConnection()).getResponseCode());
        }
    }
    @Test void httpApprovalCannotLeakOrRepeatAPurchase() throws Exception {
        JsonTransport fake=(m,k,body,t)->{
            if(body.contains("json_schema"))return new JsonTransport.Response(200,AgentRoomTest.parsed(body));
            Map<String,Object> obs=Json.asObject(Json.asObject(Json.read((String)Json.asObject(Json.read(body)).get("input"))).get("observation"));
            String fn,args;
            if(!"true".equals(obs.get("nearMerchant"))) {fn="move_to";args="{\"locationId\":\"market\"}";}
            else if(obs.containsKey("approvalToken")) {fn="purchase_item";args=Json.write(Json.object("itemId","BERRY","quantity",1,"approvalToken",obs.get("approvalToken")));}
            else if("0".equals(obs.get("carriedBerry"))) {fn="request_player_approval";args="{\"itemId\":\"BERRY\",\"quantity\":1,\"actionType\":\"PURCHASE_BERRY\",\"reason\":\"purchase one\"}";}
            else {fn="observe_quest";args="{}";}
            return new JsonTransport.Response(200,OpenAiGatewayTest.function(fn,args));
        };
        try(AgentWebServer server=new AgentWebServer(0,AgentRoomTest.provider(fake),Paths.get("client/agent"))) {
            server.start();Browser b=new Browser(server.getPort());String id=(String)b.post("/api/agent/rooms",Collections.emptyMap()).get("roomId");
            b.cmd(id,b.snapshot(id),"PARSE",Json.object("text","完成任务，不花金币"));b.cmd(id,await(b,id,"READY"),"CONFIRM",Collections.emptyMap());
            Map<String,Object> waiting=await(b,id,"WAITING_APPROVAL");Object turn=Json.asObject(waiting.get("world")).get("turn");
            Thread.sleep(250);assertEquals(turn,Json.asObject(b.snapshot(id).get("world")).get("turn"));
            Map<String,Object> params=Json.object("proposalId",Json.asObject(waiting.get("approval")).get("proposalId"));
            Map<String,Object> cmd=Json.object("requestId","exact-approval","taskId",waiting.get("taskId"),"expectedRevision",waiting.get("revision"),"command","APPROVE","params",params);
            String path="/api/agent/rooms/"+id+"/commands";Map<String,Object> ack=b.post(path,cmd);assertFalse(Json.write(ack).contains("approvalToken"));
            assertEquals(ack,b.post(path,cmd));
            Map<String,Object> purchased=null;long until=System.nanoTime()+3000000000L;
            do {purchased=b.snapshot(id);if("1".equals(Json.asObject(purchased.get("world")).get("carriedBerry")))break;Thread.sleep(50);}while(System.nanoTime()<until);
            assertEquals("4",Json.asObject(purchased.get("world")).get("coins"));assertEquals("1",Json.asObject(purchased.get("world")).get("carriedBerry"));
            assertEquals(ack,b.post(path,cmd));assertEquals("4",Json.asObject(b.snapshot(id).get("world")).get("coins"));
            assertFalse(Json.write(purchased).contains("approvalToken"));
        }
    }
    @Test void httpPublishesOnlyDialogueActuallyObtainedInThisRoom() throws Exception {
        JsonTransport fake=(m,k,body,t)->new JsonTransport.Response(200,body.contains("json_schema")?AgentRoomTest.parsed(body):
            OpenAiGatewayTest.function("talk_to","{\"targetNpcId\":\"treecko\",\"message\":\"哪里有树果\"}"));
        try(AgentWebServer server=new AgentWebServer(0,AgentRoomTest.provider(fake),Paths.get("client/agent"))) {
            server.start();Browser a=new Browser(server.getPort()),b=new Browser(server.getPort());
            String id=(String)a.post("/api/agent/rooms",Collections.emptyMap()).get("roomId");String other=(String)b.post("/api/agent/rooms",Collections.emptyMap()).get("roomId");
            assertTrue(Json.asArray(a.snapshot(id).get("dialogues")).isEmpty());a.cmd(id,a.snapshot(id),"PARSE",Json.object("text","完成树果任务"));
            a.cmd(id,await(a,id,"READY"),"CONFIRM",Collections.emptyMap());Map<String,Object> view=null;long until=System.nanoTime()+3000000000L;
            do{view=a.snapshot(id);if(!Json.asArray(view.get("dialogues")).isEmpty())break;Thread.sleep(30);}while(System.nanoTime()<until);
            assertFalse(Json.asArray(view.get("dialogues")).isEmpty());Map<String,Object> first=Json.asObject(Json.asArray(view.get("dialogues")).get(0));
            assertEquals("treecko",first.get("speakerId"));assertEquals("true",first.get("historical"));assertTrue(Json.asArray(b.snapshot(other).get("dialogues")).isEmpty());
            assertEquals("0",Json.asObject(view.get("world")).get("carriedBerry"));assertFalse(Json.write(view).contains("approvalToken"));
        }
    }
    @Test void concurrentLocalPortsCannotOverwriteEachOthersBrowserSession() throws Exception {
        ProviderSelection unavailable=ProviderSelection.fromEnvironment(Collections.emptyMap());
        try(AgentWebServer one=new AgentWebServer(0,unavailable,Paths.get("client/agent"));AgentWebServer two=new AgentWebServer(0,unavailable,Paths.get("client/agent"))) {
            one.start();two.start();Browser a=new Browser(one.getPort()),b=new Browser(two.getPort());
            assertNotEquals(a.cookie.split("=",2)[0],b.cookie.split("=",2)[0],"Cookies on one host are shared across ports");
            String idA=(String)a.post("/api/agent/rooms",Collections.emptyMap()).get("roomId"),idB=(String)b.post("/api/agent/rooms",Collections.emptyMap()).get("roomId");
            String shared=a.cookie+"; "+b.cookie;a.cookie=shared;b.cookie=shared;
            assertEquals(idA,a.snapshot(idA).get("roomId"));assertEquals(idB,b.snapshot(idB).get("roomId"));
            assertNull(a.connection("/api/agent/session","GET").getHeaderField("Set-Cookie"));
            assertNull(b.connection("/api/agent/session","GET").getHeaderField("Set-Cookie"));
        }
    }
}
