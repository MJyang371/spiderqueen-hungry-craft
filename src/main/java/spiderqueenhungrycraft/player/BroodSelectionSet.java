package spiderqueenhungrycraft.player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The spiders a queen has selected (Brood Command Group round).
 *
 * <p>It is the one thing the sceptre commands of a queen act on: a move order, an attack order and the stand-down key
 * all go to the spiders in here, and only to them. Nothing else about a spider is written here - not its order, not its
 * victim, not its errand - because the spider itself stays the truth for all of that. This set answers one question
 * only: <b>which units did the queen pick?</b>
 *
 * <p>It lives in the queen data of the player, so it is saved with her, survives a logout and a rebuild of the player
 * entity, exactly like the silk of her glands and the escort roster. An id whose spider is gone is simply never
 * resolved: every command resolves the set against the spiders that are loaded right now, and a stale entry therefore
 * costs nothing but a few bytes in the save file.
 *
 * <p><b>Reserved space for squadrons.</b> A later round that wants numbered squadrons (one key per group, one key to
 * switch the active one) adds several sets beside this one - the shape is already here: the commands of the sceptre ask
 * {@code SpiderCommand#commanded} for the units they act on and never touch a set themselves, so a squadron round
 * changes that single method and the keys, not a single command.
 */
public final class BroodSelectionSet
{
    /** Root NBT key of the selection inside the queen data. */
    public static final String TAG_KEY = "BroodSelection";

    /** One selected spider. */
    private static final String TAG_SPIDER = "Spider";

    /** How many units one queen may select: far more than a brood ever is, and a hard cap for the save file. */
    public static final int MAX_SELECTED = 512;

    /** The selected spiders, in the order they were selected. */
    private final Set<UUID> selected = new LinkedHashSet<>();

    /** @return the ids of the selected spiders, in the order they were selected. */
    public Collection<UUID> ids()
    {
        return this.selected;
    }

    public int size()
    {
        return this.selected.size();
    }

    public boolean isEmpty()
    {
        return this.selected.isEmpty();
    }

    public boolean contains(UUID spider)
    {
        return this.selected.contains(spider);
    }

    /**
     * Replaces the whole selection with these spiders.
     *
     * <p>A selection is always replaced at once and never grown quietly: a drag, a click on one spider, "what I can
     * see" and "the same species" are four ways of saying "this is my group now", and each of them answers the whole
     * question.
     */
    public void selectOnly(Collection<UUID> spiders)
    {
        this.selected.clear();

        for (UUID spider : spiders)
        {
            if (this.selected.size() >= MAX_SELECTED)
            {
                return;
            }

            this.selected.add(spider);
        }
    }

    /** Drops every selected spider: the next command of the sceptre needs a group again. */
    public void clear()
    {
        this.selected.clear();
    }

    /** Writes the selection under its own key into that tag. */
    public void save(CompoundTag parent)
    {
        ListTag list = new ListTag();

        for (UUID spider : this.selected)
        {
            CompoundTag one = new CompoundTag();
            one.putUUID(TAG_SPIDER, spider);
            list.add(one);
        }

        parent.put(TAG_KEY, list);
    }

    /** Reads the selection back. A save without one (or an older save) simply has none. */
    public void load(CompoundTag parent)
    {
        this.selected.clear();

        if (parent == null || !parent.contains(TAG_KEY, Tag.TAG_LIST))
        {
            return;
        }

        ListTag list = parent.getList(TAG_KEY, Tag.TAG_COMPOUND);

        for (int index = 0; index < list.size() && this.selected.size() < MAX_SELECTED; index++)
        {
            CompoundTag one = list.getCompound(index);

            if (one.hasUUID(TAG_SPIDER))
            {
                this.selected.add(one.getUUID(TAG_SPIDER));
            }
        }
    }

    /** Copies the selection of another queen data, used when the player entity is rebuilt. */
    public void copyFrom(BroodSelectionSet other)
    {
        this.selected.clear();
        this.selected.addAll(other.selected);
    }
}
