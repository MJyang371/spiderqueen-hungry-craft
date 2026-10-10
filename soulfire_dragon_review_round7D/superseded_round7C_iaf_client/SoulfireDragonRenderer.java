package spiderqueenhungrycraft.compat.iceandfire.client;

import javax.annotation.Nullable;

import com.github.alexthe666.citadel.client.model.AdvancedEntityModel;
import com.github.alexthe666.citadel.client.model.TabulaModel;
import com.github.alexthe666.citadel.client.model.TabulaModelHandler;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;

/**
 * The renderer of the soul fire dragon (round 7C, born as the renderer of round 7B).
 *
 * <p>Ice and Fire's own {@code RenderDragonBase} can not be used: it is a {@code MobRenderer<EntityDragonBase, …>} and
 * asks the entity for {@code getRenderSize()}, {@code getVariant()}, {@code getDragonStage()} and half a dozen more
 * methods of a class this unit deliberately does not extend. What <b>is</b> reused is everything below the renderer:
 * the Tabula model geometry loaded from Ice and Fire's own model files, the pose library, and the bone texture
 * ({@code fire_skeleton_<stage>.png}).
 *
 * <p><b>One model for every soul fire dragon.</b> The round unifies the unit, so the renderer does not branch on what
 * the remains were: an ice, a fire and a lightning corpse all rise as the same creature and are drawn with the same
 * bones. The blue eye layer and the soul fire tint of the final look are the visual half of a later round; the model,
 * the poses and the texture are Ice and Fire's already.
 */
public class SoulfireDragonRenderer extends MobRenderer<SoulfireDragonEntity, AdvancedEntityModel<SoulfireDragonEntity>>
{
    /** The one model geometry this unit is drawn with. */
    private static final String MODEL_PATH = "/assets/iceandfire/models/tabula/firedragon/firedragon_Ground";

    /** The bone texture of that model, by stage, exactly as the skeleton of the other mod wears it. */
    private static final String TEXTURE = "firedragon/fire_skeleton_";

    /** Our own model, built <b>on the first frame</b> and kept afterwards; null while it could not be built. */
    @Nullable
    private AdvancedEntityModel<SoulfireDragonEntity> soulfireModel;

    /** True once the model was asked for, successfully or not, so the attempt is never repeated. */
    private boolean modelAttempted;

    /** True while the model of the base class is only a stand-in, which is what keeps it from ever being drawn. */
    private boolean placeholder = true;

    @SuppressWarnings("unchecked")
    public SoulfireDragonRenderer(EntityRendererProvider.Context context)
    {
        // <b>Nothing is loaded here.</b> The constructor runs while the renderers are registered, which is far too early
        // for a Tabula model: Citadel bakes those through a model loader that belongs to the resource reload, and
        // starting one from a registry event is what can leave the model manager - and with it the shaders of the
        // chunk layers - in a state the game then trips over. Ice and Fire's own base model is used as the stand-in,
        // it is never drawn (see render), and our own model is built on the first frame instead.
        super(context, (AdvancedEntityModel<SoulfireDragonEntity>) (Object) standIn(), 0.15F);
    }

    /** @return the model of Ice and Fire this renderer may borrow until its own is built, or null while there is none. */
    @Nullable
    private static TabulaModel standIn()
    {
        try
        {
            return com.github.alexthe666.iceandfire.client.IafClientSetup.FIRE_DRAGON_BASE_MODEL;
        }
        catch (Throwable notReady)
        {
            return null;
        }
    }

    /**
     * Builds the model of this unit, once, at a moment when the resource managers of the game are up.
     *
     * <p>Every failure is swallowed and logged: a unit without a model is a cosmetic problem, and a crash of the whole
     * client because a picture is missing is not.
     */
    @SuppressWarnings("unchecked")
    private void ensureModel()
    {
        if (this.modelAttempted)
        {
            return;
        }

        this.modelAttempted = true;

        try
        {
            this.soulfireModel = (AdvancedEntityModel<SoulfireDragonEntity>) (Object) new TabulaModel(
                    TabulaModelHandler.INSTANCE.loadTabulaModel(MODEL_PATH), new SoulfireDragonAnimator());
            this.placeholder = false;
        }
        catch (Throwable missingModel)
        {
            // Loud, but not fatal: the unit is invisible instead of the game being gone.
            spiderqueenhungrycraft.SpiderQueenHungryCraft.LOGGER.error(
                    "[round7C] the soul fire dragon model {} could not be loaded: the unit will be drawn with the "
                            + "borrowed stand-in or not at all",
                    MODEL_PATH, missingModel);
        }
    }

    @Override
    public void render(SoulfireDragonEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight)
    {
        this.ensureModel();

        if (this.soulfireModel == null)
        {
            // The borrowed model belongs to Ice and Fire's own animator, which expects one of its dragons and would
            // throw on ours: without our own model this renderer draws nothing at all.
            return;
        }

        if (this.placeholder)
        {
            this.model = this.soulfireModel;
            this.placeholder = false;
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SoulfireDragonEntity entity)
    {
        // The bone textures of Ice and Fire, by path: the very same pictures the skeleton of that mod wears, in the
        // stage of the remains this unit was raised from.
        int stage = Math.max(1, Math.min(5, entity.getStage()));

        return new ResourceLocation("iceandfire", "textures/models/" + TEXTURE + stage + ".png");
    }
}
