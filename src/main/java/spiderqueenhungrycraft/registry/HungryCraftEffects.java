package spiderqueenhungrycraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.ability.DayVisionEffect;
import spiderqueenhungrycraft.ability.LightSensitivityEffect;
import spiderqueenhungrycraft.ability.NecromancyCountdownEffect;
import spiderqueenhungrycraft.web.WebElement;
import spiderqueenhungrycraft.web.WebEmpowermentEffect;
import spiderqueenhungrycraft.web.WebbedEffect;

/** Mob effects of the mod. */
public final class HungryCraftEffects
{
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, SpiderQueenHungryCraft.MODID);

    public static final RegistryObject<MobEffect> WEBBED =
            MOB_EFFECTS.register("webbed", WebbedEffect::new);

    /**
     * The marker of the queen's daylight vision: while she has it, she never gains the light sensitivity of a
     * bright light. The rule itself lives in {@code QueenLightSensitivity}, which is also what clears the
     * countdown the moment the potion is drunk.
     */
    public static final RegistryObject<MobEffect> DAY_VISION =
            MOB_EFFECTS.register("day_vision", DayVisionEffect::new);

    /**
     * The light sensitivity of the queen (feature LIGHT-SENSITIVITY): the visible marker a bright light puts
     * on her, and the countdown that turns into weakness, slowness, nausea and finally blindness. The whole
     * rule lives in {@code QueenLightSensitivity}, this is only the effect it hands out.
     */
    public static final RegistryObject<MobEffect> LIGHT_SENSITIVITY =
            MOB_EFFECTS.register("light_sensitivity", LightSensitivityEffect::new);

    /** State marker of the queen: her next web shot carries poison. */
    public static final RegistryObject<MobEffect> POISON_WEB_EMPOWERMENT =
            MOB_EFFECTS.register("poison_web_empowerment",
                    () -> new WebEmpowermentEffect(WebElement.POISON));

    /** State marker of the queen: her next web shot carries fire. */
    public static final RegistryObject<MobEffect> FIRE_WEB_EMPOWERMENT =
            MOB_EFFECTS.register("fire_web_empowerment",
                    () -> new WebEmpowermentEffect(WebElement.FIRE));

    /**
     * 距下班还剩 (Necromancy IV round): the mark of a bone dragon that is under a HungryCraft contract. It carries no
     * attribute of any kind - the contract itself, and not this effect, is what a dragon obeys.
     */
    public static final RegistryObject<MobEffect> NECROMANCY_COUNTDOWN =
            MOB_EFFECTS.register("necromancy_countdown", NecromancyCountdownEffect::new);

    private HungryCraftEffects()
    {
    }

    public static void register(IEventBus modEventBus)
    {
        MOB_EFFECTS.register(modEventBus);
    }
}
