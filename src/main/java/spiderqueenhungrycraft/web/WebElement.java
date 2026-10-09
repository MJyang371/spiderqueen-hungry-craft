package spiderqueenhungrycraft.web;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.joml.Vector3f;
import spiderqueenhungrycraft.registry.HungryCraftBlocks;

/**
 * The elements of the web features (Phase 7A).
 *
 * <p>Everything that differs between a normal, a poison and a fire web lives here: the damage and the
 * capture rule of the shot, the block it leaves behind, the tint of that block, the particles of the shot
 * and the icon of the empowerment effect.
 *
 * <p>Adding an element later means adding one constant, one block and one empowerment effect. The
 * projectile, the network code and the harvest never have to be copied or touched.
 */
public enum WebElement
{
    /** The ordinary shot: no damage, capture allowed, temporary queen web, plain grey white look. */
    NORMAL("normal", 0.0F, true, 0xFFFFFF, Items.STRING, null, false),

    /** Poison shot: real damage, poison on the hit, permanent poison web, never captures, dark green look. */
    POISON("poison", 4.0F, false, 0x3B7D32, Items.SPIDER_EYE, rgb(0x3B7D32), false),

    /** Fire shot: real damage, burning on the hit, permanent fire web, never captures, orange glowing look. */
    FIRE("fire", 4.0F, false, 0xFF7A20, Items.BLAZE_POWDER, null, true);

    /** Scale of the dust particles of the poison element. */
    private static final float DUST_SCALE = 1.0F;

    private final String id;
    private final float directDamage;
    private final boolean captureAllowed;
    private final int tintColor;
    private final Item icon;
    private final Vector3f dustColor;
    private final boolean emissive;

    WebElement(String id, float directDamage, boolean captureAllowed, int tintColor, Item icon,
            Vector3f dustColor, boolean emissive)
    {
        this.id = id;
        this.directDamage = directDamage;
        this.captureAllowed = captureAllowed;
        this.tintColor = tintColor;
        this.icon = icon;
        this.dustColor = dustColor;
        this.emissive = emissive;
    }

    /** The stable id, saved in the projectile NBT and sent to the client. */
    public String id()
    {
        return this.id;
    }

    /** @return the element of that id, {@link #NORMAL} for everything unknown or missing. */
    public static WebElement byId(String id)
    {
        if (id != null && !id.isEmpty())
        {
            for (WebElement element : values())
            {
                if (element.id.equals(id))
                {
                    return element;
                }
            }
        }

        return NORMAL;
    }

    /** Real damage of the shot, 0 for the normal shot. */
    public float directDamage()
    {
        return this.directDamage;
    }

    /** @return true only for the normal shot, elemental shots never roll a capture. */
    public boolean captureAllowed()
    {
        return this.captureAllowed;
    }

    /** @return true for every element that is more than the plain web, currently poison and fire. */
    public boolean isElemental()
    {
        return this != NORMAL;
    }

    /** The block colour of the web of this element, the normal element stays white. */
    public int tintColor()
    {
        return this.tintColor;
    }

    /**
     * @return true when the shot and its web are drawn with a full bright light map. The fire element then
     *         looks hot even in the dark, without emitting any real block light: the queen is blinded by
     *         bright light herself, so her own web must never light up the world.
     */
    public boolean isEmissive()
    {
        return this.emissive;
    }

    /** The vanilla item whose icon represents the empowerment of this element, no new texture needed. */
    public Item icon()
    {
        return this.icon;
    }

    /** The web block a shot of this element leaves behind. */
    public Block webBlock()
    {
        return switch (this)
        {
            case NORMAL -> HungryCraftBlocks.QUEEN_WEB.get();
            case POISON -> HungryCraftBlocks.POISON_QUEEN_WEB.get();
            case FIRE -> HungryCraftBlocks.FIRE_QUEEN_WEB.get();
        };
    }

    /** The particle of the flying shot, of the impact burst and of the ambient web decoration. */
    public ParticleOptions particle()
    {
        return switch (this)
        {
            case NORMAL -> WebShotProjectile.cobwebParticles();
            case POISON -> new DustParticleOptions(this.dustColor, DUST_SCALE);
            case FIRE -> ParticleTypes.FLAME;
        };
    }

    private static Vector3f rgb(int color)
    {
        return new Vector3f(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F);
    }
}
