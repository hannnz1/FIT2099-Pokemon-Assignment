package game.agent;
import game.agent.web.*;
import java.util.*;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WebDeploymentTest {
    @Test void publicHttpsConfigurationIsExplicitAndValidated() {
        Map<String,String> env=new HashMap<>();env.put("PUBLIC_ORIGIN","https://pokemon.hanzhu-lab.online");env.put("AGENT_BIND_ADDRESS","0.0.0.0");
        WebDeployment c=WebDeployment.fromEnvironment(env,8080);
        assertEquals("pokemon.hanzhu-lab.online",c.host);assertEquals("0.0.0.0",c.bindAddress);assertTrue(c.secureCookie);
        for(String invalid:Arrays.asList("https://example.com/path","https://user:pass@example.com","http://example.com","https://example.com?x=1","https://example.com/#x")) {
            env.put("PUBLIC_ORIGIN",invalid);assertThrows(IllegalArgumentException.class,()->WebDeployment.fromEnvironment(env,8080));
        }
    }
    @Test void proxiedRequestsKeepHostOriginAndCsrfChecks() throws Exception {
        Map<String,String> env=new HashMap<>();env.put("PUBLIC_ORIGIN","https://pokemon.hanzhu-lab.online");
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),Paths.get("client/agent"),false,null,false,env)) {
            s.start();
            // Raw browser headers avoid JDK filtering of Host and Origin.
            AgentWebServerTest.Browser local;
            String response=wire(s.getPort(),"GET","/api/quest/session","pokemon.hanzhu-lab.online",null,null,null,"{}");
            assertTrue(response.startsWith("HTTP/1.1 200"));assertTrue(response.contains("Secure"));
            String cookie=Arrays.stream(response.split("\r\n")).filter(x->x.toLowerCase().startsWith("set-cookie:")).findFirst().get().substring(12).trim().split(";",2)[0];
            String csrf=(String)game.agent.llm.Json.asObject(game.agent.llm.Json.read(response.split("\r\n\r\n",2)[1])).get("csrfToken");
            assertTrue(wire(s.getPort(),"POST","/api/quest/rooms","pokemon.hanzhu-lab.online","https://pokemon.hanzhu-lab.online",cookie,csrf,"{}").startsWith("HTTP/1.1 200"));
            assertTrue(wire(s.getPort(),"POST","/api/quest/rooms","pokemon.hanzhu-lab.online","https://evil.example",cookie,csrf,"{}").startsWith("HTTP/1.1 403"));
            assertTrue(wire(s.getPort(),"POST","/api/quest/rooms","evil.example","https://pokemon.hanzhu-lab.online",cookie,csrf,"{}").startsWith("HTTP/1.1 403"));
            assertTrue(wire(s.getPort(),"POST","/api/quest/rooms","pokemon.hanzhu-lab.online","https://pokemon.hanzhu-lab.online",cookie,"wrong","{}").startsWith("HTTP/1.1 403"));
        }
    }
    static String wire(int port,String method,String path,String host,String origin,String cookie,String csrf,String body)throws Exception{
        try(java.net.Socket s=new java.net.Socket("127.0.0.1",port)){
            s.setSoTimeout(4000);byte[] b=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String h=method+" "+path+" HTTP/1.1\r\nHost: "+host+"\r\nConnection: close\r\nContent-Type: application/json\r\nContent-Length: "+b.length+"\r\n";
            if(origin!=null)h+="Origin: "+origin+"\r\n";if(cookie!=null)h+="Cookie: "+cookie+"\r\n";if(csrf!=null)h+="X-CSRF-Token: "+csrf+"\r\n";
            s.getOutputStream().write((h+"\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));s.getOutputStream().write(b);
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buf=new byte[4096];for(int n;(n=s.getInputStream().read(buf))!=-1;)out.write(buf,0,n);
            return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
