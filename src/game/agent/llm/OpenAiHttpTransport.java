package game.agent.llm;

import javax.net.ssl.HttpsURLConnection;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Java 8, fixed official HTTPS endpoint, system certificate validation, no redirects. */
public final class OpenAiHttpTransport implements JsonTransport {
    interface ConnectionFactory { HttpsURLConnection open(URL url) throws IOException; }
    private static final ScheduledThreadPoolExecutor DEADLINES=new ScheduledThreadPoolExecutor(1,job->{
        Thread thread=new Thread(job,"openai-http-deadline");thread.setDaemon(true);return thread;
    });
    static { DEADLINES.setRemoveOnCancelPolicy(true); }
    private final ConnectionFactory factory;
    public OpenAiHttpTransport() { this(url->(HttpsURLConnection)url.openConnection()); }
    OpenAiHttpTransport(ConnectionFactory factory) { this.factory=java.util.Objects.requireNonNull(factory); }
    @Override public Response send(String model,String apiKey,String json,int timeoutMillis) throws IOException {
        if(timeoutMillis<1 || timeoutMillis>60000 || apiKey==null || !apiKey.matches("[!-~]{1,512}")) throw new IOException("INVALID_CONFIG");
        byte[] payload=json.getBytes(StandardCharsets.UTF_8);if(payload.length>Json.MAX_LENGTH)throw new IOException("REQUEST_LIMIT");
        HttpsURLConnection connection=factory.open(new URL("https://api.openai.com/v1/responses"));
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);AtomicBoolean timedOut=new AtomicBoolean();
        ScheduledFuture<?> timer=DEADLINES.schedule(()->{timedOut.set(true);connection.disconnect();},timeoutMillis,TimeUnit.MILLISECONDS);
        try {
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(timeoutMillis);connection.setReadTimeout(timeoutMillis);
            connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("Authorization","Bearer "+apiKey);connection.setFixedLengthStreamingMode(payload.length);
            try(OutputStream out=connection.getOutputStream()){out.write(payload);}
            int status=connection.getResponseCode();check(timedOut,deadline);
            if(status!=200 && status!=429)return new Response(status,"");
            InputStream stream=status==200?connection.getInputStream():connection.getErrorStream();
            if(stream==null)return new Response(status,"");
            String body;
            try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];int count;
                while((count=in.read(buffer))!=-1){check(timedOut,deadline);if(out.size()+count>Json.MAX_LENGTH)throw new IOException("RESPONSE_LIMIT");out.write(buffer,0,count);}
                body=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(out.toByteArray())).toString();
            }
            check(timedOut,deadline);
            if(status==429) { // retain only known quota code, never error text or echoed credentials
                boolean quota=false;
                try {quota="insufficient_quota".equals(Json.asObject(Json.asObject(Json.read(body)).get("error")).get("code"));}
                catch(IllegalArgumentException ignored) { }
                body=quota?"{\"error\":{\"code\":\"insufficient_quota\"}}":"";
            }
            return new Response(status,body);
        } catch(IOException error){check(timedOut,deadline);throw error;}
        finally {timer.cancel(false);connection.disconnect();}
    }
    private static void check(AtomicBoolean timedOut,long deadline) throws SocketTimeoutException {
        if(timedOut.get() || System.nanoTime()-deadline>=0)throw new SocketTimeoutException("REQUEST_TIMEOUT");
    }
}
