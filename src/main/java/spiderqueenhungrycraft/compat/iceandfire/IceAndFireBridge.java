package spiderqueenhungrycraft.compat.iceandfire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.github.alexthe666.iceandfire.IceAndFire;
import com.github.alexthe666.iceandfire.api.FoodUtils;
import com.github.alexthe666.iceandfire.entity.DragonType;
import com.github.alexthe666.iceandfire.entity.EntityDragonBase;
import com.github.alexthe666.iceandfire.entity.IafEntityRegistry;
import com.github.alexthe666.iceandfire.entity.ai.DragonAIEscort;
import com.github.alexthe666.iceandfire.entity.ai.DragonAIMate;
import com.github.alexthe666.iceandfire.entity.ai.DragonAIReturnToRoost;
import com.github.alexthe666.iceandfire.entity.ai.DragonAITarget;
import com.github.alexthe666.iceandfire.entity.ai.DragonAITargetItems;
import com.github.alexthe666.iceandfire.entity.ai.DragonAITargetNonTamed;
import com.github.alexthe666.iceandfire.entity.ai.DragonAIWander;
import com.github.alexthe666.iceandfire.item.IafItemRegistry;
import com.github.alexthe666.iceandfire.item.ItemDragonArmor;
import com.github.alexthe666.iceandfire.item.ItemSummoningCrystal;
import com.github.alexthe666.iceandfire.message.MessageStartRidingMob;
import com.github.alexthe666.iceandfire.misc.IafSoundRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.compat.iceandfire.ai.NecromancyMoveGoal;
import spiderqueenhungrycraft.compat.iceandfire.ai.NecromancyStanceGoal;
import spiderqueenhungrycraft.compat.iceandfire.ai.NecromancyTargetGoal;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CNecromancyStatePacket;
import spiderqueenhungrycraft.soulfire.SoulfireRising;

/**
 * The only class of this mod that really touches Ice and Fire (Necromancy IV prototype 1).
 *
 * <p>It is named from exactly one place - the inside of a method of {@link IceAndFireSupport} that has already asked
 * {@link IceAndFireSupport#present()} - so in a game without Ice and Fire this class is never loaded, its constant
 * pool is never resolved and nothing of it can fail. That is the whole isolation: the facade speaks vanilla, this
 * class speaks Ice and Fire, and between the two there is one boolean.
 *
 * <p><b>What the prototype proves.</b> One class hierarchy carries three ideas of "a dragon is dead" - the game's own
 * ({@code health <= 0}), Ice and Fire's ({@code isModelDead}) and the world's idea of a skeleton
 * ({@code isSkeletal}) - and Ice and Fire has bound two of them to one field. The prototype does the one thing that
 * separates them again: it takes the corpse flag away ({@link EntityDragonBase#setModelDead}) and gives the animal its
 * health back ({@link EntityDragonBase#setHealth}), while the <b>death stage is never touched</b>, so
 * {@code isSkeletal()} keeps answering true and the client keeps drawing bones. Nothing else about the dragon is
 * changed: not its age, not its stage, not its variant, not its gender, not its owner, not its command, and above all
 * not its identity - the entity is never replaced.
 *
 * <p>Everything here is a probe for a developer. There is no cost, no cooldown, no skill gate and no timer yet.
 */
final class IceAndFireBridge
{
    /** How far in front of the player a probe dragon appears. */
    private static final double SPAWN_DISTANCE = 8.0D;

    /** How far above the aimed point the probe looks for a place to stand. */
    private static final int SPAWN_SEARCH_UP = 4;

    /** And how far below, so a dragon aimed at the sky can still land on the ground a player is standing on. */
    private static final int SPAWN_SEARCH_DOWN = 6;

    /**
     * The stage a dragon has to reach before the contract lets it breathe (Necromancy IV round): three.
     *
     * <p>It is the rule of the round and not a rule of Ice and Fire. That mod lets a dragon breathe from stage two, in
     * a much shorter and weaker shape, and draws its stage from the age of the animal
     * ({@code age >= 100 -> 5, >= 75 -> 4, >= 50 -> 3, >= 25 -> 2, else 1}).
     */
    static final int BREATH_STAGE = 3;

    /** The stage a contract dragon may be ridden from: three as well, as the round asks. */
    static final int RIDE_STAGE = 3;

    /**
     * The priority of the target goal of a contract dragon inside the target selector of the entity: {@code 0}.
     *
     * <p>It has to beat the native target goals of Ice and Fire ({@code DragonAITarget} at 6 and
     * {@code DragonAITargetNonTamed} at 5), because a goal keeps the flag it declared while it runs - so this one, and
     * only this one, decides who a contracted dragon may fight.
     */
    static final int TARGET_GOAL_PRIORITY = 0;

    /**
     * The priority of the move goal of a contract dragon inside the goal selector: {@code 3}.
     *
     * <p>Above the wander of Ice and Fire (7) and above its melee goal (5), so a march really is walked, and below the
     * float goal it adds at 0 and the sit goal at 1, which stay exactly where that mod put them.
     */
    static final int MOVE_GOAL_PRIORITY = 3;

    /**
     * The priority of the goal that keeps a contract dragon at its post or beside its queen: {@code 2}.
     *
     * <p>It is above the marching goal ({@value #MOVE_GOAL_PRIORITY}) so a dragon that is at its post or beside its
     * queen looks after that first, and the two never run at once anyway (an errand of the sceptre and a stance are
     * exclusive states of the contract).
     */
    static final int STANCE_GOAL_PRIORITY = 2;

    /** One spark of soul fire every this many ticks, and never more (see {@link #soulSparks}). */
    private static final int SOUL_SPARK_TICKS = 10;

    private IceAndFireBridge()
    {
    }

    /**
     * Builds a bone dragon exactly the way Ice and Fire's own world feature builds one
     * ({@code com.github.alexthe666.iceandfire.world.feature.SpawnDragonSkeleton}), which is the whole reason the
     * command does not write raw NBT: the four steps below are that feature, line for line.
     *
     * <pre>
     * dragon.growDragon(ageDays);            // age, size and attributes of that age
     * dragon.modelDeadProgress = 20.0F;      // the collapsed pose, already fully applied
     * dragon.setModelDead(true);             // Ice and Fire's corpse flag
     * dragon.setDeathStage(ageDays / 5 / 2); // exactly the bone threshold of isSkeletal()
     * </pre>
     *
     * <p>{@code deathStage = ageInDays / 5 / 2} is not a coincidence: {@code isSkeletal()} is
     * {@code getDeathStage() >= getAgeInDays() / 5 / 2}, so the value the world feature writes is the <b>first</b>
     * value at which Ice and Fire calls a corpse a skeleton - the same dragon a player would get by harvesting a
     * corpse down to its bones and no further.
     */
    static ProbeReport spawnBoneDragon(ServerPlayer player, String type, int ageDays)
    {
        List<String> refusal = new ArrayList<>();
        EntityDragonBase dragon = spawnCorpse(player, type, ageDays, refusal);

        if (dragon == null)
        {
            return ProbeReport.failure(refusal);
        }

        List<String> lines = new ArrayList<>();
        lines.add("spawned " + describe(dragon) + " in front of " + player.getGameProfile().getName());
        lines.addAll(state(dragon));

        boolean boneDragon = dragon.isModelDead() && dragon.isSkeletal() && !dragon.isRemoved() && dragon.isPickable();
        lines.add(boneDragon ? "verdict: a real Ice and Fire bone dragon"
                : "verdict: WRONG - this is not the corpse state the round describes");

        return new ProbeReport(boneDragon, lines);
    }

    /**
     * The whole chain of the <b>awakened bone dragon</b> debug item: a bone dragon in front of that player, woken up,
     * and put under the contract of that player.
     *
     * <p>It is three steps and <b>no new rule</b>: the corpse is built by the very same recipe the probe command uses
     * ({@link #spawnCorpse}), the waking is the very same {@link #revive} the skill uses, and the contract is the very
     * same {@link #startContract} - so an item can never grow a rule of its own, can never fake a permanent ownership,
     * and is refused by exactly the same checks as every other entrance (already woken, not a bone dragon, no Ice and
     * Fire, no server level).
     *
     * @return what happened, in the same lines the probe command reports
     */
    static ProbeReport spawnAwakenedDragon(ServerPlayer player, String type, int ageDays, int durationTicks)
    {
        List<String> refusal = new ArrayList<>();
        EntityDragonBase dragon = spawnCorpse(player, type, ageDays, refusal);

        if (dragon == null)
        {
            return ProbeReport.failure(refusal);
        }

        ProbeReport contract = startContract(player, dragon, durationTicks);

        List<String> lines = new ArrayList<>();
        lines.add("spawned " + describe(dragon) + " in front of " + player.getGameProfile().getName());
        lines.addAll(contract.lines());

        return new ProbeReport(contract.ok(), lines);
    }

    /**
     * Builds one bone dragon in front of that player, exactly the way Ice and Fire's own world feature builds one, and
     * adds it to the level as a corpse.
     *
     * <p>The four steps below are that feature, line for line (see the class comment of {@code SpawnDragonSkeleton}),
     * and the only difference is the position it is built at: the probe and the debug items put it in front of the
     * player instead of on a random spot of a generated world.
     *
     * @param refusal a list of one line, filled in when there is no dragon to return
     * @return the corpse, or null when the level, the type or the age was refused
     */
    @Nullable
    private static EntityDragonBase spawnCorpse(ServerPlayer player, String type, int ageDays, List<String> refusal)
    {
        if (!(player.level() instanceof ServerLevel level))
        {
            refusal.add("the probe needs a server level");

            return null;
        }

        if (ageDays < IceAndFireSupport.MIN_AGE_DAYS || ageDays > IceAndFireSupport.MAX_AGE_DAYS)
        {
            refusal.add("age " + ageDays + " is out of range, use "
                    + IceAndFireSupport.MIN_AGE_DAYS + ".." + IceAndFireSupport.MAX_AGE_DAYS);

            return null;
        }

        EntityDragonBase dragon = create(level, type);

        if (dragon == null)
        {
            refusal.add("unknown dragon type '" + type + "', use " + String.join(", ", IceAndFireSupport.DRAGON_TYPES));

            return null;
        }

        Vec3 where = spawnPosition(level, player);
        dragon.moveTo(where.x, where.y, where.z, player.getYRot() + 180.0F, 0.0F);

        // --- the four steps of SpawnDragonSkeleton, in its own order ---------------------------------------------
        dragon.growDragon(ageDays);

        if (dragon.getAgeInDays() < ageDays)
        {
            // growDragon refuses to work while Ice and Fire has aging switched off for a dragon. A corpse with age
            // zero would be deleted by the very rule this prototype relies on, so a silent failure is not an option.
            refusal.add("the game refused to age the new dragon (isAgingDisabled), its age is "
                    + dragon.getAgeInDays() + " instead of " + ageDays);

            return null;
        }

        dragon.modelDeadProgress = 20.0F;
        dragon.setModelDead(true);
        dragon.setDeathStage(dragon.getAgeInDays() / 5 / 2);
        dragon.setYRot(level.getRandom().nextFloat() * 360.0F);

        level.addFreshEntity(dragon);
        // -------------------------------------------------------------------------------------------------------

        return dragon;
    }

    /** @return the dragon of that name, or null when the name is not one of the three. */
    @Nullable
    private static EntityDragonBase create(ServerLevel level, String type)
    {
        EntityType<? extends EntityDragonBase> entityType = switch (type)
        {
            case "fire" -> IafEntityRegistry.FIRE_DRAGON.get();
            case "ice" -> IafEntityRegistry.ICE_DRAGON.get();
            case "lightning" -> IafEntityRegistry.LIGHTNING_DRAGON.get();
            default -> null;
        };

        return entityType == null ? null : entityType.create(level);
    }

    /**
     * @return where a probe dragon should stand: the point in front of the player, moved to the nearest place with room
     *         for a dragon and ground under it - or, when there is none, the aimed point itself, so that a probe never
     *         fails without saying anything.
     */
    private static Vec3 spawnPosition(ServerLevel level, ServerPlayer player)
    {
        Vec3 aim = player.getEyePosition().add(player.getViewVector(1.0F).scale(SPAWN_DISTANCE));
        BlockPos base = BlockPos.containing(aim);

        for (int dy = SPAWN_SEARCH_UP; dy >= -SPAWN_SEARCH_DOWN; dy--)
        {
            BlockPos candidate = base.offset(0, dy, 0);

            if (hasRoom(level, candidate))
            {
                return Vec3.atBottomCenterOf(candidate);
            }
        }

        return aim;
    }

    /** @return true when two blocks are free there and something solid is below them. */
    private static boolean hasRoom(ServerLevel level, BlockPos pos)
    {
        return free(level, pos) && free(level, pos.above()) && !free(level, pos.below());
    }

    /** @return true when nothing solid occupies that block. */
    private static boolean free(ServerLevel level, BlockPos pos)
    {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Reports everything about the creature under the crosshair: what it is, the Ice and Fire state, the vanilla state,
     * the command and movement state, and whether HungryCraft has woken it up.
     */
    static ProbeReport inspect(Entity pointed)
    {
        List<String> lines = new ArrayList<>();
        lines.add("entity: " + EntityType.getKey(pointed.getType()) + " class=" + pointed.getClass().getName()
                + " uuid=" + pointed.getUUID() + " at " + formatVec(pointed.position()));

        if (!(pointed instanceof EntityDragonBase dragon))
        {
            // A perfectly normal answer, and the one the round asks for by name: the debug command is allowed to say
            // what it sees instead of pretending the crosshair was empty.
            lines.add("Not an Ice and Fire dragon.");
            return ProbeReport.failure(lines);
        }

        lines.add("dragon: " + describe(dragon));
        lines.addAll(state(dragon));
        return ProbeReport.success(lines);
    }

    /**
     * Wakes one bone dragon up, in place.
     *
     * <p>The entity is never replaced: the same {@link Entity}, the same UUID, the same age, the same stage, the same
     * variant, the same gender, the same death stage and the same owner stay exactly where they were, and only two
     * fields of the animal change - the corpse flag Ice and Fire calls {@code isModelDead} and the health. Everything
     * else in this method is housekeeping for a body that is about to move again.
     */
    static ProbeReport revive(ServerPlayer player, Entity pointed)
    {
        String refusal = refusalFor(pointed);

        if (refusal != null)
        {
            return ProbeReport.failure(refusal);
        }

        EntityDragonBase dragon = (EntityDragonBase) pointed;
        Identity before = Identity.of(dragon);

        NecromancyMarker.mark(dragon, player.getUUID(), dragon.getCommand(), dragon.getHealth());

        // --- the whole revival: take the corpse flag away, give the health back -------------------------------
        dragon.setModelDead(false);
        dragon.setHealth(dragon.getMaxHealth());
        // -----------------------------------------------------------------------------------------------------

        // Housekeeping of a body that is about to be an animal again. None of it is a state of the dragon itself.
        dragon.getNavigation().stop();
        dragon.setTarget(null);
        dragon.setFlying(false);
        dragon.setHovering(false);
        dragon.ejectPassengers();

        Identity after = Identity.of(dragon);

        List<String> lines = new ArrayList<>();
        lines.add("revived " + describe(dragon));
        lines.addAll(state(dragon));
        lines.add(before.equals(after) ? "identity: untouched (" + before.describe() + ")"
                : "identity: CHANGED " + before.describe() + " -> " + after.describe());

        boolean awake = !dragon.isModelDead() && dragon.getHealth() > 0.0F && dragon.isAlive()
                && dragon.isEffectiveAi();
        lines.add(awake ? "verdict: the same entity is awake and Ice and Fire's AI is running again"
                : "verdict: the dragon did not wake up, read the lines above");

        // The client can not read the marker - persistent data never travels - so the server says it out loud, to
        // everybody who can see the dragon. That is what keeps it looking like the skeleton it is.
        broadcastRevived(dragon, true);

        return new ProbeReport(awake && before.equals(after), lines);
    }

    /**
     * The waking up of {@link #revive} with the flash of a lightning bolt in front of it, which is the shape the
     * formal skill of the next round will have.
     *
     * <p>The bolt is struck first and only after every rule of the waking has been checked, so a refused command
     * never throws a bolt at a corpse it then leaves alone.
     */
    static ProbeReport lightningRevive(ServerPlayer player, Entity pointed)
    {
        String refusal = refusalFor(pointed);

        if (refusal != null)
        {
            return ProbeReport.failure(refusal);
        }

        EntityDragonBase dragon = (EntityDragonBase) pointed;
        Vec3 where = dragon.position();
        boolean struck = player.level() instanceof ServerLevel level && VisualLightning.strike(level, where);

        ProbeReport revived = revive(player, dragon);

        List<String> lines = new ArrayList<>(revived.lines());
        lines.add(struck
                ? "visual lightning struck at " + formatVec(where) + " (visualOnly: no damage, no fire, no weather)"
                : "WARNING: the game refused to build the lightning bolt");

        return new ProbeReport(revived.ok(), lines);
    }

    /** @return true when that creature is a bone dragon this mod could take under contract right now. */
    static boolean isBoneDragon(Entity entity)
    {
        return refusalFor(entity) == null;
    }

    /** The key the type of the remains is written under, so the restore below never has to guess it. */
    private static final String BONE_TYPE_KEY = "HungryCraftBoneType";

    /**
     * The exact remains of that bone dragon (round 7C, the rising).
     *
     * <p>It is the whole state of the corpse - its saved tag - plus the key of its own entity type, so that the soul
     * fire dragon raised from it can carry it and hand it back at the end of the contract without this class having to
     * remember anything between two calls (a server restart in the middle of a contract must not lose the corpse).
     *
     * @return the remains, or null when that creature is not a bone dragon this mod may raise
     */
    @Nullable
    static IceAndFireSupport.BoneRecord boneRecordOf(Entity pointed)
    {
        if (refusalFor(pointed) != null)
        {
            return null;
        }

        EntityDragonBase dragon = (EntityDragonBase) pointed;
        CompoundTag state = new CompoundTag();
        dragon.saveWithoutId(state);
        state.putString(BONE_TYPE_KEY, EntityType.getKey(dragon.getType()).toString());

        String kind = EntityType.getKey(dragon.getType()).getPath();
        String origin = kind.replace("_dragon", "").toUpperCase(java.util.Locale.ROOT);

        return new IceAndFireSupport.BoneRecord(origin, dragon.getDragonStage(), state);
    }

    /**
     * Lays the remains of an ended contract back down (round 7C).
     *
     * <p>The entity is rebuilt from its own saved state, with <b>the very identity it was taken with</b>: the same
     * uuid, the same type, the same stage, the same corpse flags. Nothing of this mod is left on it, which is the
     * promise of the round - an ice bone dragon that was raised and whose contract ran out is an ice bone dragon again,
     * not a fire one and not a new one.
     *
     * @return true when the remains are standing in the world again
     */
    static boolean restoreBone(ServerLevel level, @Nullable CompoundTag state, UUID identity, Vec3 where, float yaw)
    {
        if (state == null)
        {
            return false;
        }

        Optional<EntityType<?>> type = EntityType.byString(state.getString(BONE_TYPE_KEY));

        if (type.isEmpty())
        {
            NecromancyDiagnostics.log("remains could not be laid down: unknown entity type '"
                    + state.getString(BONE_TYPE_KEY) + "'");

            return false;
        }

        Entity restored = type.get().create(level);

        if (restored == null)
        {
            NecromancyDiagnostics.log("remains could not be laid down: the game refused to build "
                    + state.getString(BONE_TYPE_KEY));

            return false;
        }

        restored.load(state);
        restored.setUUID(identity);
        restored.moveTo(where.x, where.y, where.z, yaw, 0.0F);

        boolean added = level.addFreshEntity(restored);

        NecromancyDiagnostics.log("remains laid down: type=" + state.getString(BONE_TYPE_KEY) + " uuid=" + identity
                + " at " + formatVec(where) + " (added=" + added + ")");

        return added;
    }

    /**
     * The debug entrance of round 7C: a bone dragon in front of that player, raised into a soul fire dragon of his own.
     *
     * <p>It is deliberately the same two steps the skill takes - build the remains, then raise them - so the debug item,
     * the debug command and the skill can never disagree about what a revived bone dragon is.
     */
    static ProbeReport spawnSoulfireDragon(ServerPlayer player, String type, int ageDays, int durationTicks)
    {
        List<String> refusal = new ArrayList<>();
        EntityDragonBase corpse = spawnCorpse(player, type, ageDays, refusal);

        if (corpse == null)
        {
            return ProbeReport.failure(refusal.isEmpty() ? List.of("the remains could not be built") : refusal);
        }

        SoulfireRising.Rising rising = SoulfireRising.raise(player, corpse);
        List<String> lines = new ArrayList<>(refusal);
        lines.addAll(rising.report().lines());

        return new ProbeReport(rising.report().ok(), lines);
    }

    /** @return why that creature can not be woken right now, or null when it can. */
    @Nullable
    private static String refusalFor(Entity pointed)
    {
        if (!(pointed instanceof EntityDragonBase dragon))
        {
            return "Not an Ice and Fire dragon.";
        }

        if (!(dragon.isModelDead() && dragon.isSkeletal() && !dragon.isRemoved()))
        {
            return "refused: that dragon is not a bone dragon (isModelDead=" + dragon.isModelDead()
                    + " isSkeletal=" + dragon.isSkeletal() + " isRemoved=" + dragon.isRemoved() + ")";
        }

        if (NecromancyMarker.isRevived(dragon))
        {
            return "refused: HungryCraft already revived this dragon";
        }

        return null;
    }

    /** Tells everybody who can see that dragon what its marker says now. */
    private static void broadcastRevived(EntityDragonBase dragon, boolean revived)
    {
        HungryCraftNetwork.sendToTrackingEntity(new S2CNecromancyStatePacket(dragon.getId(), revived), dragon);

        // And the owner of the contract is told the fuller answer, which is what her inspect panel draws: the skeleton
        // texture is public, the sheet of a contract is hers.
        if (revived)
        {
            NecromancyService.resendState(dragon);
        }
    }

    /** Tells one player the same thing, because that player just started to see the dragon. */
    static void resyncTracking(ServerPlayer player, Entity target)
    {
        if (target instanceof EntityDragonBase dragon && NecromancyMarker.isRevived(dragon))
        {
            HungryCraftNetwork.sendToPlayer(new S2CNecromancyStatePacket(dragon.getId(), true), player);

            // A queen who just came back to her own skeleton is given its whole sheet again, so the inspect panel is
            // right the moment she sees it.
            if (NecromancyService.isContractOwner(dragon, player))
            {
                NecromancyService.resendState(dragon);
            }
        }
    }

    /**
     * The formal skill of the round: wakes one bone dragon and puts it under a contract of that player.
     *
     * <p>Everything the debug command of the prototype already proved is reused here without a single change - the
     * same validation, the same waking up, the same visual lightning, the same identity - and the only new thing is
     * the contract written beside it: an absolute deadline in game time, the aging flag the dragon had before, and an
     * entry in the index of the level so that the clock of the world finds this dragon every tick without ever
     * scanning for it.
     */
    static ProbeReport startContract(ServerPlayer player, Entity pointed, int durationTicks)
    {
        String refusal = refusalFor(pointed);

        if (refusal != null)
        {
            return ProbeReport.failure(refusal);
        }

        if (!(player.level() instanceof ServerLevel level))
        {
            return ProbeReport.failure("the contract needs a server level");
        }

        EntityDragonBase dragon = (EntityDragonBase) pointed;
        long now = level.getGameTime();
        boolean agingWasDisabled = dragon.isAgingDisabled();
        Vec3 where = dragon.position();
        boolean struck = VisualLightning.strike(level, where);

        ProbeReport revived = revive(player, dragon);

        if (!revived.ok())
        {
            return revived;
        }

        NecromancyContract contract = NecromancyContract.started(player.getUUID(), now, durationTicks,
                agingWasDisabled);
        contract.write(dragon);
        NecromancyIndex.get(level).put(dragon.getUUID(), contract.expiresAt());

        // Both of these were given back when the corpse was harvested away; a woken skeleton does not grow and does
        // not court anything. The love timer is <b>zeroed</b> and not "set to no cause": Animal#setInLove(null) is
        // what the game writes for an animal that was just bred, and it is followed by hearts every ten ticks for six
        // hundred ticks - which is exactly the little cloud a bone dragon threw the first time it was revived. The
        // quiet half of that pair is Animal#resetLove.
        dragon.setAgingDisabled(true);
        dragon.resetLove();

        NecromancyDiagnostics.log("contract created: dragon=" + dragon.getUUID() + " type="
                + EntityType.getKey(dragon.getType()) + " stage=" + dragon.getDragonStage()
                + " owner=" + player.getUUID() + " expiresAt=" + contract.expiresAt()
                + " (" + durationTicks + " ticks)");

        List<String> lines = new ArrayList<>(revived.lines());
        lines.add("contract: owner=" + player.getUUID() + " started=" + now + " expires=" + contract.expiresAt()
                + " (" + durationTicks + " ticks)");
        lines.add(struck
                ? "visual lightning struck at " + formatVec(where) + " (visualOnly: no damage, no fire, no weather)"
                : "WARNING: the game refused to build the lightning bolt");

        return new ProbeReport(true, lines);
    }

    /** Keeps the Ice and Fire half of a live contract in order, once per tick. */
    static void enforceContract(LivingEntity dragon)
    {
        if (!(dragon instanceof EntityDragonBase base))
        {
            return;
        }

        if (!base.isAgingDisabled())
        {
            base.setAgingDisabled(true);
        }

        if (base.isInLove())
        {
            // Nothing under contract is allowed to breed. The flag is <b>cleared</b> and not "set to no cause": the
            // Animal#setInLove(null) of the game puts the timer at six hundred ticks and the animal then throws hearts
            // of its own every ten ticks - which is exactly the cloud of hearts a contract dragon used to sit in.
            // Animal#resetLove only zeroes the timer, so the skeleton is simply not in the mood.
            base.resetLove();
        }

        // And the skeleton does not rest <b>by itself</b>. Ice and Fire sends a dragon to sleep by day (fire and ice)
        // or by night (lightning), and it writes that state into the sleeping flag the game keeps for a "sitting
        // pose" - which is also the flag the mod asks before it lets the animal move (canMove is
        // !isOrderedToSit && !isSleeping && !ridden && sleepProgress == 0), and which is the very pose a holding
        // dragon is ordered into.
        //
        // The two are told apart here by the order of the contract: a skeleton that is not posted is put back on its
        // feet every tick, and one that is <b>on its post</b> is left lying, because lying down is exactly what its
        // order asked of it - NecromancyStanceGoal writes both the command and that pose, and clearing it here was why
        // a posted dragon looked like a dragon that had received no order at all.
        boolean posted = NecromancyOrders.stanceOf(base) == SpiderOrderMode.HOLD;

        if (!posted && base.isSleeping())
        {
            base.setInSittingPose(false);
        }

        if (!posted && base.sleepProgress != 0.0F)
        {
            base.sleepProgress = 0.0F;
        }

        keepGoals(base);
        soulSparks(base);

        if (NecromancyDiagnostics.enabled())
        {
            // The watcher of round 6: with the switch on, every real change of the state of a dragon is written down,
            // with the whole state in the line. Together with the "wrote …" lines of every decision of this
            // compatibility (see NecromancyDiagnostics#write) it answers the one question a control-contention hunt has
            // to answer - who changed this - without a debugger: a change that no "wrote" line precedes was made by
            // somebody else, and in this world that somebody else is Ice and Fire's own AI.
            NecromancyDiagnostics.stateChanged(base, describeState(base));
        }
    }

    /**
     * The look of a woken dragon (Necromancy IV round): a few sparks of soul fire around the bones.
     *
     * <p>It replaces the hearts that the previous round produced by accident (see {@link #enforceContract}) - the same
     * kind of quiet, continuous ambience, in the colour of a soul torch instead of the colour of a courtship, and
     * deliberately thin: one spark every {@value #SOUL_SPARK_TICKS} ticks, so a valley full of contracts is still a
     * valley and not a particle demo.
     */
    private static void soulSparks(EntityDragonBase dragon)
    {
        if (dragon.tickCount % SOUL_SPARK_TICKS != 0 || !(dragon.level() instanceof ServerLevel level))
        {
            return;
        }

        double spread = dragon.getBbWidth() * 0.5D;

        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, dragon.getX() + (level.random.nextDouble() - 0.5D) * spread,
                dragon.getY() + level.random.nextDouble() * dragon.getBbHeight(),
                dragon.getZ() + (level.random.nextDouble() - 0.5D) * spread, 1, 0.0D, 0.0D, 0.0D, 0.005D);
    }

    /**
     * Installs the two goals of a dragon under contract (Necromancy IV round).
     *
     * <p>Both are added at a priority that wins the flag they need against the native goals of Ice and Fire, and both
     * are only ever installed while a contract lives:
     *
     * <ul>
     * <li>{@link NecromancyTargetGoal} at priority {@value #TARGET_GOAL_PRIORITY} in the <b>target</b> selector: it
     * holds the target flag, so {@code DragonAITarget} (6) and {@code DragonAITargetNonTamed} (5) can never start -
     * which is what keeps the queen, her spiders and her other contract dragons out of the claws of her own skeleton,</li>
     * <li>{@link NecromancyMoveGoal} at priority {@value #MOVE_GOAL_PRIORITY} in the <b>goal</b> selector: above the
     * wander of the mod (7) and above its melee goal (5), so a march and a bombardment really are walked, with the
     * native navigation and the native flight decision of that mod.</li>
     * </ul>
     *
     * <p>A goal of Ice and Fire itself is never removed, moved or replaced here: the contract only ever adds these two
     * and takes them off again when it ends, so an ordinary dragon - and every dragon the moment its contract is over -
     * behaves exactly as that mod made it behave.
     */
    static void keepGoals(LivingEntity dragon)
    {
        if (!(dragon instanceof Mob minion))
        {
            return;
        }

        boolean installed = false;

        if (!hasGoal(minion.targetSelector, NecromancyTargetGoal.class))
        {
            minion.targetSelector.addGoal(TARGET_GOAL_PRIORITY, new NecromancyTargetGoal(minion));
            installed = true;
        }

        if (!hasGoal(minion.goalSelector, NecromancyStanceGoal.class))
        {
            minion.goalSelector.addGoal(STANCE_GOAL_PRIORITY, new NecromancyStanceGoal(minion));
            installed = true;
        }

        if (!hasGoal(minion.goalSelector, NecromancyMoveGoal.class))
        {
            minion.goalSelector.addGoal(MOVE_GOAL_PRIORITY, new NecromancyMoveGoal(minion));
            installed = true;
        }

        if (!NATIVE_GOALS.containsKey(minion))
        {
            // The isolation, once per contract: the autonomous movement and target goals come off, and the snapshot
            // remembers them for the end of the contract (Necromancy IV round 7).
            isolateNativeGoals(minion);
        }

        if (installed)
        {
            // Said once per installation, and an installation happens once per dragon per contract (the scan above is
            // what makes it idempotent), so this line can not flood a log.
            NecromancyDiagnostics.log("goals installed for " + minion.getUUID() + ": target=" + TARGET_GOAL_PRIORITY
                    + " (target flag), stance=" + STANCE_GOAL_PRIORITY + ", move=" + MOVE_GOAL_PRIORITY + " (move flag)");
        }
    }

    /** Takes the goals of the contract off a dragon again, which is what ending one does. */
    static void dropGoals(LivingEntity dragon)
    {
        if (!(dragon instanceof Mob minion))
        {
            return;
        }

        removeGoal(minion.targetSelector, NecromancyTargetGoal.class);
        removeGoal(minion.goalSelector, NecromancyStanceGoal.class);
        removeGoal(minion.goalSelector, NecromancyMoveGoal.class);

        // The reverse of the isolation: the goals Ice and Fire installed are given back at their original priorities,
        // so the dragon is exactly the creature that mod made it again (Necromancy IV round 7).
        NativeGoalSnapshot snapshot = NATIVE_GOALS.remove(minion);

        if (snapshot != null)
        {
            NecromancyService.restoreGoals(minion.goalSelector, snapshot.movement());
            NecromancyService.restoreGoals(minion.targetSelector, snapshot.target());
            NecromancyDiagnostics.log("native AI restored for " + minion.getUUID() + ": " + snapshot.movement().size()
                    + " movement goals, " + snapshot.target().size() + " target goals");
        }
    }

    /** The command of Ice and Fire that means "lie down and stay": the sit of a tamed dragon. */
    private static final int SIT_COMMAND = 1;

    /** And the one that means "on your feet". */
    private static final int STAND_COMMAND = 0;

    /**
     * The post pose of a contract dragon: down on its anchor, or back on its feet.
     *
     * <p><b>It takes two writes, and the round that wrote only one of them is why HOLD looked like no order at all.</b>
     * Ice and Fire keeps three states that all mean "lying down": the command of the animal ({@code command == 1}, which
     * its own logic mirrors into {@code isOrderedToSit} every tick), the sleeping flag the game keeps for a "sitting
     * pose" ({@code setInSittingPose}, which is what {@code isSleeping} answers and what raises {@code sleepProgress} to
     * twenty), and the ramp itself. For a <b>tamed</b> dragon the pose is written by the vanilla
     * {@code SitWhenOrderedToGoal}, but that goal starts with {@code if (!isTame()) return false} - a skeleton under a
     * contract is never tamed, so nothing in the game ever lays it down, and the command alone left it standing (while
     * {@code canMove} was already false, so it could not even walk away). Both halves are therefore written here, and
     * standing up clears both as well: a ramp that is only told to fall would leave the dragon unable to move for the
     * second it takes to fall.
     *
     * <p>The command the dragon had before the contract is written in its marker and is given back when the contract
     * ends.
     */
    static void setSitting(Entity dragon, boolean sitting)
    {
        if (dragon instanceof EntityDragonBase base)
        {
            int wanted = sitting ? SIT_COMMAND : STAND_COMMAND;

            if (base.getCommand() != wanted)
            {
                base.setCommand(wanted);
                NecromancyDiagnostics.write(base, "command=" + wanted, "post pose (sit/stand)");
            }

            if (base.isInSittingPose() != sitting)
            {
                base.setInSittingPose(sitting);
                NecromancyDiagnostics.write(base, "sitPose=" + sitting, "post pose (sit/stand)");
            }

            if (!sitting && base.sleepProgress != 0.0F)
            {
                base.sleepProgress = 0.0F;
            }
        }
    }

    /** @return true when a goal of that kind is already installed, which is what makes the installation idempotent. */
    private static boolean hasGoal(GoalSelector selector, Class<? extends Goal> type)
    {
        for (WrappedGoal wrapped : selector.getAvailableGoals())
        {
            if (type.isInstance(wrapped.getGoal()))
            {
                return true;
            }
        }

        return false;
    }

    /** Removes every goal of that kind from that selector, and nothing else. */
    private static void removeGoal(GoalSelector selector, Class<? extends Goal> type)
    {
        Goal found = null;

        for (WrappedGoal wrapped : selector.getAvailableGoals())
        {
            if (type.isInstance(wrapped.getGoal()))
            {
                found = wrapped.getGoal();
            }
        }

        if (found != null)
        {
            selector.removeGoal(found);
        }
    }

    /**
     * The native goals a contract took off a dragon, so they can be given back the moment the contract ends
     * (Necromancy IV round 7, the isolation).
     *
     * <p>Weak keys: an entity that is unloaded while contracted simply drops its entry with the garbage collector, and
     * the fresh entity of the reloaded chunk gets a fresh snapshot on its next contract tick - so an unload can never
     * leak a goal list, and a reload can never restore a goal twice.
     */
    private record NativeGoalSnapshot(List<NecromancyService.RemovedGoal> movement, List<NecromancyService.RemovedGoal> target)
    {
    }

    private static final Map<Mob, NativeGoalSnapshot> NATIVE_GOALS = new WeakHashMap<>();

    /**
     * Takes the autonomous goals off a contract dragon exactly once per contract (Necromancy IV round 7).
     *
     * <p><b>Idempotent</b> by the {@link #NATIVE_GOALS} guard in {@link #keepGoals}: the removal below runs only for a
     * mob that has no snapshot yet, so an entity loaded twice, a chunk reloaded and a renewed contract never remove or
     * install anything twice. What is removed is deliberate and small:
     *
     * <ul>
     * <li>the four movement goals that can make a dragon move without an order ({@code DragonAIMate},
     * {@code DragonAIReturnToRoost}, {@code DragonAIEscort}, {@code DragonAIWander}),</li>
     * <li>all six target goals, so nothing ever sets a target on its own ({@code OwnerHurtTargetGoal},
     * {@code OwnerHurtByTargetGoal}, {@code HurtByTargetGoal}, {@code DragonAITargetNonTamed},
     * {@code DragonAITarget}, {@code DragonAITargetItems}).</li>
     * </ul>
     *
     * <p><b>Kept, on purpose</b>: {@code SitWhenOrderedToGoal} and {@code DragonAIAttackMelee} - the first only reacts
     * to {@code isOrderedToSit}, which this mod writes through the command, and the second only follows a target that
     * now only the controller of the contract can set; together they are the compliant executors of HOLD and of the
     * melee half of ATTACK, and removing them would remove the very behaviours this round has to keep. The two look
     * goals ({@code DragonAIWatchClosest}, {@code DragonAILookIdle}) write nothing but head rotation.
     */
    private static void isolateNativeGoals(Mob minion)
    {
        List<NecromancyService.RemovedGoal> movement = NecromancyService.detachGoals(minion.goalSelector,
                DragonAIMate.class, DragonAIReturnToRoost.class, DragonAIEscort.class, DragonAIWander.class);
        List<NecromancyService.RemovedGoal> target = NecromancyService.detachGoals(minion.targetSelector,
                OwnerHurtTargetGoal.class, OwnerHurtByTargetGoal.class, HurtByTargetGoal.class,
                DragonAITargetNonTamed.class, DragonAITarget.class, DragonAITargetItems.class);

        NATIVE_GOALS.put(minion, new NativeGoalSnapshot(movement, target));

        NecromancyDiagnostics.log("native AI isolated for " + minion.getUUID() + ": removed " + movement.size()
                + " movement goals and " + target.size() + " target goals (wander, escort, roost, mate and every"
                + " autonomous target source); the sit goal and the melee executor stay");
    }

    /**
     * Everything a bombardment needs from the dragon itself (Necromancy IV round).
     *
     * <p>The stream of a breath is the native one, and that is the whole point of it: nothing here builds a fireball or
     * a fake target. {@code stimulateFire(x, y, z, 1)} of the dragon is the very call its own AI makes, so the
     * particles, the damage, the terrain transformation, the configuration of Ice and Fire
     * ({@code IafConfig.dragonAttackDamageFire} and the rest) and the client sync all stay exactly what that mod
     * defines. The one thing added on top is the muzzle ramp, which that mod advances while
     * {@code isBreathingFire()} is true.
     *
     * @return true when the dragon really breathed this tick, false when it is too young for it
     */
    static boolean breatheAt(Entity dragon, Vec3 target)
    {
        if (!(dragon instanceof EntityDragonBase base) || base.getDragonStage() < BREATH_STAGE)
        {
            return false;
        }

        base.setBreathingFire(true);
        base.stimulateFire(target.x, target.y, target.z, 1);

        return true;
    }

    /** @return true when that dragon is old enough (stage {@value #BREATH_STAGE}) for a bombardment. */
    static boolean canBreathe(Entity dragon)
    {
        return dragon instanceof EntityDragonBase base && base.getDragonStage() >= BREATH_STAGE;
    }

    /** Puts one contract dragon into the hovering state a bombardment breathes from. */
    static void hover(Entity dragon)
    {
        if (dragon instanceof EntityDragonBase base && !base.isHovering())
        {
            base.setHovering(true);
            NecromancyDiagnostics.write(base, "hover=true", "bombard (hover over the point)");
        }
    }

    /**
     * How far away a destination of the sceptre may be before a contract dragon takes off instead of walking to it
     * (Necromancy IV round, the marching fix).
     *
     * <p>The round asked for exactly this rule: close by, the skeleton <b>walks</b> - the ground navigation of the game
     * with its own path finder, its own doors, its own climbing of a single block - and far away it <b>flies</b>: up
     * into the air, straight across whatever is in the way, and down onto the ground at the destination. Twenty four
     * blocks is where a walk usually stops being a walk and starts being a detour.
     */
    static final double FLY_DISTANCE = 24.0D;

    /**
     * How many times in a row the contract may ask a dragon to fly before it stops asking, and stops asking for how long
     * (Necromancy IV round 6).
     *
     * <p><b>Why this exists.</b> Ice and Fire owns the flight of its own dragons: its logic writes {@code setFlying} and
     * {@code setHovering} from twenty two decision sites and may put a dragon down again that the contract has just sent
     * into the air. A contract that simply asked again on every repath then produced exactly the symptom of this round's
     * report - a skeleton that clawed its way up and was pushed down again, on and on ("unlimited take-off and landing",
     * which the design of the round forbids). So the asks are counted: after three in a row that did not leave the
     * dragon airborne, the contract gives up asking for {@value #FLIGHT_GIVE_UP_TICKS} ticks and walks instead. A
     * refusal is not an error - it is the dragon's own answer, respected.
     */
    private static final int FLIGHT_REFUSAL_LIMIT = 3;

    /** How long the contract walks instead, after that many refusals: ten seconds. */
    private static final int FLIGHT_GIVE_UP_TICKS = 200;

    /** How long ago the last ask may be for a dragon that is on the ground again to count as a refusal. */
    private static final int FLIGHT_REFUSAL_WINDOW = 60;

    /** The vertical deadband of the straight line: climb above this error, dive below it, and level off under the stop. */
    private static final double CLIMB_START = 4.0D;

    /** The error under which the vertical control is released again (the deadband's inner edge). */
    private static final double CLIMB_STOP = 1.5D;

    /** How far below a destination the ground under it is looked for, in blocks. */
    private static final int GROUND_SEARCH = 12;

    /**
     * The dragons whose flight <b>the contract</b> started, and the bookkeeping of that intent (Necromancy IV round 6).
     *
     * <p>It exists for two reasons: so that the contract can <b>finish</b> a flight it began (see
     * {@link #contractFlight}) instead of leaving a skeleton in the air with nobody driving it, and so that it can tell
     * "I asked three times and it landed again" from "it never wanted to fly" (see {@link #noteFlightAttempt}).
     *
     * <p>Weak keys, because an entity may be removed at any time and none of this is worth a leak.
     */
    private static final Set<EntityDragonBase> CONTRACT_FLIGHT = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Map<EntityDragonBase, Long> FLIGHT_ASKED_AT = new WeakHashMap<>();

    private static final Map<EntityDragonBase, Integer> FLIGHT_REFUSALS = new WeakHashMap<>();

    private static final Map<EntityDragonBase, Long> FLIGHT_GIVE_UP_UNTIL = new WeakHashMap<>();

    /** The last vertical command of the straight line of one dragon: {@code TRUE} up, {@code FALSE} down, null level. */
    private static final Map<EntityDragonBase, Boolean> VERTICAL_INTENT = new WeakHashMap<>();

    /**
     * Sends a contract dragon at a point of the world, on foot or on the wing (Necromancy IV round).
     *
     * <p>It is a decision and not a movement: the destination is handed to the navigation of the animal, and the native
     * walk, the native flight, the native path finder and the native animation of Ice and Fire do the rest - exactly as
     * they do for a dragon that escorts its owner. The one thing added here is the switch between the two:
     *
     * <ul>
     * <li><b>far away</b> - {@code setFlying(true)} first, then the destination. The mod then uses its flying navigator
     * (its own {@code useFlyingPathFinder} answers true for a dragon that flies and has no rider), and the skeleton
     * climbs out of whatever valley it was standing in,</li>
     * <li><b>close by</b> - the destination while it stands on the ground, and while it is still in the air the point
     * on the ground <b>under</b> the destination first. The flight is dropped only when it has really landed, which is
     * what keeps a march from ending in a fall,</li>
     * <li>and a dragon that already walks and is already close is left completely alone.</li>
     * </ul>
     */
    static void approach(Entity dragon, Vec3 target, double speed)
    {
        if (!(dragon instanceof EntityDragonBase base))
        {
            return;
        }

        double distance = base.position().distanceTo(target);

        if (distance >= FLY_DISTANCE && wantsToFly(base))
        {
            if (base.isFlying())
            {
                // It is flying, and it was the contract that put it there: the ask worked, so the refusal count is
                // forgotten (Necromancy IV round 6).
                FLIGHT_REFUSALS.remove(base);
            }
            else
            {
                noteFlightAttempt(base);
                takeOff(base, "approach distance=" + round(distance));
            }

            base.getNavigation().moveTo(target.x, target.y, target.z, speed);

            return;
        }

        if (base.isFlying())
        {
            Vec3 ground = groundUnder(base, target);

            base.getNavigation().moveTo(ground.x, ground.y, ground.z, speed);

            if (base.onGround())
            {
                land(base);
            }

            return;
        }

        base.getNavigation().moveTo(target.x, target.y, target.z, speed);
    }

    /**
     * @return true when the contract may still ask that dragon to fly: not while a rider holds the reins, and not during
     *         the pause after the dragon has refused a few times in a row (Necromancy IV round 6).
     */
    private static boolean wantsToFly(EntityDragonBase base)
    {
        if (base.getControllingPassenger() != null)
        {
            // A ridden dragon is flown by its rider - that is what the riding hook of round 5 establishes - and an AI
            // that keeps taking off under the hands on the controls is the one thing a passenger must never feel.
            return false;
        }

        Long giveUpUntil = FLIGHT_GIVE_UP_UNTIL.get(base);

        return giveUpUntil == null || base.level().getGameTime() >= giveUpUntil;
    }

    /**
     * Asks one contract dragon to fly, and remembers that the contract did.
     *
     * @return true when this call put it in the air, false when it was already flying
     */
    private static boolean takeOff(EntityDragonBase base, String source)
    {
        if (base.isFlying())
        {
            CONTRACT_FLIGHT.add(base);

            return false;
        }

        base.setFlying(true);
        base.setHovering(false);
        FLIGHT_ASKED_AT.put(base, base.level().getGameTime());
        CONTRACT_FLIGHT.add(base);
        NecromancyDiagnostics.write(base, "flying=true hovering=false", source);

        return true;
    }

    /**
     * Counts one more ask for flight, and gives up asking when the dragon keeps refusing it (Necromancy IV round 6).
     *
     * <p>It is the answer to the control contention this round investigated: Ice and Fire's own logic owns the flight of
     * its dragons, so the contract may ask and be refused. Three refusals in a row ({@link #FLIGHT_REFUSAL_LIMIT}) make
     * the contract walk for {@value #FLIGHT_GIVE_UP_TICKS} ticks instead of asking a fourth time - which is what turns
     * "unlimited take-off and landing" into "it walked, because it would not fly".
     */
    private static void noteFlightAttempt(EntityDragonBase base)
    {
        long now = base.level().getGameTime();
        Long askedAt = FLIGHT_ASKED_AT.get(base);

        if (askedAt != null && now - askedAt <= FLIGHT_REFUSAL_WINDOW)
        {
            int refusals = FLIGHT_REFUSALS.merge(base, 1, Integer::sum);

            if (refusals >= FLIGHT_REFUSAL_LIMIT)
            {
                FLIGHT_REFUSALS.put(base, 0);
                FLIGHT_GIVE_UP_UNTIL.put(base, now + FLIGHT_GIVE_UP_TICKS);
                NecromancyDiagnostics.write(base, "flight given up for " + FLIGHT_GIVE_UP_TICKS / 20 + "s",
                        "approach: the dragon landed again " + refusals + " times in a row, so it walks instead");

                return;
            }

            NecromancyDiagnostics.write(base, "asking again for flight (" + refusals + " refusals)",
                    "approach: the last ask left it on the ground");
        }
    }

    /** @return true when that dragon is in the air <b>because of the contract</b> and that flight is not finished yet. */
    static boolean contractFlight(Entity dragon)
    {
        return dragon instanceof EntityDragonBase base && CONTRACT_FLIGHT.contains(base) && base.isFlying()
                && !base.onGround();
    }

    /** Forgets everything the contract remembers about the flight of one dragon, when its contract ends. */
    private static void forgetFlight(EntityDragonBase base)
    {
        CONTRACT_FLIGHT.remove(base);
        FLIGHT_ASKED_AT.remove(base);
        FLIGHT_REFUSALS.remove(base);
        FLIGHT_GIVE_UP_UNTIL.remove(base);
        VERTICAL_INTENT.remove(base);
    }

    /**
     * The straight line: one bounded stretch of flight that the mod does not steer (Necromancy IV round).
     *
     * <p>It is the second answer of the round to a skeleton that would not come - "it should fly up and go straight at
     * the place" - and it is deliberately <b>not</b> a path: the vanilla move control of the animal is pointed at the
     * point, the vertical control of Ice and Fire decides whether it climbs or dives, and the physics, the drag and the
     * collision of the game carry it there. Nothing is teleported and no velocity is written by hand.
     *
     * <p>It is only ever called by a goal that has already watched the native navigation fail to make progress, and for
     * a bounded number of ticks: the navigation is the ruler of the two and this is the fallback.
     */
    static void straightLine(Entity dragon, Vec3 target, double speed)
    {
        if (!(dragon instanceof EntityDragonBase base))
        {
            return;
        }

        base.getNavigation().stop();
        takeOff(base, "straight line");
        base.getMoveControl().setWantedPosition(target.x, target.y, target.z, speed);

        double climb = target.y - base.getY();
        Boolean intent = VERTICAL_INTENT.get(base);

        // A deadband with a memory (Necromancy IV round 6). The first version of this steered the vertical control on
        // every single tick, which is a bang-bang controller: the moment the dragon crossed the height of its
        // destination the command flipped, and it flipped back on the next tick - the "rapid up and down along the Y
        // axis" the round reported. Now the climb starts at CLIMB_START blocks of error, stops under CLIMB_STOP, and
        // between the two the last command stands.
        if (climb > CLIMB_START)
        {
            intent = Boolean.TRUE;
        }
        else if (climb < -CLIMB_START)
        {
            intent = Boolean.FALSE;
        }
        else if (intent != null && Math.abs(climb) < CLIMB_STOP)
        {
            intent = null;
        }

        VERTICAL_INTENT.put(base, intent);

        boolean up = intent == Boolean.TRUE;
        boolean down = intent == Boolean.FALSE;

        if (base.isGoingUp() != up || base.isGoingDown() != down)
        {
            NecromancyDiagnostics.write(base, "vertical up=" + up + " down=" + down,
                    "straight line (height error " + round(climb) + ")");
            base.up(up);
            base.down(down);
        }
    }

    /**
     * Holds a contract dragon exactly where it is for as long as it is breathing (Necromancy IV round).
     *
     * <p>The round asks for a muzzle that does not wander: an aim that slides is an aim that misses. So the path is
     * dropped, the move control is pointed at the spot the animal already stands on, the vertical control is released
     * and the horizontal motion that was left over is zeroed. A dragon in the air keeps hovering - the native hover of
     * the mod is what holds its altitude - and one on the ground simply stands, because from there it is the game that
     * holds it.
     */
    static void holdStill(Entity dragon)
    {
        if (dragon instanceof EntityDragonBase base)
        {
            base.getNavigation().stop();
            base.getMoveControl().setWantedPosition(base.getX(), base.getY(), base.getZ(), 0.0D);
            base.up(false);
            base.down(false);

            Vec3 motion = base.getDeltaMovement();

            if (motion.x != 0.0D || motion.z != 0.0D)
            {
                base.setDeltaMovement(0.0D, motion.y, 0.0D);
            }
        }
    }

    /** Puts one contract dragon back on the ground: the flight and the hover are both given up. */
    static void land(EntityDragonBase dragon)
    {
        boolean wasFlying = dragon.isFlying();

        dragon.setFlying(false);
        dragon.setHovering(false);
        dragon.up(false);
        dragon.down(false);
        forgetFlight(dragon);

        if (wasFlying)
        {
            NecromancyDiagnostics.write(dragon, "flying=false hovering=false", "contract landing (finished)");
        }
    }

    /**
     * Interrupts whatever a contract dragon is doing, in the order Ice and Fire needs (Necromancy IV round 5).
     *
     * <p>It is the first half of the stand-down key, and it exists because a post is a <b>high priority order</b>: it
     * has to be able to take a skeleton off a march, off a bombardment, out of a breath, off a path and off a victim,
     * and only then can the second half - the native sitting pose - mean anything. Without this, a HOLD written while a
     * dragon was marching did nothing at all: the errand was still on the contract, so the goal that holds a post was
     * not allowed to run (see {@code NecromancyStanceGoal#stanceState}), and the queen saw a skeleton that ignored her.
     *
     * <p>What it writes, and why each line is there:
     *
     * <ul>
     * <li>the <b>target</b> is dropped, so the native melee and breath goals have nothing to aim at while the dragon
     * turns around,</li>
     * <li>the <b>path</b> and the <b>move control</b> are both cleared, which is what stops a body that is already
     * walking,</li>
     * <li>the <b>vertical control</b> is released ({@code up(false)/down(false)}), so a skeleton that was climbing does
     * not keep climbing,</li>
     * <li>a <b>breath</b> that is running is switched off, so the stream does not follow the new order,</li>
     * <li>and a dragon that was <b>hovering</b> is told to stop hovering, because hovering is exactly what keeps a
     * skeleton in the air for ever. Flight itself is deliberately left alone: switching {@code setFlying(false)} off
     * under a dragon that is a hundred blocks up is not a landing, it is a fall. The walk/fly steering of the contract
     * (see {@link #approach}) is what brings it down and lands it, and the sitting pose is written only once it really
     * stands on the ground.</li>
     * </ul>
     */
    static void interrupt(Entity dragon)
    {
        if (!(dragon instanceof EntityDragonBase base))
        {
            return;
        }

        base.setTarget(null);
        base.getNavigation().stop();
        base.getMoveControl().setWantedPosition(base.getX(), base.getY(), base.getZ(), 0.0D);
        base.up(false);
        base.down(false);
        VERTICAL_INTENT.remove(base);

        if (base.isBreathingFire())
        {
            base.setBreathingFire(false);
            NecromancyDiagnostics.write(base, "breathing=false", "interrupt");
        }

        if (base.isHovering())
        {
            base.setHovering(false);
            NecromancyDiagnostics.write(base, "hover=false", "interrupt");
        }

        NecromancyDiagnostics.write(base, "target=none path=stopped", "interrupt");
    }

    /** @return one line describing everything the diagnostics of this round care about on that dragon. */
    static String describeState(Entity dragon)
    {
        if (!(dragon instanceof EntityDragonBase base))
        {
            return "state of " + dragon.getUUID() + ": not an Ice and Fire dragon";
        }

        Optional<NecromancyContract> contract = NecromancyContract.of(base);
        StringBuilder state = new StringBuilder("state of ").append(base.getUUID())
                .append(": order=").append(contract.map(NecromancyContract::order).orElse(null))
                .append(" stance=").append(contract.map(NecromancyContract::stance).orElse(null))
                .append(" phase=").append(contract.map(NecromancyContract::phase).orElse(null))
                .append(" flying=").append(base.isFlying())
                .append(" hovering=").append(base.isHovering())
                .append(" onGround=").append(base.onGround())
                .append(" flying-stage=").append(base.getDragonStage())
                .append(" sleeping=").append(base.isSleeping())
                .append(" sitPose=").append(base.isInSittingPose())
                .append(" sleepProgress=").append(base.sleepProgress)
                .append(" command=").append(base.getCommand())
                .append(" breathing=").append(base.isBreathingFire())
                .append(" target=").append(base.getTarget() == null ? "none" : base.getTarget().getName().getString())
                .append(" passengers=").append(base.getPassengers().size())
                .append(" rider=").append(base.getControllingPassenger() == null ? "none"
                        : base.getControllingPassenger().getUUID().toString());

        if (NecromancyDiagnostics.enabled())
        {
            // The two questions a control-contention hunt has to answer, and the reason they are only read with the
            // switch on (Necromancy IV round 6): WHICH goal of the dragon is running right now - ours and Ice and
            // Fire's, both selectors - and in WHICH movement mode it is being moved. "FlightMoveHelper" against
            // "GroundMoveHelper" and "PlayerFlightMoveHelper" is Ice and Fire's own ground/air/ridden split, so the
            // move control names the mode without a second table, and the navigator says whether the walking navigator
            // or the flying one is in charge while a path is in progress.
            state.append(" goals=[").append(runningGoals(base)).append(']')
                    .append(" nav=").append(base.getNavigation().getClass().getSimpleName())
                    .append(base.getNavigation().isInProgress() ? "(busy)" : "(idle)")
                    .append(" moveControl=").append(base.getMoveControl().getClass().getSimpleName());
        }

        return state.toString();
    }

    /**
     * @return the simple names of every goal that is running on that dragon right now, goal selector and target
     *         selector together, or {@code none}: the answer to "which logic is driving it at this moment".
     */
    private static String runningGoals(Mob minion)
    {
        String goals = Stream.concat(minion.goalSelector.getRunningGoals(), minion.targetSelector.getRunningGoals())
                .map(goal -> goal.getGoal().getClass().getSimpleName())
                .collect(Collectors.joining(","));

        return goals.isEmpty() ? "none" : goals;
    }

    /** @return the first free spot under that point that a dragon could stand on, or the point itself. */
    private static Vec3 groundUnder(EntityDragonBase dragon, Vec3 target)
    {
        BlockPos from = BlockPos.containing(target);

        for (int depth = 0; depth <= GROUND_SEARCH; depth++)
        {
            BlockPos candidate = from.below(depth);

            if (dragon.level().isEmptyBlock(candidate) && dragon.level().isEmptyBlock(candidate.above())
                    && !dragon.level().isEmptyBlock(candidate.below()))
            {
                return Vec3.atBottomCenterOf(candidate);
            }
        }

        return target;
    }

    /** Stops the breath of that dragon, which every ending of a bombardment and every end of the contract does. */
    static void stopBreath(Entity dragon)
    {
        if (dragon instanceof EntityDragonBase base && base.isBreathingFire())
        {
            base.setBreathingFire(false);
        }
    }

    /** Plays the breath sound of that dragon, which is the one its own breath loop plays. */
    static void breathSound(Entity dragon)
    {
        if (dragon instanceof EntityDragonBase base)
        {
            base.playSound(breathSoundOf(base), 4.0F, 1.0F);
        }
    }

    /** @return the breath sound of that kind of dragon. */
    private static SoundEvent breathSoundOf(EntityDragonBase dragon)
    {
        if (dragon.dragonType == DragonType.ICE)
        {
            return IafSoundRegistry.ICEDRAGON_BREATH;
        }

        if (dragon.dragonType == DragonType.LIGHTNING)
        {
            return IafSoundRegistry.LIGHTNINGDRAGON_BREATH;
        }

        return IafSoundRegistry.FIREDRAGON_BREATH;
    }

    /** Tells the player who holds the contract of that dragon that it is too young to breathe. */
    static void warnTooYoungToBreathe(Entity dragon)
    {
        NecromancyService.contractOwner(dragon).ifPresent(
                owner -> tell(owner, dragon, "message.spiderqueen_hungrycraft.necromancy.too_young_for_breath"));
    }

    /**
     * The Ice and Fire half of ending a contract: the dragon becomes its corpse again, the aging flag it had before
     * comes back, and the contract tag is cleared.
     *
     * <p>It is idempotent: with no contract on the dragon it does nothing at all, so the two or three endings that can
     * arrive at the same dragon in the same tick (the clock, a death, an operator) produce one corpse and nothing else.
     */
    static void endContract(Entity dragon)
    {
        if (!(dragon instanceof EntityDragonBase base))
        {
            return;
        }

        Optional<NecromancyContract> contract = NecromancyContract.of(base);

        if (contract.isEmpty())
        {
            return;
        }

        // The execution half of the contract goes first, so the corpse that is made below is not walked anywhere by a
        // march that was still on the contract, and no breath is left running.
        dropGoals(base);
        stopBreath(base);

        // A dragon that was posted is stood up before it becomes a corpse: the lying pose is a flag of the entity and
        // it would outlive the contract that asked for it. The command of the animal is written back right after this
        // from its marker, so the order it had before HungryCraft ever touched it is the one it really ends with.
        setSitting(base, false);

        if (base.isInLove())
        {
            // The quiet half of Animal#setInLove: what is handed back to the world is a corpse that is not courting.
            base.resetLove();
        }

        base.setAgingDisabled(contract.get().agingWasDisabled());

        // The very restore of the prototype: same entity, same identity, corpse again. It clears the necromancy
        // marker itself, which is why it is asked before the contract tag is dropped.
        NecromancyDiagnostics.log("contract ending: dragon=" + base.getUUID()
                + " (goals removed, breath stopped, stood up, riders set down)");
        NecromancyDiagnostics.forget(base);
        forgetFlight(base);
        restore(base);
        NecromancyContract.clear(base);
    }

    /**
     * The quasi tame layer of a contract dragon (Necromancy IV round): every right click on it is decided here.
     *
     * <p><b>The rule of the round.</b> A contract dragon behaves like a summon of its queen - she rides it from stage
     * three, she feeds it meat, her spiders are its allies - but it is <b>not</b> tamed by her: the tame flag, the owner
     * of the record, the command and the equipment of Ice and Fire stay untouched. So the permissions of the tame menu
     * are handed out one by one here, and the ones that are taken away are said out loud rather than swallowed.
     *
     * @param localHit the exact point of the model a client sent, which this layer does not need: no decision of the
     *        contract depends on where on a wing a player happened to click
     * @return true when the contract decided this interaction, in which case the caller cancels the event
     */
    static boolean guardInteraction(ServerPlayer player, InteractionHand hand, Entity target, @Nullable Vec3 localHit)
    {
        Entity subject = parentOf(target);

        if (!(subject instanceof EntityDragonBase dragon))
        {
            return false;
        }

        boolean owner = mayActAsNecromancyOwner(dragon, player);

        if (!NecromancyContract.isContracted(dragon) && !owner)
        {
            // An ordinary dragon, or a revived dragon and somebody who is not the player who owns it: nothing of this
            // compatibility decides that interaction, and the menu of Ice and Fire answers it as it always did.
            return false;
        }

        ItemStack held = player.getItemInHand(hand);

        if (blocksInteraction(held))
        {
            // The command staff, both dragon meals, the creative meal, the dragon horn, a summoning crystal, dragon
            // armour and any banner: the whole equipment surface of that mod stays shut for the whole contract.
            warnProtected(player, dragon);

            return true;
        }

        if (held.isEmpty() && player.isShiftKeyDown())
        {
            // The inventory of a dragon is an empty hand while sneaking, and the armour and the banners of that mod live
            // in that container, so it stays shut as well.
            warnProtected(player, dragon);

            return true;
        }

        if (owner)
        {
            if (held.isEmpty())
            {
                return ride(dragon, player);
            }

            if (feedWith(dragon, player, held))
            {
                return true;
            }

            if (held.isEdible())
            {
                // Food of the game that this dragon does not count as its own: refused rather than passed on, because
                // the food path of the game behind it would put a skeleton that must not breed in love with its summoner
                // and eat the item for nothing.
                warnProtected(player, dragon);

                return true;
            }

            return false;
        }

        if (dragon.isOwnedBy(player))
        {
            // Somebody else holds the contract of this dragon. While it lasts the tamer of the record gets none of the
            // tame interactions either: no ride, no inventory and no meal - which is what keeps a second player from
            // riding or feeding a dragon around a contract that is not his.
            warnProtected(player, dragon);

            return true;
        }

        return false;
    }

    /**
     * @return true when that player may act as the <b>necromancy owner</b> of that dragon right now.
     *
     * <p>It is the single answer to "whose dragon is this", and there is exactly one of it on purpose: the interaction
     * guard of the contract, the riding hook and the debug items all ask this method, so the rule can not drift apart
     * into three slightly different ones.
     *
     * <p>The rule, in order:
     * <ul>
     * <li><b>the holder of the contract</b> is always the owner, and nothing else is asked about him,</li>
     * <li><b>the player who woke the dragon up</b> is its owner too, contract or not - that is the basic fix: a bone
     * dragon belongs to whoever woke it, and it stays his until a contract takes over the question. The marker of the
     * waking has carried that UUID since the first prototype; what was missing is this reader,</li>
     * <li>but the waking never overrules <b>the tamer of the record</b>: a dragon that Ice and Fire really tamed, that
     * somebody later harvested into bones and woke up again, keeps answering to its tamer - so a stranger can not take a
     * tamed dragon over by clicking its bones first.</li>
     * </ul>
     */
    static boolean mayActAsNecromancyOwner(Entity dragon, @Nullable Entity who)
    {
        if (!(dragon instanceof EntityDragonBase base) || who == null)
        {
            return false;
        }

        if (NecromancyService.isContractOwner(base, who))
        {
            return true;
        }

        boolean wokenByHim = NecromancyMarker.isRevived(base)
                && NecromancyMarker.owner(base).map(owner -> owner.equals(who.getUUID())).orElse(false);

        if (!wokenByHim)
        {
            return false;
        }

        return base.getOwnerUUID() == null || who instanceof net.minecraft.world.entity.LivingEntity living
                && base.isOwnedBy(living);
    }

    /** @return true when that item is one of the things a contract takes away from a dragon. */
    static boolean blocksInteraction(ItemStack stack)
    {
        Item item = stack.getItem();

        return item == IafItemRegistry.DRAGON_STAFF.get()
                || item == IafItemRegistry.DRAGON_MEAL.get()
                || item == IafItemRegistry.SICKLY_DRAGON_MEAL.get()
                || item == IafItemRegistry.CREATIVE_DRAGON_MEAL.get()
                || item == IafItemRegistry.DRAGON_HORN.get()
                || item instanceof ItemDragonArmor
                || item instanceof ItemSummoningCrystal
                || item instanceof BannerItem;
    }

    /** The riding half of the quasi tame layer: stage {@value #RIDE_STAGE} or more, and the contract owner alone. */
    private static boolean ride(EntityDragonBase dragon, ServerPlayer player)
    {
        if (dragon.getDragonStage() < RIDE_STAGE)
        {
            tell(player.getUUID(), dragon, "message.spiderqueen_hungrycraft.necromancy.too_young_to_ride");

            return true;
        }

        if (!player.isPassenger())
        {
            // The three steps the native interact makes, and each of them is needed for the ride to be a ride:
            // the shift state is dropped so the queen does not slip off the moment she gets on, the seat is taken,
            // and - the part that a plain startRiding misses - the mod is told to announce the mount to every client.
            // Without that announcement the client keeps drawing the passenger the way it draws somebody in the mouth
            // of a dragon, which is exactly what riding looked like before this round.
            player.setShiftKeyDown(false);
            player.startRiding(dragon, true);
            setSitting(dragon, false);
            IceAndFire.sendMSGToAll(new MessageStartRidingMob(dragon.getId(), true, false));
            dragon.getNavigation().stop();
        }

        return true;
    }

    /**
     * Takes every rider off a contract dragon (Necromancy IV round).
     *
     * <p>It is the last step of ending a contract: a queen who is on the back of a skeleton that is about to lie down
     * is put on solid ground first - up to twenty blocks below the animal when it is in the air - and the mod is told
     * that the mount is over as well, so no client keeps a rider on a corpse.
     */
    private static void dismountAll(EntityDragonBase dragon)
    {
        List<Entity> riders = new ArrayList<>(dragon.getPassengers());

        for (Entity rider : riders)
        {
            if (rider instanceof ServerPlayer player)
            {
                BlockPos ground = safeGroundBelow(player.level(), player.blockPosition(), 20);

                if (ground != null)
                {
                    player.teleportTo(ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D);
                }
            }

            rider.stopRiding();
        }

        if (!riders.isEmpty())
        {
            IceAndFire.sendMSGToAll(new MessageStartRidingMob(dragon.getId(), false, false));
        }
    }

    /** @return the first free spot with solid ground under it within that many blocks below, or null. */
    @Nullable
    private static BlockPos safeGroundBelow(net.minecraft.world.level.Level level, BlockPos from, int range)
    {
        for (int depth = 0; depth <= range; depth++)
        {
            BlockPos candidate = from.below(depth);

            if (level.isEmptyBlock(candidate) && level.isEmptyBlock(candidate.above())
                    && !level.isEmptyBlock(candidate.below()) && level.getFluidState(candidate).isEmpty())
            {
                return candidate;
            }
        }

        return null;
    }

    /**
     * The feeding half of the quasi tame layer: meat, with the very numbers the tame path of Ice and Fire uses.
     *
     * <p>Nothing is invented here. {@code FoodUtils#getFoodPoints} decides what one item is worth, the hunger of the
     * dragon grows by it and its health by a tenth of it, the native eating sound and particles play, and one item is
     * consumed unless the player is in creative - which is the native food branch of that mod, line for line. The only
     * difference is who may do it: the player who holds the contract, and nobody else.
     *
     * @return true when that item really was eaten, false when it is not food at all, so the click may still mean
     *         something else
     */
    private static boolean feedWith(EntityDragonBase dragon, ServerPlayer player, ItemStack stack)
    {
        int points = FoodUtils.getFoodPoints(stack, true, dragon.dragonType.isPiscivore());

        if (points <= 0 || dragon.getHunger() >= 100 && dragon.getHealth() >= dragon.getMaxHealth())
        {
            return false;
        }

        dragon.setHunger(dragon.getHunger() + points);
        dragon.setHealth(Math.min(dragon.getMaxHealth(), dragon.getHealth() + points / 10));

        // The native volume of that sound is protected on a living entity, so the eating sound of a skeleton is played
        // at the ordinary volume of the dragon, with its own pitch.
        dragon.playSound(SoundEvents.GENERIC_EAT, 1.0F, dragon.getVoicePitch());
        dragon.spawnItemCrackParticles(stack.getItem());
        dragon.eatFoodBonus(stack);

        if (!player.isCreative())
        {
            stack.shrink(1);
        }

        return true;
    }

    /** @return the dragon behind a hit on one of its parts, so that a click on a wing is a click on the animal. */
    private static Entity parentOf(Entity target)
    {
        return target instanceof PartEntity<?> part && part.getParent() != null ? part.getParent() : target;
    }

    /** Says the one line of the quasi tame layer to that player. */
    private static void warnProtected(ServerPlayer player, Entity dragon)
    {
        tell(player.getUUID(), dragon, "message.spiderqueen_hungrycraft.necromancy.protected");
    }

    /** Tells one player one line about one dragon. A player who is not in the world is simply not told. */
    private static void tell(UUID owner, Entity dragon, String key)
    {
        if (dragon.level().getServer() == null)
        {
            return;
        }

        ServerPlayer player = dragon.level().getServer().getPlayerList().getPlayer(owner);

        if (player != null && player.connection != null)
        {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    /**
     * Gives one revived dragon back to Ice and Fire as the corpse it was.
     *
     * <p>The prototype does not build a corpse itself. It empties the health and lets Ice and Fire do what it always
     * does with a dragon whose health reaches zero: on the next tick {@code tickDeath} freezes the death timer at
     * zero, so the entity is never removed, and sets the corpse flag again. Because the death stage was never touched,
     * the dragon that comes back is the same skeleton it was before - and, when the timer round arrives, a corpse that
     * is still the very same save entry.
     */
    static ProbeReport restore(Entity pointed)
    {
        if (!(pointed instanceof EntityDragonBase dragon))
        {
            return ProbeReport.failure("Not an Ice and Fire dragon.");
        }

        if (!NecromancyMarker.isRevived(dragon))
        {
            return ProbeReport.failure("refused: HungryCraft did not revive this dragon");
        }

        Identity before = Identity.of(dragon);
        int savedCommand = NecromancyMarker.savedCommand(dragon, dragon.getCommand());

        dragon.setTarget(null);
        dragon.getNavigation().stop();
        dragon.setFlying(false);
        dragon.setHovering(false);

        // Every rider off, onto solid ground, and every client told that the mount is over: a contract that ends in the
        // air must not leave the queen on the back of a corpse.
        dismountAll(dragon);

        dragon.setCommand(savedCommand);

        NecromancyMarker.clear(dragon);

        // And the client is told the same: from here on the dragon is a corpse again and Ice and Fire's own decision
        // about its texture stands.
        broadcastRevived(dragon, false);

        boolean removalRisk = dragon.getDeathStage() >= dragon.getAgeInDays() / 5;
        dragon.setHealth(0.0F);

        Identity after = Identity.of(dragon);

        List<String> lines = new ArrayList<>();
        lines.add("restored " + describe(dragon));
        lines.add("identity: " + (before.sameIdentity(after) ? "untouched (" + before.describe() + ")"
                : "CHANGED " + before.describe() + " -> " + after.describe()));
        lines.add("health is 0; the next tick of Ice and Fire turns this very entity into its corpse again");

        if (removalRisk)
        {
            lines.add("WARNING: deathStage " + dragon.getDeathStage() + " is not below ageInDays / 5 ("
                    + (dragon.getAgeInDays() / 5) + "), so Ice and Fire's own tickDeath will REMOVE this entity.");
        }

        return ProbeReport.success(lines);
    }

    /**
     * The panic button of a test world: every dragon of the level that HungryCraft has woken is given back to Ice and
     * Fire at once. Chunks are never force loaded - only what is loaded right now is looked at.
     */
    static ProbeReport restoreAll(ServerPlayer player)
    {
        if (!(player.level() instanceof ServerLevel level))
        {
            return ProbeReport.failure("the probe needs a server level");
        }

        List<String> details = new ArrayList<>();
        int restored = 0;
        int refused = 0;

        for (Entity entity : level.getAllEntities())
        {
            if (!(entity instanceof EntityDragonBase dragon) || !NecromancyMarker.isRevived(dragon))
            {
                continue;
            }

            ProbeReport one = restore(dragon);

            if (one.ok())
            {
                restored++;
            }
            else
            {
                refused++;
            }

            details.add("  " + (one.ok() ? "restored " : "refused  ") + describe(dragon));
        }

        List<String> lines = new ArrayList<>();
        lines.add("Restored " + restored + " necromancy dragons."
                + (refused > 0 ? " " + refused + " were refused." : ""));
        lines.addAll(details);

        return new ProbeReport(restored > 0 || refused == 0, lines);
    }

    /** @return the one line that says what a dragon is: the entity type, its class, its uuid and where it is. */
    private static String describe(EntityDragonBase dragon)
    {
        return EntityType.getKey(dragon.getType()) + " (" + dragon.getClass().getSimpleName() + ") uuid="
                + dragon.getUUID() + " at " + formatVec(dragon.position());
    }

    /** @return every state of a dragon the round asks to be recorded, in four readable lines and the marker line. */
    private static List<String> state(EntityDragonBase dragon)
    {
        List<String> lines = new ArrayList<>();
        lines.add("iceandfire: isModelDead=" + dragon.isModelDead() + " deathStage=" + dragon.getDeathStage()
                + " isSkeletal=" + dragon.isSkeletal() + " ageInDays=" + dragon.getAgeInDays()
                + " dragonStage=" + dragon.getDragonStage() + " variant=" + dragon.getVariant()
                + " male=" + dragon.isMale());
        lines.add("vanilla: health=" + dragon.getHealth() + "/" + dragon.getMaxHealth() + " isAlive=" + dragon.isAlive()
                + " isDeadOrDying=" + dragon.isDeadOrDying() + " isRemoved=" + dragon.isRemoved()
                + " isNoAi=" + dragon.isNoAi() + " isAiDisabled=" + dragon.isAiDisabled()
                + " isEffectiveAi=" + dragon.isEffectiveAi() + " deathTime=" + dragon.deathTime
                + " isPickable=" + dragon.isPickable());
        lines.add("command: command=" + dragon.getCommand() + " isOrderedToSit=" + dragon.isOrderedToSit()
                + " isTame=" + dragon.isTame() + " owner=" + ownerName(dragon));
        lines.add("movement: flying=" + dragon.isFlying() + " hovering=" + dragon.isHovering() + " target="
                + targetName(dragon) + " navigationInProgress=" + dragon.getNavigation().isInProgress());
        lines.add("orders: moveGoal=" + hasGoal(dragon.goalSelector, NecromancyMoveGoal.class)
                + " targetGoal=" + hasGoal(dragon.targetSelector, NecromancyTargetGoal.class)
                + " targetIsFriend=" + (dragon.getTarget() != null
                        && NecromancyService.isFriendlyToContract(dragon, dragon.getTarget()))
                + " stage=" + dragon.getDragonStage() + " mayRide=" + (dragon.getDragonStage() >= RIDE_STAGE)
                + " mayBreathe=" + (dragon.getDragonStage() >= BREATH_STAGE));
        lines.add("necromancy: " + (NecromancyMarker.isRevived(dragon)
                ? "revived=true owner=" + NecromancyMarker.owner(dragon).map(Object::toString).orElse("?")
                        + " savedCommand=" + NecromancyMarker.savedCommand(dragon, -1)
                        + " savedHealth=" + NecromancyMarker.savedHealth(dragon, -1.0F)
                : "revived=false")
                + " restoreWouldRemove=" + (dragon.getDeathStage() >= dragon.getAgeInDays() / 5));
        lines.add("contract: " + NecromancyContract.of(dragon).map(contract -> "owner=" + contract.owner()
                + " endsAt=" + contract.expiresAt() + " remaining=" + contract.remaining(dragon.level().getGameTime())
                + " order=" + contract.order() + (contract.position() == null ? "" : " pos=" + contract.position())
                + " phase=" + contract.phase()).orElse("none")
                + " agingDisabled=" + dragon.isAgingDisabled()
                + " inLove=" + dragon.isInLove()
                + " breathingFire=" + dragon.isBreathingFire());

        return lines;
    }

    /** @return the owner of that dragon, or "none". */
    private static String ownerName(EntityDragonBase dragon)
    {
        return dragon.getOwnerUUID() == null ? "none" : dragon.getOwnerUUID().toString();
    }

    /** @return the creature the dragon is hunting right now, or "none". */
    private static String targetName(EntityDragonBase dragon)
    {
        LivingEntity target = dragon.getTarget();

        return target == null ? "none" : EntityType.getKey(target.getType()).toString();
    }

    /** @return a position as three rounded numbers. */
    private static String formatVec(Vec3 vec)
    {
        return "[" + round(vec.x) + ", " + round(vec.y) + ", " + round(vec.z) + "]";
    }

    /** @return one number with a single decimal, written the same way in every locale. */
    private static String round(double value)
    {
        return Double.toString(Math.round(value * 10.0D) / 10.0D);
    }

    /**
     * Everything about a dragon that must not change when it is woken up and put back: its identity, its age, its
     * stage, its variant, its gender, its death stage and its owner. The command is compared separately, because a
     * restored dragon is deliberately given its old command back.
     */
    private record Identity(UUID uuid, ResourceLocation type, int ageInDays, int dragonStage, int variant,
            boolean male, int deathStage, boolean tame, @Nullable UUID owner, int command)
    {
        static Identity of(EntityDragonBase dragon)
        {
            return new Identity(dragon.getUUID(), EntityType.getKey(dragon.getType()), dragon.getAgeInDays(),
                    dragon.getDragonStage(), dragon.getVariant(), dragon.isMale(), dragon.getDeathStage(),
                    dragon.isTame(), dragon.getOwnerUUID(), dragon.getCommand());
        }

        /** @return true when nothing but the command may differ. */
        boolean sameIdentity(Identity other)
        {
            return this.uuid.equals(other.uuid) && this.type.equals(other.type) && this.ageInDays == other.ageInDays
                    && this.dragonStage == other.dragonStage && this.variant == other.variant && this.male == other.male
                    && this.deathStage == other.deathStage && this.tame == other.tame
                    && Objects.equals(this.owner, other.owner);
        }

        String describe()
        {
            return this.type + " uuid=" + this.uuid + " age=" + this.ageInDays + " stage=" + this.dragonStage
                    + " variant=" + this.variant + " male=" + this.male + " deathStage=" + this.deathStage
                    + " tame=" + this.tame + " owner=" + (this.owner == null ? "none" : this.owner);
        }
    }
}
