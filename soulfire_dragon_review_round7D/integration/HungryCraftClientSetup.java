package spiderqueenhungrycraft.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.client.model.ChadSteveModel;
import spiderqueenhungrycraft.client.model.SoulfireDragonModel;
import spiderqueenhungrycraft.compat.iceandfire.client.SoulfireIafDragonRenderer;
import spiderqueenhungrycraft.client.renderer.BroodSpiderRenderer;
import spiderqueenhungrycraft.client.renderer.ChadSteveRenderer;
import spiderqueenhungrycraft.client.renderer.ChronoSpiderRenderer;
import spiderqueenhungrycraft.client.renderer.CreeperSpiderRenderer;
import spiderqueenhungrycraft.client.renderer.HumanCombatantRenderer;
import spiderqueenhungrycraft.client.renderer.SweatlordSteveRenderer;
import spiderqueenhungrycraft.client.renderer.HuskSpiderRenderer;
import spiderqueenhungrycraft.client.renderer.ImpSpiderRenderer;
import spiderqueenhungrycraft.client.renderer.SkeletonSpiderRenderer;
import spiderqueenhungrycraft.client.renderer.SoulfireDragonRenderer;
import spiderqueenhungrycraft.client.renderer.SpiderArrowRenderer;
import spiderqueenhungrycraft.client.renderer.SpiderEggArrowRenderer;
import spiderqueenhungrycraft.client.renderer.PrimedSpiderMineRenderer;
import spiderqueenhungrycraft.client.renderer.SpiderTriggerRenderer;
import spiderqueenhungrycraft.client.renderer.StraySpiderRenderer;
import spiderqueenhungrycraft.client.renderer.ZombieSpiderRenderer;
import spiderqueenhungrycraft.client.renderer.SpiderHookEntityRenderer;
import spiderqueenhungrycraft.hook.SpiderHookItem;
import spiderqueenhungrycraft.registry.HungryCraftBlocks;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.registry.HungryCraftBlockEntities;
import spiderqueenhungrycraft.registry.HungryCraftItems;
import spiderqueenhungrycraft.registry.HungryCraftParticles;
import spiderqueenhungrycraft.web.WebElement;

/**
 * Client only renderer setup. The web shot reuses the vanilla thrown item renderer, the elemental webs
 * reuse the vanilla cobweb model and are coloured through the block tint of their element.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HungryCraftClientSetup
{
    private HungryCraftClientSetup()
    {
    }

    /**
     * The little web head on the tip of the rod (feature SPIDER-HOOK) is the second layer of the item model,
     * and it leaves with the web: while the rod carries the mark of a cast that is still out, the model with
     * the head is replaced by the one without it, and the head is back the moment the web is home. It is the
     * very trick the vanilla fishing rod plays with its own hook, only the state it reads is not the bobber
     * of a player but the cast mark of the rod.
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() ->
        {
            ResourceLocation cast = new ResourceLocation(SpiderQueenHungryCraft.MODID, "cast");

            for (Item rod : new Item[] { HungryCraftItems.SPIDER_HOOK.get(), HungryCraftItems.IRON_SPIDER_HOOK.get(),
                    HungryCraftItems.GOLDEN_SPIDER_HOOK.get(), HungryCraftItems.DIAMOND_SPIDER_HOOK.get(),
                    HungryCraftItems.NETHERITE_SPIDER_HOOK.get() })
            {
                ItemProperties.register(rod, cast,
                        (stack, level, entity, seed) -> SpiderHookItem.castId(stack) == 0L ? 0.0F : 1.0F);
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        // The renderer paints the elemental shot in the colour of its element, no new texture is needed.
        event.registerEntityRenderer(HungryCraftEntities.WEB_SHOT.get(), WebShotRenderer::new);
        event.registerEntityRenderer(HungryCraftEntities.COCOON.get(), CocoonRenderer::new);

        // The spider hook (feature SPIDER-HOOK) draws the web head and the silk from the hand to it, so the
        // rod and the web are visibly connected without a second entity for the thread.
        event.registerEntityRenderer(HungryCraftEntities.SPIDER_HOOK.get(), SpiderHookEntityRenderer::new);

        // The soul fire dragon (round 7D): the unit of this mod is drawn with the model of this mod, baked by the very
        // same event that bakes every vanilla mob. There is no optional mod in this line any more, and that is the
        // whole point of round 7D: in round 7C the picture of this unit came from the Tabula file of the other mod and
        // a wrong path in it turned the unit into something that spawned, obeyed orders and could not be seen.
        // Round 7F: I&F geometry/poses are mandatory in the I&F test instance.
        // Missing optional dependencies must never load the Tabula adapter.
        if (ModList.get().isLoaded("iceandfire") && ModList.get().isLoaded("citadel"))
        {
            event.registerEntityRenderer(HungryCraftEntities.SOULFIRE_DRAGON.get(),
                    SoulfireIafDragonRenderer::new);
            SpiderQueenHungryCraft.LOGGER.info("[Soulfire7F] I&F Tabula renderer selected");
        }
        else
        {
            event.registerEntityRenderer(HungryCraftEntities.SOULFIRE_DRAGON.get(), SoulfireDragonRenderer::new);
            SpiderQueenHungryCraft.LOGGER.info("[Soulfire7F] no I&F: 7D diagnostic renderer selected");
        }
        SpiderQueenHungryCraft.LOGGER.info("[round7D] soulfire dragon renderer registered (model of this mod)");

        // The stomach core is invisible on purpose: it is the wall of a cell, not a creature.
        event.registerEntityRenderer(HungryCraftEntities.STOMACH_CORE.get(), StomachCoreRenderer::new);

        // Chad Steve (Phase 9A-GAMEPASS1) draws his own model with his own skin, at the size of the model file.
        event.registerEntityRenderer(HungryCraftEntities.CHAD_STEVE.get(), ChadSteveRenderer::new);

        // Sweatlord Steve (Sweatlord round): the second transcendent human draws on the vanilla player model as well,
        // with his own renderer so the nine wide skins of the ordinary humans are available to him too. A missing
        // renderer would crash the client the moment he is spawned, which is exactly what this line prevents.
        event.registerEntityRenderer(HungryCraftEntities.SWEATLORD_STEVE.get(), SweatlordSteveRenderer::new);

        // Every human of this mod draws the same model (Phase HF0C): the hidden test entity of Phase HF0A and the
        // three tiers of Phase HF0C. The loop runs over the one list that also drives the attributes, so a human type
        // can never end up without a renderer - a missing renderer crashes the client the moment one is spawned.
        HungryCraftEntities.HUMAN_TYPES
                .forEach(type -> event.registerEntityRenderer(type.get(), HumanCombatantRenderer::new));

        // One line at client start, so a missing human renderer can never slip through unnoticed again.
        SpiderQueenHungryCraft.LOGGER.info("[HF0C] human renderer registered for {} entity types",
                HungryCraftEntities.HUMAN_TYPES.size());

        // The two spiders of the brood (this round's breeding pass) both draw the vanilla spider model: the
        // imp at half size, the ordinary brood spider at full size. The blue eyes layer is added inside each
        // renderer, so no spider type can be registered without its glow.
        event.registerEntityRenderer(HungryCraftEntities.IMP_SPIDER.get(), ImpSpiderRenderer::new);
        event.registerEntityRenderer(HungryCraftEntities.BROOD_SPIDER.get(), BroodSpiderRenderer::new);
        event.registerEntityRenderer(HungryCraftEntities.ZOMBIE_SPIDER.get(), ZombieSpiderRenderer::new);

        // The two special spiders of the Special Spiders round I. The skeleton spider also fires an arrow of its own
        // type (a vanilla arrow that flies through the allies of its queen), and that one is drawn by the very same
        // vanilla arrow renderer as the arrow of a skeleton of the game - no new texture anywhere.
        event.registerEntityRenderer(HungryCraftEntities.SKELETON_SPIDER.get(), SkeletonSpiderRenderer::new);
        event.registerEntityRenderer(HungryCraftEntities.CHRONO_SPIDER.get(), ChronoSpiderRenderer::new);
        event.registerEntityRenderer(HungryCraftEntities.SPIDER_ARROW.get(), SpiderArrowRenderer::new);

        // The spider egg arrow of a bow (Spider Mine and Egg Arrow round): the model of a plain arrow of the game,
        // pointed at the texture of a clutch of eggs. One line that can never be forgotten for a type that really
        // spawns, because a projectile without a renderer crashes the client the moment one is shot.
        event.registerEntityRenderer(HungryCraftEntities.SPIDER_EGG_ARROW.get(), SpiderEggArrowRenderer::new);

        // The Spider Trigger round: the block of TNT a delayed spider mine turns into, drawn with the model and the
        // white flash of the block of TNT of the game, and the plate of a trigger, drawn with the top of whatever
        // block it is disguised as.
        event.registerEntityRenderer(HungryCraftEntities.PRIMED_SPIDER_MINE.get(), PrimedSpiderMineRenderer::new);
        event.registerBlockEntityRenderer(HungryCraftBlockEntities.SPIDER_TRIGGER.get(), SpiderTriggerRenderer::new);

        // The two desert spiders of the Stray and Husk Spider round: the same vanilla spider model, their own colours and
        // their own glowing eyes.
        event.registerEntityRenderer(HungryCraftEntities.STRAY_SPIDER.get(), StraySpiderRenderer::new);
        event.registerEntityRenderer(HungryCraftEntities.HUSK_SPIDER.get(), HuskSpiderRenderer::new);

        // The drowned spider and its two tridents (Drowned Spider round). The ordinary trident is drawn by the renderer of
        // the game itself, the giant one by the same renderer at three times the size - fully qualified here so that this
        // block stays readable beside the imports above.
        event.registerEntityRenderer(HungryCraftEntities.DROWNED_SPIDER.get(),
                spiderqueenhungrycraft.client.renderer.DrownedSpiderRenderer::new);
        event.registerEntityRenderer(HungryCraftEntities.BROOD_TRIDENT.get(),
                net.minecraft.client.renderer.entity.ThrownTridentRenderer::new);
        event.registerEntityRenderer(HungryCraftEntities.GIANT_BROOD_TRIDENT.get(),
                spiderqueenhungrycraft.client.renderer.GiantSpiderTridentRenderer::new);

        // The creeper spider (Phase X): the same vanilla spider model as the whole brood, in creeper green, and with the
        // swell and the blink of its fuse drawn by its own renderer - no new model and no new animation file.
        event.registerEntityRenderer(HungryCraftEntities.CREEPER_SPIDER.get(), CreeperSpiderRenderer::new);

        // The infested Steve (Infested Steve round): the vanilla player model with the two vanilla equipment layers and
        // a texture of its own, so a soldier of the queen is never mistaken for an ordinary human.
        event.registerEntityRenderer(HungryCraftEntities.INFESTED_STEVE.get(),
                spiderqueenhungrycraft.client.renderer.InfestedSteveRenderer::new);

        // The soul remnant of a dead player (Soul Foundation V1): two crossed vertical planes painted with the one
        // soul texture, drawn with the emissive render type so the remnant glows in the dark like an eye of the game.
        event.registerEntityRenderer(HungryCraftEntities.SOUL_REMNANT.get(), SoulRemnantRenderer::new);
    }

    /**
     * Colours the elemental webs. The tint only reaches the model because the elemental model uses faces
     * with a tint index; the ordinary queen web keeps its untouched vanilla model and colour.
     */
    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event)
    {
        event.register((state, level, pos, tintIndex) -> WebElement.POISON.tintColor(),
                HungryCraftBlocks.POISON_QUEEN_WEB.get());
        event.register((state, level, pos, tintIndex) -> WebElement.FIRE.tintColor(),
                HungryCraftBlocks.FIRE_QUEEN_WEB.get());
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event)
    {
        event.registerLayerDefinition(CocoonModel.LAYER_LOCATION, CocoonModel::createBodyLayer);
        event.registerLayerDefinition(ChadSteveModel.LAYER_LOCATION, ChadSteveModel::createBodyLayer);
        event.registerLayerDefinition(SoulfireDragonModel.LAYER_LOCATION, SoulfireDragonModel::createBodyLayer);
    }

    /**
     * The two droplets of the digestive fluid (Phase 8E2A-POLISH-2). Both are json based and share the sprite set
     * of the vanilla dust particle, so a droplet looks exactly like the dust cloud of a regurgitation.
     */
    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event)
    {
        event.registerSpriteSet(HungryCraftParticles.GASTRIC_DROPLET.get(),
                GastricDropletParticle.SalivaProvider::new);
        event.registerSpriteSet(HungryCraftParticles.GASTRIC_DRIP.get(), GastricDropletParticle.DripProvider::new);
    }

    /**
     * The beast vision overlay is registered directly below the hotbar, which also puts it below the crosshair,
     * health, armor and hunger, so the presence layer can never cover a HUD element that has to stay readable.
     */
    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event)
    {
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "beast_vision", new BeastVisionOverlay());
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "spider_inspect", new SpiderInspectHud());

        // The jump meter of a charging beast sits exactly where the vanilla experience bar sits, which is
        // hidden while transformed: same place, same texture, same width as the horse jump bar.
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "beast_jump_meter", new QueenBeastJumpMeter());

        // The web bar of a queen (feature SILK-CAPACITY) is registered above the vanilla health bar, which is
        // the element it belongs to: the row of webs sits above the hearts and above the armor row, and it is
        // drawn right after them.
        event.registerAbove(VanillaGuiOverlay.PLAYER_HEALTH.id(), "queen_silk", new QueenSilkHud());

        // The stomach status panel of a queen sits in the bottom right corner, away from the hotbar, the hunger
        // bar and every beast element, and it is drawn last so nothing can cover it.
        event.registerAboveAll("stomach_status", new StomachHudOverlay());

        // The command wheel of the sceptre (Command Wheel round) is drawn above everything as well: while it is open it
        // is the only thing the queen is looking at, and nothing may cover its shortcuts.
        event.registerAboveAll("brood_wheel", new BroodWheelClient());

        // The fade of a Spider Bed Hibernation, drawn above everything for the moment in which no hibernation
        // screen is open. While that screen is open it draws the very same fade itself.
        event.registerAboveAll("spider_bed_hibernation", new SpiderBedHibernationOverlay());

        // The souls of the player (Soul Foundation V1) sit in the bottom left corner - the bottom right one belongs
        // to the stomach panel - and are registered below the chat panel, so a line of chat is never covered by them.
        event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(), "queen_souls", new SoulHud());
    }
}
