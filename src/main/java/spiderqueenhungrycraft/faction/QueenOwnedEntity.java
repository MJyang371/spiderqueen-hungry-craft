package spiderqueenhungrycraft.faction;

import java.util.UUID;

import javax.annotation.Nullable;

/**
 * The one thing a creature of a Spider Queen has to tell the colony layer (Queen Colony framework).
 *
 * <p>It is a <b>read only view</b> on purpose: the interface says nothing about how the owner is stored, saved,
 * synced or set, and it holds no field of its own. A minion that already knows its queen - like the brood spiders
 * with their {@code MinionOwner} tag - implements this method by returning that value, and nothing else in the code
 * has to change.
 *
 * <p>This is what keeps {@code queen_faction} from being "everybody on the queen side is always a friend": the
 * faction says <i>which side</i> a creature stands on, this interface says <i>whose</i> creature it is. See
 * {@link QueenColonyRelations} for the questions the future AI is supposed to ask.
 */
public interface QueenOwnedEntity
{
    /** @return the UUID of the Spider Queen player this creature belongs to, or null when it has no queen. */
    @Nullable
    UUID getQueenOwnerUUID();
}
