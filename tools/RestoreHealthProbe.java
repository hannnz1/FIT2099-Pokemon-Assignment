import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import game.agent.llm.Json;
/** Read-only probe inside an isolated network namespace. Never submits game/model commands. */
public class RestoreHealthProbe {
 public static void main(String[] args)throws Exception{
  for(int i=0;i<30;i++){try{HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:8080/healthz").openConnection();c.setConnectTimeout(1000);c.setReadTimeout(1000);if(c.getResponseCode()!=200)throw new IOException("NOT_READY");String text=new String(c.getInputStream().readAllBytes(),StandardCharsets.UTF_8);if(!Boolean.TRUE.equals(Json.asObject(Json.read(text)).get("ready")))throw new IOException("NOT_READY");HttpURLConnection page=(HttpURLConnection)new URL("http://127.0.0.1:8080/growth/").openConnection();if(page.getResponseCode()!=200)throw new IOException("PAGE_UNAVAILABLE");System.out.println("RESTORED_SERVICE_HEALTH_AND_GROWTH_HTTP200=true");return;}catch(IOException e){Thread.sleep(500);}}
  throw new IllegalStateException("RESTORED_SERVICE_NOT_READY");
 }
}
