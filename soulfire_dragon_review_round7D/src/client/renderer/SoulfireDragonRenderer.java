package spiderqueenhungrycraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.client.model.SoulfireDragonModel;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;

/**
 * The renderer of the soul fire dragon (round 7D).
 *
 * <p><b>It cannot fail to draw.</b> Round 7C built this unit's picture out of the Tabula model of the other mod and
 * swallowed every failure to protect the client, which is why a broken model path turned into a dragon nobody could
 * see. The model this renderer draws is baked by the game itself out of {@link SoulfireDragonModel} - the same
 * {@code RegisterLayerDefinitions} event that bakes every vanilla mob - so there is no file to find, no resource to
 * load and nothing to catch: if the game can draw a cow, it can draw this unit.
 *
 * <p>The texture is this mod's own ({@code textures/entity/soulfire_dragon.png}) and the renderer holds no state: the
 * only thing it keeps is a frame counter, which is what the render probe of round 7D reads to prove that the unit is
 * really being drawn on a screen.
 */
@OnlyIn(Dist.CLIENT)
public class SoulfireDragonRenderer extends MobRenderer<SoulfireDragonEntity, SoulfireDragonModel>
{
    /** The picture of the unit: bone, with the soul fire in the eyes, the mouth, the wings and the tail. */
    public static final ResourceLocation TEXTURE = new ResourceLocation(SpiderQueenHungryCraft.MODID,
            "textures/entity/soulfire_dragon.png");

    /** How many frames this renderer has drawn a soul fire dragon in. Read by the render probe of round 7D. */
    private static int renderedFrames;

    public SoulfireDragonRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SoulfireDragonModel(context.bakeLayer(SoulfireDragonModel.LAYER_LOCATION)), 0.9F);
    }

    @Override
    public ResourceLocation getTextureLocation(SoulfireDragonEntity entity)
    {
        return TEXTURE;
    }

    @Override
    public void render(SoulfireDragonEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight)
    {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        renderedFrames++;
    }

    /** @return how many frames of this game session have drawn a soul fire dragon. */
    public static int renderedFrames()
    {
        return renderedFrames;
    }
}
