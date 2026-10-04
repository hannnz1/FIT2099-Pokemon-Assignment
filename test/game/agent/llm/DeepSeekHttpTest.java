package game.agent.llm;
import java.net.URL;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeepSeekHttpTest {
    @Test void usesOnlyOfficialEndpointWithoutRedirectsOrKeyInBody() throws Exception {
        GeminiHttpTransportTest.Connection c=new GeminiHttpTransportTest.Connection(new URL(DeepSeekTransport.ENDPOINT));
        c.response="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}]}".getBytes("UTF-8");
        DeepSeekTransport transport=new DeepSeekTransport(url->{assertEquals(DeepSeekTransport.ENDPOINT,url.toString());return c;});
        String body=Json.write(Json.object("instructions","Return JSON","input","test","max_output_tokens",32,
            "text",Json.object("format",Json.object("schema",Json.object("type","object")))));
        assertEquals(200,transport.send("deepseek-flash","test-key",body,1000).status);
        assertEquals("Bearer test-key",c.getRequestProperty("Authorization"));assertFalse(c.output.toString("UTF-8").contains("test-key"));
        assertFalse(c.getInstanceFollowRedirects());assertEquals(1000,c.getReadTimeout());assertTrue(c.disconnected);
    }
}
