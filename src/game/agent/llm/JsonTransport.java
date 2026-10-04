package game.agent.llm;

import java.io.IOException;

/** Trusted provider HTTP boundary. Implementations never execute game actions. */
public interface JsonTransport {
    final class Response {
        public final int status;
        public final String body;
        public Response(int status,String body) { this.status=status; this.body=body; }
    }
    Response send(String model,String apiKey,String json,int timeoutMillis) throws IOException;
}
