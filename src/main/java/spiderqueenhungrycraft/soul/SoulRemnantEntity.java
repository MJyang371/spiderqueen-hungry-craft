package spiderqueenhungrycraft.soul;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The soul remnant of a dead player (Soul Foundation V1): the souls one life left behind, waiting where that life
 * ended for its owner - or for a spider of that owner - to come back for them.
 *
 * <p><b>It is scenery, never a participant.</b> A remnant is a plain {@link Entity} on purpose: it has no brain, no
 * hitbox that matters, no gravity and no physics at all. It can not fall, can not be pushed by water or by anything
 * else, can not be hurt, killed or blown up, does not block a player, a creature or a projectile, and it never
 * despawns with age or distance. Every one of those promises is an override below rather than a hope.
 *
 * <p><b>It learns whether it is still real from {@link SoulRemnantData}, never from itself.</b> A remnant that was
 * replaced by a newer death of the same player (or by an administrator) finds its own token gone the moment it
 * ticks, and removes itself. That is what makes the death of a player whose old remnant sits in an unloaded chunk
 * cheap: nothing has to be loaded, the record is simply replaced, and the abandoned entity cleans itself up the
 * next time anybody walks by.
 */
public class SoulRemnantEntity extends Entity
{
    /** NBT keys of what a remnant carries. */
    private static final String TAG_OWNER = "RemnantOwner";
    private static final String TAG_TOKEN = "RemnantToken";
    private static final String TAG_SOULS = "RemnantSouls";

    /** How often the remnant looks around for somebody who may take it: four times a second is instant to a player. */
    private static final int SCAN_INTERVAL_TICKS = 5;

    /** How far outside its own box the owner has to come for the remnant to be picked up. */
    private static final double OWNER_CLAIM_MARGIN = 0.9D;

    /** A spider of the owner may stand a little further away, because a spider walks where its queen points it. */
    private static final double SPIDER_CLAIM_MARGIN = 1.6D;

    /** The player this remnant belongs to, written to the save file. */
    @Nullable
    private UUID owner;

    /** The identity of this very remnant, compared against {@link SoulRemnantData} on every tick. */
    @Nullable
    private UUID token;

    /** The souls this remnant holds. Server side truth; the client never needs the number. */
    private long storedSouls;

    /** Counts down to the next look around. */
    private int scanCooldown;

    public SoulRemnantEntity(EntityType<? extends SoulRemnantEntity> entityType, Level level)
    {
        super(entityType, level);

        // No physics and no gravity: the remnant floats exactly where it was left. It is not moved by the world,
        // so it also needs no collision response of its own.
        this.noPhysics = true;
        this.setNoGravity(true);

        // The first look around happens immediately, so a remnant that was loaded without a record is gone in its
        // very first tick instead of after a short grace period.
        this.scanCooldown = 0;
    }

    // ------------------------------------------------------------------ the physics of scenery

    /**
     * A remnant synchronises nothing: it looks the same whoever owns it, and how many souls are inside it is
     * server business that no client ever needs.
     */
    @Override
    protected void defineSynchedData()
    {
    }

    /** Never a target of a projectile, a ray or an interaction: it is not part of the world's collisions. */
    @Override
    public boolean isPickable()
    {
        return false;
    }

    /** Nothing pushes a remnant around: not a player, not a creature, not a flowing current. */
    @Override
    public boolean isPushable()
    {
        return false;
    }

    /**
     * Flowing water and lava leave it where it is.
     *
     * <p>Both questions the game asks are answered here: the plain one, which its fluid routine still asks as a first
     * look, and the fluid type aware one Forge added, which is the one that really decides. The plain one is
     * deprecated by Forge, hence the suppression - it is still called, so it is still overridden.
     */
    @SuppressWarnings("deprecation")
    @Override
    public boolean isPushedByFluid()
    {
        return false;
    }

    /** The fluid type aware half of the very same answer: no current of any kind moves a remnant. */
    @Override
    public boolean isPushedByFluid(FluidType fluidType)
    {
        return false;
    }

    /** It blocks nobody: a remnant is something a player walks through, not into. */
    @Override
    public boolean canBeCollidedWith()
    {
        return false;
    }

    /** And nobody attacks it - not a player, not a creature, not a fake player of another mod. */
    @Override
    public boolean isAttackable()
    {
        return false;
    }

    /** The attacker path of the game (a swing that would hit a boat or an item frame) is refused here too. */
    @Override
    public boolean skipAttackInteraction(Entity attacker)
    {
        return true;
    }

    /** No damage of any kind, from any source: a remnant can not be killed - only collected. */
    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        return false;
    }

    /** Even the damage types that normally ignore invulnerability find nothing to take here. */
    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        return true;
    }

    /** Fire and lava can not destroy what can not be destroyed. */
    @Override
    public boolean fireImmune()
    {
        return true;
    }

    /** A blast passes straight through it: the remnant is never pushed and never hurt by an explosion. */
    @Override
    public boolean ignoreExplosion()
    {
        return true;
    }

    /** It does not press a pressure plate or trip a tripwire of a queen who happens to build around it. */
    @Override
    public boolean isIgnoringBlockTriggers()
    {
        return true;
    }

    /**
     * A portal never takes a remnant anywhere.
     *
     * <p>An ordinary entity that stands in a nether portal is carried to the other side of it after a moment; a
     * remnant has to stay exactly where the life that left it ended, so it is not allowed to change dimensions at
     * all. This is the same answer an item frame gives, and it is what keeps a remnant of the overworld from quietly
     * turning up in the nether.
     */
    @Override
    public boolean canChangeDimensions()
    {
        return false;
    }

    // ------------------------------------------------------------------ what it carries

    /** Writes what this remnant belongs to and what it holds. */
    public void setRemnant(UUID owner, UUID token, long souls)
    {
        this.owner = owner;
        this.token = token;
        this.storedSouls = Math.max(0L, souls);
    }

    /** @return the player this remnant belongs to, or null when it was never assigned one. */
    @Nullable
    public UUID ownerUUID()
    {
        return this.owner;
    }

    /** @return the identity of this remnant, or null when it was never assigned one. */
    @Nullable
    public UUID remnantToken()
    {
        return this.token;
    }

    /** @return the souls this remnant holds. */
    public long storedSouls()
    {
        return this.storedSouls;
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        this.owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        this.token = tag.hasUUID(TAG_TOKEN) ? tag.getUUID(TAG_TOKEN) : null;
        this.storedSouls = tag.getLong(TAG_SOULS);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        if (this.owner != null)
        {
            tag.putUUID(TAG_OWNER, this.owner);
        }

        if (this.token != null)
        {
            tag.putUUID(TAG_TOKEN, this.token);
        }

        tag.putLong(TAG_SOULS, this.storedSouls);
    }

    /**
     * Builds one remnant of that player, ready to be added to the world.
     *
     * <p>The record in {@link SoulRemnantData} has to be written <b>before</b> the entity is added, because the very
     * first tick of a remnant already asks that record whether it exists.
     *
     * @return the remnant, or null when the game refused to build the entity of this mod.
     */
    @Nullable
    public static SoulRemnantEntity createAt(ServerLevel level, Vec3 pos, UUID owner, UUID token, long souls)
    {
        SoulRemnantEntity remnant = HungryCraftEntities.SOUL_REMNANT.get().create(level);

        if (remnant == null)
        {
            return null;
        }

        remnant.setRemnant(owner, token, souls);
        remnant.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);

        return remnant;
    }

    // ------------------------------------------------------------------ the life of a remnant

    @Override
    public void tick()
    {
        super.tick();

        // Only the server decides anything: a client just draws the crossed planes where the server put them.
        if (this.level().isClientSide)
        {
            return;
        }

        // A remnant that is no longer the record of its owner is a leftover of an older life (or of an
        // administrator's cleanup). It removes itself before it could ever pay anything out.
        if (!this.isStillTheRecordOfItsOwner())
        {
            this.discard();
            return;
        }

        if (this.scanCooldown > 0)
        {
            this.scanCooldown--;
            return;
        }

        this.scanCooldown = SCAN_INTERVAL_TICKS;
        this.scanForClaimants();
    }

    /** @return true when {@link SoulRemnantData} still names this very remnant as the active one of its owner. */
    public boolean isStillTheRecordOfItsOwner()
    {
        MinecraftServer server = this.level().getServer();

        if (server == null || this.owner == null || this.token == null)
        {
            return false;
        }

        SoulRemnantData.ActiveRemnant record = SoulRemnantData.get(server).activeFor(this.owner);

        return record != null && record.token().equals(this.token);
    }

    /** Looks for the owner and for spiders of the owner close enough to collect this remnant. */
    private void scanForClaimants()
    {
        AABB ownerBox = this.getBoundingBox().inflate(OWNER_CLAIM_MARGIN);

        for (ServerPlayer player : this.level().getEntitiesOfClass(ServerPlayer.class, ownerBox))
        {
            if (this.tryClaimByPlayer(player))
            {
                return;
            }
        }

        AABB spiderBox = this.getBoundingBox().inflate(SPIDER_CLAIM_MARGIN);

        for (SpiderMinionEntity spider : this.level().getEntitiesOfClass(SpiderMinionEntity.class, spiderBox))
        {
            if (this.tryClaimBySpider(spider))
            {
                return;
            }
        }
    }

    /**
     * The owner walks into their own remnant.
     *
     * @return true when the remnant was really collected by this call.
     */
    public boolean tryClaimByPlayer(ServerPlayer player)
    {
        if (this.owner == null || !this.owner.equals(player.getUUID()))
        {
            return false;
        }

        return this.tryClaim(player);
    }

    /**
     * A spider of the owner walks into the remnant of its queen and carries the souls home for her.
     *
     * <p>Only a spider that really belongs to that player may do it: the check is the ownership of the brood
     * itself (the owner UUID of {@link SpiderMinionEntity}), not a name and not "some spider". And the owner has
     * to be reachable and alive right now - see {@link #tryClaim(ServerPlayer)} - so a spider that walks past the
     * remnant of a player who has just died can never bring an old life back.
     *
     * @return true when the remnant was really collected by this call.
     */
    public boolean tryClaimBySpider(SpiderMinionEntity spider)
    {
        if (this.owner == null || !spider.isOwnedBy(this.owner))
        {
            return false;
        }

        ServerPlayer queen = this.resolveOwner();

        return queen != null && this.tryClaim(queen);
    }

    /**
     * @return the owner as an online player, or null while that player is not on this server at all.
     *
     *         <p>Protected and overridable so a regression test can answer for a fake player that is
     *         deliberately never put into the player list of the server.
     */
    @Nullable
    protected ServerPlayer resolveOwner()
    {
        MinecraftServer server = this.level().getServer();

        return server == null || this.owner == null ? null : server.getPlayerList().getPlayer(this.owner);
    }

    /**
     * The one transaction that pays a remnant out.
     *
     * <p>Three rules stand between a claim and a duplicated currency:
     * <ol>
     * <li>the record of the player still has to be this very remnant - {@link SoulRemnantData#removeIfMatches}
     * takes it away here, and a second claimant in the same tick is simply told that there was nothing to take,</li>
     * <li>the remnant is then removed from the world,</li>
     * <li>and only after that are the souls really added, so a payout happens exactly once.</li>
     * </ol>
     *
     * <p>The owner also has to be <b>alive</b>. A corpse, a player on the death screen and a player who has not
     * respawned yet can never collect: that is what stops the remnant of the very life that just ended from being
     * carried home by a spider that happens to stand next to it.
     *
     * @return true when this call is the one that collected the remnant.
     */
    public boolean tryClaim(ServerPlayer queen)
    {
        if (this.level().isClientSide || this.owner == null || this.token == null)
        {
            return false;
        }

        if (!this.owner.equals(queen.getUUID()) || !queen.isAlive())
        {
            return false;
        }

        MinecraftServer server = this.level().getServer();

        if (server == null || !SoulRemnantData.get(server).removeIfMatches(this.owner, this.token))
        {
            return false;
        }

        Souls.addSouls(queen, this.storedSouls);
        SoulClaimFx.play(this);
        this.discard();

        return true;
    }
}
