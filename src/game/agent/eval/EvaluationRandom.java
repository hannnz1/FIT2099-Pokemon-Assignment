package game.agent.eval;
import game.agent.llm.Json;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Java Random's 48-bit stream with an explicit cursor; no reflection or seed replay. */
public final class EvaluationRandom {
 private static final long MASK=(1L<<48)-1;
 private final long seed;private long state,calls,identities;
 public EvaluationRandom(long seed){this.seed=seed;state=(seed^0x5DEECE66DL)&MASK;}
 public int nextInt(){state=(state*0x5DEECE66DL+0xBL)&MASK;calls++;return(int)(state>>>16);}
 public String identity(){return UUID.nameUUIDFromBytes(("growth-eval-v1:"+seed+":"+identities++).getBytes(StandardCharsets.UTF_8)).toString();}
 public Map<String,Object> checkpoint(){return Json.object("seed",seed,"state",state,"calls",calls,"identities",identities);}
 public static EvaluationRandom restore(Map<String,Object> row){EvaluationRandom r=new EvaluationRandom(number(row.get("seed")));r.state=number(row.get("state"));r.calls=number(row.get("calls"));r.identities=number(row.get("identities"));if(r.state<0||r.state>MASK||r.calls<0||r.identities<0)throw new IllegalArgumentException("INVALID_RNG_CURSOR");return r;}
 static long number(Object value){try{return new java.math.BigDecimal(String.valueOf(value)).longValueExact();}catch(RuntimeException e){throw new IllegalArgumentException("INVALID_NUMBER");}}
}
