package spiderqueenhungrycraft.mixin.compat.iaf;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.mojang.logging.LogUtils;

/**
 * The gate that makes the Ice and Fire mixin of this mod <b>truly optional</b> (Necromancy IV prototype 1).
 *
 * <p>{@link EnumDragonTexturesMixin} names a class of another mod in its own annotations, so it can only ever be
 * loaded in a game that has that mod. This plugin is what guarantees that: it answers every
 * {@link #shouldApplyMixin} with "no" when Ice and Fire is not installed, and a mixin that is not applied is a mixin
 * whose class Mixin never loads - it is read with ASM to find out what it wants to do, and then dropped.
 *
 * <p>It deliberately references nothing of the game and nothing of this mod except this package, exactly as the Mixin
 * documentation asks of a plugin: the two questions below are answered with a mod list of the loading phase and with
 * the Java class loader, and with nothing else.
 */
public final class IceAndFireMixinPlugin implements IMixinConfigPlugin
{
    /** The mod id of Ice and Fire. */
    private static final String ICE_AND_FIRE_MOD_ID = "iceandfire";

    /**
     * The reference map of this configuration (Necromancy IV round, the riding fix): the one member of the game that
     * one of these hooks overrides, written the way Mixin resolves it. See {@link #getRefMapperConfig()}.
     */
    private static final String REFMAP = "spiderqueen_hungrycraft_iaf.refmap.json";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Whether Ice and Fire is part of this launch, asked once, at the earliest moment the answer exists.
     *
     * <p>This single boolean is the whole gate. There is deliberately <b>no</b> class lookup in this plugin, see the
     * note on {@link #shouldApplyMixin}.
     */
    private static final boolean ICE_AND_FIRE_LOADED = loadingModListKnowsIceAndFire();

    static
    {
        if (ICE_AND_FIRE_LOADED)
        {
            LOGGER.info("Hungry Craft: Ice and Fire detected, the dragon compatibility hooks are active.");
        }
        else
        {
            // The normal case of a player who does not use that mod: nothing is said loudly and nothing is done.
            LOGGER.debug("Hungry Craft: Ice and Fire is not installed, the dragon compatibility hooks stay off.");
        }
    }

    @Override
    public void onLoad(String mixinPackage)
    {
        LOGGER.debug("Hungry Craft: loading the Ice and Fire mixin configuration '{}'.", mixinPackage);
    }

    /**
     * @return the reference map of this configuration, named explicitly, so that the question "which refmap does this
     *         configuration use" has exactly one answer in exactly one place.
     *
     *         <p>It is {@code spiderqueen_hungrycraft_iaf.refmap.json}, which is shipped in the jar and declared in
     *         {@code spiderqueen_hungrycraft_iaf.mixins.json} as well: the same value from both ends, because a null
     *         here would leave the question to Mixin's own reading of the configuration file.
     *
     *         <p>That file exists for <b>one</b> hook and one hook only:
     *         {@link EntityDragonBaseRiderMixin}, which injects into {@code Entity#getControllingPassenger} - a member
     *         of the game that Ice and Fire overrides, so its name in a published jar is the SRG name {@code m_6688_}
     *         while a development workspace calls it by its official name. A reference map is the only way to bridge
     *         that, and the generated refmap of the main configuration can not carry the entry: the Minecraft
     *         mappings do not know the class of another mod. The entry is therefore written by hand, and the file is
     *         complete with that one line.
     *
     *         <p>The two client hooks of this configuration ({@code EnumDragonTexturesMixin},
     *         {@code EntityDragonBaseEyesMixin}) are declared {@code remap = false} and target methods of Ice and Fire
     *         itself, whose names are identical in a development workspace and in a published jar:
     *         {@code getTextureFromDragon} and {@code shouldRenderEyes} have no entry, and must have none.
     */
    @Override
    public String getRefMapperConfig()
    {
        return REFMAP;
    }

    /**
     * @return true only when Ice and Fire is part of this launch.
     *
     *         <p><b>Why the class of a target is not looked up here as well.</b> An earlier version of this plugin
     *         also asked the class loader whether the class a hook targets can be loaded, and put that question into
     *         a static initialiser. It is the wrong place for it: a plugin is instantiated <b>inside</b> the mixin
     *         transformer, so a {@code Class.forName} from here can pull a class into the transformer that the
     *         transformer is not ready for, and there is no answer that is worth that risk. The loading mod list
     *         already answers the only question that matters - is the mod there - and everything below the mod level
     *         is Ice and Fire's own business: if one of its classes ever moved, this configuration is
     *         {@code "required": false}, so Mixin reports the hook it could not apply and the game starts anyway.
     */
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        return ICE_AND_FIRE_LOADED;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
    {
    }

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }

    /**
     * Writes one line for every hook that was really applied to a real class of this game.
     *
     * <p><b>Why this is worth a permanent line</b> (Necromancy IV round 5). The riding hook of this compatibility once
     * failed to apply in a live client and nobody noticed for a whole session: Mixin had logged that failure as a
     * warning in the middle of a startup, and there was nothing in the log that said "the hook is fine" - so silence had
     * to be read as success, which is exactly the kind of assumption that hid the bug. From this round on, every applied
     * hook says so, and a game whose log holds neither an application line nor a failure for a hook did not run it.
     *
     * <p>It is deliberately not behind the diagnostics switch of the compatibility: it is one line per hook per
     * launch, it is written by the only component that can know the answer for certain, and the answer is "is this
     * build's optional compatibility really in the game" - which is not a detail.
     */
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
        LOGGER.info("Hungry Craft: Ice and Fire hook applied - {} -> {}", mixinClassName,
                targetClassName.replace('/', '.'));
    }

    /**
     * @return true when the <b>loading</b> mod list knows Ice and Fire.
     *
     *         <p>{@code net.minecraftforge.fml.loading.LoadingModList} is the mod list of the scan phase: its instance
     *         is created in {@code FMLLoader.completeScan(IModuleLayerManager)}, which ModLauncher calls as a
     *         transformation service callback while the mods are discovered - strictly before the launch plugins run
     *         and therefore strictly before this plugin is ever instantiated. Its {@code getModFileById} is a plain map
     *         lookup that answers {@code null} for a mod that is not there.
     *
     *         <p>The ordinary {@code net.minecraftforge.fml.ModList} of the rest of this mod must <b>not</b> be asked
     *         here: its instance is created in {@code ModLoader.gatherAndInitializeMods(...)}, which runs while
     *         Minecraft starts, long after the mixins have been applied. In this phase {@code ModList.get()} hands
     *         back {@code null}, so {@code ModList.get().isLoaded(...)} would throw a {@code NullPointerException}
     *         inside the launcher instead of answering a simple question.
     */
    private static boolean loadingModListKnowsIceAndFire()
    {
        try
        {
            net.minecraftforge.fml.loading.LoadingModList loading = net.minecraftforge.fml.loading.LoadingModList.get();

            return loading != null && loading.getModFileById(ICE_AND_FIRE_MOD_ID) != null;
        }
        catch (Throwable notAvailableHere)
        {
            // Every other way of asking is allowed to fail, and a "no" here means only one thing: this compatibility
            // stays switched off. That is the safe direction - a hook that is not applied can not be wrong.
            return false;
        }
    }
}
