package game.agent.llm;

import java.util.*;

/** Gemini structured interpretation; player confirmation is required before start. */
public final class NaturalTaskParser implements TaskInterpreter {
    private final GeminiGateway gateway;
    public NaturalTaskParser(GeminiGateway gateway) { this.gateway=Objects.requireNonNull(gateway); }
    @Override public TaskIntent parse(String text,String questId) {
        Map<String,Object> request=gateway.base(TaskIntentCodec.INSTRUCTION,TaskIntentCodec.input(text,questId));
        Map<String,Object> generation=Json.asObject(request.get("generationConfig"));
        generation.put("responseMimeType","application/json"); generation.put("responseJsonSchema",TaskIntentCodec.schema(questId));
        try {
            StringBuilder output=new StringBuilder();
            for(Object part:GeminiGateway.parts(gateway.exchange(request))) {
                Map<String,Object> value=Json.asObject(part);
                if(value.containsKey("functionCall")) throw GeminiGateway.invalid();
                if(!Boolean.TRUE.equals(value.get("thought")) && value.get("text") instanceof String) output.append(value.get("text"));
            }
            return TaskIntentCodec.decode(output.toString(),questId);
        } catch(IllegalArgumentException error) { throw GeminiGateway.invalid(); }
    }
}
