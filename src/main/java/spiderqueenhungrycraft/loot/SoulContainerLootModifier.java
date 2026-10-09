package spiderqueenhungrycraft.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import spiderqueenhungrycraft.soul.SoulLootRules;

/**
 * The soul containers of the world (Soul Consumable round): a loot container that belongs to a structure may hold a
 * lump of soul, and the family of that structure decides which soul it is.
 *
 * <p><b>One container, at most one stack.</b> The modifier rolls the count once and appends exactly one
 * {@link ItemStack} of that count - never one stack per soul - so a chest of a large modpack loses one slot at most to
 * this mod. The dice are the dice of the loot context, so a world seed reproduces its chests.
 *
 * <p><b>Only a real container is touched.</b> A generation is a container when the block at its origin really holds a
 * container block entity, or when the entity of the generation is a container on wheels (a chest minecart, a chest
 * boat). That is what keeps this modifier out of monster drops, broken blocks, fishing, brushing and gifts, which all
 * carry an origin of their own and would otherwise be paid for standing inside a structure.
 *
 * <p><b>The structure decides, the loot table refines.</b> The structures that really cover the container are asked
 * first and mapped through the rule book of {@link SoulLootRules}; the loot table is asked only to name a sub type of
 * that family, or the family itself when no structure owns the container - which is how a monster room, a chest set
 * into a wall and never a registered structure, still holds a benevolent soul.
 *
 * <p><b>Nested loot tables cannot pay twice.</b> Forge applies the global loot modifiers in
 * {@code LootTable#getRandomItems(LootContext)} only: a table that is called by another table runs the raw path without
 * them, so a container that borrows the table of another structure injects at most one stack - and the table it
 * borrows is not even the one this modifier sees, because the queried id of a context stays the top level table.
 */
public class SoulContainerLootModifier extends LootModifier
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<SoulContainerLootModifier> CODEC = RecordCodecBuilder.create(
            instance -> LootModifier.codecStart(instance).apply(instance, SoulContainerLootModifier::new));

    public SoulContainerLootModifier(LootItemCondition[] conditions)
    {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context)
    {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        ServerLevel level = context.getLevel();

        if (origin == null || !isContainer(level, origin, context))
        {
            return generatedLoot;
        }

        List<ResourceLocation> structures = new ArrayList<>();
        List<ResourceLocation> tags = new ArrayList<>();
        collectStructures(level, BlockPos.containing(origin), structures, tags);

        Optional<SoulLootRules.Drop> drop = SoulLootRules.roll(structures, tags, context.getQueriedLootTableId(),
                context.getRandom());

        if (drop.isEmpty())
        {
            return generatedLoot;
        }

        Item soul = BuiltInRegistries.ITEM.get(drop.get().soul());

        if (soul == Items.AIR)
        {
            // A rule may only name an item that existed when it was read, so this is unreachable - and if it ever were
            // reached, an empty stack must never be handed to a container.
            return generatedLoot;
        }

        // ONE stack, whatever the count is: four souls are a stack of four and never four stacks of one.
        generatedLoot.add(new ItemStack(soul, drop.get().count()));

        LOGGER.debug("[SoulLoot] {} at {} pays {} x{}", drop.get().family().id(), BlockPos.containing(origin),
                drop.get().soul(), drop.get().count());

        return generatedLoot;
    }

    /**
     * @return true when this generation really fills a container.
     *
     *         <p>The block of the origin is the container itself when a chest, a barrel, a shulker box or a hopper is
     *         filled: vanilla sets the origin to the middle of that block and passes no block entity at all, so the
     *         world is asked instead of the context. A container on wheels has no block, so the entity of the
     *         generation is asked second. A mob that is killed, a block that is broken, a caught fish, a brushed block
     *         and a gift of a cat are none of the two, even though every one of them carries an origin.
     */
    private static boolean isContainer(ServerLevel level, Vec3 origin, LootContext context)
    {
        if (level.getBlockEntity(BlockPos.containing(origin)) instanceof RandomizableContainerBlockEntity)
        {
            return true;
        }

        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);

        return entity instanceof ContainerEntity && entity instanceof Container;
    }

    /**
     * Fills two lists with the structures that really own that position and with the tags those structures carry.
     *
     * <p>The reference map of a chunk is only a hint - it lists every structure that <i>may</i> reach into it - so every
     * candidate has to prove that one of its pieces really covers the position. Nothing here loads a chunk: the
     * container is being filled, so its chunk is loaded by definition, and only that chunk and the pieces that reach
     * into it are read.
     */
    private static void collectStructures(ServerLevel level, BlockPos pos, List<ResourceLocation> structures,
            List<ResourceLocation> tags)
    {
        StructureManager manager = level.structureManager();
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (Structure structure : manager.getAllStructuresAt(pos).keySet())
        {
            if (!manager.getStructureWithPieceAt(pos, structure).isValid())
            {
                continue;
            }

            ResourceLocation id = registry.getKey(structure);

            if (id == null)
            {
                continue;
            }

            structures.add(id);

            registry.getResourceKey(structure).flatMap(registry::getHolder)
                    .ifPresent(holder -> holder.tags().forEach(tag -> tags.add(tag.location())));
        }
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec()
    {
        return HungryCraftLootModifiers.SOUL_CONTAINER.get();
    }
}
