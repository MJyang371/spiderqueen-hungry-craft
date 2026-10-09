package spiderqueenhungrycraft.necromancy;

import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import spiderqueenhungrycraft.compat.iceandfire.IceAndFireSupport;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyService;
import spiderqueenhungrycraft.compat.iceandfire.ProbeReport;

/**
 * The creative-mode debug items of the necromancy feature (Necromancy IV round, the two entrances of the basic fix).
 *
 * <p>Two items, one implementation:
 * <ul>
 * <li><b>dormant</b> - a bone dragon that has <b>not</b> been awakened: the corpse Ice and Fire's own world generation
 * would leave in a desert, placed in front of the player so that the waking of a dragon can be tested at all,</li>
 * <li><b>awakened</b> - a bone dragon that is awake <b>and belongs to the player who placed it</b>, under the ordinary
 * ten-minute contract, which is the whole point of the fix: a dragon that was woken out of its bones is the property of
 * whoever woke it, so it can be selected, commanded and ridden by him like any other skeleton on the payroll.</li>
 * </ul>
 *
 * <p><b>Nothing here is a new rule.</b> Both halves call the very same entrances the debug command and the skill use
 * ({@link IceAndFireSupport#spawnBoneDragon} and {@link IceAndFireSupport#spawnAwakenedDragon}), so the item can not
 * hand out an ownership the rest of the feature would not recognise, can not produce a dragon the refusal checks would
 * refuse, and can not survive as a second way to spawn a dragon that later drifts away from the first one. A world
 * without Ice and Fire is handled by the same facade: the item says so and does nothing.
 *
 * <p>It is a debug entrance in the sense the round means: it is in the creative tab, it is not craftable, and the
 * ordinary way to get a skeleton is still the skill (or the bone dragon that already lies in the world).
 */
public class NecromancyDebugItem extends Item
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How long the contract of the awakened item lasts: read from the one place that owns the ten minutes. */
    private static final int CONTRACT_TICKS = NecromancyService.CONTRACT_TICKS;

    /** The kind of dragon both items build: the fire dragon, the one every probe of this feature uses. */
    private static final String DRAGON_TYPE = "fire";

    /**
     * Their age: seventy five days is stage four - old enough to be ridden and to breathe, and the same default the
     * debug command uses.
     */
    private static final int AGE_DAYS = IceAndFireSupport.DEFAULT_AGE_DAYS;

    /** Ten ticks between two spawns, so that a held mouse button can not fill a world with skeletons by accident. */
    private static final int COOLDOWN_TICKS = 10;

    /** True for the item that wakes the dragon up and takes it under the contract of the player who placed it. */
    private final boolean awakened;

    public NecromancyDebugItem(boolean awakened, Properties properties)
    {
        super(properties);
        this.awakened = awakened;
    }

    /** Right click on a block: the same spawn as the air right click, so aiming is never required. */
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();

        if (player == null)
        {
            return InteractionResult.PASS;
        }

        return spawn(context.getLevel(), player) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** Right click in the air: the entrance of both items. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        boolean placed = spawn(level, player);

        return new InteractionResultHolder<>(placed ? InteractionResult.SUCCESS : InteractionResult.FAIL,
                player.getItemInHand(hand));
    }

    /**
     * Does the work, on the server only.
     *
     * <p>On a client it answers "yes" and does nothing else - the same division every command of this mod uses: the
     * click is asked on the client, the dragon is built on the server, and the server is the only side that could build
     * one.
     */
    private boolean spawn(Level level, Player player)
    {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer queen))
        {
            return true;
        }

        if (queen.getCooldowns().isOnCooldown(this))
        {
            return false;
        }

        queen.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        if (!IceAndFireSupport.present())
        {
            // The same sentence the skill says, and it is the honest one: an item that spawns a dragon of another mod
            // can do nothing in a world that does not have that mod.
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.necromancy.no_support"));

            return false;
        }

        ProbeReport report = awakened
                ? IceAndFireSupport.spawnSoulfireDragon(queen, DRAGON_TYPE, AGE_DAYS, CONTRACT_TICKS)
                : IceAndFireSupport.spawnBoneDragon(queen, DRAGON_TYPE, AGE_DAYS);

        // Every line the probe command would have written is written here too, under the same prefix, so a debug item
        // and a debug command can be read out of one log with one filter.
        for (String line : report.lines())
        {
            LOGGER.info("{} {}", IceAndFireSupport.PROBE_LOG_PREFIX, line);
        }

        if (!report.ok())
        {
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.necromancy.debug_failed"));

            return false;
        }

        tell(queen, awakened
                ? Component.translatable("message.spiderqueen_hungrycraft.necromancy.debug_awakened",
                        CONTRACT_TICKS / 20 / 60)
                : Component.translatable("message.spiderqueen_hungrycraft.necromancy.debug_dormant"));

        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag)
    {
        lines.add(Component.translatable(awakened
                ? "item.spiderqueen_hungrycraft.awakened_bone_dragon.desc"
                : "item.spiderqueen_hungrycraft.dormant_bone_dragon.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.spiderqueen_hungrycraft.bone_dragon_debug.recipe")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** One line on the action bar, never in the chat: the same rule the commands of the brood follow. */
    private static void tell(ServerPlayer queen, Component message)
    {
        if (queen.connection != null)
        {
            queen.displayClientMessage(message, true);
        }
    }
}
