package game.web;
import game.runtime.DemoMap;
public final class WebApplication {
    public static void main(String[] args) throws Exception {
        int port=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));boolean secure=Boolean.parseBoolean(System.getenv().getOrDefault("SECURE_COOKIE","false"));
        String origin=System.getenv("PUBLIC_ORIGIN");if(secure&&(origin==null||!origin.startsWith("https://")))throw new IllegalArgumentException("SECURE_COOKIE requires HTTPS PUBLIC_ORIGIN");
        GameHttpServer server=new GameHttpServer(port,new SessionStore(DemoMap::create),secure,origin);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));server.start();System.out.println("Pokemon demo: "+(origin==null?"http://localhost:"+port:origin));
    }
}
