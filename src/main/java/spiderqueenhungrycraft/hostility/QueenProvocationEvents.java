package spiderqueenhungrycraft.hostility;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.web.WebShotProjectile;
import spiderqueenhungrycraft.web.WebVolleyTracker;

/**
 * Turns real attacks of the Spider Queen into provocation (Phase 6-SIMPLE-3, extended by the beast bite of
 * phase 8B2).
 *
 * <p>It listens to exactly one damage event, so a single sword hit, a single arrow or a single bite produces
 * exactly one pack alert per victim. Reflected and passive damage (thorns, fall, fire, anything without an
 * offensive damage type) never provokes, and no damage value is ever touched here.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenProvocationEvents
{
    /**
     * Damage types that count as an attack of the queen. The beast bite of this mod is in this tag, so a bite
     * wakes the pack up exactly like a melee swing does. A pack may add its own types here.
     */
    public static final TagKey<DamageType> BEAST_ATTACKS = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "beast_attacks"));

    private QueenProvocationEvents()
    {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event)
    {
        LivingEntity victim = event.getEntity();

        if (victim.level().isClientSide() || event.getAmount() <= 0.0F)
        {
            return;
        }

        DamageSource source = event.getSource();

        if (!isOffensive(source))
        {
            return;
        }

        ServerPlayer queen = queenAttacker(source);

        if (queen == null)
        {
            return;
        }

        // A web volley projectile names the salvo it belongs to, so a salvo alerts a pack only once per victim.
        // Everything else about the hit, above all the damage itself, is never deduplicated.
        int volleyId = source.getDirectEntity() instanceof WebShotProjectile shot
                ? shot.volleyId()
                : WebVolleyTracker.NO_VOLLEY;

        QueenProvocation.provoke(queen, victim,
                source.is(DamageTypeTags.IS_PROJECTILE) ? QueenProvocation.Cause.PROJECTILE
                        : QueenProvocation.Cause.MELEE_OR_DAMAGE,
                volleyId);
    }

    /** @return the queen behind that damage source, or null when the queen is not the attacker. */
    private static ServerPlayer queenAttacker(DamageSource source)
    {
        Entity attacker = source.getEntity();

        if (attacker instanceof ServerPlayer player && QueenDataCapability.isQueen(player))
        {
            return player;
        }

        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer owner && QueenDataCapability.isQueen(owner))
        {
            return owner;
        }

        return null;
    }

    /**
     * @return true only for a real offensive hit of the queen: her melee attack or one of her projectiles.
     *         Reflected damage such as thorns names the queen as the owner as well, but it is not an attack
     *         the queen made, so it must not wake up a whole pack.
     */
    private static boolean isOffensive(DamageSource source)
    {
        if (source.is(DamageTypeTags.IS_PROJECTILE))
        {
            return true;
        }

        // The beast bite of this mod (and everything else a pack puts into that tag).
        if (source.is(BEAST_ATTACKS))
        {
            return true;
        }

        String id = source.getMsgId();
        return "player_attack".equals(id) || "trident".equals(id);
    }
}
