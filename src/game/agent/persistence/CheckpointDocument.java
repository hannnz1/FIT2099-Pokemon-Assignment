package game.agent.persistence;

import game.agent.llm.Json;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** A bounded JSON envelope, never Java serialization or executable content. */
final class CheckpointDocument {
    static final int MAX_BYTES=1024*1024;
    private CheckpointDocument() {}
    static String ownerKey(String owner) {
        if(owner==null || owner.isEmpty() || owner.length()>256) throw new StoreException(StoreException.Code.INVALID_OWNER);
        for(int i=0;i<owner.length();i++) {
            char c=owner.charAt(i);
            if(Character.isISOControl(c)) throw new StoreException(StoreException.Code.INVALID_OWNER);
            if(Character.isHighSurrogate(c)) {
                if(++i>=owner.length() || !Character.isLowSurrogate(owner.charAt(i))) throw new StoreException(StoreException.Code.INVALID_OWNER);
            } else if(Character.isLowSurrogate(c)) throw new StoreException(StoreException.Code.INVALID_OWNER);
        }
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(owner.getBytes(StandardCharsets.UTF_8));
            StringBuilder key=new StringBuilder(); for(byte b:hash) key.append(String.format(Locale.ROOT,"%02x",b&255));
            return key.toString();
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }
    static byte[] encode(String key,Map<String,Object> checkpoint) {
        if(checkpoint==null) throw new StoreException(StoreException.Code.INVALID_CHECKPOINT);
        try {
            validate(checkpoint,0,new int[]{0});
            String json=Json.write(Json.object("version",1,"ownerHash",key,"checkpoint",checkpoint));
            byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
            if(bytes.length>MAX_BYTES) throw new StoreException(StoreException.Code.TOO_LARGE);
            // Decode once to enforce the parser's node/number/Unicode limits before touching storage.
            decode(key,bytes); return bytes;
        } catch(StoreException e) { throw e; }
        catch(RuntimeException e) { throw new StoreException(StoreException.Code.INVALID_CHECKPOINT); }
    }
    static Map<String,Object> decode(String key,byte[] bytes) {
        if(bytes.length>MAX_BYTES) throw new StoreException(StoreException.Code.TOO_LARGE);
        try {
            String json=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            Map<String,Object> envelope=Json.asObject(Json.read(json));
            if(envelope.size()!=3 || !(envelope.get("version") instanceof Number)
                    || !"1".equals(String.valueOf(envelope.get("version"))) || !key.equals(envelope.get("ownerHash")))
                throw new IllegalArgumentException();
            Map<String,Object> checkpoint=Json.asObject(envelope.get("checkpoint"));
            validate(checkpoint,0,new int[]{0}); return checkpoint;
        } catch(Exception e) { throw new StoreException(StoreException.Code.CORRUPT); }
    }
    private static void validate(Object value,int depth,int[] nodes) {
        if(depth>24 || ++nodes[0]>8000) throw new StoreException(StoreException.Code.INVALID_CHECKPOINT);
        if(value instanceof Map) {
            if(((Map<?,?>)value).size()>8000) throw new StoreException(StoreException.Code.INVALID_CHECKPOINT);
            for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()) {
                if(!(entry.getKey() instanceof String)) throw new StoreException(StoreException.Code.INVALID_CHECKPOINT);
                String key=(String)entry.getKey();
                String normalized=key.toLowerCase(Locale.ROOT).replace("_","").replace("-","");
                if(normalized.equals("apikey") || normalized.equals("approvaltoken") || normalized.equals("csrftoken")
                        || normalized.equals("password") || normalized.equals("authorization") || normalized.equals("secret")
                        || normalized.equals("accesstoken") || normalized.equals("refreshtoken") || normalized.equals("privatekey"))
                    throw new StoreException(StoreException.Code.INVALID_CHECKPOINT);
                validate(key,depth+1,nodes); validate(entry.getValue(),depth+1,nodes);
            }
        } else if(value instanceof List) {
            if(((List<?>)value).size()>8000) throw new StoreException(StoreException.Code.INVALID_CHECKPOINT);
            for(Object child:(List<?>)value) validate(child,depth+1,nodes);
        } else if(value instanceof String) {
            String text=(String)value;
            if(text.length()>MAX_BYTES) throw new StoreException(StoreException.Code.TOO_LARGE);
            if(text.matches("(?is).*\\bBearer\\s+\\S+.*") || text.matches("(?s).*\\bsk-(?:proj-)?[A-Za-z0-9_-]{8,}.*")
                    || text.matches("(?s).*\\bAIza[A-Za-z0-9_-]{20,}.*")) throw new StoreException(StoreException.Code.INVALID_CHECKPOINT);
        } else if(value!=null && !(value instanceof Number) && !(value instanceof Boolean))
            throw new StoreException(StoreException.Code.INVALID_CHECKPOINT);
    }
}
