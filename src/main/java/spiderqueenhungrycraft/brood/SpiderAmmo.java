package spiderqueenhungrycraft.brood;

/**
 * The ammunition of a spider that shoots (Special Spiders round I).
 *
 * <p>Only the skeleton spider of the brood has one so far, and it is deliberately a two position cycle instead of a
 * weapon: the spider always attacks with the same rhythm and the same reach, and the ammunition only decides what
 * comes out of it - a vanilla arrow, or the very web projectile the spider queen herself spits.
 *
 * <p>The mode travels to every client (synced entity data), because the inspect HUD names it and the player has to
 * see which one is loaded before pressing the key again.
 */
public enum SpiderAmmo
{
    /** A vanilla arrow, shot exactly like the arrow of a skeleton of the game. */
    ARROW("hud.spiderqueen_hungrycraft.ammo.arrow"),

    /**
     * The arrow of a stray spider: an ordinary arrow of the game with a chill on it, so the victim is slowed when it
     * lands (Stray and Husk Spider round). It is the same arrow entity with an effect added, exactly the way a tipped
     * arrow of the game is built.
     */
    SLOWNESS_ARROW("hud.spiderqueen_hungrycraft.ammo.slowness_arrow"),

    /** The ordinary web shot of the queen, with every web rule that projectile already carries. */
    WEB("hud.spiderqueen_hungrycraft.ammo.web");

    /** All values, in the order of the cycle. */
    public static final SpiderAmmo[] VALUES = values();

    /** The default mode of a freshly hatched skeleton spider is always the arrow. */
    public static final SpiderAmmo DEFAULT = ARROW;

    private final String translationKey;

    SpiderAmmo(String translationKey)
    {
        this.translationKey = translationKey;
    }

    /** @return the translation key of the name of this mode. */
    public String translationKey()
    {
        return this.translationKey;
    }

    /**
     * @return the mode after that one in the cycle of a species, or the first mode of the cycle when the given one does
     *         not belong to it at all (an old save, or a species that changed its ammunition)
     *
     * <p>The cycle belongs to the species and not to the enum: a skeleton spider goes arrow, web, arrow, while a stray
     * spider goes slowness arrow, web, slowness arrow. Both lists live where the spider that uses them lives.
     */
    public static SpiderAmmo next(SpiderAmmo current, SpiderAmmo[] modes)
    {
        for (int index = 0; index < modes.length; index++)
        {
            if (modes[index] == current)
            {
                return modes[(index + 1) % modes.length];
            }
        }

        return modes[0];
    }

    /** @return the mode of that id, the default one for an id that makes no sense (an old save, a broken packet). */
    public static SpiderAmmo byId(int id)
    {
        return id < 0 || id >= VALUES.length ? DEFAULT : VALUES[id];
    }

    /** @return the mode of that name as it is written into the save file, the default one when it is unknown. */
    public static SpiderAmmo byName(String name)
    {
        for (SpiderAmmo ammo : VALUES)
        {
            if (ammo.name().equals(name))
            {
                return ammo;
            }
        }

        return DEFAULT;
    }
}
