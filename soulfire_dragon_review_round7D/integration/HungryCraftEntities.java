package spiderqueenhungrycraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.BroodSpiderEntity;
import spiderqueenhungrycraft.brood.ChronoSpiderEntity;
import spiderqueenhungrycraft.brood.CreeperSpiderEntity;
import spiderqueenhungrycraft.brood.DrownedSpiderEntity;
import spiderqueenhungrycraft.brood.HuskSpiderEntity;
import spiderqueenhungrycraft.brood.ImpSpiderEntity;
import spiderqueenhungrycraft.brood.InfestedSteveEntity;
import spiderqueenhungrycraft.brood.PrimedSpiderMineEntity;
import spiderqueenhungrycraft.brood.SkeletonSpiderEntity;
import spiderqueenhungrycraft.brood.SpiderArrow;
import spiderqueenhungrycraft.brood.SpiderEggArrow;
import spiderqueenhungrycraft.brood.SpiderTrident;
import spiderqueenhungrycraft.brood.StraySpiderEntity;
import spiderqueenhungrycraft.brood.ZombieSpiderEntity;
import spiderqueenhungrycraft.cocoon.CocoonEntity;
import spiderqueenhungrycraft.entity.ChadSteveEntity;
import spiderqueenhungrycraft.entity.HumanCombatantEntity;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.hook.SpiderHookEntity;
import spiderqueenhungrycraft.soul.SoulRemnantEntity;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;
import spiderqueenhungrycraft.stomach.StomachConstants;
import spiderqueenhungrycraft.stomach.StomachCoreEntity;
import spiderqueenhungrycraft.web.WebShotProjectile;

/** Entities of the mod. */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HungryCraftEntities
{
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SpiderQueenHungryCraft.MODID);

    public static final RegistryObject<EntityType<WebShotProjectile>> WEB_SHOT = ENTITY_TYPES.register("web_shot",
            () -> EntityType.Builder.<WebShotProjectile>of(WebShotProjectile::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("web_shot"));

    /**
     * 灵火龙 / Soulfire Dragon (round 7C): the one unit every revived bone dragon becomes, and the unit this mod
     * controls itself.
     *
     * <p><b>It is registered here, in the ordinary registry of this mod, and not by Ice and Fire.</b> The entity class
     * is pure vanilla, so the type exists, spawns and saves in a world that does not have that mod at all; only its
     * renderer and its animations are the optional part, and those live behind the compatibility gate.
     *
     * <p>The size is that of a stage four dragon, and the tracking range is generous because a unit of this kind is
     * meant to be flown over long distances and must stay visible to its owner while it does.
     *
     * <p><b>The id used to be {@code contracted_bone_dragon}</b> during the prototype of round 7B. The rename is safe
     * for an old test world because {@link #remapOldIds} hands every leftover of the old
     * id the new one, so nothing is lost and nothing is duplicated.
     */
    public static final RegistryObject<EntityType<SoulfireDragonEntity>> SOULFIRE_DRAGON =
            ENTITY_TYPES.register("soulfire_dragon",
                    () -> EntityType.Builder.<SoulfireDragonEntity>of(SoulfireDragonEntity::new,
                                    MobCategory.CREATURE)
                            .sized(3.0F, 3.5F)
                            // 灵火龙 is a creature of soul fire (round 7E A2): fire, burning and lava do not hurt it,
                            // and nothing else is made immune by this flag - a blade, a fall, a spell and the void all
                            // still reach it.
                            .fireImmune()
                            .clientTrackingRange(16)
                            .updateInterval(2)
                            .build("soulfire_dragon"));

    /**
     * The web head of a spider hook (feature SPIDER-HOOK). It is the flying half of the hook rod: fired at
     * the world, it hangs on the first block it touches and drags its thrower towards that block. It carries
     * no damage, hits no creature and cannot be summoned, so it never becomes anything but a grapple point.
     *
     * <p>The tracking range is generous because a web may fly quite far, and the update interval is short so
     * the web head and the silk that is drawn to it move smoothly instead of stepping.
     */
    /**
     * The web head of the spider hook. It moves 1.6 blocks every tick, so its position has to reach the clients
     * every single tick: with an update every second tick a client would only ever hear about a jump of more
     * than three blocks and the flight would look like a block being teleported instead of a thrown thing.
     *
     * <p>The tracking range is wider than the longest silk of the mod (128 blocks = 8 chunks), so a client never
     * loses the web while the thread to it still has to be drawn.
     */
    public static final RegistryObject<EntityType<SpiderHookEntity>> SPIDER_HOOK = ENTITY_TYPES.register("spider_hook",
            () -> EntityType.Builder.<SpiderHookEntity>of(SpiderHookEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .noSummon()
                    .build("spider_hook"));

    /**
     * The arrow of a skeleton spider (Special Spiders round I).
     *
     * <p>It is a full entity type of its own rather than a plain vanilla arrow, because only a real type keeps the one
     * rule that matters for it - flying straight through the allies of its queen - even after a chunk is unloaded and
     * loaded again. It carries no item and no texture: it is drawn by the vanilla arrow renderer.
     */
    public static final RegistryObject<EntityType<SpiderArrow>> SPIDER_ARROW = ENTITY_TYPES.register("spider_arrow",
            () -> EntityType.Builder.<SpiderArrow>of(SpiderArrow::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("spider_arrow"));

    /**
     * The spider egg arrow of a bow (Spider Mine and Egg Arrow round).
     *
     * <p>It is a real entity type of its own, exactly like the arrow of a skeleton spider, and for the same reason and
     * one more: its own type is what survives a chunk being unloaded and loaded again, and it is what lets the client
     * draw the clutch it carries with its own renderer. Everything else about it is the arrow of the game - see
     * {@link spiderqueenhungrycraft.brood.SpiderEggArrow}.
     *
     * <p>Its size, its tracking range and its update interval are the ones of a plain arrow of the game, because it
     * flies exactly like one.
     */
    public static final RegistryObject<EntityType<SpiderEggArrow>> SPIDER_EGG_ARROW =
            ENTITY_TYPES.register("spider_egg_arrow",
                    () -> EntityType.Builder.<SpiderEggArrow>of(SpiderEggArrow::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build("spider_egg_arrow"));

    /**
     * The primed delayed spider mine (Spider Trigger round): the block of TNT a fired delayed mine turns into.
     *
     * <p>Its size, its immunity to fire, its tracking range and its update interval are the ones of
     * {@code minecraft:tnt} itself, because it falls, flashes and is drawn exactly like a block of TNT - it is one,
     * with the brood of a spider mine inside it.
     */
    public static final RegistryObject<EntityType<PrimedSpiderMineEntity>> PRIMED_SPIDER_MINE =
            ENTITY_TYPES.register("primed_spider_mine",
                    () -> EntityType.Builder.<PrimedSpiderMineEntity>of(PrimedSpiderMineEntity::new, MobCategory.MISC)
                            .fireImmune()
                            .sized(0.98F, 0.98F)
                            .clientTrackingRange(10)
                            .updateInterval(10)
                            .build("primed_spider_mine"));

    /** One generic cocoon entity for every captured creature, size comes from the synced prey data. */
    public static final RegistryObject<EntityType<CocoonEntity>> COCOON = ENTITY_TYPES.register("cocoon",
            () -> EntityType.Builder.<CocoonEntity>of(CocoonEntity::new, MobCategory.MISC)
                    .sized(CocoonEntity.MIN_VISUAL_WIDTH, CocoonEntity.MIN_VISUAL_HEIGHT)
                    .clientTrackingRange(10)
                    // Mob like position updates, so a falling cocoon is interpolated smoothly instead
                    // of jumping from one synced position to the next.
                    .updateInterval(2)
                    .build("cocoon"));

    /**
     * The core of a stomach cell (Phase 8E1-GATE2A). It is a runtime only entity: it is never written to the
     * save file, so a restart can neither duplicate a core nor keep a cell loaded through one.
     */
    public static final RegistryObject<EntityType<StomachCoreEntity>> STOMACH_CORE =
            ENTITY_TYPES.register("stomach_core",
                    () -> EntityType.Builder.<StomachCoreEntity>of(StomachCoreEntity::new, MobCategory.MISC)
                            .sized(StomachConstants.CORE_WIDTH, StomachConstants.CORE_HEIGHT)
                            .clientTrackingRange(2)
                            // A core never moves, so its position never has to be synced after it was added.
                            .updateInterval(Integer.MAX_VALUE)
                            .noSave()
                            .build("stomach_core"));

    /**
     * Chad Steve (Phase 9A-GAMEPASS1). The category is {@link MobCategory#CREATURE} on purpose: he is
     * <b>not</b> a monster, he only hunts monsters. There is no spawn placement for him anywhere, so he can
     * never spawn naturally - his spawn egg and {@code /summon} are the only ways in.
     */
    public static final RegistryObject<EntityType<ChadSteveEntity>> CHAD_STEVE = ENTITY_TYPES.register("chad_steve",
            () -> EntityType.Builder.<ChadSteveEntity>of(ChadSteveEntity::new, MobCategory.CREATURE)
                    .sized(ChadSteveEntity.COLLISION_WIDTH, ChadSteveEntity.COLLISION_HEIGHT)
                    .clientTrackingRange(10)
                    .build("chad_steve"));

    /**
     * The standard human combatant (Phase HF0A-GAMEPASS1). Like Chad Steve he is filed under
     * {@link MobCategory#CREATURE}: he is a human, not a monster. There is no spawn placement for him anywhere, so
     * only the spawn egg and {@code /summon} can bring him into a world.
     */
    public static final RegistryObject<EntityType<SweatlordSteveEntity>> SWEATLORD_STEVE =
            ENTITY_TYPES.register("sweatlord_steve",
                    () -> EntityType.Builder.<SweatlordSteveEntity>of(SweatlordSteveEntity::new, MobCategory.CREATURE)
                            .sized(SweatlordSteveEntity.COLLISION_WIDTH, SweatlordSteveEntity.COLLISION_HEIGHT)
                            .clientTrackingRange(10)
                            .build("sweatlord_steve"));

    /**
     * The standard human combatant (Phase HF0A-GAMEPASS1). Like Chad Steve he is filed under
     * {@link MobCategory#CREATURE}: he is a human, not a monster. There is no spawn placement for him anywhere, so
     * only the spawn egg and {@code /summon} can bring him into a world.
     */
    public static final RegistryObject<EntityType<HumanCombatantEntity>> HUMAN_COMBATANT =
            ENTITY_TYPES.register("human_combatant",
                    () -> EntityType.Builder.<HumanCombatantEntity>of(HumanCombatantEntity::new, MobCategory.CREATURE)
                            .sized(HumanCombatantEntity.COLLISION_WIDTH, HumanCombatantEntity.COLLISION_HEIGHT)
                            .clientTrackingRange(10)
                            .build("human_combatant"));

    /**
     * The three tiered humans of Phase HF0C: low, mid and high prepared Steve.
     *
     * <p>All three are the very same java class, the very same AI, the very same renderer and the very same
     * attributes. What makes them different is only their entity type, which is what selects their tier in the json
     * loadout table, and their spawn egg.
     */
    public static final RegistryObject<EntityType<HumanCombatantEntity>> LOW_PREPARED_HUMAN =
            registerPreparedHuman("low_prepared_human");

    public static final RegistryObject<EntityType<HumanCombatantEntity>> MID_PREPARED_HUMAN =
            registerPreparedHuman("mid_prepared_human");

    public static final RegistryObject<EntityType<HumanCombatantEntity>> HIGH_PREPARED_HUMAN =
            registerPreparedHuman("high_prepared_human");

    private static RegistryObject<EntityType<HumanCombatantEntity>> registerPreparedHuman(String name)
    {
        return ENTITY_TYPES.register(name,
                () -> EntityType.Builder.<HumanCombatantEntity>of(HumanCombatantEntity::new, MobCategory.CREATURE)
                        .sized(HumanCombatantEntity.COLLISION_WIDTH, HumanCombatantEntity.COLLISION_HEIGHT)
                        .clientTrackingRange(10)
                        .build(name));
    }

    /**
     * Every human of this mod: the hidden test entity of Phase HF0A and the three tiers of Phase HF0C.
     *
     * <p>This one list drives both the attribute registration and the client renderer registration, so a new human
     * type can never be added with a missing renderer - which is exactly what crashed the client in Phase HF0C's
     * first pass.
     */
    public static final java.util.List<RegistryObject<EntityType<HumanCombatantEntity>>> HUMAN_TYPES =
            java.util.List.of(HUMAN_COMBATANT, LOW_PREPARED_HUMAN, MID_PREPARED_HUMAN, HIGH_PREPARED_HUMAN);

    /**
     * The small imp spider of the brood (this round's breeding pass): the spider a spider egg on the ground
     * hatches into.
     *
     * <p>It is filed under {@link MobCategory#MONSTER} like any spider of the game, but it has no spawn
     * placement anywhere and it never despawns - see {@code SpiderMinionEntity} - so a wild imp spider can
     * only be created by an egg, a spawn egg or a command.
     */
    public static final RegistryObject<EntityType<ImpSpiderEntity>> IMP_SPIDER = ENTITY_TYPES.register("imp_spider",
            () -> EntityType.Builder.<ImpSpiderEntity>of(ImpSpiderEntity::new, MobCategory.MONSTER)
                    .sized(ImpSpiderEntity.COLLISION_WIDTH, ImpSpiderEntity.COLLISION_HEIGHT)
                    .clientTrackingRange(8)
                    .build("imp_spider"));

    /**
     * The ordinary brood spider (this round's breeding pass): the spider a cocoon with a spider egg hatches.
     * It is the mod's own spider, never a {@code minecraft:spider}, and it is registered exactly like the
     * imp spider.
     */
    public static final RegistryObject<EntityType<BroodSpiderEntity>> BROOD_SPIDER =
            ENTITY_TYPES.register("brood_spider",
                    () -> EntityType.Builder.<BroodSpiderEntity>of(BroodSpiderEntity::new, MobCategory.MONSTER)
                            .sized(BroodSpiderEntity.COLLISION_WIDTH, BroodSpiderEntity.COLLISION_HEIGHT)
                            .clientTrackingRange(10)
                            .build("brood_spider"));

    /**
     * The zombie spider (Brood Ability framework): the first special spider of the brood, hatched from a cocoon that
     * digested a zombie. It is registered exactly like the two spiders above - it is a spider of this mod first and a
     * special spider second.
     */
    public static final RegistryObject<EntityType<ZombieSpiderEntity>> ZOMBIE_SPIDER =
            ENTITY_TYPES.register("zombie_spider",
                    () -> EntityType.Builder.<ZombieSpiderEntity>of(ZombieSpiderEntity::new, MobCategory.MONSTER)
                            .sized(ZombieSpiderEntity.COLLISION_WIDTH, ZombieSpiderEntity.COLLISION_HEIGHT)
                            .clientTrackingRange(10)
                            .build("zombie_spider"));

    /**
     * The skeleton spider (Special Spiders round I): the ranged spider of the brood, hatched from a cocoon that
     * digested a skeleton. Registered exactly like every other spider of this mod.
     */
    public static final RegistryObject<EntityType<SkeletonSpiderEntity>> SKELETON_SPIDER =
            ENTITY_TYPES.register("skeleton_spider",
                    () -> EntityType.Builder.<SkeletonSpiderEntity>of(SkeletonSpiderEntity::new, MobCategory.MONSTER)
                            .sized(SkeletonSpiderEntity.COLLISION_WIDTH, SkeletonSpiderEntity.COLLISION_HEIGHT)
                            .clientTrackingRange(10)
                            .build("skeleton_spider"));

    /**
     * The chrono spider (Special Spiders round I): the heavy spider of the brood, and the only one that teleports -
     * and only when its queen orders a chrono shift. Registered exactly like every other spider of this mod, and with
     * no teleport of its own anywhere.
     */
    public static final RegistryObject<EntityType<ChronoSpiderEntity>> CHRONO_SPIDER =
            ENTITY_TYPES.register("chrono_spider",
                    () -> EntityType.Builder.<ChronoSpiderEntity>of(ChronoSpiderEntity::new, MobCategory.MONSTER)
                            .sized(ChronoSpiderEntity.COLLISION_WIDTH, ChronoSpiderEntity.COLLISION_HEIGHT)
                            .clientTrackingRange(10)
                            .build("chrono_spider"));

    /**
     * The stray spider (Stray and Husk Spider round): the ranged spider of the cold, hatched from a cocoon that digested
     * a stray. It is the skeleton spider with a chilled arrow, registered exactly like every other spider of this mod.
     */
    public static final RegistryObject<EntityType<StraySpiderEntity>> STRAY_SPIDER =
            ENTITY_TYPES.register("stray_spider",
                    () -> EntityType.Builder.<StraySpiderEntity>of(StraySpiderEntity::new, MobCategory.MONSTER)
                            .sized(StraySpiderEntity.COLLISION_WIDTH, StraySpiderEntity.COLLISION_HEIGHT)
                            .clientTrackingRange(10)
                            .build("stray_spider"));

    /**
     * The husk spider (Stray and Husk Spider round): the brawler of the desert, hatched from a cocoon that digested a
     * husk. It is the zombie spider with venom in its fangs, registered exactly like every other spider of this mod.
     */
    public static final RegistryObject<EntityType<HuskSpiderEntity>> HUSK_SPIDER =
            ENTITY_TYPES.register("husk_spider",
                    () -> EntityType.Builder.<HuskSpiderEntity>of(HuskSpiderEntity::new, MobCategory.MONSTER)
                            .sized(HuskSpiderEntity.COLLISION_WIDTH, HuskSpiderEntity.COLLISION_HEIGHT)
                            .clientTrackingRange(10)
                            .build("husk_spider"));

    /**
     * The drowned spider (Drowned Spider round): the heavy ranged spider of the brood, hatched from a cocoon that
     * digested a drowned. Thirty five health, two armour, thirty five blocks of reach and a real trident in its claws.
     */
    public static final RegistryObject<EntityType<DrownedSpiderEntity>> DROWNED_SPIDER =
            ENTITY_TYPES.register("drowned_spider",
                    () -> EntityType.Builder.<DrownedSpiderEntity>of(DrownedSpiderEntity::new, MobCategory.MONSTER)
                            .sized(DrownedSpiderEntity.COLLISION_WIDTH, DrownedSpiderEntity.COLLISION_HEIGHT)
                            .clientTrackingRange(10)
                            .build("drowned_spider"));

    /**
     * The creeper spider (Phase X): the living blast of the brood, hatched from a cocoon that digested a creeper.
     *
     * <p>Thirty five health, six damage and a walk 30 percent faster than the rest of the brood, plus the two blasts of
     * its species - one when it dies and one when it decides to. It is a spider of the mod in every other way and is
     * registered exactly like its eight siblings.
     */
    public static final RegistryObject<EntityType<CreeperSpiderEntity>> CREEPER_SPIDER =
            ENTITY_TYPES.register("creeper_spider",
                    () -> EntityType.Builder.<CreeperSpiderEntity>of(CreeperSpiderEntity::new, MobCategory.MONSTER)
                            .sized(CreeperSpiderEntity.COLLISION_WIDTH, CreeperSpiderEntity.COLLISION_HEIGHT)
                            .clientTrackingRange(10)
                            .build("creeper_spider"));

    /**
     * The infested Steve (Infested Steve round): the body of a digested human, driven by the spiders inside it.
     *
     * <p>It is filed under {@link MobCategory#MONSTER} like every spider of the brood, and it is a spider of the brood
     * in every way that matters - owner, orders, commands, faction, carrier, ranks - but its hitbox is the hitbox of a
     * human, because that is the body it wears and the human model has to stand in it.
     */
    public static final RegistryObject<EntityType<InfestedSteveEntity>> INFESTED_STEVE =
            ENTITY_TYPES.register("infested_steve",
                    () -> EntityType.Builder.<InfestedSteveEntity>of(InfestedSteveEntity::new, MobCategory.MONSTER)
                            .sized(InfestedSteveEntity.COLLISION_WIDTH, InfestedSteveEntity.COLLISION_HEIGHT)
                            .clientTrackingRange(10)
                            .build("infested_steve"));

    /**
     * The soul remnant of a dead player (Soul Foundation V1).
     *
     * <p>It is a plain entity of the {@code MISC} category: it fights nothing, walks nowhere and carries no loot, it
     * only waits. Its box is the size of the crossed planes that are drawn for it. The tracking range is the ordinary
     * creature range so that a queen really sees the remnant of her last death from where she died, and the update
     * interval is long because the entity can not move at all - there is nothing to update twenty times a second.
     *
     * <p>{@code fireImmune} is the belt to the braces of the entity itself, which refuses every source of damage
     * (see {@link SoulRemnantEntity}); {@code noSummon} keeps it out of {@code /summon} and out of spawn eggs, because
     * a remnant without a player to belong to would be a piece of scenery that can never be collected.
     */
    public static final RegistryObject<EntityType<SoulRemnantEntity>> SOUL_REMNANT =
            ENTITY_TYPES.register("soul_remnant",
                    () -> EntityType.Builder.<SoulRemnantEntity>of(SoulRemnantEntity::new, MobCategory.MISC)
                            .sized(0.7F, 0.9F)
                            .clientTrackingRange(10)
                            .updateInterval(20)
                            .fireImmune()
                            .noSummon()
                            .build("soul_remnant"));

    /**
     * The tridents of the drowned spider (Drowned Spider round): one entity type for each of its two shots, because the
     * giant one is drawn three times as large and the scale belongs to the renderer of its own type.
     *
     * <p>Both are real projectiles of the brood - never items, never loot, never loyal - and both fly through the allies
     * of their queen. See {@link SpiderTrident}.
     */
    public static final RegistryObject<EntityType<SpiderTrident>> BROOD_TRIDENT =
            ENTITY_TYPES.register("brood_trident",
                    () -> EntityType.Builder.<SpiderTrident>of(
                            (type, level) -> new SpiderTrident(type, level, SpiderTrident.Mode.NORMAL), MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(8)
                            .updateInterval(20)
                            .build("brood_trident"));

    public static final RegistryObject<EntityType<SpiderTrident>> GIANT_BROOD_TRIDENT =
            ENTITY_TYPES.register("giant_brood_trident",
                    () -> EntityType.Builder.<SpiderTrident>of(
                            (type, level) -> new SpiderTrident(type, level, SpiderTrident.Mode.GIANT), MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(8)
                            .updateInterval(20)
                            .build("giant_brood_trident"));

    /** The attributes of the mod's living entity, which are handed over through the mod bus. */
    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event)
    {
        event.put(STOMACH_CORE.get(), StomachCoreEntity.createAttributes().build());
        event.put(SOULFIRE_DRAGON.get(), SoulfireDragonEntity.createAttributes().build());
        event.put(CHAD_STEVE.get(), ChadSteveEntity.createAttributes().build());
        event.put(SWEATLORD_STEVE.get(), SweatlordSteveEntity.createAttributes().build());
        HUMAN_TYPES.forEach(type -> event.put(type.get(), HumanCombatantEntity.createAttributes().build()));
        event.put(IMP_SPIDER.get(), ImpSpiderEntity.createAttributes().build());
        event.put(BROOD_SPIDER.get(), BroodSpiderEntity.createAttributes().build());
        event.put(ZOMBIE_SPIDER.get(), ZombieSpiderEntity.createAttributes().build());
        event.put(SKELETON_SPIDER.get(), SkeletonSpiderEntity.createAttributes().build());
        event.put(CHRONO_SPIDER.get(), ChronoSpiderEntity.createAttributes().build());
        event.put(STRAY_SPIDER.get(), StraySpiderEntity.createAttributes().build());
        event.put(HUSK_SPIDER.get(), HuskSpiderEntity.createAttributes().build());
        event.put(DROWNED_SPIDER.get(), DrownedSpiderEntity.createAttributes().build());
        event.put(CREEPER_SPIDER.get(), CreeperSpiderEntity.createAttributes().build());
        event.put(INFESTED_STEVE.get(), InfestedSteveEntity.createAttributes().build());
    }

    private HungryCraftEntities()
    {
    }

    public static void register(IEventBus modEventBus)
    {
        ENTITY_TYPES.register(modEventBus);

        // The alias below listens on the ORDINARY Forge bus, not on this mod bus: MissingMappingsEvent is not an
        // IModBusEvent (its own source says "Fired on the MinecraftForge#EVENT_BUS forge bus"), so annotating the
        // method with @SubscribeEvent inside a Bus.MOD subscriber is refused at load time. It is added by hand here,
        // which is the one place where the two buses have to be told apart.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(HungryCraftEntities::remapOldIds);
    }

    /**
     * The old id of the unit, handed to the new one (round 7C).
     *
     * <p>The prototype of round 7B registered the very same creature as {@code contracted_bone_dragon}. A test world
     * that already contains one is not broken by the rename: this handler answers the missing mapping with the new
     * entry, so the entity in that chunk is read as a soul fire dragon - same class, same data, new id. Nothing is
     * dropped, nothing is duplicated, and a world that never saw the old id never notices this method at all.
     */
    public static void remapOldIds(net.minecraftforge.registries.MissingMappingsEvent event)
    {
        net.minecraft.resources.ResourceLocation oldId = new net.minecraft.resources.ResourceLocation(
                SpiderQueenHungryCraft.MODID, "contracted_bone_dragon");

        for (net.minecraftforge.registries.MissingMappingsEvent.Mapping<EntityType<?>> mapping : event
                .getAllMappings(Registries.ENTITY_TYPE))
        {
            if (mapping.getKey().equals(oldId))
            {
                mapping.remap(SOULFIRE_DRAGON.get());
            }
        }
    }
}
