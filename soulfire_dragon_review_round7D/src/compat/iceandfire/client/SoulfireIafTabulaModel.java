package spiderqueenhungrycraft.compat.iceandfire.client;

import java.util.Collections;

import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.TabulaModel;
import com.github.alexthe666.citadel.client.model.basic.BasicModelPart;
import com.github.alexthe666.iceandfire.client.model.util.TabulaModelHandlerHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;

/**
 * Stable MobRenderer model type which lazily delegates all geometry to the real
 * Ice and Fire fire-dragon Tabula skeleton model.
 *
 * Never bakes Tabula in the EntityRenderersEvent renderer constructor: the old
 * implementation did that too early and broke shader initialization.
 *
 * The source file belongs to HungryCraft. Models/texture/poses stay in I&F's jar.
 *
 * <p><b>Why this extends the vanilla {@link EntityModel} instead of Citadel's {@code AdvancedEntityModel},
 * and why that changes nothing about the picture:</b> the Ice and Fire and Citadel jars in {@code libs} are the raw
 * release jars, which still carry the SRG names of Minecraft ({@code m_6973_}, {@code m_7695_}), while this workspace
 * compiles against the official (Mojang) names - the build script deliberately keeps them as {@code compileOnly}
 * references and performs no remapping step. Extending {@code AdvancedEntityModel} therefore asks a class to
 * implement the same animation callback twice, once under each name, and a single name survives the reobfuscation
 * of the finished jar, which would be a broken class. Extending the plain model class of the game and <i>delegating
 * every part, every pose and every pixel</i> to the Citadel {@link TabulaModel} keeps one name per method and delivers
 * exactly the same geometry: no box of this mod is ever drawn by this class.
 */
public final class SoulfireIafTabulaModel extends EntityModel<SoulfireDragonEntity>
{
    // Exact same lower-case resource as IafClientSetup, including required .tbl.
    private static final String PATH = "/assets/iceandfire/models/tabula/firedragon/firedragon_ground.tbl";

    private TabulaModel delegate;
    private boolean attempted;

    /** Development probe switch: when on, this model reports whether the animation path is really used at all. */
    private static final boolean RIG_DEBUG = Boolean.getBoolean("spiderqueen.soulfire.rig");
    private int setupCalls;
    private int drawCalls;

    public boolean ensureLoaded()
    {
        if (!this.attempted)
        {
            this.attempted = true;
            try
            {
                // A static Tabula geometry without its original animation library
                // is not a successful I&F animation integration.
                if (com.github.alexthe666.iceandfire.client.model.util.DragonAnimationsLibrary.getModel(
                        com.github.alexthe666.iceandfire.client.model.util.EnumDragonPoses.GROUND_POSE,
                        com.github.alexthe666.iceandfire.client.model.util.EnumDragonModelTypes.FIRE_DRAGON_MODEL) == null
                        || com.github.alexthe666.iceandfire.client.model.util.DragonAnimationsLibrary.getModel(
                        com.github.alexthe666.iceandfire.client.model.util.EnumDragonPoses.FLIGHT1,
                        com.github.alexthe666.iceandfire.client.model.util.EnumDragonModelTypes.FIRE_DRAGON_MODEL) == null)
                {
                    throw new IllegalStateException("I&F GROUND/FLIGHT pose library not ready");
                }
                this.delegate = new TabulaModel(
                        TabulaModelHandlerHelper.loadTabulaModel(PATH), new SoulfireDragonAnimator());
                if (this.delegate == null || !this.delegate.getAllParts().iterator().hasNext())
                {
                    throw new IllegalStateException("I&F Tabula skeleton has no model parts");
                }
                SpiderQueenHungryCraft.LOGGER.info(
                        "[Soulfire7F] REAL I&F bone-dragon Tabula model loaded: {}", PATH);
            }
            catch (Exception e)
            {
                SpiderQueenHungryCraft.LOGGER.error(
                        "[Soulfire7F] FAILED to load real I&F skeleton: {}. "
                                + "7D fallback is displayed ONLY AS A FAILURE INDICATOR.", PATH, e);
                this.delegate = null;
            }
        }
        return this.delegate != null;
    }

    /** Read-only access for the eye overlay to follow the REAL pose hierarchy. */
    public TabulaModel tabula()
    {
        return this.delegate;
    }

    /** Every pixel drawn here is drawn by Ice and Fire's own Tabula model. */
    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay,
            float red, float green, float blue, float alpha)
    {
        if (RIG_DEBUG && ++this.drawCalls % 100 == 1)
        {
            SpiderQueenHungryCraft.LOGGER.info("[round7G] model: renderToBuffer call {} , delegate={}", this.drawCalls,
                    this.delegate != null);
        }

        if (this.delegate != null)
        {
            this.delegate.renderToBuffer(pose, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    @Override
    public void setupAnim(SoulfireDragonEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch)
    {
        if (RIG_DEBUG && ++this.setupCalls % 1000 == 1)
        {
            SpiderQueenHungryCraft.LOGGER.info("[round7G] model: setupAnim call {} , delegate={} , limbAmount={}",
                    this.setupCalls, this.delegate != null, limbSwingAmount);
        }

        // The delegate hands this straight to the pose adapter of Ice and Fire, which is the whole animation.
        if (this.delegate != null)
        {
            this.delegate.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }

    /** Kept for the probes: the parts of the real skeleton, exactly as Citadel hands them out. */
    public Iterable<BasicModelPart> parts()
    {
        return this.delegate == null ? Collections.emptyList() : this.delegate.parts();
    }

    /** Kept for the probes: the boxes of the real skeleton, exactly as Citadel hands them out. */
    public Iterable<AdvancedModelBox> getAllParts()
    {
        return this.delegate == null ? Collections.emptyList() : this.delegate.getAllParts();
    }
}
