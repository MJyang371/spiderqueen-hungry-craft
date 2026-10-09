package spiderqueenhungrycraft.soul;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/**
 * The world rule of the soul system (Soul Foundation V1): whether a death really costs the souls of a player.
 *
 * <p>{@code /gamerule keepSpiderQueenSouls} is a real game rule of the game and not a config file of this mod, so
 * every player can read it with the command they already know, and a world carries it in its own level data. It is
 * deliberately <b>independent of {@code keepInventory}</b>: keeping the items of a corpse never decides what happens
 * to its souls, and the two rules are read in two different places.
 *
 * <p><b>Registration time matters.</b> The game rule types of the game are collected in a static map, and every
 * {@code GameRules} instance that a level was built with keeps the rules that existed at that moment. A rule that is
 * registered after the first level was created would exist in the command but not in the world, so
 * {@link #register()} is called from the constructor of the mod and from nowhere else - before any level is built.
 */
public final class SoulRules
{
    /** The name the rule has in the world and in {@code /gamerule}. */
    public static final String KEEP_SOULS_ID = "keepSpiderQueenSouls";

    /** The rule itself, created once by {@link #register()}. */
    @Nullable
    private static GameRules.Key<GameRules.BooleanValue> keepSouls;

    private SoulRules()
    {
    }

    /**
     * Registers the rule, exactly once, from the constructor of the mod.
     *
     * <p>The default is {@code false}: by default a death does cost the souls of a player, which is the Dark Souls
     * rule this system is built on. An administrator turns it off with {@code /gamerule keepSpiderQueenSouls true}.
     */
    public static void register()
    {
        if (keepSouls == null)
        {
            keepSouls = GameRules.register(KEEP_SOULS_ID, GameRules.Category.PLAYER,
                    GameRules.BooleanValue.create(false));
        }
    }

    /** @return the key of the rule, or null in the impossible case that it was never registered. */
    @Nullable
    public static GameRules.Key<GameRules.BooleanValue> keepSoulsRule()
    {
        return keepSouls;
    }

    /** @return true when this world spares the souls of a dead player. */
    public static boolean keepsSouls(@Nullable MinecraftServer server)
    {
        GameRules.Key<GameRules.BooleanValue> key = keepSouls;

        return server != null && key != null && server.getGameRules().getBoolean(key);
    }
}
