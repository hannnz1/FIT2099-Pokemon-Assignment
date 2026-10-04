package game.agent.llm;

import org.junit.jupiter.api.Test;
import javax.net.ssl.HttpsURLConnection;
import java.net.*;
import java.io.*;
import java.security.cert.Certificate;
import static org.junit.jupiter.api.Assertions.*;

class GeminiHttpTransportTest {
    static class Connection extends HttpsURLConnection {
        final ByteArrayOutputStream output=new ByteArrayOutputStream();
        int status=200; byte[] response="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        boolean disconnected,inputRead;
        Connection(URL url) { super(url); }
        @Override public void disconnect() { disconnected=true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
        @Override public String getCipherSuite() { return "test"; }
        @Override public Certificate[] getLocalCertificates() { return null; }
        @Override public Certificate[] getServerCertificates() { return new Certificate[0]; }
        @Override public OutputStream getOutputStream() { return output; }
        @Override public int getResponseCode() { return status; }
        @Override public InputStream getInputStream() { inputRead=true; return new ByteArrayInputStream(response); }
    }
    @Test void keyIsHeaderOnlyAtFixedHostAndRequestsHaveTimeouts() throws Exception {
        Connection[] seen=new Connection[1];
        GeminiHttpTransport transport=new GeminiHttpTransport(url->{seen[0]=new Connection(url);return seen[0];});
        assertEquals(200,transport.send("gemini-3.8-flash","test-key","{\"text\":\"树果\"}",1200).status);
        Connection c=seen[0]; assertEquals("generativelanguage.googleapis.com",c.getURL().getHost()); assertNull(c.getURL().getQuery());
        assertEquals("test-key",c.getRequestProperty("x-goog-api-key")); assertEquals("POST",c.getRequestMethod());
        assertFalse(c.getInstanceFollowRedirects()); assertEquals(1200,c.getReadTimeout()); assertEquals(1200,c.getConnectTimeout());
        assertFalse(c.output.toString("UTF-8").contains("test-key")); assertTrue(c.disconnected);
    }
    @Test void redirectAndErrorBodiesAreNotReadAndConnectionCloses() throws Exception {
        Connection c=new Connection(new URL("https://generativelanguage.googleapis.com")); c.status=302;
        assertEquals(302,new GeminiHttpTransport(url->c).send("gemini-3.8-flash","test-key","{}",1000).status);
        assertFalse(c.inputRead); assertTrue(c.disconnected);
    }
    @Test void excessiveBodiesAndMalformedUtf8AreRejected() throws Exception {
        Connection c=new Connection(new URL("https://generativelanguage.googleapis.com")); c.response=new byte[Json.MAX_LENGTH+1];
        assertThrows(IOException.class,()->new GeminiHttpTransport(url->c).send("gemini-3.8-flash","test-key","{}",1000)); assertTrue(c.disconnected);
        c.response=new byte[]{(byte)0xc3,0x28};
        assertThrows(IOException.class,()->new GeminiHttpTransport(url->c).send("gemini-3.8-flash","test-key","{}",1000));
    }
    @Test void overallDeadlineDisconnectsEvenWithoutReadInactivityTimeout() throws Exception {
        java.util.concurrent.CountDownLatch disconnectSignal=new java.util.concurrent.CountDownLatch(1);
        Connection c=new Connection(new URL("https://generativelanguage.googleapis.com")) {
            @Override public void disconnect() { super.disconnect(); disconnectSignal.countDown(); }
            @Override public int getResponseCode() {
                try { disconnectSignal.await(2,java.util.concurrent.TimeUnit.SECONDS); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                return 200;
            }
        };
        assertThrows(SocketTimeoutException.class,()->new GeminiHttpTransport(url->c).send("gemini-3.8-flash","test-key","{}",50));
        assertTrue(c.disconnected); assertFalse(c.inputRead);
    }
}
