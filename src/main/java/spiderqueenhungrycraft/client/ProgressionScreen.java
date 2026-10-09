package spiderqueenhungrycraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import spiderqueenhungrycraft.network.C2SProgressionPurchasePacket;
import spiderqueenhungrycraft.network.C2SProgressionSkillPacket;
import spiderqueenhungrycraft.network.C2SProgressionUpgradePacket;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.progression.ProgressionFeedback;
import spiderqueenhungrycraft.progression.ProgressionSnapshot;
import spiderqueenhungrycraft.progression.QueenSkills;
import spiderqueenhungrycraft.progression.SkillNodeDefinition;
import spiderqueenhungrycraft.progression.SkillState;

/**
 * The growth interface of a territory core (Progression Foundation round): one window with three tabs, drawn in the
 * visual language of the vanilla advancements screen.
 *
 * <p><b>It is not the advancement system.</b> No advancement is created, registered or granted by any of this - the
 * window frame, the tab strip, the node frames, the connection lines and the tooltip panel are the very textures the
 * game uses for its own screen, so a queen recognises what she is looking at, and everything behind it is the
 * progression system of this mod and nothing else.
 *
 * <p>The trunk of two hundred levels is never drawn as two hundred widgets: only the levels inside the viewport (plus
 * one node of buffer) are laid out and painted, and the scroll position decides which ones those are. A player who
 * opens the interface at level one hundred and forty sees level one hundred and forty, not level one.
 *
 * <p>The screen decides nothing. A click is a request: it names a level or a product and sends it, and the server
 * answers with the state and the verdict. Nothing here is trusted by the server, and nothing here can spend a soul.
 */
@OnlyIn(Dist.CLIENT)
public final class ProgressionScreen extends Screen
{
    /** The three textures of the vanilla advancements screen: the window frame, the node sheet and the tab strip. */
    public static final ResourceLocation WINDOW =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");
    public static final ResourceLocation WIDGETS =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/widgets.png");
    public static final ResourceLocation TABS =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/tabs.png");

    /** The window of the game, and the inside of it a page draws in. */
    public static final int WINDOW_WIDTH = 252;
    public static final int WINDOW_HEIGHT = 140;
    public static final int INSIDE_X = 9;
    public static final int INSIDE_Y = 18;
    public static final int INSIDE_WIDTH = 234;
    public static final int INSIDE_HEIGHT = 113;

    /** The tabs above the window: the size and pitch of the tab strip of the game, and its three sprites. */
    private static final int TAB_WIDTH = 28;
    private static final int TAB_HEIGHT = 32;
    private static final int TAB_STEP = 32;
    private static final int TAB_ABOVE_Y = -28;
    private static final int TAB_SPRITE_FIRST = 0;
    private static final int TAB_SPRITE_MIDDLE = 28;
    private static final int TAB_SPRITE_LAST = 56;
    private static final int TAB_SPRITE_V_SELECTED = 32;

    /** One node of the vanilla widget sheet: a frame of this size, and its two vertical states. */
    private static final int FRAME_SIZE = 26;
    private static final int FRAME_V_OBTAINED = 128;
    private static final int FRAME_V_UNOBTAINED = 154;

    /** The distance between two levels of the trunk: a frame plus a piece of visible line. */
    private static final int NODE_STEP = 40;

    /**
     * How far out one lane of the skill tree stands (Skill Progression round): a frame plus a piece of visible line and
     * a small gap, so a node of lane one and the frame of the trunk never touch, and lane two never covers lane one.
     */
    private static final int LANE_STEP = 38;

    /** The gold of a node that can be bought right now, which is what a click would answer. */
    private static final int COLOR_SKILL_AVAILABLE = 0xFFFFD24A;

    /** The three icons of a level: what is done, what is next, and what is still far away. */
    private static final ItemStack ICON_COMPLETED = new ItemStack(Items.IRON_INGOT);
    private static final ItemStack ICON_NEXT = new ItemStack(Items.GOLD_INGOT);
    private static final ItemStack ICON_FUTURE = new ItemStack(Items.COPPER_INGOT);

    /** The icons of the three tabs. */
    private static final ItemStack TAB_ICON_GROWTH = new ItemStack(Items.IRON_INGOT);
    private static final ItemStack TAB_ICON_SHOP = new ItemStack(Items.SPIDER_EYE);
    private static final ItemStack TAB_ICON_LEGEND = new ItemStack(Items.NETHER_STAR);

    /** How far one step of the wheel scrolls, and how many nodes past the edge are still drawn. */
    private static final int SCROLL_STEP = 24;
    private static final int BUFFER_NODES = 1;

    /** The card of one product of the shop: its height, its margin, and the button that buys it. */
    private static final int CARD_HEIGHT = 34;
    private static final int CARD_MARGIN = 8;
    private static final int BUTTON_WIDTH = 56;
    private static final int BUTTON_HEIGHT = 16;

    /** How dark a level that can not be bought yet is drawn, and the colours of the window. */
    private static final int COLOR_FUTURE_SHADE = 0xB8000000;
    private static final int COLOR_TITLE = 4210752;
    private static final int COLOR_TEXT = 0xFFE8E8E8;
    private static final int COLOR_DIM = 0xFF9E9E9E;
    private static final int COLOR_HINT = 0xFFA0E0A0;
    private static final int COLOR_LINE_DARK = 0xFF000000;
    private static final int COLOR_LINE_LIGHT = 0xFFFFFFFF;

    /** The pages of the window. The legendary one is shown from the first day and locked, on purpose. */
    private enum Page
    {
        GROWTH,
        SHOP,
        LEGEND
    }

    /** The three states a level can be in for a player. */
    private enum NodeState
    {
        COMPLETED,
        NEXT,
        FUTURE
    }

    /** The last state the server sent; never computed here. */
    private ProgressionSnapshot snapshot;

    private Page page = Page.GROWTH;

    /** How far the trunk and the list of products are scrolled. */
    private double trunkScroll;
    private double shopScroll;

    /** The state of a drag over the trunk: panning is the second way to travel it, next to the wheel. */
    private boolean dragging;
    private double dragAnchorY;
    private double dragStartScroll;

    /** What the mouse is over this frame, for the tooltips. */
    private int hoveredLevel = -1;
    private ProgressionSnapshot.ShopEntryView hoveredProduct;

    /** The node of the skill tree the mouse is over, if any (Skill Progression round). */
    private ProgressionSnapshot.SkillNodeView hoveredSkill;

    public ProgressionScreen(ProgressionSnapshot snapshot)
    {
        super(Component.translatable("gui.spiderqueen_hungrycraft.progression.title"));
        this.snapshot = snapshot;
    }

    /** Applies a fresh picture of the server state; the trunk is centred on the player when it is the first one. */
    public void refresh(ProgressionSnapshot fresh)
    {
        boolean first = this.snapshot == null;
        this.snapshot = fresh;

        if (first)
        {
            this.centerOnCurrentLevel();
        }
    }

    /** The screen of a core is a screen of the game: the world keeps running behind it. */
    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    protected void init()
    {
        super.init();

        if (this.snapshot != null)
        {
            this.centerOnCurrentLevel();
        }
    }

    /** Puts the level the player is on, and the one step she may take next, into the middle of the window. */
    private void centerOnCurrentLevel()
    {
        if (this.snapshot == null)
        {
            return;
        }

        this.trunkScroll = -(double)(this.snapshot.queenLevel() - 1) * NODE_STEP + INSIDE_HEIGHT / 2.0D;
        this.clampTrunkScroll();
    }

    private int windowLeft()
    {
        return (this.width - WINDOW_WIDTH) / 2;
    }

    private int windowTop()
    {
        return (this.height - WINDOW_HEIGHT) / 2;
    }

    private int contentLeft()
    {
        return this.windowLeft() + INSIDE_X;
    }

    private int contentTop()
    {
        return this.windowTop() + INSIDE_Y;
    }

    /** The left edge of one tab, in screen coordinates. */
    private int tabLeft(int index)
    {
        return this.windowLeft() + TAB_STEP * index;
    }

    private int tabTop()
    {
        return this.windowTop() + TAB_ABOVE_Y;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        ProgressionClientState.tickFeedback();
        this.hoveredLevel = -1;
        this.hoveredProduct = null;
        this.hoveredSkill = null;

        this.renderBackground(graphics);

        int left = this.windowLeft();
        int top = this.windowTop();

        graphics.blit(WINDOW, left, top, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        graphics.drawString(this.font, this.title, left + 8, top + 6, COLOR_TITLE, false);
        graphics.fill(left + INSIDE_X, top + INSIDE_Y, left + INSIDE_X + INSIDE_WIDTH,
                top + INSIDE_Y + INSIDE_HEIGHT, 0xB0101010);

        switch (this.page)
        {
            case GROWTH -> this.renderGrowth(graphics, mouseX, mouseY);
            case SHOP -> this.renderShop(graphics, mouseX, mouseY);
            case LEGEND -> this.renderLegend(graphics);
        }

        this.renderTabs(graphics, mouseX, mouseY);
        this.renderFeedback(graphics, left, top);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** The answer of the server, in the one line under the window. */
    private void renderFeedback(GuiGraphics graphics, int left, int top)
    {
        Component line = ProgressionClientState.feedbackMessage();

        if (line != null)
        {
            graphics.drawCenteredString(this.font, line, left + WINDOW_WIDTH / 2, top + WINDOW_HEIGHT + 4,
                    COLOR_HINT);
        }
    }

    /** The three tabs, in the shapes and colours the game uses for its own tab strip. */
    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY)
    {
        Page[] pages = Page.values();

        for (int index = 0; index < pages.length; index++)
        {
            Page tab = pages[index];
            int u = index == 0 ? TAB_SPRITE_FIRST
                    : (index == pages.length - 1 ? TAB_SPRITE_LAST : TAB_SPRITE_MIDDLE);
            int v = tab == this.page ? TAB_SPRITE_V_SELECTED : 0;
            int x = this.tabLeft(index);
            int y = this.tabTop();

            graphics.blit(TABS, x, y, u, v, TAB_WIDTH, TAB_HEIGHT);
            graphics.renderFakeItem(this.tabIcon(tab), x + 6, y + 9);

            if (this.isOverTab(index, mouseX, mouseY))
            {
                graphics.renderTooltip(this.font, Component.translatable(this.tabTitleKey(tab)), mouseX, mouseY);
            }
        }
    }

    private ItemStack tabIcon(Page tab)
    {
        return switch (tab)
        {
            case GROWTH -> TAB_ICON_GROWTH;
            case SHOP -> TAB_ICON_SHOP;
            case LEGEND -> TAB_ICON_LEGEND;
        };
    }

    private String tabTitleKey(Page tab)
    {
        return switch (tab)
        {
            case GROWTH -> "gui.spiderqueen_hungrycraft.progression.tab.growth";
            case SHOP -> "gui.spiderqueen_hungrycraft.progression.tab.shop";
            case LEGEND -> "gui.spiderqueen_hungrycraft.progression.tab.legend";
        };
    }

    private boolean isOverTab(int index, double mouseX, double mouseY)
    {
        int x = this.tabLeft(index);
        int y = this.tabTop();

        return mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT;
    }

    // ------------------------------------------------------------------ the trunk of levels

    /** Draws the levels and the skill nodes inside the viewport - and a single node of buffer - and nothing else. */
    private void renderGrowth(GuiGraphics graphics, int mouseX, int mouseY)
    {
        if (this.snapshot == null)
        {
            return;
        }

        int current = this.snapshot.queenLevel();
        int first = Math.max(1, this.firstVisibleLevel() - BUFFER_NODES);
        int last = Math.min(this.highestLevel(), this.lastVisibleLevel() + BUFFER_NODES);
        int centreX = this.contentLeft() + INSIDE_WIDTH / 2;
        int nodeX = centreX - FRAME_SIZE / 2;

        graphics.enableScissor(this.contentLeft(), this.contentTop(), this.contentLeft() + INSIDE_WIDTH,
                this.contentTop() + INSIDE_HEIGHT);

        // The branches of the tree first, then every frame: a line never lies on top of a node, whatever the branches
        // do (Skill Progression round).
        this.renderSkillBranches(graphics, centreX);

        for (int level = Math.max(2, first); level <= last; level++)
        {
            this.drawLink(graphics, centreX, this.nodeY(level - 1) + FRAME_SIZE, this.nodeY(level),
                    level <= current);
        }

        this.renderSkillNodes(graphics, centreX, mouseX, mouseY);

        for (int level = first; level <= last; level++)
        {
            int y = this.nodeY(level);
            this.drawLevelNode(graphics, nodeX, y, this.stateOf(level));

            if (this.isOverNode(nodeX, y, mouseX, mouseY))
            {
                this.hoveredLevel = level;
            }
        }

        graphics.disableScissor();

        // The node of the tree wins the tooltip when the mouse is on one: the branches stand beside the trunk, so both
        // can never be hovered at the same time.
        if (this.hoveredSkill != null)
        {
            this.renderSkillTooltip(graphics, this.hoveredSkill, mouseX, mouseY);
        }
        else if (this.hoveredLevel > 0)
        {
            this.renderLevelTooltip(graphics, this.hoveredLevel, mouseX, mouseY);
        }
        else
        {
            this.renderScrollHint(graphics);
        }
    }

    /** @return the highest level the trunk draws: the maximum of the world, or the level of a player above it. */
    private int highestLevel()
    {
        return Math.max(this.snapshot.queenLevel(), Math.max(1, this.snapshot.definition().maxLevel()));
    }

    /** @return the first level that can be seen in the viewport at the current scroll. */
    private int firstVisibleLevel()
    {
        return 1 + Mth.floor(-this.trunkScroll / NODE_STEP);
    }

    /** @return the last level that can be seen in the viewport at the current scroll. */
    private int lastVisibleLevel()
    {
        return 1 + Mth.ceil((INSIDE_HEIGHT - this.trunkScroll) / NODE_STEP);
    }

    private void clampTrunkScroll()
    {
        double contentHeight = (double)(this.highestLevel() - 1) * NODE_STEP + FRAME_SIZE;
        double lowest = Math.min(0.0D, INSIDE_HEIGHT - contentHeight);

        this.trunkScroll = Mth.clamp(this.trunkScroll, lowest, 0.0D);
    }

    /** The vertical position of one level on the screen. */
    private int nodeY(int level)
    {
        return this.contentTop() + (int)Math.round((level - 1) * (double)NODE_STEP + this.trunkScroll);
    }

    /** The state of one level for the player, from the value the server sent and from nothing else. */
    private NodeState stateOf(int level)
    {
        int current = this.snapshot.queenLevel();

        if (level <= current)
        {
            return NodeState.COMPLETED;
        }

        return level == current + 1 ? NodeState.NEXT : NodeState.FUTURE;
    }

    /** One connection between two levels, in the two passes the game uses for its own lines. */
    private void drawLink(GuiGraphics graphics, int x, int fromY, int toY, boolean walked)
    {
        graphics.vLine(x - 1, fromY, toY, COLOR_LINE_DARK);
        graphics.vLine(x, fromY, toY, COLOR_LINE_DARK);
        graphics.vLine(x + 1, fromY, toY, COLOR_LINE_DARK);

        if (walked)
        {
            graphics.vLine(x, fromY, toY, COLOR_LINE_LIGHT);
        }
    }

    /** One level: the frame of the game, the icon of its state, and a shade over what can not be bought yet. */
    private void drawLevelNode(GuiGraphics graphics, int x, int y, NodeState state)
    {
        int v = state == NodeState.FUTURE ? FRAME_V_UNOBTAINED : FRAME_V_OBTAINED;

        graphics.blit(WIDGETS, x, y, 0, v, FRAME_SIZE, FRAME_SIZE);
        graphics.renderFakeItem(iconOf(state), x + 5, y + 5);

        if (state == NodeState.FUTURE)
        {
            graphics.fill(x + 3, y + 3, x + FRAME_SIZE - 3, y + FRAME_SIZE - 3, COLOR_FUTURE_SHADE);
        }
    }

    private ItemStack iconOf(NodeState state)
    {
        return switch (state)
        {
            case COMPLETED -> ICON_COMPLETED;
            case NEXT -> ICON_NEXT;
            case FUTURE -> ICON_FUTURE;
        };
    }

    private boolean isOverNode(int nodeX, int y, double mouseX, double mouseY)
    {
        return mouseX >= nodeX && mouseX < nodeX + FRAME_SIZE && mouseY >= y && mouseY < y + FRAME_SIZE;
    }

    // ------------------------------------------------------------------ the skill tree

    /** The horizontal position of one node of the tree: the trunk in the middle, lane one and lane two beside it. */
    private int skillNodeX(int centreX, ProgressionSnapshot.SkillNodeView node)
    {
        int offset = Math.max(1, node.lane()) * LANE_STEP;

        return node.side() == SkillNodeDefinition.Side.LEFT ? centreX - offset - FRAME_SIZE : centreX + offset;
    }

    /** The vertical position of one node: the rung of the trunk it hangs on, so it travels with the scroll. */
    private int skillNodeY(ProgressionSnapshot.SkillNodeView node)
    {
        return this.nodeY(node.anchorLevel());
    }

    /**
     * The state of one node for this player, decided by the very rule the server enforces - the same method, the same
     * level and the same set of bought skills, which is why the frame of a node and the answer of a click agree.
     */
    private SkillState skillState(ProgressionSnapshot.SkillNodeView node)
    {
        return QueenSkills.stateOf(this.snapshot.queenLevel(), SkillClientState.unlocked(), node.requiredLevel(),
                node.id(), node.parents());
    }

    /** @return the node under the mouse, or null when there is none. */
    private ProgressionSnapshot.SkillNodeView skillAt(double mouseX, double mouseY)
    {
        if (this.snapshot == null)
        {
            return null;
        }

        int centreX = this.contentLeft() + INSIDE_WIDTH / 2;

        for (ProgressionSnapshot.SkillNodeView node : this.snapshot.skills())
        {
            int x = this.skillNodeX(centreX, node);
            int y = this.skillNodeY(node);

            if (mouseX >= x && mouseX < x + FRAME_SIZE && mouseY >= y && mouseY < y + FRAME_SIZE)
            {
                return node;
            }
        }

        return null;
    }

    /** @return the node of that identity in the picture the server sent, or null when the tree does not hold it. */
    private ProgressionSnapshot.SkillNodeView skillNode(ResourceLocation id)
    {
        for (ProgressionSnapshot.SkillNodeView node : this.snapshot.skills())
        {
            if (node.id().equals(id))
            {
                return node;
            }
        }

        return null;
    }

    /** Draws every branch of the tree: the link to the rung of the trunk, and the link of every prerequisite. */
    private void renderSkillBranches(GuiGraphics graphics, int centreX)
    {
        for (ProgressionSnapshot.SkillNodeView node : this.snapshot.skills())
        {
            int x = this.skillNodeX(centreX, node);
            int y = this.skillNodeY(node);
            boolean unlocked = this.skillState(node) == SkillState.UNLOCKED;

            // The rung of the trunk it hangs on: one short horizontal line from the trunk frame to the node frame.
            int trunkEdge = node.side() == SkillNodeDefinition.Side.LEFT ? centreX - FRAME_SIZE / 2
                    : centreX + FRAME_SIZE / 2;
            int nodeEdge = node.side() == SkillNodeDefinition.Side.LEFT ? x + FRAME_SIZE : x;

            this.drawBranch(graphics, trunkEdge, y + FRAME_SIZE / 2, nodeEdge, y + FRAME_SIZE / 2, unlocked);

            // And the link of every prerequisite: from the middle of the parent to the middle of the child, lit only
            // when both are really owned.
            for (ResourceLocation parent : node.parents())
            {
                ProgressionSnapshot.SkillNodeView parentNode = this.skillNode(parent);

                if (parentNode == null)
                {
                    continue;
                }

                boolean lit = unlocked && this.skillState(parentNode) == SkillState.UNLOCKED;

                this.drawBranch(graphics, this.skillNodeX(centreX, parentNode) + FRAME_SIZE / 2,
                        this.skillNodeY(parentNode) + FRAME_SIZE / 2, x + FRAME_SIZE / 2, y + FRAME_SIZE / 2, lit);
            }
        }
    }

    /**
     * One line of the tree, drawn the way the game draws its own: a dark core of three pixels and, when it was walked,
     * a light one in the middle of it. It is an elbow - first the horizontal half, then the vertical half - so two
     * nodes that do not share a row are still connected by one readable line.
     */
    private void drawBranch(GuiGraphics graphics, int fromX, int fromY, int toX, int toY, boolean lit)
    {
        for (int offset = -1; offset <= 1; offset++)
        {
            graphics.hLine(Math.min(fromX, toX), Math.max(fromX, toX), fromY + offset, COLOR_LINE_DARK);
            graphics.vLine(toX + offset, Math.min(fromY, toY), Math.max(fromY, toY), COLOR_LINE_DARK);
        }

        if (lit)
        {
            graphics.hLine(Math.min(fromX, toX), Math.max(fromX, toX), fromY, COLOR_LINE_LIGHT);
            graphics.vLine(toX, Math.min(fromY, toY), Math.max(fromY, toY), COLOR_LINE_LIGHT);
        }
    }

    /** Draws every node of the tree, and remembers the one the mouse is over. */
    private void renderSkillNodes(GuiGraphics graphics, int centreX, int mouseX, int mouseY)
    {
        for (ProgressionSnapshot.SkillNodeView node : this.snapshot.skills())
        {
            int x = this.skillNodeX(centreX, node);
            int y = this.skillNodeY(node);

            this.drawSkillNode(graphics, x, y, node);

            if (mouseX >= x && mouseX < x + FRAME_SIZE && mouseY >= y && mouseY < y + FRAME_SIZE)
            {
                this.hoveredSkill = node;
            }
        }
    }

    /**
     * One node of the tree.
     *
     * <p>Its icon is always its own - never iron, gold or copper, which belong to the levels of the trunk - and its
     * state is told by the three things the task asks for: the frame, the brightness of the icon, and the gold mark of
     * a node that can really be bought right now.
     */
    private void drawSkillNode(GuiGraphics graphics, int x, int y, ProgressionSnapshot.SkillNodeView node)
    {
        SkillState state = this.skillState(node);

        graphics.blit(WIDGETS, x, y, 0, state == SkillState.LOCKED ? FRAME_V_UNOBTAINED : FRAME_V_OBTAINED,
                FRAME_SIZE, FRAME_SIZE);
        graphics.renderFakeItem(this.skillIcon(node), x + 5, y + 5);

        if (state == SkillState.LOCKED)
        {
            graphics.fill(x + 3, y + 3, x + FRAME_SIZE - 3, y + FRAME_SIZE - 3, COLOR_FUTURE_SHADE);
        }
        else if (state == SkillState.AVAILABLE)
        {
            // The gold frame of a node she can really buy: whether she can also pay for it is a question her purse
            // answers on the click, and never a reason to hide the node from her.
            this.drawGoldFrame(graphics, x, y);
        }
    }

    /** The thin gold frame of a buyable node, drawn around the vanilla one. */
    private void drawGoldFrame(GuiGraphics graphics, int x, int y)
    {
        graphics.hLine(x - 1, x + FRAME_SIZE, y - 1, COLOR_SKILL_AVAILABLE);
        graphics.hLine(x - 1, x + FRAME_SIZE, y + FRAME_SIZE, COLOR_SKILL_AVAILABLE);
        graphics.vLine(x - 1, y - 1, y + FRAME_SIZE, COLOR_SKILL_AVAILABLE);
        graphics.vLine(x + FRAME_SIZE, y - 1, y + FRAME_SIZE, COLOR_SKILL_AVAILABLE);
    }

    /** @return the icon of one node, resolved against the registry of this client. */
    private ItemStack skillIcon(ProgressionSnapshot.SkillNodeView node)
    {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(node.icon()));
    }

    /**
     * @return that text, broken into lines that fit into the tooltip.
     *
     *         <p>It is wrapped here and not by the game, because the effect of a skill may be a whole sentence in any
     *         language: a line is cut at the last space when there is one, and otherwise between two characters, which
     *         is what a language without spaces (Chinese, for one) needs.
     */
    private List<String> wrap(String text, int width)
    {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (int index = 0; index < text.length(); index++)
        {
            char character = text.charAt(index);

            if (line.length() > 0 && this.font.width(line.toString() + character) > width)
            {
                int cut = line.lastIndexOf(" ");

                if (cut > 0)
                {
                    lines.add(line.substring(0, cut));
                    line.delete(0, cut + 1);
                }
                else
                {
                    lines.add(line.toString());
                    line.setLength(0);
                }
            }

            line.append(character);
        }

        if (line.length() > 0)
        {
            lines.add(line.toString());
        }

        return lines;
    }

    /** How wide the text of a tooltip may be before it is wrapped, in pixels. */
    private static final int TOOLTIP_WIDTH = 200;

    /**
     * What one node of the tree says when the mouse is on it.
     *
     * <p>Everything the task asks for is in this panel: the name, the state, the level it needs, the prerequisites it
     * is still missing, what it costs while it is not bought, what it does, and <b>the key that really answers for it on
     * this client right now</b>. That last line is read from the live {@link KeyMapping} of the player and never from a
     * default written down here, so a rebound key shows the new one and an unbound one says so.
     */
    private void renderSkillTooltip(GuiGraphics graphics, ProgressionSnapshot.SkillNodeView node, int mouseX,
            int mouseY)
    {
        List<Component> lines = new ArrayList<>();
        SkillState state = this.skillState(node);

        lines.add(Component.translatable(node.nameKey()));
        lines.add(Component.translatable(this.skillStateKey(state)).withStyle(this.skillStateColor(state)));
        lines.add(Component.translatable("gui.spiderqueen_hungrycraft.progression.skill.required_level",
                node.requiredLevel()).withStyle(ChatFormatting.GRAY));

        if (!node.parents().isEmpty())
        {
            lines.add(Component.translatable("gui.spiderqueen_hungrycraft.progression.skill.prerequisites")
                    .withStyle(ChatFormatting.GRAY));

            for (ResourceLocation parent : node.parents())
            {
                lines.add(Component.literal(" - ").append(this.skillName(parent))
                        .withStyle(SkillClientState.has(parent) ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }

        if (state != SkillState.UNLOCKED)
        {
            lines.add(Component.translatable("gui.spiderqueen_hungrycraft.progression.skill.cost", node.soulCost()));
        }

        // The effect of the skill, wrapped by hand so a long sentence never runs off the screen.
        for (String piece : this.wrap(Component.translatable(node.descriptionKey()).getString(), TOOLTIP_WIDTH))
        {
            lines.add(Component.literal(piece).withStyle(ChatFormatting.GRAY));
        }

        lines.add(this.keyLine(node));

        if (state == SkillState.AVAILABLE)
        {
            long souls = SoulClientState.targetSouls();

            lines.add(souls >= node.soulCost()
                    ? Component.translatable("gui.spiderqueen_hungrycraft.progression.skill.click_to_unlock")
                            .withStyle(ChatFormatting.YELLOW)
                    : ProgressionClientState.message(ProgressionFeedback.INSUFFICIENT_SOULS).copy()
                            .withStyle(ChatFormatting.RED));
        }
        else if (state == SkillState.LOCKED)
        {
            lines.add(Component.translatable("gui.spiderqueen_hungrycraft.progression.skill.locked_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    /** The translation key of one state, which is what the second line of the tooltip says. */
    private String skillStateKey(SkillState state)
    {
        return switch (state)
        {
            case UNLOCKED -> "gui.spiderqueen_hungrycraft.progression.skill.state.unlocked";
            case AVAILABLE -> "gui.spiderqueen_hungrycraft.progression.skill.state.available";
            case LOCKED -> "gui.spiderqueen_hungrycraft.progression.skill.state.locked";
        };
    }

    private ChatFormatting skillStateColor(SkillState state)
    {
        return switch (state)
        {
            case UNLOCKED -> ChatFormatting.GREEN;
            case AVAILABLE -> ChatFormatting.YELLOW;
            case LOCKED -> ChatFormatting.GRAY;
        };
    }

    /** @return the name of one node, resolved through the tree the server sent, falling back to its identity. */
    private Component skillName(ResourceLocation id)
    {
        ProgressionSnapshot.SkillNodeView node = this.skillNode(id);

        return node == null ? Component.literal(id.toString()) : Component.translatable(node.nameKey());
    }

    /**
     * The line of the key of one node.
     *
     * <p>Three answers, and they mean three different things: a passive skill has no binding at all and says "no key",
     * an active skill whose key the player unbound says "not configured", and an active skill with a key says the key
     * that player really bound - read from the live binding of this client, so a rebound key is shown at once.
     */
    private Component keyLine(ProgressionSnapshot.SkillNodeView node)
    {
        KeyMapping binding = keyMappingOf(node.inputHint());

        if (binding == null)
        {
            return Component.translatable("gui.spiderqueen_hungrycraft.progression.skill.key.none")
                    .withStyle(ChatFormatting.GRAY);
        }

        if (binding.isUnbound())
        {
            return Component.translatable("gui.spiderqueen_hungrycraft.progression.skill.key.unbound")
                    .withStyle(ChatFormatting.GRAY);
        }

        return Component.translatable("gui.spiderqueen_hungrycraft.progression.skill.key",
                binding.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY);
    }

    /** @return the binding of this client that answers for that hint, or null for a passive skill. */
    private static KeyMapping keyMappingOf(SkillNodeDefinition.InputHint hint)
    {
        return switch (hint)
        {
            case NONE -> null;
            case EMPOWERED_ATTACK -> HungryCraftKeyMappings.EMPOWERED_ATTACK;
            case BEAST_TRANSFORM -> HungryCraftKeyMappings.BEAST_TRANSFORM;
            case DEVOUR -> HungryCraftKeyMappings.DEVOUR_REGURGITATE;
            case SNIFF -> HungryCraftKeyMappings.SNIFF;
            case NECROMANCY_IV -> HungryCraftKeyMappings.NECROMANCY_IV;
        };
    }

    /** What one level says when the mouse is on it: the state, the price, the balance, and what a click would do. */
    private void renderLevelTooltip(GuiGraphics graphics, int level, int mouseX, int mouseY)
    {
        List<Component> lines = new ArrayList<>();
        NodeState state = this.stateOf(level);

        lines.add(Component.translatable("gui.spiderqueen_hungrycraft.progression.level", level));

        if (state == NodeState.COMPLETED)
        {
            lines.add(Component.translatable("gui.spiderqueen_hungrycraft.progression.unlocked")
                    .withStyle(ChatFormatting.GREEN));
        }
        else if (state == NodeState.FUTURE)
        {
            lines.add(Component.translatable("gui.spiderqueen_hungrycraft.progression.needs_previous", level - 1)
                    .withStyle(ChatFormatting.GRAY));
        }
        else
        {
            long cost = this.snapshot.costToReach(level);
            long souls = SoulClientState.targetSouls();

            lines.add(Component.translatable("gui.spiderqueen_hungrycraft.progression.upgrade_cost", cost));
            lines.add(Component.translatable("gui.spiderqueen_hungrycraft.progression.current_souls", souls, cost)
                    .withStyle(ChatFormatting.GRAY));
            lines.add(souls >= cost
                    ? Component.translatable("gui.spiderqueen_hungrycraft.progression.click_to_upgrade")
                            .withStyle(ChatFormatting.YELLOW)
                    : ProgressionClientState.message(ProgressionFeedback.INSUFFICIENT_SOULS).copy()
                            .withStyle(ChatFormatting.RED));
        }

        graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    /** The one line of help of the page, shown while the mouse is not on a level. */
    private void renderScrollHint(GuiGraphics graphics)
    {
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.spiderqueen_hungrycraft.progression.scroll_hint"),
                this.contentLeft() + INSIDE_WIDTH / 2, this.contentTop() + INSIDE_HEIGHT - 11, COLOR_DIM);
    }

    // ------------------------------------------------------------------ the shop

    /**
     * The products, one card each, built from the list the server sent.
     *
     * <p>Nothing here knows what a spider egg is: a card is a name, a count, a price and a button, all of them read
     * from the snapshot. The day a second product is added to the data pack it appears here without a line of code,
     * and the list already scrolls like the trunk does.
     */
    private void renderShop(GuiGraphics graphics, int mouseX, int mouseY)
    {
        if (this.snapshot == null)
        {
            return;
        }

        List<ProgressionSnapshot.ShopEntryView> shop = this.snapshot.shop();

        if (shop.isEmpty())
        {
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.spiderqueen_hungrycraft.progression.shop_empty"),
                    this.contentLeft() + INSIDE_WIDTH / 2, this.contentTop() + INSIDE_HEIGHT / 2, COLOR_DIM);
            return;
        }

        graphics.enableScissor(this.contentLeft(), this.contentTop(), this.contentLeft() + INSIDE_WIDTH,
                this.contentTop() + INSIDE_HEIGHT);

        for (int index = 0; index < shop.size(); index++)
        {
            int y = this.cardTop(index);

            if (y + CARD_HEIGHT < this.contentTop() || y > this.contentTop() + INSIDE_HEIGHT)
            {
                continue;
            }

            this.renderProductCard(graphics, shop.get(index), y, mouseX, mouseY);
        }

        graphics.disableScissor();

        graphics.drawCenteredString(this.font,
                Component.translatable("gui.spiderqueen_hungrycraft.progression.shop_balance",
                        SoulClientState.targetSouls()),
                this.contentLeft() + INSIDE_WIDTH / 2, this.contentTop() + INSIDE_HEIGHT - 11, COLOR_DIM);

        if (this.hoveredProduct != null)
        {
            this.renderProductTooltip(graphics, this.hoveredProduct, mouseX, mouseY);
        }
    }

    /** One card: the item, its name and count, its price, and the button that buys one batch of it. */
    private void renderProductCard(GuiGraphics graphics, ProgressionSnapshot.ShopEntryView entry, int y, int mouseX,
            int mouseY)
    {
        ItemStack icon = this.iconOf(entry);
        int x = this.cardLeft();

        graphics.fill(x, y, x + this.cardWidth(), y + CARD_HEIGHT, 0x60000000);
        graphics.blit(WIDGETS, x + 3, y + 4, 0, FRAME_V_OBTAINED, FRAME_SIZE, FRAME_SIZE);
        graphics.renderFakeItem(icon, x + 8, y + 9);

        graphics.drawString(this.font, Component.translatable("gui.spiderqueen_hungrycraft.progression.shop_item",
                icon.getHoverName(), entry.count()), x + 34, y + 6, COLOR_TEXT, false);
        graphics.drawString(this.font,
                Component.translatable("gui.spiderqueen_hungrycraft.progression.shop_price", entry.price()),
                x + 34, y + 18, COLOR_DIM, false);

        boolean overButton = this.isOverBuyButton(y, mouseX, mouseY);
        int buttonX = this.buyButtonLeft();
        int buttonY = y + (CARD_HEIGHT - BUTTON_HEIGHT) / 2;

        graphics.fill(buttonX, buttonY, buttonX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT,
                overButton ? 0xFF3A5A3A : 0xFF2A2A2A);
        graphics.fill(buttonX, buttonY, buttonX + BUTTON_WIDTH, buttonY + 1, 0xFF5A5A5A);
        graphics.fill(buttonX, buttonY, buttonX + 1, buttonY + BUTTON_HEIGHT, 0xFF5A5A5A);
        graphics.fill(buttonX, buttonY + BUTTON_HEIGHT - 1, buttonX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT,
                0xFF1E1E1E);
        graphics.fill(buttonX + BUTTON_WIDTH - 1, buttonY, buttonX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT,
                0xFF1E1E1E);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.spiderqueen_hungrycraft.progression.shop_buy"),
                buttonX + BUTTON_WIDTH / 2, buttonY + 4, overButton ? 0xFFFFFFA0 : COLOR_TEXT);

        if (this.isOverCard(x, y, mouseX, mouseY))
        {
            this.hoveredProduct = entry;
        }
    }

    /** What one product says: the name, the price, and what a click of its button would do. */
    private void renderProductTooltip(GuiGraphics graphics, ProgressionSnapshot.ShopEntryView entry, int mouseX,
            int mouseY)
    {
        List<Component> lines = new ArrayList<>();
        ItemStack icon = this.iconOf(entry);
        long souls = SoulClientState.targetSouls();

        lines.add(Component.translatable("gui.spiderqueen_hungrycraft.progression.shop_item", icon.getHoverName(),
                entry.count()));
        lines.add(Component.translatable("gui.spiderqueen_hungrycraft.progression.shop_price", entry.price()));
        lines.add(souls >= entry.price()
                ? Component.translatable("gui.spiderqueen_hungrycraft.progression.click_to_buy")
                        .withStyle(ChatFormatting.YELLOW)
                : ProgressionClientState.message(ProgressionFeedback.INSUFFICIENT_SOULS).copy()
                        .withStyle(ChatFormatting.RED));

        graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    /** @return the item of one product, resolved against the registry of this client. */
    private ItemStack iconOf(ProgressionSnapshot.ShopEntryView entry)
    {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(entry.itemId()),
                Math.max(1, entry.count()));
    }

    private int cardLeft()
    {
        return this.contentLeft() + CARD_MARGIN;
    }

    private int cardWidth()
    {
        return INSIDE_WIDTH - CARD_MARGIN * 2;
    }

    private int cardTop(int index)
    {
        return this.contentTop() + CARD_MARGIN / 2 + index * CARD_HEIGHT + (int)Math.round(this.shopScroll);
    }

    private int buyButtonLeft()
    {
        return this.cardLeft() + this.cardWidth() - BUTTON_WIDTH - 4;
    }

    private boolean isOverCard(int x, int y, double mouseX, double mouseY)
    {
        return mouseX >= x && mouseX < x + this.cardWidth() && mouseY >= y && mouseY < y + CARD_HEIGHT;
    }

    private boolean isOverBuyButton(int cardY, double mouseX, double mouseY)
    {
        int x = this.buyButtonLeft();
        int y = cardY + (CARD_HEIGHT - BUTTON_HEIGHT) / 2;

        return mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    private void clampShopScroll()
    {
        if (this.snapshot == null)
        {
            return;
        }

        double contentHeight = this.snapshot.shop().size() * (double)CARD_HEIGHT + CARD_MARGIN;
        double lowest = Math.min(0.0D, INSIDE_HEIGHT - contentHeight);

        this.shopScroll = Mth.clamp(this.shopScroll, lowest, 0.0D);
    }

    // ------------------------------------------------------------------ the legend

    /**
     * The legendary page: visible from the first day and not open yet, exactly as this round asks.
     *
     * <p><b>It does not say "locked".</b> Nothing of the legendary growth exists yet - there is no legendary level, no
     * price and no condition a player could be missing -, so the page says that the door is simply not open yet instead
     * of showing a progress somebody could have failed. The day the system is really implemented (the
     * {@code SYSTEM_ENABLED} switch of the legendary progression), the very same page turns into the condition it will
     * ask: the finished main line and every required node of the tree.
     */
    private void renderLegend(GuiGraphics graphics)
    {
        int centreX = this.contentLeft() + INSIDE_WIDTH / 2;
        int y = this.contentTop() + 10;

        graphics.blit(WIDGETS, centreX - FRAME_SIZE / 2, y, 0, FRAME_V_UNOBTAINED, FRAME_SIZE, FRAME_SIZE);
        graphics.renderFakeItem(TAB_ICON_LEGEND, centreX - FRAME_SIZE / 2 + 5, y + 5);
        graphics.fill(centreX - FRAME_SIZE / 2 + 3, y + 3, centreX + FRAME_SIZE / 2 - 3, y + FRAME_SIZE - 3,
                COLOR_FUTURE_SHADE);

        graphics.drawCenteredString(this.font,
                Component.translatable("gui.spiderqueen_hungrycraft.progression.legend.title"), centreX, y + 32,
                COLOR_TEXT);

        // Nothing of the legendary growth is implemented yet: there is nothing to unlock, so nothing about a condition
        // is said either. Only a system that really exists may talk about what it takes.
        if (this.snapshot == null || !this.snapshot.legendaryEnabled())
        {
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.spiderqueen_hungrycraft.progression.legend.soon"), centreX, y + 50,
                    COLOR_DIM);
            return;
        }

        graphics.drawCenteredString(this.font,
                Component.translatable("gui.spiderqueen_hungrycraft.progression.legend.locked"), centreX, y + 46,
                COLOR_DIM);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.spiderqueen_hungrycraft.progression.legend.requirement"), centreX,
                y + 60, COLOR_DIM);

        Component progress = this.snapshot.legendaryEligible()
                ? Component.translatable("gui.spiderqueen_hungrycraft.progression.legend.done")
                : Component.translatable("gui.spiderqueen_hungrycraft.progression.legend.not_done");

        graphics.drawCenteredString(this.font, progress, centreX, y + 74, COLOR_DIM);
    }

    // ------------------------------------------------------------------ what a player does with it

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if (this.page == Page.GROWTH)
        {
            this.trunkScroll += delta * SCROLL_STEP;
            this.clampTrunkScroll();
            return true;
        }

        if (this.page == Page.SHOP)
        {
            this.shopScroll += delta * CARD_HEIGHT;
            this.clampShopScroll();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button != 0)
        {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        Page[] pages = Page.values();

        for (int index = 0; index < pages.length; index++)
        {
            if (this.isOverTab(index, mouseX, mouseY))
            {
                this.page = pages[index];
                return true;
            }
        }

        if (!this.isInsideContent(mouseX, mouseY))
        {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // Anywhere inside a page starts a drag, which is the second way to travel it next to the wheel.
        this.dragging = true;
        this.dragAnchorY = mouseY;
        this.dragStartScroll = this.page == Page.GROWTH ? this.trunkScroll : this.shopScroll;

        if (this.page == Page.GROWTH)
        {
            // A node of the tree first (Skill Progression round): only one that is available answers a click. A locked
            // node asks the server for nothing at all - its tooltip already says what is missing - and a node she
            // already owns has nothing left to buy, so neither of them produces a packet.
            ProgressionSnapshot.SkillNodeView skill = this.skillAt(mouseX, mouseY);

            if (skill != null)
            {
                if (this.snapshot != null && this.skillState(skill) == SkillState.AVAILABLE)
                {
                    HungryCraftNetwork.sendToServer(
                            new C2SProgressionSkillPacket(this.snapshot.corePos(), skill.id()));
                }

                return true;
            }

            int level = this.levelAt(mouseX, mouseY);

            // Only the one node the player is allowed to take next answers a click; a level further down the trunk
            // is refused here and would be refused by the server as well.
            if (level > 0 && this.snapshot != null && this.stateOf(level) == NodeState.NEXT)
            {
                HungryCraftNetwork.sendToServer(
                        new C2SProgressionUpgradePacket(this.snapshot.corePos(), level));
            }
        }
        else if (this.page == Page.SHOP)
        {
            int index = this.productIndexAt(mouseX, mouseY);

            if (index >= 0 && this.snapshot != null
                    && this.isOverBuyButton(this.cardTop(index), mouseX, mouseY))
            {
                HungryCraftNetwork.sendToServer(new C2SProgressionPurchasePacket(this.snapshot.corePos(),
                        this.snapshot.shop().get(index).id()));
            }
        }

        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        if (this.dragging && button == 0)
        {
            double delta = mouseY - this.dragAnchorY;

            if (this.page == Page.GROWTH)
            {
                this.trunkScroll = this.dragStartScroll + delta;
                this.clampTrunkScroll();
            }
            else if (this.page == Page.SHOP)
            {
                this.shopScroll = this.dragStartScroll + delta;
                this.clampShopScroll();
            }

            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        this.dragging = false;

        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isInsideContent(double mouseX, double mouseY)
    {
        return mouseX >= this.contentLeft() && mouseX < this.contentLeft() + INSIDE_WIDTH
                && mouseY >= this.contentTop() && mouseY < this.contentTop() + INSIDE_HEIGHT;
    }

    /** @return the level whose node is under the mouse, or -1. Asked of the geometry, never of the last frame. */
    private int levelAt(double mouseX, double mouseY)
    {
        if (this.snapshot == null)
        {
            return -1;
        }

        int nodeX = this.contentLeft() + INSIDE_WIDTH / 2 - FRAME_SIZE / 2;
        int first = Math.max(1, this.firstVisibleLevel() - BUFFER_NODES);
        int last = Math.min(this.highestLevel(), this.lastVisibleLevel() + BUFFER_NODES);

        for (int level = first; level <= last; level++)
        {
            if (this.isOverNode(nodeX, this.nodeY(level), mouseX, mouseY))
            {
                return level;
            }
        }

        return -1;
    }

    /** @return the index of the product card under the mouse, or -1. */
    private int productIndexAt(double mouseX, double mouseY)
    {
        if (this.snapshot == null)
        {
            return -1;
        }

        for (int index = 0; index < this.snapshot.shop().size(); index++)
        {
            if (this.isOverCard(this.cardLeft(), this.cardTop(index), mouseX, mouseY))
            {
                return index;
            }
        }

        return -1;
    }
}
