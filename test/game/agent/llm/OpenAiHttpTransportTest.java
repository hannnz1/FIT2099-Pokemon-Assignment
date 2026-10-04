package game.agent.llm;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiHttpTransportTest {
    static class Connection extends GeminiHttpTransportTest.Connection {
        boolean errorRead;
        Connection() throws Exception { super(new URL("https://api.openai.com/v1/responses")); }
        @Override public InputStream getErrorStream() { errorRead=true; return new ByteArrayInputStream(response); }
    }
    @Test void keyOnlyGoesToAuthorizationHeaderAtFixedOpenAiEndpoint() throws Exception {
        Connection c=new Connection();
        new OpenAiHttpTransport(url->{assertEquals("https://api.openai.com/v1/responses",url.toString());return c;})
            .send("gpt-6.1-sol","test-key","{}",1000);
        assertEquals("Bearer test-key",c.getRequestProperty("Authorization")); assertFalse(c.output.toString("UTF-8").contains("test-key"));
        assertEquals("POST",c.getRequestMethod());assertFalse(c.getInstanceFollowRedirects());assertTrue(c.disconnected);
    }
    @Test void errorsAreNotReturnedAndQuotaRetainsOnlyFiniteCode() throws Exception {
        Connection c=new Connection(); c.status=401;c.response="test-key private details".getBytes("UTF-8");
        assertEquals("",new OpenAiHttpTransport(url->c).send("gpt-6.1-sol","test-key","{}",1000).body);assertFalse(c.errorRead);
        c.status=429;c.response="{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"test-key private details\"}}".getBytes("UTF-8");
        String body=new OpenAiHttpTransport(url->c).send("gpt-6.1-sol","test-key","{}",1000).body;
        assertFalse(body.contains("test-key"));assertFalse(body.contains("private details"));assertTrue(body.contains("insufficient_quota"));
    }
    @Test void totalDeadlineRejectsResponseThatArrivesAfterTimeout() throws Exception {
        CountDownLatch signal=new CountDownLatch(1);
        Connection c=new Connection() {
            @Override public void disconnect() {super.disconnect();signal.countDown();}
            @Override public int getResponseCode() {try {signal.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return 200;}
        };
        assertThrows(SocketTimeoutException.class,()->new OpenAiHttpTransport(url->c).send("gpt-6.1-sol","test-key","{}",50));
        assertTrue(c.disconnected);assertFalse(c.inputRead);
    }
    @Test void malformedUtf8AndExcessiveResponseAreRejected() throws Exception {
        Connection c=new Connection();c.response=new byte[]{(byte)0xc3,0x28};
        assertThrows(IOException.class,()->new OpenAiHttpTransport(url->c).send("gpt-6.1-sol","test-key","{}",1000));
        c.response=new byte[Json.MAX_LENGTH+1];
        assertThrows(IOException.class,()->new OpenAiHttpTransport(url->c).send("gpt-6.1-sol","test-key","{}",1000));
    }
}
