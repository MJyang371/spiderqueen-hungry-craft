package spiderqueenhungrycraft.brood;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import spiderqueenhungrycraft.brood.ability.SpiderAbility;
import spiderqueenhungrycraft.brood.ability.SpiderAbilityContext;
import spiderqueenhungrycraft.brood.goal.SpiderFactionTargetGoal;
import spiderqueenhungrycraft.brood.goal.SpiderFollowOwnerGoal;
import spiderqueenhungrycraft.brood.goal.SpiderForcedTargetGoal;
import spiderqueenhungrycraft.brood.goal.SpiderHoldAnchorGoal;
import spiderqueenhungrycraft.brood.goal.SpiderMoveToGoal;
import spiderqueenhungrycraft.brood.goal.SpiderOwnerAssistGoal;
import spiderqueenhungrycraft.brood.goal.SpiderPatrolGoal;
import spiderqueenhungrycraft.brood.goal.SpiderSelfDefenseGoal;
import spiderqueenhungrycraft.faction.QueenOwnedEntity;

/**
 * The shared base of every spider of the brood (this round's breeding pass): the small imp spider and the
 * ordinary brood spider.
 *
 * <p>It is a real vanilla {@link Spider}, so walking, leaping, the wall climbing of the game and the sound
 * set all come from the game itself and nothing here reimplements them. What this class adds is:
 * <ul>
 * <li>an owner, remembered across a save and a reload (and the {@link QueenOwnedEntity} view of it),</li>
 * <li>a long term order (follow, hold, patrol, free attack) with its anchor, saved with the world,</li>
 * <li>the brood command AI: natural faction targeting, help for the queen, self defence and the direct order of the
 * queen sceptre,</li>
 * <li>the collection of the spider into a carrier item by an empty hand,</li>
 * <li>a minion that does not vanish: no despawn with distance, none in peaceful, and no bed blocked by a
 * pet spider.</li>
 * </ul>
 *
 * <p><b>Orders and fights are two different things.</b> The order says where the spider belongs; a fight is a
 * temporary target on top of it. Nothing in this class ever changes the order because of a fight, so a patrolling
 * spider goes back to its anchor the moment its target is gone - see {@link SpiderOrderMode} and
 * {@link #getForcedTarget()}.
 */
public class SpiderMinionEntity extends Spider implements QueenOwnedEntity
{
    /** Vanilla spider walking speed, so every minion moves exactly like a spider of the game. */
    public static final double MOVEMENT_SPEED = 0.3D;

    /** NBT key of the owner of this spider, written as a real UUID. */
    private static final String KEY_OWNER = "MinionOwner";

    /** NBT keys of the long term order and its anchor (Brood Command AI). */
    /** The roaming area of a patrolling spider, around its anchor. */
    public static final double PATROL_RADIUS = 8.0D;

    /**
     * How far a queen may walk away from a spider that is on a scout move before that move is dropped and the spider
     * returns to being an ordinary follower.
     *
     * <p>A normal follower is teleported back at twelve blocks, which would make a sixty block scout order useless, so
     * a temporary move has its own, much longer leash instead.
     */
    public static final double TEMP_MOVE_OWNER_LEASH = 96.0D;

    private static final String KEY_ORDER = "SpiderOrder";
    private static final String KEY_ANCHOR = "SpiderAnchor";
    private static final String KEY_ANCHOR_DIMENSION = "SpiderAnchorDimension";

    /** The rest of the brood ability of this spider, in ticks (Brood Ability framework). */
    private static final String KEY_ABILITY_COOLDOWN = "SpiderAbilityCooldown";

    /** A follower that just finished a fight may walk home this long before the emergency teleport may take it. */
    public static final int COMBAT_RETURN_GRACE_TICKS = 60;

    /** How often the follow roster of the queen is refreshed by one spider: once a second is plenty. */
    private static final int ROSTER_UPDATE_TICKS = 20;

    /** Server side only, the client never needs the owner in this round. */
    @Nullable
    private UUID owner;

    /** The long term order of this spider. Every spider a player creates starts as a follower. */
    private SpiderOrderMode orderMode = SpiderOrderMode.FOLLOW;

    /** Where a {@link SpiderOrderMode#HOLD} or {@link SpiderOrderMode#PATROL} spider belongs. */
    @Nullable
    private BlockPos orderAnchor;

    /** The dimension of the anchor, so the same xyz in another world is never mistaken for it. */
    @Nullable
    private ResourceKey<Level> orderAnchorDimension;

    /**
     * The direct order of the queen sceptre (Brood Command AI): the one target this spider has to deal with right now,
     * whatever else it would rather do.
     *
     * <p>Runtime only on purpose. A fight is not a standing order, so it is deliberately not written to the save file:
     * after a world reload a spider is simply back to its follow, hold or patrol order.
     */
    @Nullable
    private LivingEntity forcedTarget;

    /**
     * The temporary command of this spider (Queen Scepter command patch): nothing, a scout move or a forced attack.
     * Always at most one, always runtime only, and never a replacement for the order above.
     */
    private SpiderTemporaryCommand temporaryCommand = SpiderTemporaryCommand.NONE;

    /** Where a {@link SpiderTemporaryCommand#MOVE_TO} spider walks, and which command sent it there. */
    @Nullable
    private BlockPos moveAnchor;
    private int moveCommandId;

    /** True once the arrival of the current move command was reported, so the marker of the queen clears exactly once. */
    private boolean moveArrivalReported;

    /**
     * How many ticks the ability of this spider still rests (Brood Ability framework). Server side only, written to the
     * save file so a restarted world does not hand a free cast to a spider that just used its ability, and carried
     * through a spider carrier for the same reason.
     */
    private int abilityCooldownTicks;

    /**
     * The same rest, expressed as the game time at which it ends, synchronised to every client that can see this
     * spider (Brood Selection + Inspect HUD round).
     *
     * <p>Only this value travels over the network, and only when it really changes - once when an ability is cast and
     * once when the rest is over. A HUD then counts the rest down itself from the game time it already has, so looking
     * at a spider never costs a packet. The countdown above stays the authority of the server.
     */
    private static final EntityDataAccessor<Integer> DATA_ABILITY_COOLDOWN_END = SynchedEntityData
            .defineId(SpiderMinionEntity.class, EntityDataSerializers.INT);

    /**
     * The veterancy of this spider (Brood Veterancy round): the health it was born with, the kills it has to its name
     * and the rank those kills are worth.
     *
     * <p>It is a record of its own, kept apart from the ability data, the faction data and the order data of this
     * spider: nothing else in the class reads it except the few methods below that translate it into attributes, into
     * the HUD and into the save file.
     */
    private final SpiderVeterancyData veterancy = new SpiderVeterancyData();

    /**
     * The rank of this spider, synchronised to every client that can see it.
     *
     * <p>Only the rank travels. The kills behind it are the business of the server alone - a HUD never shows them - so
     * nothing about experience is ever sent, not once per kill and certainly not once per tick.
     */
    private static final EntityDataAccessor<Integer> DATA_RANK = SynchedEntityData
            .defineId(SpiderMinionEntity.class, EntityDataSerializers.INT);

    /**
     * The identities of the three attribute modifiers a rank applies.
     *
     * <p>They are fixed, and that is the entire trick against a bonus that stacks with itself: a modifier of the game
     * carries its identity, so applying the rank again replaces the old modifier instead of adding a second one. It
     * also means the modifiers can be transient - the values live in the rank, never in the save file.
     */
    private static final UUID RANK_HEALTH_ID = UUID.fromString("6d1d4f0a-3b64-4a37-9d58-9d5f3c2f0a11");
    private static final UUID RANK_DAMAGE_ID = UUID.fromString("6d1d4f0a-3b64-4a37-9d58-9d5f3c2f0a12");
    private static final UUID RANK_SPEED_ID = UUID.fromString("6d1d4f0a-3b64-4a37-9d58-9d5f3c2f0a13");

    /**
     * The owner, the order and the errand of this spider, synchronised to every client that can see it.
     *
     * <p>They live in the save file, which a client never has. Without these three values a client can not tell its own
     * spiders from anybody else's, so the inspect HUD and the view selection of the queen read them from here.
     */
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData
            .defineId(SpiderMinionEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final EntityDataAccessor<Integer> DATA_ORDER = SynchedEntityData
            .defineId(SpiderMinionEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> DATA_TASK = SynchedEntityData
            .defineId(SpiderMinionEntity.class, EntityDataSerializers.INT);

    /**
     * How long a follower that just finished a fight is allowed to walk home before the emergency teleport may grab it
     * (Follow teleport optimisation). It is the difference between "my spider walked back" and "my spider blinked out of
     * the fight it just won".
     */
    private int combatReturnGraceTicks;

    /**
     * The ticks this spider is spared by explosions (Spider Mine and Egg Arrow round).
     *
     * <p>It is set by exactly one thing in the whole mod - the blast of a spider mine, which puts its eight spiders into
     * the world <b>before</b> it fires and has to be sure they survive their own delivery. It is therefore a property of
     * an individual spider and never of a species: an imp spider that hatched from an egg, or one that was summoned, has
     * this at zero and is hurt by a blast exactly like everything else.
     *
     * <p>Runtime only and deliberately short: nothing about it belongs in a save file, because a mine hands it out one
     * moment before the explosion it was granted for.
     */
    private int explosionGraceTicks;

    public SpiderMinionEntity(EntityType<? extends SpiderMinionEntity> entityType, Level level)
    {
        super(entityType, level);
    }

    /**
     * The wandering goals of the game are kept and the random stroll is replaced by the patrol goal, because only that
     * one knows about the order of the spider. Every target goal of the vanilla spider is dropped and replaced by the
     * brood command goals of this mod, which all pass the owner protection of
     * {@link spiderqueenhungrycraft.faction.QueenOwnerProtection} first.
     *
     * <p>The priority of the target goals is the priority of the design: the order of the queen sceptre, then the
     * queen in danger, then what the queen attacked, then the spider itself, and only then nature.
     */
    @Override
    protected void registerGoals()
    {
        super.registerGoals();

        this.goalSelector.removeAllGoals(goal -> goal instanceof RandomStrollGoal);
        this.targetSelector.removeAllGoals(goal -> true);

        this.goalSelector.addGoal(4, new SpiderMoveToGoal(this));
        this.goalSelector.addGoal(5, new SpiderFollowOwnerGoal(this));
        this.goalSelector.addGoal(6, new SpiderHoldAnchorGoal(this));
        this.goalSelector.addGoal(7, new SpiderPatrolGoal(this));

        this.targetSelector.addGoal(0, new SpiderForcedTargetGoal(this));
        this.targetSelector.addGoal(1, new SpiderOwnerAssistGoal(this, SpiderOwnerAssistGoal.Assist.HURT_BY_OWNER));
        this.targetSelector.addGoal(2, new SpiderOwnerAssistGoal(this, SpiderOwnerAssistGoal.Assist.HURT_TARGET));
        this.targetSelector.addGoal(3, new SpiderSelfDefenseGoal(this));
        this.targetSelector.addGoal(4, new SpiderFactionTargetGoal(this));
    }

    /**
     * No spider of the brood is ever a mount (spider jockey fix).
     *
     * <p>The spider of the game rolls itself a rider when its spawn is finalised: one spawn in a hundred comes with a
     * skeleton on its back, and that roll is not limited to any particular kind of spawn - a spawn egg, a summon
     * command and a natural spawn all take it. Every spider of this mod inherits the roll, because every spider of this
     * mod is a spider first. But a spider of the queen is a soldier, never a taxi for the undead.
     *
     * <p>So the spawn is finalised exactly as the game does it - the attributes, the spawn bonuses and everything else
     * of {@code Spider#finalizeSpawn} stay untouched - and the rider is taken off again immediately afterwards and
     * removed. Only a skeleton rider is touched; anything else that happens to be on the spider's back is left alone.
     *
     * <p>It lives here, in the shared base of the brood, because it is one rule for every species and for every way a
     * spider can enter the world through this method: the spawn eggs of all five species, the summon command, and a
     * spawner. The paths that never call it - the cocoon, the egg block and the spider carrier, which create their
     * spider directly - were never affected in the first place.
     */
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag)
    {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);

        for (Entity passenger : new ArrayList<>(this.getPassengers()))
        {
            if (passenger instanceof AbstractSkeleton)
            {
                passenger.stopRiding();
                passenger.discard();
            }
        }

        return result;
    }

    /**
     * Keeps the temporary command honest: a direct attack order whose victim is gone is dropped, and a scout move is
     * dropped when its destination is not loaded any more or when the queen walked out of the leash of that move.
     */
    /** Keeps the temporary command honest, the ability rest and the combat grace of this spider up to date. */
    @Override
    public void tick()
    {
        // The death of a spider of the brood is final, and this is what makes it so. The game removes an entity only
        // while its health is zero, so a body that was healed after its death had already run could never be removed
        // and could never die again - it would walk around frozen in the death pose of the game. Everything else about
        // a death inside the brood is the ordinary death of the game; this only puts the health of a dead spider back
        // where its death left it, before the death tick of this very tick looks at it.
        if (!this.level().isClientSide && this.dead && this.getHealth() > 0.0F)
        {
            this.setHealth(0.0F);
        }

        super.tick();

        if (this.level().isClientSide)
        {
            return;
        }

        // The rest of the brood ability runs down on the server only, so a client can never hand out a free cast.
        if (this.abilityCooldownTicks > 0)
        {
            --this.abilityCooldownTicks;

            if (this.abilityCooldownTicks <= 0)
            {
                this.syncAbilityCooldownEnd(0);
            }
        }
        else if (this.getEntityData().get(DATA_ABILITY_COOLDOWN_END) != 0)
        {
            // A value restored from a save file has no end time yet: it is written once, so a HUD can count it down.
            this.syncAbilityCooldownEnd(this.abilityCooldownTicks <= 0 ? 0
                    : (int) (this.level().getGameTime() + this.abilityCooldownTicks));
        }

        // The veterancy of this spider: the health it was born with is measured once, and the attributes of its rank are
        // kept in step with the rank. Both are idempotent - a rank is applied by replacing its own modifier - so a
        // reload, a dimension change or a second promotion can never stack a bonus twice.
        this.syncVeterancy();

        // An elite spider knits one point of damage every second, whatever it is doing: fighting, walking, guarding or
        // standing still. There is no combat timer and no enemy check anywhere in this - the rank itself is the rule.
        if (this.getRank().hasRegeneration() && this.tickCount % SpiderRank.REGENERATION_INTERVAL_TICKS == 0
                && this.getHealth() > 0.0F && this.getHealth() < this.getMaxHealth())
        {
            this.heal(SpiderRank.REGENERATION_AMOUNT);
        }

        // The grace a mine handed out runs out on its own, a few ticks after the blast it was granted for.
        if (this.explosionGraceTicks > 0)
        {
            --this.explosionGraceTicks;
        }

        // A fight that just ended earns the spider a few seconds of walking home before the emergency teleport of its
        // follow goal may take it. Without this a spider would blink out of the corpse it just made.
        if (this.combatReturnGraceTicks > 0)
        {
            --this.combatReturnGraceTicks;
        }

        boolean fighting = this.getTarget() != null || this.getForcedTarget() != null;

        if (fighting)
        {
            this.combatReturnGraceTicks = COMBAT_RETURN_GRACE_TICKS;
        }
        else if (this.combatReturnGraceTicks > COMBAT_RETURN_GRACE_TICKS)
        {
            this.combatReturnGraceTicks = COMBAT_RETURN_GRACE_TICKS;
        }

        this.trackFollowRoster();

        // Everything of this spider a client has to know is kept in step here, so an update can never be forgotten
        // somewhere in the command code: who owns it, what its order is and what errand it is on. Each of these writes
        // only when the value really changed, so nothing is sent per tick.
        this.syncOwnerData();
        this.syncOrderData();
        this.syncTaskData();

        if (this.forcedTarget != null && !SpiderMinionTargeting.isAttackable(this, this.forcedTarget))
        {
            this.clearForcedTarget();
        }

        if (this.temporaryCommand == SpiderTemporaryCommand.MOVE_TO)
        {
            Player queen = this.getOwnerPlayer();

            // A move whose destination unloaded is over: the spider is an ordinary follower again, exactly as if the
            // order had never been given. Nothing here loads a chunk, it only asks whether one is there. And a queen who
            // walked more than the scout leash away ends the move too - then the ordinary follow teleport may bring the
            // spider home at last.
            if (this.moveAnchor == null || !this.level().hasChunkAt(this.moveAnchor)
                    || (queen != null && this.distanceToSqr(queen) > TEMP_MOVE_OWNER_LEASH * TEMP_MOVE_OWNER_LEASH))
            {
                this.clearTemporaryCommand();
            }
        }
    }

    /** The attributes of a minion spider: the walking speed of a spider, everything else is asked for. */
    protected static AttributeSupplier.Builder createMinionAttributes(double maxHealth, double attackDamage)
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }

    // ------------------------------------------------------------------ ownership

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.getEntityData().define(DATA_ABILITY_COOLDOWN_END, 0);
        this.getEntityData().define(DATA_OWNER, Optional.empty());
        this.getEntityData().define(DATA_ORDER, SpiderOrderMode.FOLLOW.ordinal());
        this.getEntityData().define(DATA_TASK, SpiderTemporaryCommand.NONE.ordinal());
        this.getEntityData().define(DATA_RANK, SpiderRank.DEFAULT.ordinal());
    }

    public void setOwnerUUID(@Nullable UUID owner)
    {
        UUID previous = this.owner;
        this.owner = owner;

        if (!this.level().isClientSide)
        {
            this.getEntityData().set(DATA_OWNER, Optional.ofNullable(owner));
        }

        if (!this.level().isClientSide && previous != null && !previous.equals(owner) && this.level().getServer() != null)
        {
            // The spider changed queen: it is not a follower of the old one any more, so it leaves her roster.
            net.minecraft.server.level.ServerPlayer old = this.level().getServer().getPlayerList().getPlayer(previous);

            if (old != null)
            {
                spiderqueenhungrycraft.player.QueenDataCapability.get(old)
                        .ifPresent(data -> data.followRoster().remove(this.getUUID()));
            }
        }
    }

    /** @return the ticks a follower that just finished a fight may still walk home before a teleport is allowed. */
    public int getCombatReturnGraceTicks()
    {
        return this.combatReturnGraceTicks;
    }

    /**
     * Spares this spider from explosions for that many ticks (Spider Mine and Egg Arrow round).
     *
     * <p>It is called by the blast of a spider mine, one moment before that blast runs, for the eight spiders the mine
     * just put into the world. The filter of the blast ({@code SpiderMineExplosion#onExplosionDetonate}) then takes this
     * spider off the list of creatures it is about to hurt.
     *
     * <p>A longer window never shortens an older one, and a mine can only ever extend this - it is a courtesy handed out
     * to a newborn spider, not a resource anybody can farm.
     */
    public void grantExplosionGrace(int ticks)
    {
        this.explosionGraceTicks = Math.max(this.explosionGraceTicks, ticks);
    }

    /** @return true while this spider is inside a grace window a spider mine handed out. */
    public boolean isSparedByExplosions()
    {
        return this.explosionGraceTicks > 0;
    }

    /**
     * @return true while this spider is fighting, has a fight forced on it, or has just finished one.
     *
     * <p>One definition for every rule that has to know whether this spider is busy with a fight right now - the gear
     * pickup of an infested Steve body and the follow teleport of a pet both ask it. The grace window
     * ({@link #COMBAT_RETURN_GRACE_TICKS}, three seconds) keeps a spider that has just killed something counted as
     * fighting for a moment, so nothing starts rummaging through the loot of the corpse it just made.
     *
     * <p>{@link #tick()} deliberately does not use this method to arm that grace: there only a real target counts,
     * otherwise the grace would renew itself for ever.
     */
    public boolean isInCombat()
    {
        return this.getTarget() != null || this.getForcedTarget() != null || this.combatReturnGraceTicks > 0;
    }

    /**
     * Notes in the roster of the queen where this spider is, once a second.
     *
     * <p>Only a follower is written down there; a spider that was told to hold or patrol removes itself, because it must
     * never be dragged along when the queen suddenly travels. A queen who is not online keeps the last known entry of
     * her spiders, which is exactly what the escort relocation needs when she comes back somewhere else.
     */
    private void trackFollowRoster()
    {
        if (this.tickCount % ROSTER_UPDATE_TICKS != 0)
        {
            return;
        }

        Player queen = this.getOwnerPlayer();

        if (queen == null)
        {
            return;
        }

        spiderqueenhungrycraft.player.QueenDataCapability.get(queen).ifPresent(data ->
        {
            if (this.orderMode == SpiderOrderMode.FOLLOW)
            {
                data.followRoster().addOrTouch(this.getUUID(), this.level().dimension(), this.chunkPosition());
            }
            else
            {
                data.followRoster().remove(this.getUUID());
            }
        });
    }

    /** A spider that leaves the world leaves the roster of its queen as well, so no dead UUID rots in there. */
    @Override
    public void remove(RemovalReason reason)
    {
        if (!this.level().isClientSide)
        {
            Player queen = this.getOwnerPlayer();

            if (queen != null)
            {
                spiderqueenhungrycraft.player.QueenDataCapability.get(queen)
                        .ifPresent(data -> data.followRoster().remove(this.getUUID()));
            }
        }

        super.remove(reason);
    }

    /** @return the player this spider belongs to, or null for a spider without an owner. */
    @Nullable
    public UUID getOwnerUUID()
    {
        // A client has no save file, so the ownership travels in the synced data: without it a client could never tell
        // its own spiders from anybody else's, and neither the inspect HUD nor the view selection would work.
        if (this.level().isClientSide)
        {
            return this.getEntityData().get(DATA_OWNER).orElse(null);
        }

        return this.owner;
    }

    /**
     * The colony view of the very same value (Queen Colony framework): the queen of this minion is its owner, so the
     * owner UUID is the colony UUID. Read only, nothing is stored twice.
     */
    @Override
    @Nullable
    public UUID getQueenOwnerUUID()
    {
        return this.owner;
    }

    /** @return true when this spider belongs to exactly that player. */
    public boolean isOwnedBy(Player player)
    {
        return this.isOwnedBy(player.getUUID());
    }

    /**
     * @return true when this spider belongs to exactly that owner.
     *
     *         <p>The owner UUID form of the very same question. It exists because not every caller has the player at
     *         hand: the soul remnant of a queen (Soul Foundation round), for one, only knows the UUID it is looking
     *         for, and a spider may well be asked about a queen who is not even in this level.
     */
    public boolean isOwnedBy(@Nullable UUID playerId)
    {
        UUID owner = this.getOwnerUUID();

        return owner != null && owner.equals(playerId);
    }

    /**
     * @return true when that player may pick this spider up. In survival only the owner may, in creative
     *         anybody may - which is the test entrance of this round.
     */
    public boolean canBeCollectedBy(Player player)
    {
        return player.isCreative() || this.isOwnedBy(player);
    }

    // ------------------------------------------------------------------ the long term order

    /** @return the order this spider follows: follow, hold or patrol. */
    public SpiderOrderMode getOrderMode()
    {
        if (this.level().isClientSide)
        {
            int ordinal = this.getEntityData().get(DATA_ORDER);

            return SpiderOrderMode.values()[Math.max(0, Math.min(ordinal, SpiderOrderMode.values().length - 1))];
        }

        return this.orderMode;
    }

    /** @return the player this spider belongs to, or null when that player is not in this world. */
    @Nullable
    public Player getOwnerPlayer()
    {
        UUID owner = this.getOwnerUUID();

        return owner == null ? null : this.level().getPlayerByUUID(owner);
    }

    /**
     * @return the anchor of a hold or patrol spider, or null when the spider has no order that needs one - or when the
     *         anchor lies in another dimension, which this round deliberately does not bridge: a spider whose queen
     *         walked through a portal stays where it is until she comes back.
     */
    @Nullable
    public BlockPos getOrderAnchor()
    {
        if (this.orderAnchor == null || this.orderAnchorDimension == null)
        {
            return null;
        }

        return this.orderAnchorDimension.equals(this.level().dimension()) ? this.orderAnchor : null;
    }

    /** Sets the order without touching the anchor: the sceptre uses this after its own checks. */
    public void setOrderMode(SpiderOrderMode mode)
    {
        this.orderMode = mode;

        if (!mode.needsAnchor())
        {
            // A follower has no post to keep, so the anchor is simply dropped instead of being kept as dead data.
            this.orderAnchor = null;
            this.orderAnchorDimension = null;
        }
    }

    /**
     * The cycle of the sceptre on a spider: follow, hold, patrol, follow.
     *
     * <p>Going from follow to hold takes the place the spider stands on right now as its post, hold to patrol keeps
     * that anchor, and patrol back to follow drops it. It is called by the group cycle of the sceptre
     * ({@code SpiderCommand#cycleOrder(Player)}), which is why the anchor rule lives here on the spider: a group that is
     * told to hold guards where each of its spiders happens to stand.
     */
    public SpiderOrderMode cycleOrder()
    {
        SpiderOrderMode next = this.orderMode.next();

        if (next == SpiderOrderMode.HOLD && this.orderMode == SpiderOrderMode.FOLLOW)
        {
            this.orderAnchor = this.blockPosition();
            this.orderAnchorDimension = this.level().dimension();
        }

        this.setOrderMode(next);

        return this.orderMode;
    }

    /**
     * Puts this spider on the <b>free attack</b> order: it hunts everything around it that is not of its own side, and
     * it walks its rounds while it does.
     *
     * <p>The place it stands on becomes its hunting ground - but only when it has none yet. A spider that was already
     * guarding or patrolling a post keeps that post, because the order key steps hold, patrol and free attack at the same
     * spot; only a follower, which has no post at all, is given the block it happens to stand on, exactly like the step
     * from follow to hold does it.
     */
    public void freeAttackHere()
    {
        if (this.orderAnchor == null || this.orderAnchorDimension == null
                || !this.orderAnchorDimension.equals(this.level().dimension()))
        {
            this.orderAnchor = this.blockPosition();
            this.orderAnchorDimension = this.level().dimension();
        }

        // Written directly instead of through setOrderMode, so the anchor that was just decided is kept.
        this.orderMode = SpiderOrderMode.FREE_ATTACK;
    }

    /** @return true when this spider is hunting right now. */
    public boolean isFreeAttacking()
    {
        return this.orderMode == SpiderOrderMode.FREE_ATTACK;
    }

    /**
     * Sends this spider to a post (the block command of the sceptre): it becomes a {@link SpiderOrderMode#HOLD} spider
     * at that position. Nothing is teleported here - the spider walks there through the ordinary navigation.
     */
    public void holdAt(BlockPos pos)
    {
        this.orderAnchor = pos.immutable();
        this.orderAnchorDimension = this.level().dimension();
        this.orderMode = SpiderOrderMode.HOLD;
    }

    /**
     * Takes this spider off the free attack order, because a new command outranks the hunt: it becomes an ordinary
     * follower again, which is the state every spider of the brood starts in.
     *
     * <p>What counts as a new command is decided by the callers and not here. The two commands that do <b>not</b> write
     * an order of their own - the scout move and the attack order - call this method. Every other command is an order
     * and therefore leaves the hunt by writing that order (a rally, a post, a march), and the order key walks the cycle
     * of the four orders on purpose - so being told to hunt again is not a new command either, it is the same one.
     *
     * @return true when this spider really was hunting until now
     */
    public boolean leaveFreeAttack()
    {
        if (!this.isFreeAttacking())
        {
            return false;
        }

        this.setOrderMode(SpiderOrderMode.FOLLOW);

        return true;
    }

    /**
     * The emergency call of the queen sceptre: this spider is a follower again, its old post is forgotten and the last
     * direct order is dropped. Whatever the queen is fighting right now is found again by the ordinary
     * "my queen is in danger" rule.
     */
    public void rally()
    {
        this.setOrderMode(SpiderOrderMode.FOLLOW);
        this.clearTemporaryCommand();
        this.clearForcedTarget();
    }

    // ------------------------------------------------------------------ the brood ability

    /**
     * @return the ability of this kind of spider, or null when it has none.
     *
     * <p>Being null is the normal case: an imp spider and an ordinary brood spider have no ability at all, and a
     * broadcast of the queen is simply nothing for them. A special spider returns its own ability here - that is the
     * only place the sceptre and the ability framework ever meet.
     */
    @Nullable
    public SpiderAbility getAbility()
    {
        return null;
    }

    /** @return true when this spider has an ability to offer. */
    public boolean hasAbility()
    {
        return this.getAbility() != null;
    }

    /** @return the stable id of the ability of this spider, or null when it has none. */
    @Nullable
    public String getAbilityId()
    {
        SpiderAbility ability = this.getAbility();

        return ability == null ? null : ability.id();
    }

    /** @return the name of the ability of this spider, or null when it has none. */
    @Nullable
    public Component getAbilityDisplayName()
    {
        SpiderAbility ability = this.getAbility();

        return ability == null ? null : ability.displayName();
    }

    /** @return the full rest of the ability of this spider, 0 when it has none. */
    public int getAbilityCooldownTotal()
    {
        SpiderAbility ability = this.getAbility();

        return ability == null ? 0 : ability.cooldownTicks();
    }

    /** @return the ticks the ability of this spider still rests, 0 when it is ready. */
    public int getAbilityCooldownRemaining()
    {
        return this.abilityCooldownTicks;
    }

    /** @return true when this spider has an ability and is not resting right now. */
    public boolean isAbilityReady()
    {
        return this.getAbility() != null && this.abilityCooldownTicks <= 0;
    }

    /** Puts the ability of this spider back on rest, used by the carrier that keeps its cooldown. */
    public void setAbilityCooldownRemaining(int ticks)
    {
        this.abilityCooldownTicks = Math.max(0, ticks);

        if (!this.level().isClientSide)
        {
            this.syncAbilityCooldownEnd(this.abilityCooldownTicks <= 0 ? 0
                    : (int) (this.level().getGameTime() + this.abilityCooldownTicks));
        }
    }

    /** @return the game time at which the ability of this spider is ready again, or 0 when it is ready now. */
    public int getAbilityCooldownEndGameTime()
    {
        return this.getEntityData().get(DATA_ABILITY_COOLDOWN_END);
    }

    /**
     * @return the ticks the ability of this spider still rests, on both sides.
     *
     * <p>The server answers from its own countdown, which is the authority. A client has no countdown of its own and
     * reads the synced end time instead, so the HUD counts down exactly as long as the server said.
     */
    public int getAbilityCooldownRemainingNow()
    {
        if (!this.level().isClientSide)
        {
            return this.abilityCooldownTicks;
        }

        int end = this.getAbilityCooldownEndGameTime();

        return end <= 0 ? 0 : Math.max(0, end - (int) this.level().getGameTime());
    }

    /** @return true when the ability of this spider is ready, on both sides. */
    public boolean isAbilityReadyNow()
    {
        return this.hasAbility() && this.getAbilityCooldownRemainingNow() <= 0;
    }

    /** @return true when the ability of this spider is running right now (its own effect is on the spider). */
    public boolean isAbilityActive()
    {
        SpiderAbility ability = this.getAbility();

        return ability != null && ability.isActive(this);
    }

    /** Writes the end time of the ability rest into the synced data, and nothing when it does not change. */
    private void syncAbilityCooldownEnd(int endGameTime)
    {
        if (this.getEntityData().get(DATA_ABILITY_COOLDOWN_END) != endGameTime)
        {
            this.getEntityData().set(DATA_ABILITY_COOLDOWN_END, endGameTime);
        }
    }

    /** Keeps the owner a client sees in step with the real one. */
    private void syncOwnerData()
    {
        Optional<UUID> synced = this.getEntityData().get(DATA_OWNER);

        if (this.owner == null ? synced.isPresent() : !this.owner.equals(synced.orElse(null)))
        {
            this.getEntityData().set(DATA_OWNER, Optional.ofNullable(this.owner));
        }
    }

    /** Keeps the order a client sees in step with the real one. */
    private void syncOrderData()
    {
        if (this.getEntityData().get(DATA_ORDER) != this.orderMode.ordinal())
        {
            this.getEntityData().set(DATA_ORDER, this.orderMode.ordinal());
        }
    }

    /** Keeps the errand a client sees in step with the real one. */
    private void syncTaskData()
    {
        if (this.getEntityData().get(DATA_TASK) != this.temporaryCommand.ordinal())
        {
            this.getEntityData().set(DATA_TASK, this.temporaryCommand.ordinal());
        }
    }

    /**
     * Tries to cast the ability of this spider, exactly once.
     *
     * <p>A spider that has no ability, that is still resting, or whose ability refuses this context does nothing at
     * all: no message, no sound and no side effect. A spider that does cast starts its cooldown at that very moment, so
     * the rest begins with the cast and not after the effect.
     *
     * @return true when the ability really fired
     */
    public boolean tryActivateAbility(SpiderAbilityContext context)
    {
        SpiderAbility ability = this.getAbility();

        if (ability == null || this.level().isClientSide || this.abilityCooldownTicks > 0
                || !ability.targetRequirement().isSatisfiedBy(context) || !ability.canActivate(this, context))
        {
            return false;
        }

        boolean cast = ability.activate(this, context);

        if (!cast)
        {
            // The ability refused (a place the spider cannot land on, for example). Nothing is spent: no cooldown is
            // started for a cast that never happened.
            return false;
        }

        this.abilityCooldownTicks = ability.cooldownTicks();
        this.syncAbilityCooldownEnd((int) (this.level().getGameTime() + this.abilityCooldownTicks));

        return true;
    }

    /**
     * @return the extra word the HUD of the queen shows behind the name of the ability of this spider, for example
     *         "Arrow" for the ammunition a skeleton spider has loaded, or null when there is nothing to add.
     */
    @Nullable
    public Component getAbilityStateDetail()
    {
        SpiderAbility ability = this.getAbility();

        return ability == null ? null : ability.stateDetail(this);
    }

    // ------------------------------------------------------------------ veterancy

    /** @return the rank of this spider, on both sides - a client reads the synchronised value. */
    public SpiderRank getRank()
    {
        return SpiderRank.byId(this.getEntityData().get(DATA_RANK));
    }

    /** @return the maximum health this spider was born with, or 0 while it has not been measured yet. */
    public int getBaseHealth()
    {
        return this.veterancy.baseHealth();
    }

    /** @return the kills of this spider, counted as the maximum health of the creatures it killed. */
    public int getKillExperience()
    {
        return this.veterancy.killExperience();
    }

    /** @return the experience this spider still needs for its next rank, or 0 when it is at the top of the ladder. */
    public int getExperienceToNextRank()
    {
        SpiderRank current = this.getRank();

        if (current.ordinal() + 1 >= SpiderRank.VALUES.length)
        {
            return 0;
        }

        SpiderRank next = SpiderRank.VALUES[current.ordinal() + 1];

        return Math.max(0, SpiderVeterancyData.experienceFor(next, this.getBaseHealth()) - this.veterancy.killExperience());
    }

    /**
     * Writes the kill of one creature into the record of this spider.
     *
     * <p>Server side only, called by the death listener of this round: the spider that dealt the last blow and no other
     * one gets the credit. A kill that promotes the spider applies the attributes of the new rank at once and sets the
     * spider to its new maximum health - unless the spider itself is dying, in which case the kill is recorded and the
     * promotion is dropped ({@link #promote()}).
     */
    public void addKillExperience(int experience)
    {
        if (this.level().isClientSide || experience <= 0)
        {
            return;
        }

        if (this.veterancy.addExperience(experience))
        {
            this.promote();
        }
    }

    /** Applies a promotion: the rank a client sees, the attributes of the new rank, the new maximum health, a sound. */
    private void promote()
    {
        // A spider whose death has already begun is never promoted, and this guard is the whole reason it exists: the
        // kill that would promote it is very often the one its own death blast has just made (a creeper spider dies
        // between its own victims), and the promotion hands out health. A body healed in the middle of its own death
        // is a body the game can never remove - it only removes an entity whose health is zero - and never kills
        // again, because the death of a creature runs exactly once. That is a corpse that walks: the rank is not
        // worth it, and the kill is recorded anyway.
        if (this.isDeadOrDying() || this.dead)
        {
            // A line for whoever has to prove this rule: the kill is real, the promotion is not, and a spider that
            // dies between its own victims is exactly the case this guard was written for.
            spiderqueenhungrycraft.SpiderQueenHungryCraft.LOGGER
                    .debug("[BROOD_PROMOTION] a dying spider was not promoted, its death stays final");

            return;
        }

        // The rank is made visible at once, so the inspect HUD of the queen and the methods that read the rank agree
        // with the attributes in the same tick the promotion happened.
        this.syncRankData();
        this.applyRankAttributes();

        // A promotion is a reward, not a wound: the spider stands at its new maximum the very moment it is promoted, so
        // a veteran of three fights is a whole veteran and not a wounded one.
        this.setHealth(this.getMaxHealth());

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SPIDER_AMBIENT,
                SoundSource.NEUTRAL, 0.9F, 1.6F);
    }

    /** Keeps the rank a client sees in step with the real one, and writes only when it really changes. */
    private void syncRankData()
    {
        int ordinal = this.veterancy.rank().ordinal();

        if (this.getEntityData().get(DATA_RANK) != ordinal)
        {
            this.getEntityData().set(DATA_RANK, ordinal);
        }
    }

    /**
     * Records the health this spider was born with, and keeps the attributes of its rank in step with that rank.
     *
     * <p>Both halves are idempotent by construction: the base health is written once and never again, and a rank is
     * applied by writing the same modifiers again and again instead of scaling what is already there. That is what
     * makes a promotion, a reload, a dimension change and a second promotion all end up at one and the same number -
     * never at 120% of 120%, and never at 200% of 200%.
     */
    private void syncVeterancy()
    {
        if (!this.veterancy.hasBaseHealth())
        {
            // The first server tick of a spider is the moment it was born: whatever its maximum health is right now -
            // before any rank modifier exists - is what this spider is worth, and it stays that for the rest of its
            // life. Every promotion is priced against this number, never against a health a rank has already raised.
            this.veterancy.setBaseHealth((int) Math.round(this.getAttributeValue(Attributes.MAX_HEALTH)));
        }

        this.syncRankData();
        this.applyRankAttributes();
    }

    /** Puts the three multipliers of the current rank onto the attributes of this spider. */
    private void applyRankAttributes()
    {
        SpiderRank rank = this.veterancy.rank();

        this.applyRankAttribute(Attributes.MAX_HEALTH, RANK_HEALTH_ID, rank.healthMultiplier());
        this.applyRankAttribute(Attributes.ATTACK_DAMAGE, RANK_DAMAGE_ID, rank.damageMultiplier());
        this.applyRankAttribute(Attributes.MOVEMENT_SPEED, RANK_SPEED_ID, rank.speedMultiplier());
    }

    /**
     * Sets one rank multiplier on one attribute of this spider.
     *
     * <p>It is added as a multiplier on the total value, which is exactly the "the value this spider was born with
     * times its rank" the design asks for, and it is refreshed rather than added again: a modifier of the game carries
     * its own identity, so writing the same one twice replaces the first instead of stacking on it. The modifier is
     * transient, so the save file never carries a bonus that a reload could apply a second time - the rank in the save
     * file is the only thing that remembers, and the attribute is rebuilt from it.
     *
     * <p>Rank one adds no modifier at all - not one of zero - so an ordinary spider is literally the spider it was
     * before this round existed.
     */
    private void applyRankAttribute(Attribute attribute, UUID id, double multiplier)
    {
        AttributeInstance instance = this.getAttribute(attribute);

        if (instance == null)
        {
            return;
        }

        double amount = multiplier - 1.0D;
        AttributeModifier existing = instance.getModifier(id);

        if (existing != null)
        {
            if (Math.abs(existing.getAmount() - amount) < 1.0E-6D)
            {
                // Already exactly right, which is the ordinary case: one comparison per attribute per tick.
                return;
            }

            instance.removeModifier(id);
        }

        if (Math.abs(amount) > 1.0E-6D)
        {
            instance.addTransientModifier(new AttributeModifier(id, "spider rank", amount,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /**
     * @return the veterancy of this spider as its own tag, so that a carrier can keep it across packing and unpacking
     *         (Brood Veterancy round). It is exactly the tag the save file uses, so what a spider is never depends on the
     *         way it was stored.
     */
    public CompoundTag writeVeterancyTag()
    {
        CompoundTag tag = new CompoundTag();
        this.veterancy.save(tag);

        return tag;
    }

    /**
     * Puts the veterancy of that tag back onto this spider, and with it the attributes of the rank it holds.
     *
     * <p>A rank can only ever go up in the record itself, so a spider that is unpacked from an old carrier (one that
     * never learned about veterancy) simply keeps what it has. Nothing here heals the spider: the wounds of a packed
     * spider travel separately, and a wounded veteran is put down wounded.
     */
    public void readVeterancyTag(@Nullable CompoundTag tag)
    {
        if (tag == null)
        {
            return;
        }

        this.veterancy.load(tag);
        this.syncRankData();
        this.applyRankAttributes();
    }

    // ------------------------------------------------------------------ the temporary command

    /** @return the one temporary command of this spider: nothing, a scout move or a forced attack. */
    public SpiderTemporaryCommand getTemporaryCommand()
    {
        if (this.level().isClientSide)
        {
            int ordinal = this.getEntityData().get(DATA_TASK);

            return SpiderTemporaryCommand.values()[Math.max(0,
                    Math.min(ordinal, SpiderTemporaryCommand.values().length - 1))];
        }

        return this.temporaryCommand;
    }

    /** @return the scout position of a {@link SpiderTemporaryCommand#MOVE_TO} spider in this level, or null. */
    @Nullable
    public BlockPos getMoveAnchor()
    {
        return this.temporaryCommand == SpiderTemporaryCommand.MOVE_TO ? this.moveAnchor : null;
    }

    /** @return the id of the move command this spider is busy with, 0 when it is busy with none. */
    public int getMoveCommandId()
    {
        return this.temporaryCommand == SpiderTemporaryCommand.MOVE_TO ? this.moveCommandId : 0;
    }

    /** @return true when this spider walks on a scout move right now (a move that has not arrived yet). */
    public boolean isScoutTravelling()
    {
        BlockPos anchor = this.getMoveAnchor();

        return anchor != null
                && this.distanceToSqr(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D)
                    > SpiderMoveToGoal.ARRIVE_RADIUS * SpiderMoveToGoal.ARRIVE_RADIUS;
    }

    /**
     * The scout move of the sceptre: walk to that spot and stand by there, without touching the order of this spider.
     * Whatever temporary command it had is dropped, and the old fight too - a new command always replaces the old one.
     */
    public void setMoveTo(BlockPos navigationTarget, int commandId)
    {
        this.clearForcedTarget();
        this.getNavigation().stop();
        this.setTarget(null);

        this.temporaryCommand = SpiderTemporaryCommand.MOVE_TO;
        this.moveAnchor = navigationTarget.immutable();
        this.moveCommandId = commandId;
        this.moveArrivalReported = false;
    }

    /** Drops the temporary command; the order of this spider decides again, unchanged. */
    public void clearTemporaryCommand()
    {
        // One slot means one state: a spider that is told to stop its errand also stops the fight that errand was, so
        // no order can ever leave a forced victim behind without the temporary command that belonged to it.
        this.forcedTarget = null;
        this.temporaryCommand = SpiderTemporaryCommand.NONE;
        this.moveAnchor = null;
        this.moveCommandId = 0;
        this.moveArrivalReported = false;

        if (this.getTarget() != null && !this.getTarget().isAlive())
        {
            this.setTarget(null);
        }
    }

    /**
     * Reports the first arrival of a scout move to the queen who gave it, so her command marker can disappear. The
     * other spiders of the same command keep walking - only the marker is about "somebody got there".
     */
    public void reportMoveArrival()
    {
        if (this.moveArrivalReported || this.temporaryCommand != SpiderTemporaryCommand.MOVE_TO)
        {
            return;
        }

        this.moveArrivalReported = true;

        if (this.getOwnerPlayer() instanceof net.minecraft.server.level.ServerPlayer queen && queen.connection != null)
        {
            spiderqueenhungrycraft.network.HungryCraftNetwork.sendToPlayer(
                    spiderqueenhungrycraft.network.S2CQueenScepterPacket.arrived(this.moveCommandId), queen);
        }
    }

    // ------------------------------------------------------------------ the direct order

    /** @return the target the queen sceptre ordered right now, or null when there is no order. */
    @Nullable
    public LivingEntity getForcedTarget()
    {
        return SpiderMinionTargeting.isAttackable(this, this.forcedTarget) ? this.forcedTarget : null;
    }

    /** Sets the direct order of the sceptre. A target that can never be attacked is refused. */
    public void setForcedTarget(@Nullable LivingEntity target)
    {
        LivingEntity valid = SpiderMinionTargeting.isAttackable(this, target) ? target : null;

        // One slot only: an attack order always replaces whatever the spider was busy with, a scout move included.
        if (valid != null)
        {
            this.temporaryCommand = SpiderTemporaryCommand.FORCED_ATTACK;
            this.moveAnchor = null;
            this.moveCommandId = 0;
            this.moveArrivalReported = false;
            this.getNavigation().stop();
        }

        this.forcedTarget = valid;
    }

    /** Drops the direct order, after which the long term order of this spider decides again. */
    public void clearForcedTarget()
    {
        this.forcedTarget = null;

        if (this.temporaryCommand.isFight())
        {
            this.temporaryCommand = SpiderTemporaryCommand.NONE;
        }

        if (this.getTarget() != null && !this.getTarget().isAlive())
        {
            this.setTarget(null);
        }
    }

    // ------------------------------------------------------------------ a minion does not vanish

    /** A minion is not a wild monster: it stays where its owner left it, however far away that is. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    /** A minion survives a peaceful world like a pet does, it is not swept away with the monsters. */
    @Override
    protected boolean shouldDespawnInPeaceful()
    {
        return false;
    }

    /** A pet spider is not a threat: it never keeps anybody from sleeping. */
    @Override
    public boolean isPreventingPlayerRest(Player player)
    {
        return false;
    }

    // ------------------------------------------------------------------ collection

    /**
     * What a hand of a player does to a spider of the brood (Brood Feeding round):
     * <ul>
     * <li>a piece of <b>meat</b> in the hand of the spider's own queen feeds it - one piece of the stack is eaten and the
     * spider heals by exactly the hunger that food would have fed to a player ({@link BroodFood}),</li>
     * <li>an empty main hand picks the spider up: it turns into a spider carrier item and leaves the world,</li>
     * <li>and every other hand is nothing at all.</li>
     * </ul>
     *
     * <p><b>Only the queen herself may feed</b> ({@link #isOwnedBy}), which is deliberately stricter than the pick up
     * below: feeding is the care of an owner for her own brood and not a right of whoever happens to stand next to the
     * spider, so a stranger in creative mode may carry a spider away but never fill its health. A spider that is already
     * at full health eats nothing and keeps the food in the hand - its queen is told so in the action bar instead of
     * losing a piece of meat to it.
     *
     * <p>The client only decides that this is an interaction at all and swings the arm; every bite happens on the server,
     * so a modified client can ask to feed and nothing else.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        int food = BroodFood.nutritionOf(stack, this);

        if (food > 0 && this.isOwnedBy(player))
        {
            return this.feedFrom(player, stack, food);
        }

        if (hand != InteractionHand.MAIN_HAND || !stack.isEmpty())
        {
            return super.mobInteract(player, hand);
        }

        if (this.level().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }

        if (!this.canBeCollectedBy(player))
        {
            player.displayClientMessage(
                    Component.translatable("message.spiderqueen_hungrycraft.not_my_spider"), true);
            return InteractionResult.PASS;
        }

        ItemStack carrier = SpiderCarrierItem.of(this);

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SPIDER_AMBIENT,
                SoundSource.NEUTRAL, 0.8F, 1.4F);
        this.discard();

        if (!player.getInventory().add(carrier))
        {
            player.drop(carrier, false);
        }

        return InteractionResult.CONSUME;
    }

    /**
     * One bite of that food: the spider heals by the hunger the food would have fed to a player, one piece of the stack
     * leaves the hand of the queen, and the spider answers with hearts and the sound of a meal.
     *
     * <p>A queen in creative mode feeds without spending anything, exactly like every other feeding of the game.
     */
    private InteractionResult feedFrom(Player queen, ItemStack stack, int food)
    {
        // The client swings the arm and asks; the server is the one that eats.
        if (this.level().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }

        if (this.getHealth() >= this.getMaxHealth())
        {
            if (queen instanceof ServerPlayer server)
            {
                server.displayClientMessage(
                        Component.translatable("message.spiderqueen_hungrycraft.brood_not_hungry"), true);
            }

            return InteractionResult.SUCCESS;
        }

        this.heal(food);

        if (!queen.getAbilities().instabuild)
        {
            stack.shrink(1);
        }

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EAT,
                SoundSource.NEUTRAL, 0.8F, 1.2F);

        if (this.level() instanceof ServerLevel server)
        {
            server.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + this.getBbHeight() * 0.7D,
                    this.getZ(), 3, 0.3D, 0.2D, 0.3D, 0.0D);
        }

        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ saving

    /**
     * Writes everything this spider <b>wears and carries</b> that a carrier has to keep (Carrier Gear round).
     *
     * <p>A spider that is picked up with the carrier item and put down again must come back as the spider it was, and
     * "the spider it was" is more than its species, its health, its name and its rank: it is also its gear. The soldier
     * of this mod that really owns gear is the infested Steve - armour on four slots, a shield, a blade, a bow and the
     * apples in its pouch - and it used to lose every bit of it on one trip through a carrier.
     *
     * <p><b>The carrier asks the spider, never a list of species.</b> This method is empty here because an ordinary
     * spider of the brood owns no gear at all, and a species that does own gear overrides it together with
     * {@link #readCarriedGear}: from that moment the carrier keeps that gear without the carrier knowing what gear is.
     * The same rule as the class check in the carrier - every species of this mod is a {@link SpiderMinionEntity}, today
     * and later - so a spider that is added next year is carried correctly the day it is written, not the day somebody
     * remembers this file.
     *
     * <p>The tag written here travels inside the carrier item and is written back by {@link #readCarriedGear}. It is
     * deliberately <b>not</b> the save file of the spider: the owner, the order, its anchor, its rank and its ability
     * cooldown are already carried, each by the class that owns them, and a deployed spider is a fresh arrival rather
     * than a teleported one.
     */
    public void writeCarriedGear(CompoundTag tag)
    {
        // Nothing to keep: the default body of the brood owns no gear.
    }

    /** Reads back what {@link #writeCarriedGear} wrote. Never called when that method wrote nothing. */
    public void readCarriedGear(CompoundTag tag)
    {
        // Nothing to read back: the default body of the brood owns no gear.
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);

        if (this.owner != null)
        {
            tag.putUUID(KEY_OWNER, this.owner);
        }

        tag.putString(KEY_ORDER, this.orderMode.id());

        if (this.orderAnchor != null)
        {
            tag.putLong(KEY_ANCHOR, this.orderAnchor.asLong());
        }

        if (this.orderAnchorDimension != null)
        {
            tag.putString(KEY_ANCHOR_DIMENSION, this.orderAnchorDimension.location().toString());
        }

        // The rest of an ability survives a reload: quitting the world is not a way to become ready again faster.
        if (this.abilityCooldownTicks > 0)
        {
            tag.putInt(KEY_ABILITY_COOLDOWN, this.abilityCooldownTicks);
        }

        // The veterancy of this spider: what it was born as, what it has killed and what that makes it. A veteran that
        // quits the world is a veteran when it comes back - rank one is the only rank that says nothing.
        this.veterancy.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);

        if (tag.hasUUID(KEY_OWNER))
        {
            this.owner = tag.getUUID(KEY_OWNER);
        }

        this.orderMode = SpiderOrderMode.byId(tag.contains(KEY_ORDER) ? tag.getString(KEY_ORDER) : null);
        this.orderAnchor = tag.contains(KEY_ANCHOR) ? BlockPos.of(tag.getLong(KEY_ANCHOR)) : null;
        this.orderAnchorDimension = tag.contains(KEY_ANCHOR_DIMENSION)
                ? dimensionOf(tag.getString(KEY_ANCHOR_DIMENSION))
                : null;

        this.abilityCooldownTicks = Math.max(0, tag.getInt(KEY_ABILITY_COOLDOWN));

        // The veterancy comes back with the spider, and its attributes are rebuilt from the rank that was read - never
        // applied on top of what a previous life already had in its attributes.
        this.veterancy.load(tag);
        this.applyRankAttributes();
        this.syncRankData();

        // A reload never restores a fight or a scout move: a temporary command is a moment, not a standing order.
        this.forcedTarget = null;
        this.temporaryCommand = SpiderTemporaryCommand.NONE;
        this.moveAnchor = null;
        this.moveCommandId = 0;
        this.moveArrivalReported = false;
    }

    /** @return the dimension key of that id, or null when the saved id is not a valid dimension any more. */
    @Nullable
    private static ResourceKey<Level> dimensionOf(String id)
    {
        ResourceLocation location = ResourceLocation.tryParse(id);

        return location == null ? null : ResourceKey.create(Registries.DIMENSION, location);
    }
}
