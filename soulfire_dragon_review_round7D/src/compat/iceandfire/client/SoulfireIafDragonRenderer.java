package spiderqueenhungrycraft.compat.iceandfire.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import spiderqueenhungrycraft.client.renderer.SoulfireDragonRenderer;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;

/**
 * Actual I&F fire-dragon skeleton geometry and poses, driven ONLY by the custom
 * HungryCraft entity state. Absolutely no EntityDragonBase / I&F dragon AI.
 *
 * On a missing jar resource we log a precise failure and draw the old model as
 * an obvious diagnostic fallback; it MUST NOT be accepted as successful I&F art.
 */
@OnlyIn(Dist.CLIENT)
public final class SoulfireIafDragonRenderer extends MobRenderer<SoulfireDragonEntity, SoulfireIafTabulaModel>
{
    private final SoulfireDragonRenderer failureFallback;

    public SoulfireIafDragonRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SoulfireIafTabulaModel(), 0.9F);
        this.failureFallback = new SoulfireDragonRenderer(context);

        // Round 7H, the last word on the blue eyes: the layer is NOT registered any more.
        //
        // Round 7G rebuilt its constants in the units the layer really draws in, and round 7H photographed the result from
        // the muzzle, the side and at night (build\probe_shots\round7h). The eye patch is still a pair of flat quads that
        // float in the air beside the skull instead of lying in its eye sockets: the picture shows it hanging past the
        // jaw, over the sand behind the head. The head of this model is a set of Tabula boxes whose local surfaces are not
        // the planes a fixed pair of coordinates can be placed on, and a floating patch of glow is worse than no patch.
        //
        // A plain bone dragon without glowing eyes is the model that was signed off in round 7F, so that is what is left
        // here: {SoulfireEyesLayer} stays in the source, unregistered, for the day the head geometry is rebuilt properly
        // (the multi-part rig of this round is the first time the head has real, server-side coordinates to hang an eye on).
    }

    @Override
    protected void scale(SoulfireDragonEntity entity, PoseStack stack, float partialTicks)
    {
        // Visually scale the same I&F geometry, not a new model. The table lives on the unit itself (round 7G), so the
        // muzzle of the breath and this scale can never drift apart.
        float factor = entity.stageScale();
        stack.scale(factor, factor, factor);
        this.shadowRadius = 0.3F + factor * 0.18F;
    }

    @Override
    public ResourceLocation getTextureLocation(SoulfireDragonEntity entity)
    {
        int stage = Math.max(1, Math.min(5, entity.getStage()));
        return new ResourceLocation("iceandfire",
                "textures/models/firedragon/fire_skeleton_" + stage + ".png");
    }

    /**
     * The model this renderer draws, so that a development probe can ask it - at runtime, on a real client - whether the
     * skeleton of Ice and Fire is really there (<code>/landing</code> of the probe). Nothing of the game uses it.
     */
    public SoulfireIafTabulaModel boneModel()
    {
        return this.model;
    }

    @Override
    public void render(SoulfireDragonEntity entity, float yaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight)
    {
        if (!this.model.ensureLoaded())
        {
            this.failureFallback.render(entity, yaw, partialTicks, poseStack, buffers, packedLight);
            return;
        }
        super.render(entity, yaw, partialTicks, poseStack, buffers, packedLight);
    }
}
