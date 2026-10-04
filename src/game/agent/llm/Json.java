package game.agent.llm;

import java.math.*;
import java.util.*;

/** Small strict JSON wire codec for bounded Gemini payloads, with exact decimals.
 * Rejects duplicate keys, invalid Unicode, non-JSON numbers and excessive nesting.
 * Does not deserialize Java objects or execute code. No external dependency needed.
 */
public final class Json {
    public static final int MAX_LENGTH=1024*1024;
    private Json() { }
    public static Map<String,Object> object(Object... pairs) {
        if(pairs.length%2!=0) throw invalid();
        Map<String,Object> map=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2) {
            if(!(pairs[i] instanceof String) || map.containsKey(pairs[i])) throw invalid();
            map.put((String)pairs[i],pairs[i+1]);
        }
        return map;
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> asObject(Object value) {
        if(!(value instanceof Map)) throw invalid(); return (Map<String,Object>)value;
    }
    @SuppressWarnings("unchecked") public static List<Object> asArray(Object value) {
        if(!(value instanceof List)) throw invalid(); return (List<Object>)value;
    }
    public static String write(Object value) {
        StringBuilder out=new StringBuilder(); append(value,out,0); return out.toString();
    }
    private static void append(Object value,StringBuilder out,int depth) {
        if(depth>32 || out.length()>MAX_LENGTH) throw invalid();
        if(value==null) out.append("null");
        else if(value instanceof String) {
            String text=(String)value; unicode(text); out.append('"');
            for(int i=0;i<text.length();i++) {
                char c=text.charAt(i);
                if(c=='"' || c=='\\') out.append('\\').append(c);
                else if(c<32) { out.append("\\u"); String hex=Integer.toHexString(c); for(int n=hex.length();n<4;n++) out.append('0'); out.append(hex); }
                else out.append(c);
            }
            out.append('"');
        } else if(value instanceof Boolean) out.append(value);
        else if(value instanceof Number) {
            if(!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                    || value instanceof Float || value instanceof Double || value instanceof BigDecimal || value instanceof BigInteger)) throw invalid();
            try { out.append(new BigDecimal(value.toString()).toString()); } catch(NumberFormatException e) { throw invalid(); }
        } else if(value instanceof Map) {
            out.append('{'); boolean first=true;
            for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()) {
                if(!(entry.getKey() instanceof String)) throw invalid(); if(!first) out.append(','); first=false;
                append(entry.getKey(),out,depth+1); out.append(':'); append(entry.getValue(),out,depth+1);
            }
            out.append('}');
        } else if(value instanceof Collection) {
            out.append('['); boolean first=true;
            for(Object child:(Collection<?>)value) { if(!first) out.append(','); first=false; append(child,out,depth+1); }
            out.append(']');
        } else throw invalid();
        if(out.length()>MAX_LENGTH) throw invalid();
    }
    public static Object read(String input) {
        if(input==null || input.length()>MAX_LENGTH) throw invalid();
        Parser parser=new Parser(input); Object value=parser.value(0); parser.space();
        if(parser.index!=input.length()) throw invalid(); return value;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("INVALID_JSON"); }
    private static void unicode(String value) {
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c)) {
                if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid();
            } else if(Character.isLowSurrogate(c)) throw invalid();
        }
    }
    private static final class Parser {
        final String text; int index,tokens;
        Parser(String text) { this.text=text; }
        void space() { while(index<text.length() && " \r\n\t".indexOf(text.charAt(index))>=0) index++; }
        boolean eat(char c) { space(); if(index<text.length() && text.charAt(index)==c) { index++; return true; } return false; }
        Object value(int depth) {
            if(depth>32 || ++tokens>10000) throw invalid(); space(); if(index>=text.length()) throw invalid();
            char c=text.charAt(index);
            if(c=='"') return string();
            if(c=='{') {
                index++; Map<String,Object> result=new LinkedHashMap<>(); if(eat('}')) return result;
                do { space(); String key=string(); if(result.containsKey(key) || !eat(':')) throw invalid(); result.put(key,value(depth+1)); } while(eat(','));
                if(!eat('}')) throw invalid(); return result;
            }
            if(c=='[') {
                index++; List<Object> result=new ArrayList<>(); if(eat(']')) return result;
                do { result.add(value(depth+1)); } while(eat(',')); if(!eat(']')) throw invalid(); return result;
            }
            for(String literal:Arrays.asList("true","false","null")) if(text.startsWith(literal,index)) {
                index+=literal.length(); return literal.equals("null")?null:Boolean.valueOf(literal);
            }
            int start=index; if(c=='-') index++;
            if(index>=text.length()) throw invalid();
            if(text.charAt(index)=='0') index++; else digits(true);
            if(index<text.length() && text.charAt(index)=='.') { index++; digits(true); }
            if(index<text.length() && (text.charAt(index)=='e' || text.charAt(index)=='E')) {
                index++; if(index<text.length() && (text.charAt(index)=='+' || text.charAt(index)=='-')) index++; digits(true);
            }
            if(index-start>128) throw invalid();
            try { BigDecimal number=new BigDecimal(text.substring(start,index)); if(Math.abs((long)number.scale())>10000) throw invalid(); return number; }
            catch(NumberFormatException e) { throw invalid(); }
        }
        void digits(boolean required) {
            int start=index; while(index<text.length() && text.charAt(index)>='0' && text.charAt(index)<='9') index++;
            if(required && index==start) throw invalid();
        }
        String string() {
            if(index>=text.length() || text.charAt(index++)!='"') throw invalid(); StringBuilder out=new StringBuilder();
            while(index<text.length()) {
                char c=text.charAt(index++);
                if(c=='"') { String result=out.toString(); unicode(result); return result; }
                if(c<32) throw invalid();
                if(c!='\\') { out.append(c); continue; }
                if(index>=text.length()) throw invalid(); char escaped=text.charAt(index++);
                if(escaped=='"' || escaped=='\\' || escaped=='/') out.append(escaped);
                else if(escaped=='b') out.append('\b'); else if(escaped=='f') out.append('\f');
                else if(escaped=='n') out.append('\n'); else if(escaped=='r') out.append('\r'); else if(escaped=='t') out.append('\t');
                else if(escaped=='u') {
                    if(index+4>text.length()) throw invalid(); int code=0;
                    for(int n=0;n<4;n++) {
                        char hex=text.charAt(index++);
                        if(!(hex>='0' && hex<='9' || hex>='a' && hex<='f' || hex>='A' && hex<='F')) throw invalid();
                        int digit=Character.digit(hex,16); code=code*16+digit;
                    }
                    out.append((char)code);
                } else throw invalid();
            }
            throw invalid();
        }
    }
}
