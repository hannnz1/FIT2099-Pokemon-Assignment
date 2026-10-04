package game.agent.llm;

import javax.net.ssl.HttpsURLConnection;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Java 8 HTTPS, fixed Google endpoint, no redirects, retries or response logging. */
public final class GeminiHttpTransport implements GeminiTransport {
    private static final ScheduledThreadPoolExecutor DEADLINES=new ScheduledThreadPoolExecutor(1,job->{
        Thread thread=new Thread(job,"gemini-http-deadline"); thread.setDaemon(true); return thread;
    });
    static { DEADLINES.setRemoveOnCancelPolicy(true); }
    interface ConnectionFactory { HttpsURLConnection open(URL url) throws IOException; }
    private final ConnectionFactory factory;
    public GeminiHttpTransport() { this(url->(HttpsURLConnection)url.openConnection()); }
    GeminiHttpTransport(ConnectionFactory factory) { this.factory=java.util.Objects.requireNonNull(factory); }
    @Override public Response send(String model,String apiKey,String json,int timeoutMillis) throws IOException {
        if(timeoutMillis<1 || timeoutMillis>60000) throw new IOException("INVALID_TIMEOUT");
        if(!model.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")) throw new IOException("INVALID_MODEL");
        byte[] payload=json.getBytes(StandardCharsets.UTF_8); if(payload.length>Json.MAX_LENGTH) throw new IOException("REQUEST_LIMIT");
        HttpsURLConnection connection=factory.open(new URL("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent"));
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        AtomicBoolean timedOut=new AtomicBoolean();
        ScheduledFuture<?> timer=DEADLINES.schedule(()->{timedOut.set(true);connection.disconnect();},timeoutMillis,TimeUnit.MILLISECONDS);
        try {
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(timeoutMillis); connection.setReadTimeout(timeoutMillis);
            connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("x-goog-api-key",apiKey); connection.setFixedLengthStreamingMode(payload.length);
            try(OutputStream out=connection.getOutputStream()) { out.write(payload); }
            int status=connection.getResponseCode();
            checkDeadline(timedOut,deadline);
            if(status!=200) return new Response(status,""); // don't read or retain provider error bodies
            try(InputStream in=connection.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int count;
                while((count=in.read(buffer))!=-1) { checkDeadline(timedOut,deadline); if(out.size()+count>Json.MAX_LENGTH) throw new IOException("RESPONSE_LIMIT"); out.write(buffer,0,count); }
                String body=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(out.toByteArray())).toString();
                checkDeadline(timedOut,deadline); return new Response(status,body);
            }
        } catch(IOException error) { checkDeadline(timedOut,deadline); throw error; }
        finally { timer.cancel(false); connection.disconnect(); }
    }
    private static void checkDeadline(AtomicBoolean timedOut,long deadline) throws SocketTimeoutException {
        if(timedOut.get() || System.nanoTime()-deadline>=0) throw new SocketTimeoutException("REQUEST_TIMEOUT");
    }
}
