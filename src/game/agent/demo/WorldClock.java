package game.agent.demo;

import game.time.TimePeriod;

/** Per-world action clock: browser sessions never advance the legacy singleton. */
public final class WorldClock {
    private final long dayTurns;
    private long turn;
    public WorldClock(long dayTurns){if(dayTurns<1)throw new IllegalArgumentException("INVALID_DAY_LENGTH");this.dayTurns=dayTurns;}
    public void synchronize(long turn){if(turn<0)throw new IllegalArgumentException("INVALID_TURN");this.turn=turn;}
    public String period(){return (turn/dayTurns)%2==0?TimePeriod.DAY.name():TimePeriod.NIGHT.name();}
    public long nightStarts(){return dayTurns;}
}
