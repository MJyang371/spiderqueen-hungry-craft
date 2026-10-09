package spiderqueenhungrycraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import spiderqueenhungrycraft.brood.PrimedSpiderMineEntity;
import spiderqueenhungrycraft.registry.HungryCraftBlocks;

/**
 * The renderer of the primed delayed spider mine (Spider Trigger round).
 *
 * <p>It is the renderer of the block of TNT of the game, a number of lines long and changed in exactly two places: the
 * block it draws is the spider mine of this mod instead of the vanilla TNT, and the white flash and the fuse are the
 * ones {@code TntRenderer} uses, so a mine that has been lit looks like a block of TNT that is about to go off - which
 * is precisely what it is.
 */
public class PrimedSpiderMineRenderer extends EntityRenderer<PrimedSpiderMineEntity>
{
    /** The block the primed mine wears. It is the very block it was before it left the world. */
    private static final BlockState BODY = HungryCraftBlocks.DELAYED_SPIDER_MINE.get().defaultBlockState();

    private final BlockRenderDispatcher blockRenderer;

    public PrimedSpiderMineRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.shadowRadius = 0.5F;
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(PrimedSpiderMineEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight)
    {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.5F, 0.0F);

        int fuse = entity.getFuse();

        // The swell of the last second before the blast, exactly as wide as the game draws it for a block of TNT.
        if ((float) fuse - partialTick + 1.0F < 10.0F)
        {
            float swell = 1.0F - ((float) fuse - partialTick + 1.0F) / 10.0F;
            swell = Mth.clamp(swell, 0.0F, 1.0F);
            swell *= swell;
            swell *= swell;
            float scale = 1.0F + swell * 0.3F;
            poseStack.scale(scale, scale, scale);
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.translate(-0.5F, -0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));

        // The white flash of the game: the block is drawn white on every other fifth of a second of its fuse.
        TntMinecartRenderer.renderWhiteSolidBlock(this.blockRenderer, BODY, poseStack, buffer, packedLight,
                fuse / 5 % 2 == 0);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** The block atlas is the texture of this renderer: the model of a block carries its own textures. */
    @Override
    public ResourceLocation getTextureLocation(PrimedSpiderMineEntity entity)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
