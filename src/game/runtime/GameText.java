package game.runtime;

/** Chinese display text only; internal identifiers and rules stay unchanged. */
public final class GameText {
    private GameText() {}
    public static String chinese(String text) {
        String[][] names={
            {"Torchic in a Pokeball","装有火稚鸡的精灵球"},
            {"Professor Oak","大木博士"},{"Proffesor Oak","大木博士"},{"Shopkeeper","商人"},
            {"Treecko","木守宫"},{"Mudkip","水跃鱼"},{"Torchic","火稚鸡"},
            {"GreatBall","高级球"},{"MasterBall","大师球"},{"Pokeball","精灵球"},{"Candy","糖果"},
            {"Blade Cutter","叶刃"},{"Water Blast","水流冲击"},{"Ember","火花"},
            {"North-West","西北"},{"North-East","东北"},{"South-West","西南"},{"South-East","东南"},
            {"North","北"},{"South","南"},{"East","东"},{"West","西"},{"around","附近"},
            {"Ash","训练家"},{"Player","训练家"},{"Ap:","攻击力："},{"HP","生命值"},{"AP","好感度"}
        };
        for(String[] pair:names)text=text.replace(pair[0],pair[1]);
        return text;
    }
}
