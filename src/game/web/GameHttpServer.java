package game.web;
import com.sun.net.httpserver.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import java.net.*;import java.io.*;import java.security.SecureRandom;import java.util.*;import java.util.concurrent.*;
import game.runtime.GameSession;

/** Same-origin API and classpath-only release assets. No source-tree file serving. */
public final class GameHttpServer implements AutoCloseable {
    private final HttpServer server;private final SessionStore store;private final boolean secure;private final String origin;
    private final ObjectMapper json=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final ExecutorService workers=Executors.newFixedThreadPool(8);
    private final ScheduledExecutorService sweeper=Executors.newSingleThreadScheduledExecutor();
    private final Set<String> assets=new HashSet<>();private final SecureRandom random=new SecureRandom();
    public GameHttpServer(int port,SessionStore store,boolean secure,String publicOrigin) throws IOException {
        this.store=store;this.secure=secure;server=HttpServer.create(new InetSocketAddress(port),64);origin=publicOrigin==null?"http://localhost:"+server.getAddress().getPort():publicOrigin;
        try(InputStream in=getClass().getResourceAsStream("/public/files.json")){if(in!=null)for(JsonNode p:json.readTree(in))assets.add(p.asText());}
        server.setExecutor(workers);server.createContext("/",this::handle);
    }
    public int port(){return server.getAddress().getPort();}
    public void start(){server.start();sweeper.scheduleAtFixedRate(store::reap,60,60,TimeUnit.SECONDS);}
    private String identity(HttpExchange e,boolean create) {
        String cookie=e.getRequestHeaders().getFirst("Cookie"),key=secure?"__Host-pokemon":"pokemon";
        if(cookie!=null)for(String part:cookie.split(";")){String[] kv=part.trim().split("=",2);if(kv.length==2&&kv[0].equals(key)&&kv[1].matches("[A-Za-z0-9_-]{43}"))return kv[1];}
        if(!create)return "anonymous";
        byte[] token=new byte[32];random.nextBytes(token);String owner=Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        e.getResponseHeaders().add("Set-Cookie",key+"="+owner+"; Path=/; HttpOnly; SameSite=Lax"+(secure?"; Secure":""));return owner;
    }
    private JsonNode body(HttpExchange e) throws IOException {
        String content=e.getRequestHeaders().getFirst("Content-Type");if(content==null||!content.toLowerCase(Locale.ROOT).startsWith("application/json"))throw new ApiException(415,"CONTENT_TYPE","需要 JSON 请求");
        ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] chunk=new byte[2048];int count;
        while((count=e.getRequestBody().read(chunk))!=-1){if(b.size()+count>16384)throw new ApiException(413,"BODY_TOO_LARGE","请求过大");b.write(chunk,0,count);}
        try {JsonNode node=json.readTree(b.toByteArray());if(node==null||!node.isObject())throw new IOException();return node;}catch(IOException ex){throw new ApiException(400,"MALFORMED","JSON 格式错误");}
    }
    private void handle(HttpExchange e) throws IOException {
        try {
            e.getResponseHeaders().set("X-Content-Type-Options","nosniff");e.getResponseHeaders().set("Referrer-Policy","same-origin");
            e.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
            String path=e.getRequestURI().getPath(),method=e.getRequestMethod();
            if(path.startsWith("/api/")||path.equals("/health"))e.getResponseHeaders().set("Cache-Control","no-store");
            if(method.equals("POST")||method.equals("DELETE")){if(!origin.equals(e.getRequestHeaders().getFirst("Origin")))throw new ApiException(403,"ORIGIN","请求来源不匹配");}
            if(path.equals("/health")&&method.equals("GET")){send(e,200,Collections.singletonMap("status","ok"));return;}
            if(path.equals("/api/games")&&method.equals("POST")) {
                body(e);String owner=identity(e,true);GameSession previous=null;try{previous=store.get(owner,null);}catch(ApiException ignored){}
                send(e,previous==null?201:200,store.create(owner).snapshot());return;
            }
            if(path.equals("/api/games/current")&&method.equals("GET")){send(e,200,store.get(identity(e,false),null).snapshot());return;}
            if(path.matches("/api/games/[a-f0-9-]{36}(/actions)?")) {
                String[] parts=path.split("/");String id=parts[3],owner=identity(e,false);
                if(parts.length==4&&method.equals("GET")){send(e,200,store.get(owner,id).snapshot());return;}
                if(parts.length==4&&method.equals("DELETE")){store.delete(owner,id);send(e,204,null);return;}
                if(parts.length==5&&method.equals("POST")) {
                    JsonNode b=body(e);if(b.size()!=3||!b.path("requestId").isTextual()||!b.path("actionId").isTextual()||!b.path("expectedRevision").isIntegralNumber()||!b.path("expectedRevision").canConvertToInt())throw new ApiException(400,"MALFORMED","动作字段格式错误");
                    SessionStore.Result result=store.action(owner,id,b.get("requestId").asText(),b.get("expectedRevision").asInt(),b.get("actionId").asText());
                    System.out.println("action requestId="+result.requestId+" gameId="+id+" revision="+result.appliedRevision+" replayed="+result.replayed);
                    send(e,200,result);return;
                }
            }
            if(method.equals("GET")&&!path.startsWith("/api/")) {
                String name=path.equals("/")?"index.html":path.substring(1);
                if(assets.contains(name)&&!name.contains("..")&&!name.contains("\\"))try(InputStream in=getClass().getResourceAsStream("/public/"+name)){
                    if(in!=null){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)b.write(buffer,0,n);e.getResponseHeaders().set("Content-Type",mime(name));e.getResponseHeaders().set("Cache-Control",name.equals("index.html")?"no-cache":"public, max-age=3600");byte[] bytes=b.toByteArray();e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);return;}
                }
            }
            throw new ApiException(404,"NOT_FOUND","未找到资源");
        } catch(ApiException ex) {Map<String,Object> error=new LinkedHashMap<>();error.put("code",ex.code);error.put("message",ex.getMessage());send(e,ex.status,error);}
        catch(Exception ex){System.err.println("Request failed: "+e.getRequestMethod()+" "+e.getRequestURI().getPath());ex.printStackTrace();send(e,500,Collections.singletonMap("message","服务执行异常；本局可能已结束，请读取状态或重新开始"));}
        finally{e.close();}
    }
    private String mime(String name){if(name.endsWith(".html"))return "text/html; charset=utf-8";if(name.endsWith(".js"))return "application/javascript; charset=utf-8";if(name.endsWith(".css"))return "text/css; charset=utf-8";if(name.endsWith(".json"))return "application/json";if(name.endsWith(".png"))return "image/png";if(name.endsWith(".svg"))return "image/svg+xml";return "application/octet-stream";}
    private void send(HttpExchange e,int status,Object value) throws IOException {if(status==204){e.sendResponseHeaders(status,-1);return;}byte[] bytes=json.writeValueAsBytes(value);e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);}
    public void close(){server.stop(0);sweeper.shutdownNow();workers.shutdownNow();store.close();}
}
