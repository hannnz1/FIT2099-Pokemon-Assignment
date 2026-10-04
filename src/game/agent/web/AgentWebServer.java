package game.agent.web;

import com.sun.net.httpserver.*;
import game.agent.llm.Json;
import game.agent.persistence.*;
import game.agent.eval.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Local browser entry point. No database or general-purpose remote hosting implied. */
public final class AgentWebServer implements AutoCloseable {
    private static final int BODY_LIMIT=16384;
    private static final Map<String,String> ASSETS=new HashMap<>();
    static {ASSETS.put("/","index.html");ASSETS.put("/app.mjs","app.mjs");ASSETS.put("/client.mjs","client.mjs");ASSETS.put("/dialogue.mjs","dialogue.mjs");ASSETS.put("/battle-view.mjs","battle-view.mjs");ASSETS.put("/style.css","style.css");}
    static {
        ASSETS.put("/mode-navigation.mjs","mode-navigation.mjs");
        ASSETS.put("/rpg-view.mjs","rpg-view.mjs");ASSETS.put("/rpg-scene.mjs","rpg-scene.mjs");
        for(String name:Arrays.asList("vendor/phaser-3.60.0.min.js","vendor/PHASER-LICENSE.md","assets/images/monster-tamer/map/main_1_level_background.png","assets/images/monster-tamer/map/bushes.png","assets/images/monster-tamer/battle-backgrounds/forest-background.png","assets/images/monster-tamer/monsters/aquavalor.png","assets/images/monster-tamer/monsters/carnodusk.png","assets/images/monster-tamer/monsters/iguanignite.png","assets/images/axulart/character/custom.png","assets/images/axulart/character/license.txt","LICENSE","THIRD_PARTY_NOTICES.md","UPSTREAM.md"))ASSETS.put("/rpg/"+name,"rpg/"+name);
        for(String prefix:Arrays.asList("/quest","/training")){
            ASSETS.put(prefix,"index.html");ASSETS.put(prefix+"/","index.html");
            for(String name:Arrays.asList("app.mjs","client.mjs","dialogue.mjs","battle-view.mjs","mode-navigation.mjs","rpg-view.mjs","rpg-scene.mjs","pokemon-art.mjs","rpg.css","style.css"))ASSETS.put(prefix+"/"+name,name);
        }
    }
    static {
        ASSETS.put("/growth","growth.html");ASSETS.put("/growth/","growth.html");
        for(String name:Arrays.asList("growth.mjs","growth-view.mjs","growth-scene.mjs","growth.css","growth-shell.css","growth-shell.mjs","growth-maps.json","pokemon-art.mjs","client.mjs"))ASSETS.put("/growth/"+name,name);
        for(String name:Arrays.asList("assets/images/monster-tamer/map/buildings/building_1_level_background.png","assets/images/monster-tamer/map/buildings/building_1_level_foreground.png","assets/images/monster-tamer/map/forest_1_level_background.png","assets/images/monster-tamer/map/forest_1_level_foreground.png","assets/images/monster-tamer/monsters/jivy.png","assets/images/monster-tamer/monsters/frostsaber.png","assets/images/monster-tamer/monsters/Ignivolt.png"))ASSETS.put("/rpg/"+name,"rpg/"+name);
    }
    static {
        ASSETS.put("/pokemon-art.mjs","pokemon-art.mjs"); for(String species:game.agent.growth.GrowthRules.speciesIds())for(String style:Arrays.asList("portraits","sprites")){String file="rpg/assets/images/pokemon/"+style+"/"+species.toLowerCase(Locale.ROOT)+".png";ASSETS.put("/"+file,file);}
        for(String name:Arrays.asList("assets/images/pokemon/portraits/treecko.png","assets/images/pokemon/sprites/treecko.png","assets/images/pokemon/portraits/grovyle.png","assets/images/pokemon/sprites/grovyle.png","assets/images/pokemon/portraits/sceptile.png","assets/images/pokemon/sprites/sceptile.png","assets/images/pokemon/portraits/torchic.png","assets/images/pokemon/sprites/torchic.png","assets/images/pokemon/portraits/combusken.png","assets/images/pokemon/sprites/combusken.png","assets/images/pokemon/portraits/blaziken.png","assets/images/pokemon/sprites/blaziken.png","assets/images/pokemon/portraits/mudkip.png","assets/images/pokemon/sprites/mudkip.png","assets/images/pokemon/portraits/marshtomp.png","assets/images/pokemon/sprites/marshtomp.png","assets/images/pokemon/portraits/swampert.png","assets/images/pokemon/sprites/swampert.png","assets/images/pokemon/sources.json","assets/images/pokemon/UPSTREAM-LICENCE.txt"))ASSETS.put("/rpg/"+name,"rpg/"+name);
    }
    static {ASSETS.put("/evaluation","evaluation.html");ASSETS.put("/evaluation/","evaluation.html");for(String file:Arrays.asList("evaluation.mjs","evaluation-view.mjs","evaluation.css"))ASSETS.put("/evaluation/"+file,file);}
    static {for(String prefix:Arrays.asList("","/quest","/training","/growth"))for(String file:Arrays.asList("game-ui.mjs","game-ui.css","ui-journey.mjs","failure-recovery.mjs","world-art.mjs","map-boundary.mjs","building-cutout.mjs","growth-entrance.mjs","map-polish.mjs"))ASSETS.put(prefix+"/"+file,file);}
    static {ASSETS.put("/training/training-ui.css","training-ui.css");}
    static {for(String id:Arrays.asList("orchard","berry","recovery","gate")){String file="rpg/assets/ui/world/"+id+".svg";ASSETS.put("/"+file,file);}}
    static {for(String id:Arrays.asList("professor","merchant")){String file="rpg/assets/ui/npcs/"+id+".svg";ASSETS.put("/"+file,file);}}
    private static final class Session {
        final String owner,csrf=UUID.randomUUID().toString();
        Session(String owner){this.owner=owner;}
        WebRoom questRoom,trainingRoom,growthRoom;boolean selectedGrowth;long window,growthWindow;int posts,growthPosts;
        PokemonCollection collection;EvaluationService evaluation;
        Boolean selectedTraining;
        final Set<String> switched=new LinkedHashSet<>();
        WebRoom room(boolean training){return training?trainingRoom:questRoom;}
        void room(boolean training,WebRoom value){if(training)trainingRoom=value;else questRoom=value;}
    }
    private static final class Response {
        final int status;final byte[] bytes;final String type,cookie;String download;
        Response(int s,byte[] b,String t,String c) {status=s;bytes=b;type=t;cookie=c;}
    }
    private final HttpServer server;
    private final ScheduledExecutorService world=Executors.newSingleThreadScheduledExecutor(factory("pokemon-world"));
    private final ThreadPoolExecutor models=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),factory("pokemon-model"),new ThreadPoolExecutor.AbortPolicy());
    private final ThreadPoolExecutor http=new ThreadPoolExecutor(4,4,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),factory("pokemon-http"),new ThreadPoolExecutor.AbortPolicy());
    private final Map<String,Session> sessions=new LinkedHashMap<>();
    private final ProviderSelection provider;
    private final EvaluationProviders evaluationProviders=EvaluationProviders.environment();
    private final Path assetRoot;
    private final String origin,host,cookieName;
    private final boolean secureCookie;
    private final WorldStore store;
    private final boolean original;
    private final boolean battle;
    private boolean started;
    public AgentWebServer(int port,ProviderSelection provider,Path assetRoot) throws IOException {
        this(port,provider,assetRoot,false,null);
    }
    public AgentWebServer(int port,ProviderSelection provider,Path assetRoot,boolean original,WorldStore store) throws IOException {
        this(port,provider,assetRoot,original,store,false);
    }
    public AgentWebServer(int port,ProviderSelection provider,Path assetRoot,boolean original,WorldStore store,boolean battle) throws IOException {
        this(port,provider,assetRoot,original,store,battle,Collections.emptyMap());
    }
    public AgentWebServer(int port,ProviderSelection provider,Path assetRoot,boolean original,WorldStore store,boolean battle,Map<String,String> deploymentEnv) throws IOException {
        if(port<0 || port>65535) throw new IllegalArgumentException("INVALID_PORT");
        this.provider=Objects.requireNonNull(provider);this.assetRoot=assetRoot==null?null:assetRoot.toAbsolutePath().normalize();
        this.original=original;this.store=store;this.battle=battle;
        WebDeployment requested=WebDeployment.fromEnvironment(deploymentEnv,port);
        server=HttpServer.create(new InetSocketAddress(InetAddress.getByName(requested.bindAddress),port),32);
        WebDeployment config=WebDeployment.fromEnvironment(deploymentEnv,getPort());
        host=config.host;origin=config.origin;secureCookie=config.secureCookie;
        // Cookies share a host across ports; local game instances need separate names.
        cookieName="PA_SESSION_"+getPort();
        server.setExecutor(http);server.createContext("/",this::handle);
    }
    public int getPort() {return server.getAddress().getPort();}
    public void start() {
        if(started) throw new IllegalStateException("ALREADY_STARTED");started=true;
        world.scheduleWithFixedDelay(()->{
            for(Session session:sessions.values()) for(WebRoom room:Arrays.asList(session.questRoom,session.trainingRoom,session.growthRoom))if(room!=null) {
                try {room.tick(now());} catch(RuntimeException ignored) {room.close();}
            }
        for(Session session:sessions.values())if(session.evaluation!=null)session.evaluation.tick(now());
        },100,100,TimeUnit.MILLISECONDS);server.start();
    }
    private void handle(HttpExchange exchange) throws IOException {
        try {
            if(!host.equals(exchange.getRequestHeaders().getFirst("Host"))) {send(exchange,json(403,Json.object("reasonCode","INVALID_HOST")));return;}
            String path=exchange.getRequestURI().getRawPath(),method=exchange.getRequestMethod();
            if(exchange.getRequestURI().getRawQuery()!=null) {send(exchange,json(400,Json.object("reasonCode","QUERY_NOT_ALLOWED")));return;}
            if(path.startsWith("/api/agent/")||path.startsWith("/api/quest/")||path.startsWith("/api/training/")||path.startsWith("/api/growth/")||path.startsWith("/api/evaluation/")) {
                if(!method.equals("GET") && !method.equals("POST")) {send(exchange,json(405,Json.object("reasonCode","METHOD_NOT_ALLOWED")));return;}
                if(method.equals("POST") && !origin.equals(exchange.getRequestHeaders().getFirst("Origin"))) {send(exchange,json(403,Json.object("reasonCode","INVALID_ORIGIN")));return;}
                String incomingOrigin=exchange.getRequestHeaders().getFirst("Origin");
                if(incomingOrigin!=null && !incomingOrigin.equals(origin)) {send(exchange,json(403,Json.object("reasonCode","INVALID_ORIGIN")));return;}
                String cookie=cookie(exchange),csrf=exchange.getRequestHeaders().getFirst("X-CSRF-Token");
                Map<String,Object> body=Collections.emptyMap();
                if(method.equals("POST")) {
                    String type=exchange.getRequestHeaders().getFirst("Content-Type");
                    if(type==null || !type.toLowerCase(Locale.ROOT).matches("application/json(?:;\\s*charset=utf-8)?")) {send(exchange,json(415,Json.object("reasonCode","JSON_REQUIRED")));return;}
                    try {body=readBody(exchange);} catch(BodyLimit error) {send(exchange,json(413,Json.object("reasonCode","BODY_LIMIT")));return;}
                    catch(IllegalArgumentException | CharacterCodingException error) {send(exchange,json(400,Json.object("reasonCode","INVALID_JSON")));return;}
                }
                final Map<String,Object> input=body;
                final boolean training=path.startsWith("/api/training/")||path.startsWith("/api/agent/")&&battle;
                final String canonical=path.replaceFirst("^/api/(quest|training)/","/api/agent/");
                send(exchange,onWorld(()->api(canonical,method,cookie,csrf,input,training)));
            } else if(method.equals("GET") && ASSETS.containsKey(path)) {
                String file=ASSETS.get(path);boolean training=path.equals("/training")||path.startsWith("/training/")||battle&&!path.equals("/quest")&&!path.startsWith("/quest/");
                if(training&&file.equals("index.html"))file="battle.html";if(training&&file.equals("app.mjs"))file="battle.mjs";if(training&&file.equals("style.css"))file="battle.css";byte[] bytes=asset(file);
                if(bytes==null) {send(exchange,json(404,Json.object("reasonCode","ASSET_NOT_FOUND")));return;}
                String type=file.endsWith(".html")?"text/html; charset=utf-8":file.endsWith(".css")?"text/css; charset=utf-8":file.endsWith(".json")?"application/json; charset=utf-8":file.endsWith(".png")?"image/png":file.endsWith(".svg")?"image/svg+xml":file.endsWith(".md")||file.endsWith(".txt")||file.endsWith("LICENSE")?"text/plain; charset=utf-8":"text/javascript; charset=utf-8";
                send(exchange,new Response(200,bytes,type,null));
            } else send(exchange,json(404,Json.object("reasonCode","NOT_FOUND")));
        } catch(TimeoutException | RejectedExecutionException error) {send(exchange,json(503,Json.object("reasonCode","SERVER_BUSY")));}
        catch(InterruptedException error) {Thread.currentThread().interrupt();send(exchange,json(503,Json.object("reasonCode","SERVER_BUSY")));}
        catch(ExecutionException | RuntimeException error) {send(exchange,json(500,Json.object("reasonCode","SERVER_ERROR")));}
        finally {exchange.close();}
    }
    private Response api(String path,String method,String cookie,String csrf,Map<String,Object> body,boolean training) {
        Session session=sessions.get(cookie);
        if((path.equals("/api/agent/session")||path.equals("/api/growth/session")||path.equals("/api/evaluation/session")) && method.equals("GET")) {
            String setCookie=null;
            if(session==null) {
                if(sessions.size()>=8) return json(429,Json.object("reasonCode","SESSION_LIMIT"));
                String token=cookie!=null&&store!=null&&(store.load(cookie)!=null||store.load("battle:"+cookie)!=null||store.load("collection:"+cookie)!=null||store.load("growth:"+cookie)!=null||store.load("evaluation:"+cookie)!=null)?cookie:UUID.randomUUID().toString();
                session=new Session(token);sessions.put(token,session);
                setCookie=cookieName+"="+token+"; Path=/; Max-Age=2592000; HttpOnly; SameSite=Strict"+(secureCookie?"; Secure":"");
            }
            Response response=json(200,Json.object("csrfToken",session.csrf));
            return new Response(response.status,response.bytes,response.type,setCookie);
        }
        if(session==null) return json(401,Json.object("reasonCode","SESSION_REQUIRED"));
        if(method.equals("POST")) {
            if(!session.csrf.equals(csrf)) return json(403,Json.object("reasonCode","INVALID_CSRF"));
            long now=now();if(now-session.window>=60000) {session.window=now;session.posts=0;}
            // Stopping is always available for the current task, even after other commands hit the local cap.
            if(path.startsWith("/api/growth/")){if(now-session.growthWindow>=60000){session.growthWindow=now;session.growthPosts=0;}if(!"CANCEL".equals(body.get("command"))&&++session.growthPosts>600)return json(429,Json.object("reasonCode","RATE_LIMIT"));}
            else if(!Arrays.asList("CANCEL","PAUSE","RECOVER","NPC_PAUSE","NPC_CANCEL").contains(body.get("command")) && ++session.posts>60) return json(429,Json.object("reasonCode","RATE_LIMIT"));
        }
        if(path.startsWith("/api/evaluation/")) {
            if(session.evaluation==null)session.evaluation=new EvaluationService(session.owner,store,evaluationProviders,models);
            try {
                if(path.equals("/api/evaluation/catalog")&&method.equals("GET"))return json(200,session.evaluation.catalog());
                if(path.equals("/api/evaluation/batches")&&method.equals("GET"))return json(200,session.evaluation.snapshot());
                if(path.equals("/api/evaluation/commands")&&method.equals("POST"))return json(200,session.evaluation.command(body));
                String[] segments=path.split("/");
                if(segments.length==6&&segments[3].equals("batches")&&segments[5].equals("download")&&method.equals("GET"))return attachment(session.evaluation.report(segments[4]),"v4-batch-report.json");
                if(segments.length==8&&segments[3].equals("batches")&&segments[5].equals("runs")&&segments[7].equals("download")&&method.equals("GET"))return attachment(session.evaluation.replay(segments[4],segments[6]),"v4-case-replay.json");
                if(segments.length==6&&segments[3].equals("batches")&&segments[5].equals("report")&&method.equals("GET"))return json(200,session.evaluation.report(segments[4]));
                if(segments.length==8&&segments[3].equals("batches")&&segments[5].equals("runs")&&segments[7].equals("replay")&&method.equals("GET"))return json(200,session.evaluation.replay(segments[4],segments[6]));
                return json(404,Json.object("reasonCode","NOT_FOUND"));
            }catch(NoSuchElementException error){return json(404,Json.object("reasonCode",error.getMessage()));}
            catch(IllegalArgumentException error){return json(400,Json.object("reasonCode","INVALID_EVALUATION_REQUEST"));}
            catch(IllegalStateException error){return json(409,Json.object("reasonCode",error.getMessage()));}
        }
        if(path.startsWith("/api/growth/")) {
            if(path.equals("/api/growth/rooms")&&method.equals("POST")) {
                if(!body.isEmpty())return json(400,Json.object("reasonCode","INVALID_FIELDS"));
                for(WebRoom previous:Arrays.asList(session.questRoom,session.trainingRoom))if(previous!=null&&Arrays.asList("RUNNING","REPLANNING","WAITING_APPROVAL").contains(previous.snapshot().get("status"))){AgentRoom.Reply paused=pause(previous);if(paused.status!=200)return json(paused.status,paused.body);}
                if(session.collection==null)session.collection=new PokemonCollection(session.owner,store);
                if(session.growthRoom==null)session.growthRoom=new GrowthRoom(session.owner,provider,models,store,session.collection);
                AgentRoom.Reply npcPaused=pauseNpc(session.questRoom);if(npcPaused!=null&&npcPaused.status!=200)return json(npcPaused.status,npcPaused.body);
                session.selectedGrowth=true;return json(200,Json.object("roomId",session.growthRoom.getId()));
            }
            String[] parts=path.split("/");WebRoom room=session.growthRoom;
            if(parts.length!=6||!parts[3].equals("rooms")||room==null||!parts[4].equals(room.getId()))return json(404,Json.object("reasonCode","ROOM_NOT_FOUND"));
            if(parts[5].equals("snapshot")&&method.equals("GET"))return json(200,room.snapshot());
            if(parts[5].equals("commands")&&method.equals("POST")){if("RETURN_GROWTH".equals(body.get("command"))&&session.questRoom!=null&&Arrays.asList("RUNNING","REPLANNING","PAUSED","PROVIDER_UNAVAILABLE","WAITING_APPROVAL","PARSING","READY").contains(session.questRoom.snapshot().get("status")))return json(409,Json.object("reasonCode","CANCEL_QUEST_BEFORE_TRAINING"));AgentRoom.Reply result=room.command(body,now(),session.selectedGrowth?null:"MODE_NOT_SELECTED");return json(result.status,result.body);}
            return json(404,Json.object("reasonCode","NOT_FOUND"));
        }
        if(path.equals("/api/agent/rooms") && method.equals("POST")) {
            if(!body.isEmpty()) return json(400,Json.object("reasonCode","INVALID_FIELDS"));
            if(session.collection==null)session.collection=new PokemonCollection(session.owner,store);
            if(session.room(training)==null)session.room(training,training?new BattleRoom(session.owner,provider,models,store,session.collection):new AgentRoom(session.owner,provider,models,original,store,session.collection));
            WebRoom previous=session.room(!training);
            if(previous!=null&&Arrays.asList("RUNNING","REPLANNING","WAITING_APPROVAL").contains(previous.snapshot().get("status"))){
                Map<String,Object> snapshot=previous.snapshot();
                AgentRoom.Reply paused=previous.command(Json.object("requestId",UUID.randomUUID().toString(),"taskId",snapshot.get("taskId"),"expectedRevision",snapshot.get("revision"),"command","PAUSE","params",Collections.emptyMap()),now());
                if(paused.status!=200)return json(paused.status,paused.body);
            }
            if(session.growthRoom!=null&&Arrays.asList("RUNNING","REPLANNING").contains(session.growthRoom.snapshot().get("status"))){AgentRoom.Reply paused=pause(session.growthRoom);if(paused.status!=200)return json(paused.status,paused.body);}
            if(training){AgentRoom.Reply npcPaused=pauseNpc(session.questRoom);if(npcPaused!=null&&npcPaused.status!=200)return json(npcPaused.status,npcPaused.body);}
            session.selectedGrowth=false;session.selectedTraining=training;
            return json(200,Json.object("roomId",session.room(training).getId()));
        }
        String[] parts=path.split("/");
        WebRoom room=session.room(training);
        if(parts.length!=6 || !parts[1].equals("api") || !parts[2].equals("agent") || !parts[3].equals("rooms")
            || room==null || !parts[4].equals(room.getId())) return json(404,Json.object("reasonCode","ROOM_NOT_FOUND"));
        if(parts[5].equals("snapshot") && method.equals("GET")){Map<String,Object> snapshot=room.snapshot();List<Map<String,Object>> rows=session.collection.snapshot();
            if(session.collection.deployment()!=null){
                if(session.questRoom==null)session.questRoom=new AgentRoom(session.owner,provider,models,original,store,session.collection);
                Map<String,Object> quest=training?session.questRoom.snapshot():snapshot;
                Map<String,Object> actual=quest.get("summoned")==null?null:Json.asObject(quest.get("summoned"));rows=session.collection.snapshot();
                for(Map<String,Object> row:rows)if("DEPLOYED".equals(row.get("state"))){row.remove("x");row.remove("y");if(actual!=null&&row.get("captureId").equals(actual.get("captureId"))){row.put("x",actual.get("x"));row.put("y",actual.get("y"));}}
            }
            snapshot.put("collection",rows);snapshot.put("collectionAvailable",session.collection.available());return json(200,snapshot);}
        if(parts[5].equals("commands") && method.equals("POST")) {
            WebRoom other=session.room(!training);
            boolean otherActive=other!=null&&Arrays.asList("RUNNING","REPLANNING","WAITING_APPROVAL").contains(other.snapshot().get("status"));
            String block=session.selectedGrowth?"MODE_NOT_SELECTED":session.selectedTraining!=null&&session.selectedTraining!=training?"MODE_NOT_SELECTED":otherActive?"OTHER_MODE_ACTIVE":null;
            AgentRoom.Reply result=room.command(body,now(),block);
            if(result.status==200&&"SWITCH_MODE".equals(body.get("command"))){
                String receipt=room.getId()+":"+body.get("taskId")+":"+body.get("requestId");
                if(session.switched.add(receipt)){session.selectedTraining=!training;if(session.switched.size()>1024)session.switched.remove(session.switched.iterator().next());}
            }
            return json(result.status,result.body);
        }
        return json(404,Json.object("reasonCode","NOT_FOUND"));
    }
    private AgentRoom.Reply pauseNpc(WebRoom room){if(room==null)return null;Map<String,Object> snapshot=room.snapshot();if(snapshot.get("npcAgents")==null||!"RUNNING".equals(Json.asObject(snapshot.get("npcAgents")).get("status")))return null;return room.command(Json.object("requestId",UUID.randomUUID().toString(),"taskId",snapshot.get("taskId"),"expectedRevision",snapshot.get("revision"),"command","NPC_PAUSE","params",Collections.emptyMap()),now());}
    private AgentRoom.Reply pause(WebRoom room){Map<String,Object> snapshot=room.snapshot();return room.command(Json.object("requestId",UUID.randomUUID().toString(),"taskId",snapshot.get("taskId"),"expectedRevision",snapshot.get("revision"),"command","PAUSE","params",Collections.emptyMap()),now());}
    private <T> T onWorld(Callable<T> action) throws InterruptedException,ExecutionException,TimeoutException {
        Future<T> future=world.submit(action);
        try {return future.get(2,TimeUnit.SECONDS);} catch(TimeoutException error) {future.cancel(false);throw error;}
    }
    private byte[] asset(String name) throws IOException {
        try(InputStream in=AgentWebServer.class.getResourceAsStream("/web/"+name)) {
            if(in!=null) return bounded(in,name.startsWith("rpg/")?8388608:262144);
        }
        if(assetRoot!=null) {Path file=assetRoot.resolve(name);if(Files.isRegularFile(file) && Files.size(file)<=(name.startsWith("rpg/")?8388608:262144)) return Files.readAllBytes(file);}
        return null;
    }
    private static final class BodyLimit extends IOException {private static final long serialVersionUID=1L;}
    private static Map<String,Object> readBody(HttpExchange exchange) throws IOException {
        String length=exchange.getRequestHeaders().getFirst("Content-Length");
        if(length!=null && Long.parseLong(length)>BODY_LIMIT) throw new BodyLimit();
        byte[] bytes=bounded(exchange.getRequestBody(),BODY_LIMIT);
        CharsetDecoder decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        return Json.asObject(Json.read(decoder.decode(ByteBuffer.wrap(bytes)).toString()));
    }
    private static byte[] bounded(InputStream in,int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[2048];
        for(int n;(n=in.read(buffer))!=-1;) {if(out.size()+n>limit) throw new BodyLimit();out.write(buffer,0,n);}return out.toByteArray();
    }
    private String cookie(HttpExchange exchange) {
        String result=null;List<String> headers=exchange.getRequestHeaders().get("Cookie");if(headers==null)return null;
        for(String header:headers) for(String entry:header.split(";")) {
            String[] pair=entry.trim().split("=",2);if(pair.length==2 && pair[0].equals(cookieName)) {
                if(result!=null || !pair[1].matches("[a-f0-9-]{36}"))return null;result=pair[1];
            }
        }return result;
    }
    private static Response json(int status,Map<String,Object> body) {return new Response(status,Json.write(body).getBytes(StandardCharsets.UTF_8),"application/json; charset=utf-8",null);}
    private static Response attachment(Map<String,Object> body,String filename){Response response=json(200,body);response.download=filename;return response;}
    private static void send(HttpExchange exchange,Response response) throws IOException {
        Headers headers=exchange.getResponseHeaders();headers.set("Content-Type",response.type);headers.set("Cache-Control","no-store");
        headers.set("X-Content-Type-Options","nosniff");headers.set("Referrer-Policy","no-referrer");
        headers.set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        if(response.cookie!=null)headers.set("Set-Cookie",response.cookie);if(response.download!=null)headers.set("Content-Disposition","attachment; filename=\""+response.download+"\"");
        exchange.sendResponseHeaders(response.status,response.bytes.length);
        try(OutputStream out=exchange.getResponseBody()){out.write(response.bytes);}
    }
    private static ThreadFactory factory(String name) {return job->{Thread t=new Thread(job,name);t.setDaemon(true);return t;};}
    private static long now() {return System.nanoTime()/1000000L;}
    @Override public void close() {
        server.stop(0);
        try {onWorld(()->{for(Session s:sessions.values())for(WebRoom room:Arrays.asList(s.questRoom,s.trainingRoom,s.growthRoom))if(room!=null)room.close();for(Session s:sessions.values())if(s.evaluation!=null)s.evaluation.close();sessions.clear();return null;});}
        catch(Exception ignored) { /* executor shutdown still releases resources */ }
        world.shutdownNow();models.shutdownNow();http.shutdownNow();
        if(store!=null)store.close();
    }
    public static void main(String[] args) throws Exception {
        int port=Integer.parseInt(System.getenv().getOrDefault("AGENT_PORT","8088"));
        AgentWebServer server=new AgentWebServer(port,ProviderSelection.fromEnvironment(System.getenv()),Paths.get("client/agent"),
            !"compact".equals(System.getenv("AGENT_WORLD")),WorldStoreFactory.fromEnvironment(System.getenv()),"battle".equals(System.getenv("AGENT_MODE")),System.getenv());
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));server.start();
        System.out.println("Pokemon agent: http://127.0.0.1:"+server.getPort());new CountDownLatch(1).await();
    }
}
