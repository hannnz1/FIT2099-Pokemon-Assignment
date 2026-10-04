package game.agent.llm;

import java.io.IOException;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.util.*;

/** DeepSeek chat wire adapter. Reuses the trusted Responses decoder and HTTP bounds.
 * Each decision is stateless: authoritative observation and last result replace chat history.
 * JSON mode does not enforce a schema upstream; existing Java codecs validate every result.
 */
public final class DeepSeekTransport implements JsonTransport {
    public static final String ENDPOINT="https://api.deepseek.com/chat/completions";
    public static JsonTransport officialHttpTransport() {
        return new OpenAiHttpTransport(ignored->(HttpsURLConnection)new URL(ENDPOINT).openConnection());
    }
    private final JsonTransport wire;
    public DeepSeekTransport() {
        this(url->(HttpsURLConnection)url.openConnection());
    }
    DeepSeekTransport(OpenAiHttpTransport.ConnectionFactory factory) {
        this(new OpenAiHttpTransport(ignored->factory.open(new URL(ENDPOINT))));
    }
    public DeepSeekTransport(JsonTransport wire){this.wire=Objects.requireNonNull(wire);}
    @Override public Response send(String model,String key,String json,int timeout) throws IOException {
        Map<String,Object> source=Json.asObject(Json.read(json));
        String instruction=(String)source.get("instructions");
        Map<String,Object> request=Json.object("model",model,"thinking",Json.object("type","disabled"),
            "stream",false,"max_tokens",source.get("max_output_tokens"));
        if(source.containsKey("tools")) {
            List<Object> tools=new ArrayList<>();
            for(Object value:Json.asArray(source.get("tools"))) {
                Map<String,Object> tool=Json.asObject(value);
                tools.add(Json.object("type","function","function",Json.object("name",tool.get("name"),
                    "description",tool.get("description"),"parameters",tool.get("parameters"))));
            }
            request.put("tools",tools);request.put("tool_choice","required");request.put("parallel_tool_calls",false);
        } else {
            Map<String,Object> format=Json.asObject(Json.asObject(source.get("text")).get("format"));
            instruction+=" Return only a JSON object matching this schema: "+Json.write(format.get("schema"));
            request.put("response_format",Json.object("type","json_object"));
        }
        request.put("messages",Arrays.asList(Json.object("role","system","content",instruction),
            Json.object("role","user","content",source.get("input"))));
        Response response=wire.send(model,key,Json.write(request),timeout);
        if(response.status==402)return new Response(429,"{\"error\":{\"code\":\"insufficient_quota\"}}");
        if(response.status!=200)return response;
        Map<String,Object> data=Json.asObject(Json.read(response.body));
        List<Object> choices=Json.asArray(data.get("choices"));
        if(choices.size()!=1)throw new IllegalArgumentException("INVALID_CHOICES");
        Map<String,Object> choice=Json.asObject(choices.get(0));
        Map<String,Object> message=Json.asObject(choice.get("message"));
        if(!(choice.get("finish_reason") instanceof String))throw new IllegalArgumentException("INVALID_FINISH");
        String finish=(String)choice.get("finish_reason");
        List<Object> output=new ArrayList<>();
        if("content_filter".equals(finish) || message.get("refusal") instanceof String) {
            output.add(Json.object("type","message","content",Collections.singletonList(Json.object("type","refusal"))));
        } else if("tool_calls".equals(finish)) {
            for(Object value:Json.asArray(message.get("tool_calls"))) {
                Map<String,Object> call=Json.asObject(value);
                if(!"function".equals(call.get("type")))throw new IllegalArgumentException("INVALID_TOOL_TYPE");
                Map<String,Object> function=Json.asObject(call.get("function"));
                output.add(Json.object("type","function_call","status","completed","name",function.get("name"),"arguments",function.get("arguments")));
            }
        } else if("stop".equals(finish)) {
            if(message.get("tool_calls")!=null && !Json.asArray(message.get("tool_calls")).isEmpty())throw new IllegalArgumentException("INVALID_FINISH");
            output.add(Json.object("type","message","content",Collections.singletonList(Json.object("type","output_text","text",message.get("content")))));
        } else throw new IllegalArgumentException("INCOMPLETE_RESPONSE");
        Map<String,Object> usage=new LinkedHashMap<>();
        if(data.get("usage") instanceof Map) {
            Map<String,Object> raw=Json.asObject(data.get("usage"));
            usage.put("input_tokens",raw.get("prompt_tokens"));usage.put("output_tokens",raw.get("completion_tokens"));
            usage.put("total_tokens",raw.get("total_tokens"));
            Object cached=raw.get("prompt_cache_hit_tokens");
            if(cached==null && raw.get("prompt_tokens_details") instanceof Map)cached=Json.asObject(raw.get("prompt_tokens_details")).get("cached_tokens");
            usage.put("input_tokens_details",Json.object("cached_tokens",cached));
        }
        return new Response(200,Json.write(Json.object("status","completed","output",output,"usage",usage)));
    }
}
