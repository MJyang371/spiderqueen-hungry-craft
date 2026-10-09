package spiderqueenhungrycraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.SpiderRewards;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.player.QueenData;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.player.QueenRuntimeSync;
import spiderqueenhungrycraft.player.QueenSilk;
import spiderqueenhungrycraft.progression.ProgressionDefinitions;
import spiderqueenhungrycraft.progression.ProgressionService;
import spiderqueenhungrycraft.progression.ProgressionSessions;
import spiderqueenhungrycraft.progression.QueenLevels;
import spiderqueenhungrycraft.progression.QueenSkills;
import spiderqueenhungrycraft.registry.HungryCraftItems;
import spiderqueenhungrycraft.soul.SoulCleanup;
import spiderqueenhungrycraft.soul.Souls;
import spiderqueenhungrycraft.stomach.StomachCellManager;
import spiderqueenhungrycraft.stomach.StomachSavedData;

/**
 * Development commands for the Spider Queen player data. Only the executing player is supported;
 * selectors and other players are intentionally out of scope for now.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class HungryCraftCommands
{
    private static final String ROOT = "hungrycraft";
    private static final String QUEEN = "queen";
    private static final String GET = "get";
    private static final String SET = "set";
    private static final String VALUE = "value";
    private static final String SILK = "silk";
    private static final String AMOUNT = "amount";
    private static final String STOMACH = "stomach";
    private static final String VISIT = "visit";
    private static final String LEAVE = "leave";
    private static final String OWNER = "owner";
    private static final String REWARD = "reward";
    private static final String EGG_PROBE = "eggprobe";

    /** The debug entry of the Sweatlord (Sweatlord round). */
    private static final String SWEATLORD = "sweatlord";

    /** The soul system of the RPG foundation (Soul Foundation round). */
    private static final String SOULS = "souls";
    private static final String CLEAR_REMNANTS = "clear_remnants";

    /** The growth system of the RPG foundation (Progression Foundation round). */
    private static final String PROGRESSION = "progression";
    private static final String LEVEL = "level";

    /** The skill tree of the growth system (Skill Progression round). */
    private static final String SKILL = "skill";
    private static final String GRANT = "grant";
    private static final String REVOKE = "revoke";
    private static final String LIST = "list";
    private static final String SKILL_ID = "skill_id";
    private static final String PLAYER = "player";

    /** How far the Sweatlord debug command looks for a body to report on. */
    private static final double SWEATLORD_DUMP_RADIUS = 128.0D;
    private static final String ROLLS = "rolls";
    private static final String RECIPES = "recipes";

    /** Matches the vanilla "operator" permission level usually needed for cheat style commands. */
    private static final int SET_PERMISSION_LEVEL = 2;

    /**
     * The bounds the debug command accepts for the two permanent values (Progression Foundation round): wide enough to
     * test any state a player can reach, narrow enough that a slip of a finger can not write an absurd number into a
     * save file.
     */
    private static final int MAX_DEBUG_QUEEN_LEVEL = 100_000;
    private static final long MAX_DEBUG_SOULS = 1_000_000_000_000L;

    /** How far around the position of the probe its eggs are counted, in blocks. */
    private static final double EGG_PROBE_RADIUS = 8.0D;

    /** The most rewards one probe may grant: four thousand is a thousand eggs on average. */
    private static final int EGG_PROBE_MAX_ROLLS = 4000;

    private HungryCraftCommands()
    {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        // The skill tree of the growth system (Skill Progression round) is registered as a second view of the same
        // root. Brigadier merges the two trees by their names, so the commands of the helper below answer under
        // {@code /hungrycraft progression skill ...} exactly like the ones written out here, and neither tree has to
        // be threaded through the other.
        registerSkillCommands(event.getDispatcher());

        // The debug commands of the optional Ice and Fire compatibility (Necromancy IV prototype 1) are a third view
        // of the same root, for the same reason, and they are the only entrance of that compatibility: the formal
        // skill of the next round will call the same facade from a key binding.
        IceAndFireProbeCommands.register(event.getDispatcher());

        event.getDispatcher().register(Commands.literal(ROOT)
                .then(Commands.literal(QUEEN)
                        .then(Commands.literal(GET).executes(HungryCraftCommands::getQueen))
                        .then(Commands.literal(SET)
                                .requires(source -> source.hasPermission(SET_PERMISSION_LEVEL))
                                .then(Commands.argument(VALUE, BoolArgumentType.bool())
                                        .executes(HungryCraftCommands::setQueen))))
                .then(Commands.literal(SILK)
                        .then(Commands.literal(GET).executes(HungryCraftCommands::getSilk))
                        .then(Commands.literal(SET)
                                .requires(source -> source.hasPermission(SET_PERMISSION_LEVEL))
                                .then(Commands.argument(AMOUNT, IntegerArgumentType.integer(0, QueenSilk.MAX_AMOUNT))
                                        .executes(HungryCraftCommands::setSilk))))
                // The Sweatlord debug entry (Sweatlord round): one command that answers "why is he doing that".
                .then(Commands.literal(SWEATLORD).executes(HungryCraftCommands::dumpSweatlord))
                .then(Commands.literal(STOMACH)
                        .requires(source -> source.hasPermission(SET_PERMISSION_LEVEL))
                        .then(Commands.literal(VISIT)
                                .then(Commands.argument(OWNER, EntityArgument.player())
                                        .executes(HungryCraftCommands::visitStomach)))
                        .then(Commands.literal(LEAVE).executes(HungryCraftCommands::leaveStomach)))
                .then(Commands.literal(REWARD)
                        .requires(source -> source.hasPermission(SET_PERMISSION_LEVEL))
                        .then(Commands.literal(EGG_PROBE)
                                .then(Commands.argument(ROLLS, IntegerArgumentType.integer(1, EGG_PROBE_MAX_ROLLS))
                                        .executes(HungryCraftCommands::probeSpiderEggs))))
                .then(Commands.literal(RECIPES)
                        .requires(source -> source.hasPermission(SET_PERMISSION_LEVEL))
                        .executes(HungryCraftCommands::probeRecipes))
                // The emergency brake of the soul system (Soul Foundation round): one command that forgets every
                // remnant record of the server and removes every remnant that happens to be loaded right now.
                .then(Commands.literal(SOULS)
                        .then(Commands.literal(CLEAR_REMNANTS)
                                .requires(source -> source.hasPermission(SET_PERMISSION_LEVEL))
                                .executes(HungryCraftCommands::clearSoulRemnants)))
                // The growth system (Progression Foundation round): a read for anybody who may use the command, and
                // a write for an operator - the two debugging entrances a permanent value needs. There is no way to
                // take a level away here beyond setting a number on purpose, and no way for a player to respec.
                .then(Commands.literal(PROGRESSION)
                        .then(Commands.literal(LEVEL)
                                .then(Commands.literal(GET).executes(HungryCraftCommands::getQueenLevel))
                                .then(Commands.literal(SET)
                                        .requires(source -> source.hasPermission(SET_PERMISSION_LEVEL))
                                        .then(Commands.argument(AMOUNT, IntegerArgumentType.integer(
                                                        QueenData.MIN_QUEEN_LEVEL, MAX_DEBUG_QUEEN_LEVEL))
                                                .executes(HungryCraftCommands::setQueenLevel))))
                        .then(Commands.literal(SOULS)
                                .then(Commands.literal(GET).executes(HungryCraftCommands::getSouls))
                                .then(Commands.literal(SET)
                                        .requires(source -> source.hasPermission(SET_PERMISSION_LEVEL))
                                        .then(Commands.argument(AMOUNT, LongArgumentType.longArg(0L,
                                                        MAX_DEBUG_SOULS))
                                                .executes(HungryCraftCommands::setSouls))))));
    }

    /**
     * The three administrative entrances of the skill tree (Skill Progression round).
     *
     * <p>They exist for a developer and for a server operator and for nobody else: permission level two is asked for
     * here, which is the ordinary operator level, and there is deliberately no player command to give oneself a node -
     * the only way to buy one is the territory core, which checks every rule of the tree on the server.
     *
     * <p>It is registered as a second view of the same root literal, which Brigadier merges into one tree, so the
     * commands answer under {@code /hungrycraft progression skill ...} without the long chain above having to be
     * opened up for them.
     */
    private static void registerSkillCommands(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal(ROOT)
                .then(Commands.literal(PROGRESSION)
                        .then(Commands.literal(SKILL)
                                .requires(source -> source.hasPermission(SET_PERMISSION_LEVEL))
                                .then(Commands.literal(GRANT)
                                        .then(Commands.argument(PLAYER, EntityArgument.player())
                                                .then(Commands.argument(SKILL_ID, ResourceLocationArgument.id())
                                                        .executes(HungryCraftCommands::grantSkill))))
                                .then(Commands.literal(REVOKE)
                                        .then(Commands.argument(PLAYER, EntityArgument.player())
                                                .then(Commands.argument(SKILL_ID, ResourceLocationArgument.id())
                                                        .executes(HungryCraftCommands::revokeSkill))))
                                .then(Commands.literal(LIST)
                                        .then(Commands.argument(PLAYER, EntityArgument.player())
                                                .executes(HungryCraftCommands::listSkills))))));
    }

    /** Gives one node to one player, and brings her running body in line with it right away. */
    private static int grantSkill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer target = EntityArgument.getPlayer(context, PLAYER);
        ResourceLocation skill = ResourceLocationArgument.getId(context, SKILL_ID);

        boolean granted = QueenSkills.unlock(target, skill);
        afterSkillChange(target);

        context.getSource().sendSuccess(() -> Component.literal("skill " + skill + " of "
                + target.getGameProfile().getName() + ": " + (granted ? "granted" : "already owned")), false);

        return granted ? 1 : 0;
    }

    /** Takes one node away from one player, and brings her running body in line with it right away. */
    private static int revokeSkill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer target = EntityArgument.getPlayer(context, PLAYER);
        ResourceLocation skill = ResourceLocationArgument.getId(context, SKILL_ID);

        boolean revoked = QueenSkills.revoke(target, skill);
        afterSkillChange(target);

        context.getSource().sendSuccess(() -> Component.literal("skill " + skill + " of "
                + target.getGameProfile().getName() + ": " + (revoked ? "revoked" : "not owned")), false);

        return revoked ? 1 : 0;
    }

    /** Lists the bought nodes of one player, which is the answer to "what does her save file hold". */
    private static int listSkills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer target = EntityArgument.getPlayer(context, PLAYER);
        Set<ResourceLocation> owned = QueenSkills.of(target);
        String nodes = owned.isEmpty() ? "none"
                : String.join(", ", owned.stream().map(ResourceLocation::toString).sorted().toList());

        context.getSource().sendSuccess(() -> Component.literal("skills of " + target.getGameProfile().getName()
                + " (" + owned.size() + "): " + nodes), false);

        return owned.size();
    }

    /**
     * Pushes a changed permission to that player: her client is told what she owns, her running body is brought in line
     * with it (armor, stomach wall, a beast form that may be gone) and an open growth interface is refreshed, so an
     * operator sees the new node in the window without reopening it.
     */
    private static void afterSkillChange(ServerPlayer target)
    {
        QueenSkills.refreshRuntime(target);
        ProgressionService.sendSkillState(target);

        BlockPos core = ProgressionSessions.openCoreOf(target);

        if (core != null)
        {
            ProgressionService.sendSnapshot(target, core);
        }
    }

    private static int getQueen(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        Player player = context.getSource().getPlayerOrException();
        boolean queen = QueenDataCapability.isQueen(player);
        context.getSource().sendSuccess(() -> Component.literal(QUEEN + "=" + queen), false);
        return 1;
    }

    private static int setQueen(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        Player player = context.getSource().getPlayerOrException();
        boolean value = BoolArgumentType.getBool(context, VALUE);
        QueenDataCapability.setQueen(player, value);
        context.getSource().sendSuccess(() -> Component.literal(QUEEN + "=" + value), false);

        // A changed queen state is pushed to the client of that player right away, so nothing on the client side
        // keeps a stale picture of it until the next respawn or dimension change.
        if (player instanceof ServerPlayer serverPlayer)
        {
            QueenRuntimeSync.syncAll(serverPlayer, "command");
        }

        return 1;
    }

    private static int getSilk(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        Player player = context.getSource().getPlayerOrException();
        int silk = QueenSilk.getSilkAmount(player);
        context.getSource().sendSuccess(
                () -> Component.literal(SILK + "=" + silk + "/" + QueenSilk.MAX_AMOUNT), false);
        return 1;
    }

    /**
     * Development helper of the silk feature: it only exists so the resource can be set for a test without
     * waiting for the glands to spin it. Gameplay itself never writes the amount from a command.
     */
    private static int setSilk(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        Player player = context.getSource().getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(context, AMOUNT);
        QueenSilk.setSilkAmount(player, amount);
        context.getSource().sendSuccess(
                () -> Component.literal(SILK + "=" + QueenSilk.getSilkAmount(player)), false);
        return 1;
    }

    private static int visitStomach(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        ServerPlayer owner = EntityArgument.getPlayer(context, OWNER);
        StomachCellManager.visit(actor, owner.getUUID());
        StomachSavedData data = StomachSavedData.get(actor.getServer());
        int cell = data.indexFor(owner.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("stomach of " + owner.getGameProfile().getName()
                + ": cell " + cell + " at "
                + spiderqueenhungrycraft.stomach.StomachCellGeometry.centerFor(cell).toShortString() + ", cells="
                + data.allocatedCells()), false);
        return 1;
    }

    private static int leaveStomach(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer actor = context.getSource().getPlayerOrException();

        if (!StomachCellManager.leave(actor))
        {
            context.getSource().sendFailure(Component.literal("not inside a stomach"));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("left the stomach"), false);
        return 1;
    }

    /**
     * Development probe of the reward of a digested prey (breeding pass): it grants {@link SpiderRewards} a number of
     * times at the position of the command and counts the spider eggs that really appeared there.
     *
     * <p>It exists because both real ways to earn an egg - a cocoon eaten by hand and a creature that dies inside a
     * stomach - need a queen at a keyboard, so the rate and the place of the reward could not be measured otherwise.
     * The rewards are granted with {@link SpiderRewards#grantForProbe}, the plain roll without the permission of the
     * brood queen: a probe belongs to nobody and hands nothing to a player, so it measures the roll itself rather than
     * the purse of whoever typed the command (Skill Progression round).
     *
     * <p>Nothing about the gameplay changes because of this command: it only grants rewards that a meal would have
     * granted anyway, at the spot the command stands on.
     */
    private static int probeSpiderEggs(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        int rolls = IntegerArgumentType.getInteger(context, ROLLS);

        int before = countSpiderEggs(level, pos);

        for (int roll = 0; roll < rolls; roll++)
        {
            SpiderRewards.grantForProbe(level, pos, 0);
        }

        int laid = countSpiderEggs(level, pos) - before;
        long expected = Math.round(rolls * SpiderRewards.SPIDER_EGG_CHANCE);

        // Both the rate and the place are in this line: the eggs are counted in the very world and around the very
        // position the reward was handed, which is what the queen has to see in her own world.
        SpiderQueenHungryCraft.LOGGER.info("[EGG_PROBE] {} rewards in {} at ({}, {}, {}): {} spider eggs laid,"
                + " expected about {}", rolls, level.dimension().location(), pos.x, pos.y, pos.z, laid, expected);

        context.getSource().sendSuccess(() -> Component.literal("spider eggs: " + laid + " of " + rolls
                + " rewards, expected about " + expected), false);

        return laid;
    }

    /** @return how many spider eggs lie around that spot, the evidence of one probe. */
    private static int countSpiderEggs(ServerLevel level, Vec3 pos)
    {
        List<ItemEntity> eggs = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(pos, pos).inflate(EGG_PROBE_RADIUS),
                entity -> entity.getItem().is(HungryCraftItems.SPIDER_EGG.get()));

        return eggs.size();
    }

    /**
     * The debug entry of the Sweatlord (Sweatlord round).
     *
     * <p>It reports the nearest Sweatlord Steve of the executing player in one line: the state, the running skill, the
     * target and its distance, health, the encounter flags, the projectile threat, the pearl mode and its last refused
     * plan, the gear in both hands, whether he really uses an item and whether he really blocks, and every cooldown of
     * the brain. That is the whole answer to "why is he not shooting", "why is he not pearling", "why is he holding
     * that shield" - the three questions a state machine of this size can not answer from the outside.
     *
     * <p>It reads state and never changes it: no AI decision of this mod depends on this command.
     */
    private static int dumpSweatlord(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        SweatlordSteveEntity nearest = null;
        double best = SWEATLORD_DUMP_RADIUS * SWEATLORD_DUMP_RADIUS;

        for (SweatlordSteveEntity candidate : level.getEntitiesOfClass(SweatlordSteveEntity.class,
                player.getBoundingBox().inflate(SWEATLORD_DUMP_RADIUS)))
        {
            double distance = candidate.distanceToSqr(player);

            if (distance <= best)
            {
                best = distance;
                nearest = candidate;
            }
        }

        if (nearest == null)
        {
            context.getSource().sendFailure(Component.literal(
                    "no Sweatlord Steve within " + (int) SWEATLORD_DUMP_RADIUS + " blocks"));
            return 0;
        }

        final String state = nearest.getDebugState();

        SpiderQueenHungryCraft.LOGGER.info("[Sweatlord] {}", state);
        context.getSource().sendSuccess(() -> Component.literal(state), false);

        return 1;
    }

    /**
     * Development probe of the recipes of this mod: it walks the recipe manager of the server and logs every recipe.
     *
     * <p>It is the answer to "does the recipe view of a mod see everything of this mod?" without starting a client: a
     * recipe viewer of the game reads exactly this list - the recipes the server really loaded - so anything missing,
     * unparsable or of an unknown type is missing there too, and everything listed here is shown there.
     */
    private static int probeRecipes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        MinecraftServer server = context.getSource().getServer();
        RegistryAccess registries = context.getSource().registryAccess();

        List<Recipe<?>> mine = server.getRecipeManager().getRecipes().stream()
                .filter(recipe -> recipe.getId().getNamespace().equals(SpiderQueenHungryCraft.MODID))
                .sorted(Comparator.comparing(recipe -> recipe.getId().toString()))
                .toList();

        int crafting = 0;
        int other = 0;

        for (Recipe<?> recipe : mine)
        {
            if (recipe.getType() == RecipeType.CRAFTING)
            {
                crafting++;
            }
            else
            {
                other++;
            }

            ItemStack result = recipe.getResultItem(registries);

            SpiderQueenHungryCraft.LOGGER.info("[RECIPE_PROBE] {} | type={} | result={} x{} | ingredients={}",
                    recipe.getId(), BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()),
                    BuiltInRegistries.ITEM.getKey(result.getItem()), result.getCount(),
                    describeIngredients(recipe.getIngredients()));
        }

        SpiderQueenHungryCraft.LOGGER.info("[RECIPE_PROBE] {} recipes of this mod are loaded: {} crafting, {} other",
                mine.size(), crafting, other);

        // The line to the caller is built before it is handed to the command source: the counters above are written
        // in the loop, so they can never be the effectively final values a lambda is allowed to read.
        String summary = "recipes of " + SpiderQueenHungryCraft.MODID + ": " + mine.size() + " loaded (" + crafting
                + " crafting, " + other + " other)";

        context.getSource().sendSuccess(() -> Component.literal(summary), false);

        return mine.size();
    }

    /** @return the ingredients of one recipe as one readable line, for the probe above. */
    private static String describeIngredients(NonNullList<Ingredient> ingredients)
    {
        StringBuilder text = new StringBuilder();

        for (Ingredient ingredient : ingredients)
        {
            ItemStack[] items = ingredient.getItems();

            if (items.length == 0)
            {
                continue;
            }

            if (text.length() > 0)
            {
                text.append(", ");
            }

            text.append(BuiltInRegistries.ITEM.getKey(items[0].getItem()));
        }

        return text.toString();
    }

    /**
     * Forgets every soul remnant of this server (Soul Foundation round).
     *
     * <p>It is the emergency brake of the system and it is deliberately not {@code /kill @e}: only that command's
     * counterpart can reach the remnant registry, and a remnant that was never killed but no longer has a record
     * would simply be scenery that can not be collected. So this command does both halves and reports both counts:
     * the active records of every player, and the remnant entities that happened to be loaded.
     *
     * <p>Remnants in chunks that are not loaded are deliberately left where they are. They are no longer anybody's
     * record after this command, so each of them removes itself the next time its chunk ticks - no chunk is loaded
     * and no cost is paid for a server that keeps old remnants of long gone players around.
     */
    private static int clearSoulRemnants(CommandContext<CommandSourceStack> context)
    {
        MinecraftServer server = context.getSource().getServer();
        SoulCleanup.Result cleared = SoulCleanup.clearAll(server);
        String summary = "soul remnants cleared: " + cleared.records() + " records, " + cleared.entities()
                + " loaded entities";

        SpiderQueenHungryCraft.LOGGER.info("[SOULS] {}", summary);
        context.getSource().sendSuccess(() -> Component.literal(summary), true);

        return cleared.records() + cleared.entities();
    }

    /**
     * Reads the permanent growth state of the executing player (Progression Foundation round): the Queen Level
     * against the maximum of this world, so an administrator sees at a glance whether a lowered definition is what
     * keeps a player from buying the next level.
     */
    private static int getQueenLevel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        Player player = context.getSource().getPlayerOrException();
        int level = QueenLevels.getLevel(player);
        int max = ProgressionDefinitions.current().maxLevel();

        context.getSource().sendSuccess(() -> Component.literal(LEVEL + "=" + level + "/" + max), false);
        return level;
    }

    /** Writes the Queen Level of the executing player: the one debugging entrance a permanent value needs. */
    private static int setQueenLevel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        Player player = context.getSource().getPlayerOrException();
        QueenLevels.setLevel(player, IntegerArgumentType.getInteger(context, AMOUNT));
        context.getSource().sendSuccess(() -> Component.literal(LEVEL + "=" + QueenLevels.getLevel(player)), true);
        refreshOpenInterface(player);

        return QueenLevels.getLevel(player);
    }

    /** Reads the souls of the executing player. */
    private static int getSouls(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        Player player = context.getSource().getPlayerOrException();

        context.getSource().sendSuccess(() -> Component.literal(SOULS + "=" + Souls.getSouls(player)), false);
        return 1;
    }

    /** Writes the souls of the executing player, so the shop and the price of a level can be tried out by hand. */
    private static int setSouls(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        Player player = context.getSource().getPlayerOrException();
        Souls.setSouls(player, LongArgumentType.getLong(context, AMOUNT));
        context.getSource().sendSuccess(() -> Component.literal(SOULS + "=" + Souls.getSouls(player)), true);
        refreshOpenInterface(player);

        return 1;
    }

    /**
     * Pushes a changed permanent value into an interface that happens to be open, so a screen can never keep showing
     * a number the server no longer believes.
     */
    private static void refreshOpenInterface(Player player)
    {
        if (!(player instanceof ServerPlayer serverPlayer))
        {
            return;
        }

        BlockPos core = ProgressionSessions.openCoreOf(serverPlayer);

        if (core != null)
        {
            ProgressionService.sendSnapshot(serverPlayer, core);
        }
    }
}
