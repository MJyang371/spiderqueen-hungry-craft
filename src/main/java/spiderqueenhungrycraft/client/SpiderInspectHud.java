package spiderqueenhungrycraft.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import spiderqueenhungrycraft.brood.InfestedSteveEntity;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.brood.SpiderTemporaryCommand;
import spiderqueenhungrycraft.compat.iceandfire.ClientNecromancyState;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyContract;

/**
 * The inspect HUD of the queen sceptre (Brood Selection + Inspect HUD round).
 *
 * <p>While the crosshair of the sceptre rests on one of <b>her own</b> spiders, a small panel lists what that spider is:
 * its life, its armour, its order, the errand it is on, the effects that are burning on it and the state of its ability.
 * The moment the crosshair moves on, the panel is gone - it is a look at one spider, not a management screen.
 *
 * <p>Nothing here is asked from the server. Life, armour and effects are values the client already has for every entity
 * it can see, order and errand are read from the spider itself, and only two values of the spider travel: the end time of
 * its ability rest (written once when the ability is cast and once when it is over) and the stock of medicine of an
 * infested Steve (written only while it changes). Looking at a spider costs no packet.
 */
@OnlyIn(Dist.CLIENT)
public final class SpiderInspectHud implements IGuiOverlay
{
    /** The colour of the panel behind the lines. */
    private static final int BACKGROUND = 0x90101010;

    private static final int TITLE_COLOUR = 0xFFE9C46A;
    private static final int VALUE_COLOUR = 0xFFFFFFFF;

    private static final int LINE_HEIGHT = 10;

    /** The size of a rank icon, and how it sits on the line of the name of the spider. */
    private static final int RANK_ICON_SIZE = 16;
    private static final int RANK_ICON_GAP = 2;
    private static final int RANK_ICON_OVERHANG = 4;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height)
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || minecraft.level == null || minecraft.screen != null
                || !minecraft.options.getCameraType().isFirstPerson())
        {
            return;
        }

        Entity hovered = QueenScepterVisuals.hoveredTarget(minecraft.level);

        if (hovered == null)
        {
            return;
        }

        // A contract dragon of this queen shows her its own panel (Necromancy IV round): life, armour, standing order,
        // errand and ability, in the very shape a spider of the brood is shown. Everything it draws was told to this
        // client by the server, so a client can never show itself a dragon that is not its own - and an entity id that
        // was recycled onto something else is simply not drawn.
        ClientNecromancyState.Mirror dragon = ClientNecromancyState.of(hovered.getId());

        if (dragon != null && dragon.mine() && hovered instanceof LivingEntity living)
        {
            renderPanel(minecraft, graphics, linesForDragon(living, dragon), height);

            return;
        }

        // Only a spider of this queen gets the panel: an enemy keeps its outline and nothing else.
        if (!(hovered instanceof SpiderMinionEntity spider) || !spider.isOwnedBy(player))
        {
            return;
        }

        renderSpiderPanel(minecraft, graphics, spider, linesFor(spider), height);
    }

    /** Draws one panel of lines, with the rank icon of a spider in front of its name when it has one. */
    private static void renderPanel(Minecraft minecraft, GuiGraphics graphics, List<Component> lines, int height)
    {
        int textWidth = 0;

        for (Component line : lines)
        {
            textWidth = Math.max(textWidth, minecraft.font.width(line));
        }

        int left = 8;
        int top = Math.max(24, height / 3);

        graphics.fill(left - 4, top - 4, left + textWidth + 6, top + lines.size() * LINE_HEIGHT + 2, BACKGROUND);

        for (int index = 0; index < lines.size(); index++)
        {
            graphics.drawString(minecraft.font, lines.get(index), left, top + index * LINE_HEIGHT,
                    index == 0 ? TITLE_COLOUR : VALUE_COLOUR, true);
        }
    }

    /**
     * Draws the panel of one spider, with the rank icon of the veterancy round in front of its name.
     *
     * <p>The icon is the only difference to the panel of a dragon: no star, no "Level 3", no "Rank 2" - the icon of rank
     * one is nothing at all, so an ordinary spider shows exactly the panel it always showed.
     */
    private static void renderSpiderPanel(Minecraft minecraft, GuiGraphics graphics, SpiderMinionEntity spider,
            List<Component> lines, int height)
    {
        ResourceLocation rankIcon = spider.getRank().icon();
        int titleIndent = rankIcon == null ? 0 : RANK_ICON_SIZE + RANK_ICON_GAP;
        int textWidth = 0;

        for (Component line : lines)
        {
            textWidth = Math.max(textWidth, minecraft.font.width(line));
        }

        int left = 8;
        int top = Math.max(24, height / 3);

        graphics.fill(left - 4, top - 4, left + textWidth + titleIndent + 6,
                top + lines.size() * LINE_HEIGHT + 2, BACKGROUND);

        if (rankIcon != null)
        {
            graphics.blit(rankIcon, left, top - RANK_ICON_OVERHANG, 0, 0, RANK_ICON_SIZE, RANK_ICON_SIZE,
                    RANK_ICON_SIZE, RANK_ICON_SIZE);
        }

        for (int index = 0; index < lines.size(); index++)
        {
            graphics.drawString(minecraft.font, lines.get(index), index == 0 ? left + titleIndent : left,
                    top + index * LINE_HEIGHT, index == 0 ? TITLE_COLOUR : VALUE_COLOUR, true);
        }
    }

    /**
     * @return the panel of one contract dragon of this queen, line by line (Necromancy IV round).
     *
     * <p>It is the very shape a spider of the brood is shown in: life, armour, the standing order, the errand and the
     * ability. The ability of a skeleton is the bombardment, and a skeleton that is too young to breathe shows the same
     * clean "None" an imp spider shows - which is what the round asked for.
     */
    private static List<Component> linesForDragon(LivingEntity dragon, ClientNecromancyState.Mirror mirror)
    {
        List<Component> lines = new ArrayList<>();

        lines.add(dragon.getDisplayName());
        lines.add(text("hud.spiderqueen_hungrycraft.health", (int) dragon.getHealth(), (int) dragon.getMaxHealth()));
        lines.add(text("hud.spiderqueen_hungrycraft.armor", dragon.getArmorValue()));

        SpiderOrderMode[] stances = SpiderOrderMode.values();
        NecromancyContract.Order[] orders = NecromancyContract.Order.values();

        if (mirror.stance() >= 0 && mirror.stance() < stances.length)
        {
            lines.add(text("hud.spiderqueen_hungrycraft.order",
                    Component.translatable(stances[mirror.stance()].translationKey())));
        }

        if (mirror.order() >= 0 && mirror.order() < orders.length)
        {
            lines.add(text("hud.spiderqueen_hungrycraft.task",
                    Component.translatable(errandKey(orders[mirror.order()]))));
        }

        if (!mirror.canBreathe())
        {
            lines.add(text("hud.spiderqueen_hungrycraft.skill",
                    Component.translatable("hud.spiderqueen_hungrycraft.skill.none")));

            return lines;
        }

        lines.add(text("hud.spiderqueen_hungrycraft.skill_state",
                Component.translatable("hud.spiderqueen_hungrycraft.dragon_skill"),
                Component.translatable("hud.spiderqueen_hungrycraft.ability_ready")));

        return lines;
    }

    /** @return the language key of one errand of a contract, which is the task line of the same panel. */
    private static String errandKey(NecromancyContract.Order order)
    {
        return switch (order)
        {
            case NONE -> "hud.spiderqueen_hungrycraft.task_none";
            case MOVE -> "hud.spiderqueen_hungrycraft.task_move";
            case ATTACK -> "hud.spiderqueen_hungrycraft.task_attack";
            case BOMBARD -> "hud.spiderqueen_hungrycraft.task_bombard";
        };
    }

    /** @return the panel of that spider, line by line. */
    private static List<Component> linesFor(SpiderMinionEntity spider)
    {
        List<Component> lines = new ArrayList<>();

        lines.add(spider.getDisplayName());
        lines.add(text("hud.spiderqueen_hungrycraft.health", (int) spider.getHealth(), (int) spider.getMaxHealth()));
        lines.add(text("hud.spiderqueen_hungrycraft.armor", spider.getArmorValue()));

        // The one thing about a body that its look can never tell: how much medicine it still carries (golden apple
        // round). It is the number the queen could not see when she reported "it seems not to take the apples".
        if (spider instanceof InfestedSteveEntity body)
        {
            lines.add(text("hud.spiderqueen_hungrycraft.medicine", body.visibleGoldenAppleCount()));
        }
        lines.add(text("hud.spiderqueen_hungrycraft.order",
                Component.translatable(spider.getOrderMode().translationKey())));
        lines.add(text("hud.spiderqueen_hungrycraft.task",
                Component.translatable(taskKey(spider.getTemporaryCommand()))));

        for (MobEffectInstance effect : spider.getActiveEffects())
        {
            lines.add(text("hud.spiderqueen_hungrycraft.effect", effect.getEffect().getDisplayName(),
                    roman(effect.getAmplifier()), MobEffectUtil.formatDuration(effect, 1.0F)));
        }

        lines.add(abilityLine(spider));

        return lines;
    }

    /**
     * The last line: the skill of that spider and the state it is in right now.
     *
     * <p>A spider without a skill - an imp spider, an ordinary spider of the brood - never shows an empty placeholder,
     * a raw key or a null: it shows one clean line, "Skill: None" / "技能：无" (Special Spiders round I). A spider with a
     * skill shows it in brackets behind the name: the ammunition it has loaded while it is ready, its cooldown while it
     * rests, or the word for a running effect such as the rage of a zombie spider.
     */
    private static Component abilityLine(SpiderMinionEntity spider)
    {
        if (!spider.hasAbility())
        {
            return text("hud.spiderqueen_hungrycraft.skill",
                    Component.translatable("hud.spiderqueen_hungrycraft.skill.none"));
        }

        int remaining = spider.getAbilityCooldownRemainingNow();

        if (spider.isAbilityActive())
        {
            return text("hud.spiderqueen_hungrycraft.skill_state", spider.getAbilityDisplayName(),
                    Component.translatable("hud.spiderqueen_hungrycraft.ability_active"));
        }

        if (remaining > 0)
        {
            return text("hud.spiderqueen_hungrycraft.skill_state", spider.getAbilityDisplayName(),
                    Component.translatable("hud.spiderqueen_hungrycraft.ability_cooldown", (remaining + 19) / 20));
        }

        Component detail = spider.getAbilityStateDetail();

        return text("hud.spiderqueen_hungrycraft.skill_state", spider.getAbilityDisplayName(),
                detail == null ? Component.translatable("hud.spiderqueen_hungrycraft.ability_ready") : detail);
    }

    private static String taskKey(SpiderTemporaryCommand command)
    {
        return switch (command)
        {
            case NONE -> "hud.spiderqueen_hungrycraft.task_none";
            case MOVE_TO -> "hud.spiderqueen_hungrycraft.task_move";
            case FORCED_ATTACK -> "hud.spiderqueen_hungrycraft.task_attack";
        };
    }

    private static Component text(String key, Object... arguments)
    {
        return Component.translatable(key, arguments);
    }

    /** @return the roman numeral of an effect amplifier, which is how the game shows "Speed II". */
    private static String roman(int amplifier)
    {
        return switch (amplifier)
        {
            case 0 -> "I";
            case 1 -> "II";
            case 2 -> "III";
            case 3 -> "IV";
            case 4 -> "V";
            case 5 -> "VI";
            default -> Integer.toString(amplifier + 1);
        };
    }
}
