package spiderqueenhungrycraft.compat.iceandfire;

import java.util.List;

/**
 * The answer of one probe action of the compatibility prototype (Necromancy IV prototype 1).
 *
 * <p>It exists so that the debug commands - and the GameTest of a workspace <b>without</b> Ice and Fire - can be told
 * what happened without a single Ice and Fire type ever crossing that boundary: a boolean and lines of plain text,
 * nothing else. The facade {@link IceAndFireSupport} returns it, the bridge behind it builds it, and the command layer
 * prints it and writes it into the log under one common prefix.
 *
 * @param ok    true when the action really happened (a dragon was spawned, a corpse was woken, ...)
 * @param lines what to show the operator, in the order it should be read
 */
public record ProbeReport(boolean ok, List<String> lines)
{
    public ProbeReport
    {
        lines = List.copyOf(lines);
    }

    /** @return the report of an action that happened. */
    public static ProbeReport success(List<String> lines)
    {
        return new ProbeReport(true, lines);
    }

    /** @return the report of an action that was refused or failed. */
    public static ProbeReport failure(List<String> lines)
    {
        return new ProbeReport(false, lines);
    }

    /** @return the report of a single line that went wrong, with no Ice and Fire anywhere near it. */
    public static ProbeReport failure(String line)
    {
        return new ProbeReport(false, List.of(line));
    }

    /** @return the report of a probe that was never sent, because the other mod is not installed. */
    public static ProbeReport unavailable()
    {
        return failure(IceAndFireSupport.NOT_INSTALLED);
    }
}
