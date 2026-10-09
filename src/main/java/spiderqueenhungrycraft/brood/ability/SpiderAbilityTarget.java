package spiderqueenhungrycraft.brood.ability;

/**
 * What an ability needs from the world before it can be cast (Brood Ability framework).
 *
 * <p>Most of the spiders of the brood need nothing at all - a zombie spider goes berserk wherever it happens to stand.
 * The two middle values are reserved for the special spiders of later rounds (a creeper spider that needs a place to
 * charge at, an ender spider that needs somebody to appear next to), so the framework is already honest about its
 * contract: an ability says what it needs, and the cast pipeline only reports what the command ray of the sceptre
 * really saw.
 */
public enum SpiderAbilityTarget
{
    /** No target at all: the ability works on the spider itself, wherever it is. */
    NONE("none"),

    /** A place in the world is needed, the block the sceptre pointed at. */
    POSITION("position"),

    /** A creature is needed, the one the sceptre pointed at. */
    ENTITY("entity"),

    /** Either a creature or a place will do. */
    ENTITY_OR_POSITION("entity_or_position");

    private final String id;

    SpiderAbilityTarget(String id)
    {
        this.id = id;
    }

    public String id()
    {
        return this.id;
    }

    /** @return true when the context of a cast really offers what this requirement asks for. */
    public boolean isSatisfiedBy(SpiderAbilityContext context)
    {
        return switch (this)
        {
            case NONE -> true;
            case POSITION -> context.hasPosition();
            case ENTITY -> context.hasEntity();
            case ENTITY_OR_POSITION -> context.hasEntity() || context.hasPosition();
        };
    }
}
