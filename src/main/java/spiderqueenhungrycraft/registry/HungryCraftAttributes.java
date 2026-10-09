package spiderqueenhungrycraft.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The visceral armor of the stomach system (Phase 8E1-GATE2B).
 *
 * <p>Visceral armor is the inner counterpart of the ordinary armor: the outer armor of a queen decides how much
 * an enemy hurts her from the outside, while her visceral armor decides how much a prey hurts her from inside her
 * own stomach. It is an ordinary attribute of every player, so the vanilla {@code /attribute} command reads and
 * writes it, and the stomach only reads it.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HungryCraftAttributes
{
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ATTRIBUTE, SpiderQueenHungryCraft.MODID);

    /** Default of the visceral armor, which is a full set of iron armor. */
    public static final double VISCERAL_ARMOR_DEFAULT = 15.0D;

    /** Ceiling of the attribute, well above the iron default so future gear has room. */
    public static final double VISCERAL_ARMOR_MAX = 30.0D;

    /**
     * The inner armor of a queen. It is clamped to the vanilla armor range, because the value is finally handed to
     * the ordinary armor calculation of the wall of her stomach.
     */
    public static final RegistryObject<Attribute> VISCERAL_ARMOR = ATTRIBUTES.register("visceral_armor",
            () -> new RangedAttribute("attribute.spiderqueen_hungrycraft.visceral_armor", VISCERAL_ARMOR_DEFAULT, 0.0D,
                    VISCERAL_ARMOR_MAX).setSyncable(true));

    private HungryCraftAttributes()
    {
    }

    /** Every player owns the attribute, queen or not: the stomach system decides what it is worth. */
    @SubscribeEvent
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event)
    {
        event.add(EntityType.PLAYER, VISCERAL_ARMOR.get());
    }

    public static void register(IEventBus modEventBus)
    {
        ATTRIBUTES.register(modEventBus);
    }
}
