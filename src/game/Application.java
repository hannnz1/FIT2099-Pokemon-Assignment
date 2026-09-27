package game;
import game.runtime.*;
import java.io.*;
import java.util.*;
/** Console adapter uses exactly the same authoritative stepper as the web. */
public class Application {
    public static void main(String[] args) throws Exception {
        GameSession s=DemoMap.create();BufferedReader input=new BufferedReader(new InputStreamReader(System.in));
        while(!s.ended()) {
            s.map().draw(new edu.monash.fit2099.engine.displays.Display());List<ActionCatalog.Entry> actions=ActionCatalog.entries(s);
            System.out.println("Turn "+s.turn()+" / "+s.period());for(int i=0;i<actions.size();i++)System.out.println(i+": "+actions.get(i).dto.label);
            String value=input.readLine();if(value==null||value.equals("q"))break;
            try{int index=Integer.parseInt(value);if(index>=0&&index<actions.size()){s.execute(actions.get(index).action);System.out.println(s.logs().get(s.logs().size()-1).text);}}catch(NumberFormatException ignored){System.out.println("Enter an action number, or q.");}
        }
    }
}
