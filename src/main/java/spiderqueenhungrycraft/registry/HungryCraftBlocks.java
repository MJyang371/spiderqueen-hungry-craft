package spiderqueenhungrycraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.bed.SpiderBedBlock;
import spiderqueenhungrycraft.brood.DelayedSpiderMineBlock;
import spiderqueenhungrycraft.brood.SpiderEggBlock;
import spiderqueenhungrycraft.brood.SpiderMineBlock;
import spiderqueenhungrycraft.brood.SpiderTriggerBlock;
import spiderqueenhungrycraft.territory.TerritoryCoreBlock;
import spiderqueenhungrycraft.web.ElementalQueenWebBlock;
import spiderqueenhungrycraft.web.QueenWebBlock;
import spiderqueenhungrycraft.web.WebElement;

/**
 * Blocks of the mod. The web blocks intentionally have no BlockItem: they are only ever created by a web
 * shot, so they cannot be obtained or placed by a player.
 *
 * <p>No web emits real block light. The fire web only renders bright (emissive rendering plus flame
 * particles), because the Spider Queen is blinded by bright light and must never blind herself with her own
 * webs.
 */
public final class HungryCraftBlocks
{
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, SpiderQueenHungryCraft.MODID);

    /** The temporary web of an ordinary web shot, it disappears after its lifetime. */
    public static final RegistryObject<Block> QUEEN_WEB = BLOCKS.register("queen_web",
            () -> new QueenWebBlock(webProperties()));

    /** Permanent poison web of a poison shot. */
    public static final RegistryObject<Block> POISON_QUEEN_WEB = BLOCKS.register("poison_queen_web",
            () -> new ElementalQueenWebBlock(webProperties(), WebElement.POISON));

    /** Permanent fire web of a fire shot, it looks hot but emits no light at all. */
    public static final RegistryObject<Block> FIRE_QUEEN_WEB = BLOCKS.register("fire_queen_web",
            () -> new ElementalQueenWebBlock(webProperties().emissiveRendering((state, level, pos) -> true),
                    WebElement.FIRE));

    /**
     * The spider bed of a completed ritual (Phase 8E3A-GATE1).
     *
     * <p>It is an ordinary block like any other: it carries no block entity, it never checks whether the
     * frame that created it still exists and every one of the nine beds of a ritual is completely equal.
     * The ritual itself lives in {@code spiderqueenhungrycraft.bed.SpiderBedRitual}, this block only knows
     * the plane it lies in.
     */
    public static final RegistryObject<Block> SPIDER_BED = BLOCKS.register("spider_bed",
            () -> new SpiderBedBlock(spiderBedProperties()));

    /**
     * The one wall block of a Stomach Cell (Phase 8E1).
     *
     * <p>It is unbreakable, has no loot table, cannot be pushed by a piston and never spawns anything, so the
     * ordinary gameplay can neither harvest nor reshape a stomach. The StomachManager writes it directly when
     * it builds or repairs a cell, and it is never given to a player. The look is the vanilla netherrack block
     * model, so this mod ships no texture of its own for it, and the feel is the sound of a honey block, because
     * the wall is meat and not stone.
     */
    public static final RegistryObject<Block> STOMACH_WALL = BLOCKS.register("stomach_wall",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NETHER)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)
                    // The wall is meat, not stone: it is hit, stepped on and broken with the honey block sound.
                    .sound(SoundType.HONEY_BLOCK)
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)));

    /**
     * The territory core of a lair (Phase LAIR-CORE-1).
     *
     * <p>It looks like a glowing web core but is an ordinary solid block: the vanilla cobweb texture on a full cube
     * with cutout rendering, ordinary full block collision, no slowdown, no wrapping, no capture, no decay. Its one
     * power is the natural spawn suppression of the 3x3x3 chunk sections around it, which lives in
     * {@code spiderqueenhungrycraft.territory}.
     */
    public static final RegistryObject<Block> TERRITORY_CORE = BLOCKS.register("territory_core",
            () -> new TerritoryCoreBlock(territoryCoreProperties()));

    /**
     * The spider egg on the ground (this round's breeding pass).
     *
     * <p>It is the purple egg of the brood: a small, tool free block of the shape of a turtle egg that
     * hatches one imp spider after ten seconds and comes back as an item when it is broken. It is the one
     * block of the mod with a block entity, and that block entity holds nothing but the player who placed
     * the egg.
     */
    public static final RegistryObject<Block> SPIDER_EGG = BLOCKS.register("spider_egg",
            () -> new SpiderEggBlock(spiderEggProperties()));

    /**
     * The spider mine (Spider Mine and Egg Arrow round).
     *
     * <p>A block of TNT with a clutch of spider eggs built into it: a redstone signal or a click of the queen sceptre
     * fires it, and the blast leaves eight imp spiders of whoever placed the mine behind. It is the second block of the
     * mod with a block entity, and like the egg it is there to remember a player: the one who placed it.
     */
    public static final RegistryObject<Block> SPIDER_MINE = BLOCKS.register("spider_mine",
            () -> new SpiderMineBlock(spiderMineProperties()));

    /**
     * The delayed spider mine (Spider Trigger round): the same trap with a fuse.
     *
     * <p>It is built, placed and hidden exactly like a spider mine and it answers the same triggers, but a trigger
     * only lights its fuse: the block becomes a primed entity of the game, falls, flashes and blows up four seconds
     * later, and only then are the eight imp spiders delivered. It is also the one of the two mines that a blast of the
     * world takes - and lights on the way - while a spider mine is blast proof.
     */
    public static final RegistryObject<Block> DELAYED_SPIDER_MINE = BLOCKS.register("delayed_spider_mine",
            () -> new DelayedSpiderMineBlock(delayedSpiderMineProperties()));

    /**
     * The spider trigger (Spider Trigger round): a pressure plate that watches the ground around it.
     *
     * <p>Anything that is not the placer, one of her spiders or one of her tamed animals trips a redstone signal and
     * an alert to her, and the plate itself can be disguised as any full block of the game - which is what turns a
     * floor tile into a trip wire.
     */
    public static final RegistryObject<Block> SPIDER_TRIGGER = BLOCKS.register("spider_trigger",
            () -> new SpiderTriggerBlock(spiderTriggerProperties()));

    private HungryCraftBlocks()
    {
    }

    /** The properties of a territory core: solid, softly lit and glowing, with the sound of a web mat. */
    private static BlockBehaviour.Properties territoryCoreProperties()
    {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOL)
                .sound(SoundType.WOOL)
                .strength(1.5F, 6.0F)
                .lightLevel(state -> TerritoryCoreBlock.LIGHT_LEVEL)
                .noOcclusion()
                .emissiveRendering((state, level, pos) -> true);
    }

    /**
     * The properties of a spider egg: a small, softly coloured egg on the floor.
     *
     * <p>It breaks instantly and with an empty hand, needs no tool and no special ground, cannot be pushed
     * by a piston without breaking, and it never hides the view. The sound is the one of a web mat, because
     * an egg of the brood is silk as well.
     */
    private static BlockBehaviour.Properties spiderEggProperties()
    {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .sound(SoundType.WOOL)
                .instabreak()
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY)
                .isRedstoneConductor((state, level, pos) -> false)
                .isValidSpawn((state, level, pos, type) -> false);
    }

    /**
     * The properties of a spider mine (Spider Mine and Egg Arrow round): a solid block of the world that is quick to
     * break with an empty hand and <b>blast proof</b>.
     *
     * <p>It is deliberately <b>not</b> instabreak: a mine is a piece of equipment a queen places deliberately and breaks
     * deliberately, not a block that is gone the moment she brushes against it. It keeps its own block entity, so it can
     * be selected, broken and put down again like any other block - and it drops itself when it is broken by hand.
     *
     * <p>The resistance is the one of {@link SpiderMineBlock#EXPLOSION_RESISTANCE} and not the toughness of a modest
     * block: nothing that explodes takes a mine of this mod out or lights it. Its own blast does not need to break it -
     * the block leaves the world before its explosion runs.
     */
    private static BlockBehaviour.Properties spiderMineProperties()
    {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .sound(SoundType.STONE)
                .strength(0.6F, SpiderMineBlock.EXPLOSION_RESISTANCE)
                .isValidSpawn((state, level, pos, type) -> false);
    }

    /**
     * The properties of a delayed spider mine (Spider Trigger round): the same block, with the opposite answer to an
     * explosion.
     *
     * <p>The hardness is the one of a spider mine - it is broken by hand exactly like one - while the resistance is the
     * one of {@link DelayedSpiderMineBlock#EXPLOSION_RESISTANCE}, which is nothing at all. That is the whole difference
     * between the two mines, and it is what a blast of any kind reads: a delayed mine is taken by it and <b>lit</b> on
     * the way, a spider mine is not touched at all.
     */
    private static BlockBehaviour.Properties delayedSpiderMineProperties()
    {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .sound(SoundType.STONE)
                .strength(0.6F, DelayedSpiderMineBlock.EXPLOSION_RESISTANCE)
                .isValidSpawn((state, level, pos, type) -> false);
    }

    /** The properties of a spider trigger: a plate of stone that hides in a floor and never blocks the view. */
    private static BlockBehaviour.Properties spiderTriggerProperties()
    {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .sound(SoundType.STONE)
                .strength(0.5F, 3.0F)
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY)
                .isRedstoneConductor((state, level, pos) -> false)
                .isValidSpawn((state, level, pos, type) -> false);
    }

    /** The shared properties of every web block of the mod. */
    private static BlockBehaviour.Properties webProperties()
    {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOL)
                .forceSolidOn()
                .noCollission()
                .strength(4.0F)
                .pushReaction(PushReaction.DESTROY);
    }

    /**
     * The basic properties of a spider bed (Phase 8E3A-GATE1). The two numbers live in
     * {@link SpiderBedBlock}, so a later phase can tune the feel of a bed in one single place.
     *
     * <p>A bed is a mat of web: it does not block the view, it does not occlude its neighbours, it is not
     * a wall and it can still be selected, placed and broken like any other block. It deliberately has no
     * physics of its own yet, the web feel of a bed belongs to a later phase.
     */
    private static BlockBehaviour.Properties spiderBedProperties()
    {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOL)
                .sound(SoundType.WOOL)
                .strength(SpiderBedBlock.HARDNESS, SpiderBedBlock.EXPLOSION_RESISTANCE)
                .noCollission()
                .noOcclusion();
    }

    public static void register(IEventBus modEventBus)
    {
        BLOCKS.register(modEventBus);
    }
}

