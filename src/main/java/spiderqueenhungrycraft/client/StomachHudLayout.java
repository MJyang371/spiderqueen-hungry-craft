package spiderqueenhungrycraft.client;

/**
 * The one place that describes the small stomach status panel (Phase 8E1-GATE2B).
 *
 * <p>Where it sits, how big it is, how many guests it shows and how strong its background is: everything the panel
 * looks like is decided here and nowhere else, so a future settings screen only ever has to write these numbers.
 */
public final class StomachHudLayout
{
    /** The corner the panel lives in. */
    public static final Anchor ANCHOR = Anchor.BOTTOM_RIGHT;

    /** Distance from the corner of the screen. */
    public static final int OFFSET_X = 8;
    public static final int OFFSET_Y = 6;

    /** The panel is one to one with the interface scale of the player. */
    public static final float SCALE = 1.0F;

    /** At most this many guests are drawn; the rest is summarised in one line. */
    public static final int MAX_VISIBLE_ENTRIES = 8;

    /** How much of the panel background is drawn over the world. */
    public static final float OPACITY = 0.65F;

    public static final int PANEL_WIDTH = 112;
    public static final int TITLE_HEIGHT = 13;
    public static final int ENTRY_HEIGHT = 20;
    public static final int PADDING = 4;

    public static final int BAR_HEIGHT = 4;
    public static final int BAR_WIDTH = PANEL_WIDTH - PADDING * 2;

    public static final int COLOR_TITLE = 0xFFFF9FB4;
    public static final int COLOR_TEXT = 0xFFE8E8E8;
    public static final int COLOR_BAR_BACK = 0xFF3A1010;
    public static final int COLOR_BAR_FILL = 0xFFC0303A;
    public static final int COLOR_MORE = 0xFFB0B0B0;

    private StomachHudLayout()
    {
    }

    /** @return the background colour of the panel with the configured opacity. */
    public static int background()
    {
        int alpha = Math.round(255.0F * Math.min(1.0F, Math.max(0.0F, OPACITY)));

        return alpha << 24;
    }

    public enum Anchor
    {
        BOTTOM_RIGHT
    }
}
