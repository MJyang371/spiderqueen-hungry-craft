package spiderqueenhungrycraft.command;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.compat.iceandfire.IceAndFireSupport;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyOrders;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyService;
import spiderqueenhungrycraft.compat.iceandfire.ProbeReport;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The debug commands of the optional Ice and Fire compatibility (Necromancy IV prototype 1).
 *
 * <p>Five entrances under {@code /hungrycraft debug iaf ...}, and every one of them is a probe of the round the
 * prototype belongs to:
 *
 * <ul>
 * <li>{@code spawn_bone_dragon <fire|ice|lightning> [age_days]} - builds a real bone dragon in front of the player,
 * through Ice and Fire's own recipe for one, and reports its state immediately,</li>
 * <li>{@code inspect} - everything about the creature the crosshair points at: what it is, the state Ice and Fire
 * keeps, the state the game keeps, and whether HungryCraft has woken it up,</li>
 * <li>{@code revive} - wakes that bone dragon up <b>in place</b> and marks it as a HungryCraft revived dragon,</li>
 * <li>{@code restore} - gives that dragon back to Ice and Fire as the corpse it was,</li>
 * <li>{@code restore_all} - the panic button of a test world: every loaded dragon of this mod is put back at once.</li>
 * </ul>
 *
 * <p><b>Nothing here is a feature.</b> There is no skill, no key binding, no cost, no cooldown and no timer: the round
 * asks for a proof that one entity of another mod can be walked from "corpse" to "living" and back again without
 * losing its identity, and a command is what makes that proof repeatable. The formal skill of the next round will
 * call the very same {@link IceAndFireSupport} entrances.
 *
 * <p>Every action is written into the log under one common prefix as well as shown to the operator, because the
 * interesting half of this prototype happens in a game this development workspace can not run.
 */
public final class IceAndFireProbeCommands
{
    /** The root literal of this mod, merged with the other views of it by Brigadier. */
    private static final String ROOT = "hungrycraft";

    private static final String DEBUG = "debug";
    private static final String ICE_AND_FIRE = "iaf";
    private static final String SPAWN_BONE_DRAGON = "spawn_bone_dragon";
    private static final String INSPECT = "inspect";
    private static final String REVIVE = "revive";
    private static final String LIGHTNING_REVIVE = "lightning_revive";
    private static final String RESTORE = "restore";
    private static final String RESTORE_ALL = "restore_all";
    private static final String CONTRACT = "contract";
    private static final String RECALL = "recall";

    /** The entrance of round 7C: a soul fire dragon of this mod, raised from remains, with no Ice and Fire unit involved. */
    private static final String SPAWN_SOULFIRE = "spawn_soulfire_dragon";

    private static final String VARIANT = "variant";

    private static final String TYPE = "type";
    private static final String AGE_DAYS = "age_days";
    private static final String SECONDS = "seconds";

    /** How long a contract of the debug command lasts when the operator names nothing: the ten minutes of the skill. */
    private static final int DEFAULT_CONTRACT_SECONDS = 600;

    /** And the range the debug command accepts, in seconds: ten seconds to one hour. */
    private static final int MIN_CONTRACT_SECONDS = 10;
    private static final int MAX_CONTRACT_SECONDS = 3600;

    /** The permission level the round asks for: the ordinary operator level. */
    private static final int PROBE_PERMISSION_LEVEL = 2;

    /** The one suggestion the type argument ever offers. */
    private static final SuggestionProvider<CommandSourceStack> DRAGON_TYPES =
            (context, builder) -> SharedSuggestionProvider.suggest(IceAndFireSupport.DRAGON_TYPES, builder);

    private IceAndFireProbeCommands()
    {
    }

    /**
     * Registers the probe tree under the root of this mod.
     *
     * <p>It is a third view of the very same root literal, which Brigadier merges into one tree, exactly like the
     * skill commands of the progression round: neither tree has to be threaded through the other.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal(ROOT)
                .then(Commands.literal(DEBUG)
                        .then(Commands.literal(ICE_AND_FIRE)
                                .requires(source -> source.hasPermission(PROBE_PERMISSION_LEVEL))
                                .then(Commands.literal(SPAWN_BONE_DRAGON)
                                        .then(Commands.argument(TYPE, StringArgumentType.word())
                                                .suggests(DRAGON_TYPES)
                                                .executes(context -> spawnBoneDragon(context,
                                                        IceAndFireSupport.DEFAULT_AGE_DAYS))
                                                .then(Commands.argument(AGE_DAYS, IntegerArgumentType.integer(
                                                                IceAndFireSupport.MIN_AGE_DAYS,
                                                                IceAndFireSupport.MAX_AGE_DAYS))
                                                        .executes(context -> spawnBoneDragon(context,
                                                                IntegerArgumentType.getInteger(context, AGE_DAYS))))))
                                .then(Commands.literal(INSPECT).executes(IceAndFireProbeCommands::inspect))
                                .then(Commands.literal(REVIVE).executes(IceAndFireProbeCommands::revive))
                                .then(Commands.literal(LIGHTNING_REVIVE)
                                        .executes(IceAndFireProbeCommands::lightningRevive))
                                .then(Commands.literal(RESTORE).executes(IceAndFireProbeCommands::restore))
                                .then(Commands.literal(RESTORE_ALL)
                                        .executes(IceAndFireProbeCommands::restoreAll))
                                .then(Commands.literal(CONTRACT)
                                        .executes(context -> contract(context, DEFAULT_CONTRACT_SECONDS))
                                        .then(Commands.argument(SECONDS, IntegerArgumentType.integer(
                                                        MIN_CONTRACT_SECONDS, MAX_CONTRACT_SECONDS))
                                                .executes(context -> contract(context,
                                                        IntegerArgumentType.getInteger(context, SECONDS))))))
                                .then(Commands.literal(RECALL).executes(IceAndFireProbeCommands::recall))
                                .then(Commands.literal(SPAWN_SOULFIRE)
                                        .executes(context -> spawnSoulfireDragon(context, "fire"))
                                        .then(Commands.argument(VARIANT, StringArgumentType.word())
                                                .executes(context -> spawnSoulfireDragon(context,
                                                        StringArgumentType.getString(context, VARIANT)))))));
    }

    /**
     * {@code /hungrycraft debug iaf spawn_soulfire_dragon [fire|ice|lightning]} - the entrance of round 7C.
     *
     * <p>It builds remains of the named kind in front of the operator and raises them <b>with the very same code the
     * skill uses</b> (the remains record, the rising and the contract all live in {@code SoulfireRising}), so the
     * command can never produce a unit the rest of the feature would not recognise. The operator becomes the owner, so
     * the unit answers his sceptre at once - which is the whole point of the entrance: to look at the unit and order it
     * around without hunting for a bone dragon in the world first.
     */
    private static int spawnSoulfireDragon(CommandContext<CommandSourceStack> context, String type)
            throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();

        return answer(context, IceAndFireSupport.spawnSoulfireDragon(player, type.toLowerCase(Locale.ROOT),
                IceAndFireSupport.DEFAULT_AGE_DAYS, NecromancyService.CONTRACT_TICKS));
    }

    /**
     * {@code /hungrycraft debug iaf recall}: brings every contract dragon of the operator home (Necromancy IV round 6).
     *
     * <p><b>Why a debug command and not a new mechanic.</b> The round had to make riding testable, and the one thing in
     * the way was that a skeleton bound by the skill or by the probe can be anywhere in the level: the queen has no way
     * to fetch it except the command wheel, which needs the wheel, a selection and a working client. This command is
     * that same gesture without the UI - it calls {@link NecromancyOrders#rally}, which is <b>the very code the wheel's
     * rally uses</b>, so nothing new is introduced, no contract is faked, no ownership is handed out and the balance of
     * survival play is untouched: it is one line of an operator-only command tree
     * ({@code /hungrycraft debug iaf …}, permission level {@value #PROBE_PERMISSION_LEVEL}) that does what a queen can
     * already do.
     */
    private static int recall(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int called = NecromancyOrders.rally(player);

        return answer(context, called > 0
                ? ProbeReport.success(List.of("recalled " + called + " contract dragon(s) of this operator"
                        + " (order cleared, stance FOLLOW, teleported when further than "
                        + "12 blocks away)"))
                : ProbeReport.failure(List.of("no contract dragon of yours is loaded in this level"
                        + " - spawn_bone_dragon then contract first")));
    }

    /** {@code /hungrycraft debug iaf spawn_bone_dragon <type> [age_days]}. */
    private static int spawnBoneDragon(CommandContext<CommandSourceStack> context, int ageDays)
            throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String type = StringArgumentType.getString(context, TYPE).toLowerCase(Locale.ROOT);

        return answer(context, IceAndFireSupport.spawnBoneDragon(player, type, ageDays));
    }

    /** {@code /hungrycraft debug iaf inspect}. */
    private static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return answer(context, IceAndFireSupport.inspect(context.getSource().getPlayerOrException()));
    }

    /** {@code /hungrycraft debug iaf revive}. */
    private static int revive(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return answer(context, IceAndFireSupport.revive(context.getSource().getPlayerOrException()));
    }

    /**
     * {@code /hungrycraft debug iaf lightning_revive}: the same waking up, with a bolt of pure lightning in front of
     * it - the shape the formal skill of the next round will have.
     */
    private static int lightningRevive(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return answer(context, IceAndFireSupport.lightningRevive(context.getSource().getPlayerOrException()));
    }

    /** {@code /hungrycraft debug iaf restore}. */
    private static int restore(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return answer(context, IceAndFireSupport.restore(context.getSource().getPlayerOrException()));
    }

    /** {@code /hungrycraft debug iaf restore_all}. */
    private static int restoreAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return answer(context, IceAndFireSupport.restoreAll(context.getSource().getPlayerOrException()));
    }

    /**
     * {@code /hungrycraft debug iaf contract [seconds]}: wakes the bone dragon under the crosshair and puts it under a
     * contract of the operator, exactly like the formal skill does.
     *
     * <p>It exists so that a live probe of the sceptre does not have to depend on a key binding and on a skill node: one
     * line gives a skeleton on a payroll, and the sceptre can then be tested on it immediately. Every rule of the skill
     * is applied here as well - the same ray, the same refusals, the same waking up, the same visual bolt.
     */
    private static int contract(CommandContext<CommandSourceStack> context, int seconds)
            throws CommandSyntaxException
    {
        return answer(context, IceAndFireSupport.contractInFront(context.getSource().getPlayerOrException(),
                seconds * 20));
    }

    /**
     * Shows a probe report to the operator and writes the very same lines into the log, and turns it into a command
     * result.
     *
     * <p>Both are done on purpose. A probe is read by two people at once: the player in front of the game, who wants
     * the answer now, and whoever reads the log afterwards, who wants the evidence. The prefix
     * {@link IceAndFireSupport#PROBE_LOG_PREFIX} is what makes the second half findable, and nothing here runs every
     * tick, so a log stays readable.
     */
    private static int answer(CommandContext<CommandSourceStack> context, ProbeReport report)
    {
        for (String line : report.lines())
        {
            SpiderQueenHungryCraft.LOGGER.info("{} {}", IceAndFireSupport.PROBE_LOG_PREFIX, line);

            Component text = Component.literal(line);

            if (report.ok())
            {
                context.getSource().sendSuccess(() -> text, false);
            }
            else
            {
                context.getSource().sendFailure(text);
            }
        }

        return report.ok() ? 1 : 0;
    }
}
