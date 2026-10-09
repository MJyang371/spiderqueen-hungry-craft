package spiderqueenhungrycraft.client;

/**
 * The one place that describes the soul bar in the bottom left corner of the screen (Soul Foundation V1).
 *
 * <p>The bar is a <b>long frame of a fixed length</b> and not a box hugging the number: the icon of a soul sits at
 * its left end, the balance of the player at its right end, and the empty middle is what the frame is there for.
 * It is deliberately anchored to the bottom left corner - the bottom right one belongs to the stomach panel - and
 * nothing here is an absolute screen coordinate, so the bar follows the GUI scale of the player and every
 * resolution.
 *
 * <p><b>The frame only grows for a number that really needs the room.</b> {@link #panelWidthFor(int)} is the one
 * place that decides whether a balance is too long for the fixed length, and it then widens the frame by exactly
 * what is missing - so a very large balance can never run over the right edge of the frame.
 */
public final class SoulHudLayout
{
    /** The corner the bar lives in. */
    public static final Anchor ANCHOR = Anchor.BOTTOM_LEFT;

    /** Distance from the corner of the screen. */
    public static final int OFFSET_X = 6;
    public static final int OFFSET_Y = 5;

    /** The icon at the left end of the bar, drawn one to one with the pixels of its art. */
    public static final int ICON_SIZE = 16;

    /** Space between the frame and everything inside it. */
    public static final int PADDING = 2;

    /** Space between the icon and the field the number is written in. */
    public static final int ICON_GAP = 2;

    /**
     * How long the bar is before any number asks for more: room for about eleven digits of the font of the game, so
     * the balance of an ordinary world of any age fits without the frame ever moving.
     */
    public static final int BASE_WIDTH = 100;

    /** One line of the font of the game, which is exactly how tall the number is. */
    public static final int FONT_LINE_HEIGHT = 9;

    /** The thickness of the pixel frame around the bar. */
    public static final int FRAME = 1;

    /**
     * The whole bar - the frame, the icon and the number together - is drawn at this fraction of its own layout, so
     * that it is half the size it is laid out in and keeps its exact proportions while it is. It is one factor
     * instead of halved constants on purpose: the size of the bar can be tuned here without touching a coordinate.
     */
    public static final float SCALE = 0.5F;

    /** The plate behind the number: dark, and only as opaque as it has to be to stay readable. */
    public static final int COLOR_BACKGROUND = 0x8C000000;

    /** The two shades of the grey bevel - the way the game frames its own small boxes. */
    public static final int COLOR_FRAME_LIGHT = 0xFF5A5A5A;
    public static final int COLOR_FRAME_DARK = 0xFF1E1E1E;

    /** The colour of the number: the very cream the icon of a soul is drawn in. */
    public static final int COLOR_TEXT = 0xFFFFFACD;

    private SoulHudLayout()
    {
    }

    /** @return the height of the bar, driven by the icon so the frame always holds it with its padding. */
    public static int panelHeight()
    {
        return ICON_SIZE + PADDING * 2;
    }

    /** @return the height the bar really takes on the screen, which is what the distance to the corner is kept in. */
    public static int screenHeight()
    {
        return Math.round(panelHeight() * SCALE);
    }

    /**
     * @param numberWidth how wide the number of the player is in the font of the game
     * @return the width of the bar: the fixed length, or wider by exactly what a huge balance is missing.
     */
    public static int panelWidthFor(int numberWidth)
    {
        int needed = PADDING * 2 + ICON_SIZE + ICON_GAP + Math.max(0, numberWidth);

        return Math.max(BASE_WIDTH, needed);
    }

    public enum Anchor
    {
        BOTTOM_LEFT
    }
}
