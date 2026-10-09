package spiderqueenhungrycraft.compat.iceandfire;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

/**
 * A bolt of lightning that is <b>nothing but a picture</b> (Necromancy IV prototype 2).
 *
 * <p>It is the same recipe the giant trident of this mod already uses ({@code brood.SpiderTrident#strike}): a real
 * {@link LightningBolt} of the game, switched to {@code setVisualOnly(true)}. That switch is not a guess - the game
 * itself guards every consequence of a bolt with it:
 *
 * <pre>
 * LightningBolt#tick():   if (this.life >= 0) { ... } else if (!this.visualOnly) {   // damage, ignition,
 *                                                                                    // villagers, creepers,
 *                                                                                    // piglins, copper, rods
 * LightningBolt#spawnFire(int): if (!this.visualOnly &amp;&amp; !level.isClientSide &amp;&amp; RULE_DOFIRETICK)
 * </pre>
 *
 * <p>What survives is exactly what a spell wants and nothing that a world has to clean up: the flash, the thunder, and
 * the sky going bright for a moment. No being is hurt, nothing burns, no villager turns into a witch and no creeper
 * becomes charged - so an ability of this mod can never harm the player who casts it or the world they built.
 *
 * <p>The one residue that is worth knowing about is that the game's own {@code LIGHTNING_STRIKE} advancement
 * criterion is triggered anyway, because it hangs off the flash rather than off the damage. The prototype accepts
 * that; a later round that minds can replace this with a purely client side effect.
 */
public final class VisualLightning
{
    /** How loud the thunder of a probe is, and of what kind: the same values the trident of this mod uses. */
    private static final float THUNDER_VOLUME = 4.0F;

    private static final float THUNDER_PITCH = 1.0F;

    private VisualLightning()
    {
    }

    /**
     * Strikes one visual-only bolt at that place.
     *
     * @return true when the bolt was really created and added to the level, false when the game refused to build one
     *         (which is reported rather than swallowed, so a probe never fails silently)
     */
    public static boolean strike(ServerLevel level, Vec3 where)
    {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);

        if (bolt == null)
        {
            return false;
        }

        bolt.moveTo(where.x, where.y, where.z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
        level.playSound(null, where.x, where.y, where.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER,
                THUNDER_VOLUME, THUNDER_PITCH);

        return true;
    }
}
