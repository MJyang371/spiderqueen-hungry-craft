package spiderqueenhungrycraft.compat.iceandfire;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

/**
 * The <b>one and only</b> entrance of the optional Ice and Fire compatibility (Necromancy IV prototype 1).
 *
 * <p>Everything the rest of this mod is allowed to know about that compatibility is written here, and every method of
 * this class speaks vanilla: a {@link ServerPlayer}, an int, a {@link ProbeReport}. <b>Not one Ice and Fire type is
 * named in this file</b>, not even inside a method body, and that is the whole point of it:
 *
 * <ul>
 * <li>this class loads and links with no Ice and Fire anywhere near it, so the commands can call it in any world,</li>
 * <li>the classes that really touch Ice and Fire ({@link IceAndFireBridge}) are named only from the inside of a method
 * that has already been passed through {@link #present()}, so the JVM never has to resolve them when the mod is
 * absent,</li>
 * <li>the answer of a missing mod is a plain report, never an exception, never a
 * {@code NoClassDefFoundError} and never a {@code ClassNotFoundException}.</li>
 * </ul>
 *
 * <p>The guard itself is {@code ModList}, which is the <b>late</b> mod list of Forge: it is filled while the game
 * starts, so it is the right question to ask from a command, an event handler or a game test, and the wrong question
 * to ask from a mixin plugin of the launch phase (that one asks {@code LoadingModList}, see
 * {@code spiderqueenhungrycraft.mixin.compat.iaf.IceAndFireMixinPlugin}).
 */
public final class IceAndFireSupport
{
    /** The mod id of Ice and Fire. Citadel needs no question of its own: Ice and Fire declares it mandatory. */
    public static final String ICE_AND_FIRE_MOD_ID = "iceandfire";

    /** The answer of every probe when the other mod is not there. */
    public static final String NOT_INSTALLED = "Ice and Fire is not installed.";

    /**
     * The prefix of every line the compatibility writes into the log, wherever it is written from.
     *
     * <p>It lives here, on the facade, because the compatibility writes from four very different places - a command,
     * the client mirror, its server to client packet and the client texture hook - and one prefix is what makes the
     * collected evidence of a probe run findable in a log afterwards.
     */
    public static final String PROBE_LOG_PREFIX = "[HungryCraft/IceFireProbe]";

    /** The three dragons of Ice and Fire, as the debug commands spell them. */
    public static final List<String> DRAGON_TYPES = List.of("fire", "ice", "lightning");

    /**
     * The age a bone dragon is spawned with when the command names none: seventy five days, which is a stage three
     * dragon - big enough to fly, old enough that its corpse can never hit the removal rule of Ice and Fire
     * ({@code deathStage >= ageInDays / 5}), and the same kind of animal the world's own dragon skeletons are.
     */
    public static final int DEFAULT_AGE_DAYS = 75;

    /**
     * The youngest age the probe accepts. Below five days {@code ageInDays / 5} is zero, which is the one value at
     * which Ice and Fire removes a corpse instead of keeping it, so no probe ever builds one.
     */
    public static final int MIN_AGE_DAYS = 5;

    /** The oldest age the probe accepts: past a hundred days a dragon is a stage five dragon and stops growing. */
    public static final int MAX_AGE_DAYS = 200;

    private IceAndFireSupport()
    {
    }

    /**
     * @return true when Ice and Fire is loaded in this game.
     *
     *         <p>It is deliberately forgiving: a mod list that is not there yet, or a call that arrives absurdly early,
     *         answers "no" instead of throwing. Every entrance of this class turns a "no" into the plain sentence
     *         {@link #NOT_INSTALLED}, so a user without that mod can never see a crash from this compatibility.
     */
    public static boolean present()
    {
        try
        {
            ModList mods = ModList.get();

            return mods != null && mods.isLoaded(ICE_AND_FIRE_MOD_ID);
        }
        catch (Throwable tooEarly)
        {
            return false;
        }
    }

    /**
     * Builds a real Ice and Fire bone dragon in front of that player, the way the world builds its own.
     *
     * @param type    {@code fire}, {@code ice} or {@code lightning}
     * @param ageDays the age of the dragon; a value outside the accepted range is refused
     * @return what happened, in lines an operator can read
     */
    public static ProbeReport spawnBoneDragon(ServerPlayer player, String type, int ageDays)
    {
        if (!present())
        {
            return ProbeReport.unavailable();
        }

        return IceAndFireBridge.spawnBoneDragon(player, type, ageDays);
    }

    /**
     * Reports everything about the creature the crosshair of that player points at: the Ice and Fire state, the
     * vanilla state, the command and movement state and the prototype marker of this mod.
     */
    public static ProbeReport inspect(ServerPlayer player)
    {
        if (!present())
        {
            return ProbeReport.unavailable();
        }

        Entity pointed = ProbeRay.entityUnderCrosshair(player, ProbeRay.RANGE);

        return pointed == null ? noTarget() : IceAndFireBridge.inspect(pointed);
    }

    /**
     * Wakes the bone dragon the crosshair of that player points at <b>up</b> - the very same entity, with the very
     * same identity - and marks it as a HungryCraft revived dragon.
     */
    public static ProbeReport revive(ServerPlayer player)
    {
        if (!present())
        {
            return ProbeReport.unavailable();
        }

        Entity pointed = ProbeRay.entityUnderCrosshair(player, ProbeRay.RANGE);

        return pointed == null ? noTarget() : IceAndFireBridge.revive(player, pointed);
    }

    /**
     * Gives the dragon the crosshair of that player points at back to Ice and Fire as the corpse it was, and drops the
     * prototype marker.
     */
    public static ProbeReport restore(ServerPlayer player)
    {
        if (!present())
        {
            return ProbeReport.unavailable();
        }

        Entity pointed = ProbeRay.entityUnderCrosshair(player, ProbeRay.RANGE);

        return pointed == null ? noTarget() : IceAndFireBridge.restore(pointed);
    }

    /**
     * The panic button of a test world: every loaded dragon that carries the prototype marker is given back to Ice and
     * Fire, no matter where it is or what it is doing.
     */
    public static ProbeReport restoreAll(ServerPlayer player)
    {
        if (!present())
        {
            return ProbeReport.unavailable();
        }

        return IceAndFireBridge.restoreAll(player);
    }

    /**
     * The same waking up as {@link #revive}, with the flash of a lightning bolt in front of it: the very command a
     * player will one day press a key for.
     *
     * <p>The bolt is <b>visual only</b> ({@code VisualLightning}), so nothing is hurt and nothing burns. It is struck
     * only after the probe has decided that the dragon really can be woken, so a refused command never throws a bolt
     * at a corpse it then leaves alone.
     */
    public static ProbeReport lightningRevive(ServerPlayer player)
    {
        if (!present())
        {
            return ProbeReport.unavailable();
        }

        Entity pointed = ProbeRay.entityUnderCrosshair(player, ProbeRay.RANGE);

        return pointed == null ? noTarget() : IceAndFireBridge.lightningRevive(player, pointed);
    }

    /**
     * Tells one player what the necromancy marker of one entity says.
     *
     * <p>It is the answer to "a player started tracking that entity", and it is what makes the skeleton of a revived
     * dragon survive a relog, a chunk reload and a dimension change: the client mirror is emptied whenever a world is
     * left, and the server refills it every time somebody begins to see one of its woken dragons.
     */
    public static void resyncTracking(ServerPlayer player, Entity target)
    {
        if (!present())
        {
            return;
        }

        IceAndFireBridge.resyncTracking(player, target);
    }

    /** @return one short line about the marker of that entity, or null when there is none to report. */
    @Nullable
    public static String describeMarker(Entity entity)
    {
        return NecromancyMarker.isRevived(entity)
                ? "revived=true owner=" + NecromancyMarker.owner(entity).map(Object::toString).orElse("?")
                : null;
    }

    /** @return the report of a probe that found no creature to work on. */
    private static ProbeReport noTarget()
    {
        return ProbeReport.failure("no pickable creature within " + (int) ProbeRay.RANGE + " blocks");
    }

    /**
     * The formal skill: wakes the bone dragon under the crosshair and puts it under a ten minute contract of that
     * player (Necromancy IV round).
     *
     * <p>It is the same waking up, the same visual lightning and the same identity guarantees as the debug command of
     * the round before - the only thing that is new is the contract written next to it.
     */
    public static ProbeReport contractDragon(ServerPlayer player, Entity pointed, int durationTicks)
    {
        if (!present())
        {
            return ProbeReport.unavailable();
        }

        return IceAndFireBridge.startContract(player, pointed, durationTicks);
    }

    /**
     * The same contract, taken by the dragon the player is looking at, for the debug commands.
     *
     * <p>It exists so that a live probe does not depend on a key binding and on the skill node: an operator can put a
     * skeleton under contract with one line, order it with the sceptre and watch the goals work. The rules are the very
     * ones of the skill - the same ray, the same refusals, the same waking up, the same ten minutes by default.
     */
    public static ProbeReport contractInFront(ServerPlayer player, int durationTicks)
    {
        if (!present())
        {
            return ProbeReport.unavailable();
        }

        Entity pointed = ProbeRay.entityUnderCrosshair(player, ProbeRay.RANGE);

        return pointed == null ? noTarget() : IceAndFireBridge.startContract(player, pointed, durationTicks);
    }

    /**
     * The whole chain of the <b>awakened bone dragon</b> debug item: a bone dragon in front of that player, woken up,
     * and under the contract of that player.
     *
     * <p>It is the same three steps as {@code spawn_bone_dragon} + {@code revive} + {@code contract} of the probe
     * command, on the same code, so the item can not grow a rule of its own and can not hand out a permanent ownership:
     * the skeleton works for ten minutes like every other skeleton of this feature.
     *
     * @param type one of {@link #DRAGON_TYPES}
     * @param ageDays the age of the dragon, between {@link #MIN_AGE_DAYS} and {@link #MAX_AGE_DAYS}
     * @param durationTicks how long the contract lasts
     */
    public static ProbeReport spawnAwakenedDragon(ServerPlayer player, String type, int ageDays, int durationTicks)
    {
        if (!present())
        {
            return ProbeReport.failure(NOT_INSTALLED);
        }

        return IceAndFireBridge.spawnAwakenedDragon(player, type, ageDays, durationTicks);
    }

    /**
     * @return true when that player may act as the necromancy owner of that dragon: he holds its contract, or - for a
     *         dragon that was woken out of its bones - he is the player who woke it up (and Ice and Fire has no other
     *         tamer to answer to).
     *
     * <p>It is the one ownership question of this feature that includes the <b>waking</b> itself, and it is answered in
     * one place ({@code IceAndFireBridge#mayActAsNecromancyOwner}) so the interaction guard, the riding hook and the
     * debug items can never disagree about whose dragon it is.
     */
    public static boolean mayActAsNecromancyOwner(Entity dragon, Entity who)
    {
        return present() && IceAndFireBridge.mayActAsNecromancyOwner(dragon, who);
    }

    /**
     * @return true when that dragon is a bone dragon this mod could take under contract right now.
     */
    public static boolean isBoneDragon(Entity entity)
    {
        return present() && IceAndFireBridge.isBoneDragon(entity);
    }

    /** Keeps the Ice and Fire half of one live contract in order, once per tick. */
    public static void enforceContract(net.minecraft.world.entity.LivingEntity dragon)
    {
        if (present())
        {
            IceAndFireBridge.enforceContract(dragon);
        }
    }

    /** The Ice and Fire half of ending a contract: the corpse comes back and the aging flag is given back. */
    public static void endContract(Entity dragon)
    {
        if (present())
        {
            IceAndFireBridge.endContract(dragon);
        }
    }

    /**
     * The two goals of a dragon under contract (Necromancy IV round).
     *
     * <p>They are the whole execution half of the sceptre for the other kind of unit: a goal that owns the target flag
     * of the dragon (so the wild hunting of Ice and Fire can never pick the queen or her brood) and a goal that owns
     * its move flag (so a march and a bombardment are really walked, with the native movement of that mod). They are
     * installed here, from the tick of the contract, and guarded against a second installation, which is what makes a
     * chunk unload, a relog and a server restart harmless: the entity comes back without its goals and gets them here.
     */
    public static void keepGoals(net.minecraft.world.entity.LivingEntity dragon)
    {
        if (present())
        {
            IceAndFireBridge.keepGoals(dragon);
        }
    }

    /** Takes the two goals of the contract off a dragon again, called when its contract ends. */
    public static void dropGoals(net.minecraft.world.entity.LivingEntity dragon)
    {
        if (present())
        {
            IceAndFireBridge.dropGoals(dragon);
        }
    }

    /** Puts one contract dragon into its hovering state: what a bombardment breathes from. */
    public static void hover(Entity dragon)
    {
        if (present())
        {
            IceAndFireBridge.hover(dragon);
        }
    }

    /**
     * Sends one contract dragon at a point of the world, walking when it is close and flying when it is far
     * (Necromancy IV round, the marching fix).
     *
     * <p>The rule and the reason both live in {@link IceAndFireBridge#approach}: this is only the vanilla face of it,
     * so the goals of the compatibility never have to name a type of the other mod to send a skeleton somewhere.
     */
    public static void approach(Entity dragon, Vec3 target, double speed)
    {
        if (present())
        {
            IceAndFireBridge.approach(dragon, target, speed);
        }
    }

    /**
     * The bounded fallback of a march that is going nowhere: up into the air and straight at the point, on the native
     * move control of the animal (Necromancy IV round).
     */
    public static void straightLine(Entity dragon, Vec3 target, double speed)
    {
        if (present())
        {
            IceAndFireBridge.straightLine(dragon, target, speed);
        }
    }

    /** Holds one contract dragon exactly where it stands, which is what a breath is aimed from. */
    public static void holdStill(Entity dragon)
    {
        if (present())
        {
            IceAndFireBridge.holdStill(dragon);
        }
    }

    /**
     * Takes one contract dragon off everything it is doing (Necromancy IV round 5): the first half of the stand-down
     * key, and of every other order that has to outrank an errand.
     *
     * <p>It drops the target, stops the path and the move control, releases the vertical control, switches a running
     * breath off and tells a hovering skeleton to stop hovering. It deliberately leaves the <b>flight</b> state alone:
     * what brings a skeleton down is the walk/fly steering of the contract, not a fall.
     */
    public static void interrupt(Entity dragon)
    {
        if (present())
        {
            IceAndFireBridge.interrupt(dragon);
        }
    }

    /** @return one line describing the flying, hovering, sleeping and riding state of that dragon, for a log. */
    public static String describeState(Entity dragon)
    {
        return present() ? IceAndFireBridge.describeState(dragon) : "Ice and Fire is not installed";
    }

    /**
     * @return true when that dragon is in the air <b>because the contract put it there</b> and that flight has not been
     *         finished yet (Necromancy IV round 6).
     *
     * <p>It is how a goal of the compatibility tells "I am still in the middle of something in the air" from "the animal
     * decided to fly on its own": only the first one may be landed by the contract when the errand is over, and only the
     * first one keeps a goal awake so that it can do so. A dragon that Ice and Fire sends flying on its own is left
     * completely alone.
     */
    public static boolean contractFlight(Entity dragon)
    {
        return present() && IceAndFireBridge.contractFlight(dragon);
    }

    /**
     * The sit of Ice and Fire, used as the "post" pose of a holding dragon (Necromancy IV round).
     *
     * <p>It is the very pose a tamed dragon takes when it is told to stay: the animal lies down, its own AI stops
     * walking it around (the mod derives {@code canMove} from this flag), and it gets up again the moment the flag is
     * dropped. The contract only lends it - the command the dragon had before the contract is written in its marker and
     * is given back when the contract ends.
     */
    public static void setSitting(Entity dragon, boolean sitting)
    {
        if (present())
        {
            IceAndFireBridge.setSitting(dragon, sitting);
        }
    }

    /** @return true when that contract dragon is old enough (stage 3 or more) to breathe at all. */
    public static boolean canBreathe(Entity dragon)
    {
        return present() && IceAndFireBridge.canBreathe(dragon);
    }

    /** One tick of the native breath of that dragon, aimed at that point of the world. */
    public static boolean breatheAt(Entity dragon, net.minecraft.world.phys.Vec3 target)
    {
        return present() && IceAndFireBridge.breatheAt(dragon, target);
    }

    /** Turns the breath of that dragon off again. */
    public static void stopBreath(Entity dragon)
    {
        if (present())
        {
            IceAndFireBridge.stopBreath(dragon);
        }
    }

    /** Plays the native breath sound of that dragon, which a bombardment does every fifth tick. */
    public static void breathSound(Entity dragon)
    {
        if (present())
        {
            IceAndFireBridge.breathSound(dragon);
        }
    }

    /** Tells the player who holds the contract of that dragon that it is still too young to breathe. */
    public static void warnTooYoungToBreathe(Entity dragon)
    {
        if (present())
        {
            IceAndFireBridge.warnTooYoungToBreathe(dragon);
        }
    }

    /**
     * The one entrance of every right click on a dragon under a contract (Necromancy IV round, the quasi tame layer).
     *
     * <p>It is deliberately <b>not</b> a hook into {@code isOwnedBy}: that method guards the command staff, the dragon
     * meal, the inventory, the armour, the banner and the riding of Ice and Fire all at once, so answering "yes" there
     * would hand the queen the whole tame menu of that mod. Instead every interaction is decided here, case by case:
     *
     * <ul>
     * <li><b>blocked</b> - the command staff, the dragon meal, the sickly dragon meal, the creative meal, the dragon
     * horn, a summoning crystal, dragon armour and any banner; and the inventory, which is an empty hand while
     * sneaking,</li>
     * <li><b>granted to the contract owner alone</b> - riding at stage 3 or more, and meat, which is fed with the very
     * same numbers the native food branch of that mod uses,</li>
     * <li><b>granted to nobody else</b> - so a real tamer of that dragon can not ride it, feed it or open its inventory
     * while somebody else holds its contract,</li>
     * <li><b>and everything else is left alone</b> - a name tag, a lead, a bucket, another mod - so this layer only
     * takes away what the contract has to take away.</li>
     * </ul>
     *
     * @param hand the hand the player used, which is the one the item is read from
     * @param localHit the exact point of the model that was clicked, when the client sent one
     * @return true when the interaction was handled by the contract, in which case the caller cancels the event
     */
    public static boolean guardInteraction(ServerPlayer player, InteractionHand hand, Entity target, @Nullable Vec3 localHit)
    {
        if (!present())
        {
            return false;
        }

        return IceAndFireBridge.guardInteraction(player, hand, target, localHit);
    }

    /**
     * @return true when that item is one of the things a contract takes away from a dragon: the command staff, the two
     *         dragon meals, the creative meal, the dragon horn, a summoning crystal, dragon armour and any banner.
     *
     * <p>It is exposed on its own because a <b>client</b> needs the same answer: a right click is predicted locally
     * before the server ever sees it, so the client of a player who clicks a woken dragon with a dragon meal should
     * refuse it as well - not to decide anything (the server decides), but so that the equipment of Ice and Fire does
     * not flash on the screen for one frame before the answer of the server takes it away.
     */
    public static boolean blocksInteraction(net.minecraft.world.item.ItemStack stack)
    {
        return present() && IceAndFireBridge.blocksInteraction(stack);
    }

    /** Runs one tick of the contract clock of that level, which is what the world calls. */
    public static void tickContracts(net.minecraft.server.level.ServerLevel level)
    {
        NecromancyService.tickLevel(level);
    }

    /** Ends every contract of that level, for the operator command. */
    public static int endAllContracts(net.minecraft.server.level.ServerLevel level, String why)
    {
        return NecromancyService.endAll(level, why);
    }

    /**
     * The remains of one bone dragon, read out of that mod's entity and carried by the soul fire dragon of this mod
     * (round 7C).
     *
     * <p>It is deliberately a plain record of vanilla values: the kind of dragon it was ({@code FIRE}, {@code ICE} or
     * {@code LIGHTNING}), the growth stage and the whole saved state of the entity. The rest of this mod therefore
     * never has to name a type of the other mod to know what was raised, and the state is what is handed back
     * unchanged when the contract ends.
     *
     * @param origin {@code FIRE}, {@code ICE} or {@code LIGHTNING} - the kind of dragon the corpse was
     * @param stage  its growth stage, one to five
     * @param state  its saved entity tag, or null when it could not be read
     */
    public record BoneRecord(String origin, int stage, @org.jetbrains.annotations.Nullable net.minecraft.nbt.CompoundTag state)
    {
    }

    /**
     * @return the remains of the bone dragon under that entity, or null when it is not one (or when Ice and Fire is not
     *         installed) - the one question the skill asks before it raises anything.
     */
    @org.jetbrains.annotations.Nullable
    public static BoneRecord boneRecord(Entity pointed)
    {
        return present() ? IceAndFireBridge.boneRecordOf(pointed) : null;
    }

    /** Lays the remains of an ended contract back down, with the very identity they were taken with. */
    public static boolean restoreBone(net.minecraft.server.level.ServerLevel level, BoneRecord record,
            java.util.UUID identity, Vec3 where, float yaw)
    {
        return present() && IceAndFireBridge.restoreBone(level, record.state(), identity, where, yaw);
    }

    /**
     * The whole chain of the <b>awakened</b> debug item and of the debug command (round 7C): remains built in front of
     * that player, and <b>raised into a soul fire dragon of his own</b> under the ordinary ten minute contract.
     *
     * <p>It is the very same two steps the skill takes ({@code spawnBoneDragon} and then the rising of
     * {@code SoulfireRising}), so an item can never grow a rule of its own and can never hand out a unit the rest of the
     * feature would not recognise.
     *
     * @param type one of {@link #DRAGON_TYPES}
     * @param ageDays the age of the remains, between {@link #MIN_AGE_DAYS} and {@link #MAX_AGE_DAYS}
     * @param durationTicks how long the contract of the new unit lasts
     */
    public static ProbeReport spawnSoulfireDragon(ServerPlayer player, String type, int ageDays, int durationTicks)
    {
        if (!present())
        {
            return ProbeReport.failure(NOT_INSTALLED);
        }

        return IceAndFireBridge.spawnSoulfireDragon(player, type, ageDays, durationTicks);
    }

    /**
     * Strikes one <b>visual only</b> bolt of lightning at that place, which is the flash every waking of a corpse has
     * always had. The bolt of the game is used with its own {@code visualOnly} switch, so nothing is hurt and nothing
     * burns; it is asked through the facade because only the compatibility knows whether the effect is available.
     */
    public static boolean strikeLightning(net.minecraft.server.level.ServerLevel level, Vec3 where)
    {
        return present() && VisualLightning.strike(level, where);
    }
}
