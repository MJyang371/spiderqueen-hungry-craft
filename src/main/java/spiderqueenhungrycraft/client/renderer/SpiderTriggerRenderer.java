package spiderqueenhungrycraft.client.renderer;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import spiderqueenhungrycraft.brood.SpiderTriggerBlockEntity;

/**
 * The renderer of a disguised spider trigger (Spider Trigger round).
 *
 * <p>A block model of the game carries a texture that is written in the resource pack, so one block can never wear the
 * texture of an arbitrary other block through its model. This class is how it is done instead: the plate is drawn here,
 * with the <b>top</b> sprite of whatever block the trigger is disguised as - read out of the baked model of that block,
 * together with the tint the game gives it (which is what makes a grass plate green), and laid on the very box a
 * pressure plate of the game has.
 *
 * <p>The whole class therefore does one small thing, and only when the trigger is really disguised: a plate that is not
 * disguised is an ordinary block model and never reaches this renderer.
 */
public class SpiderTriggerRenderer implements BlockEntityRenderer<SpiderTriggerBlockEntity>
{
    /** The box of a pressure plate of the game, in a sixteenth of a block. */
    private static final float MIN = 1.0F / 16.0F;
    private static final float MAX = 15.0F / 16.0F;
    private static final float TOP = 1.0F / 16.0F;

    public SpiderTriggerRenderer(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public void render(SpiderTriggerBlockEntity trigger, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay)
    {
        BlockState disguise = trigger.getDisguise();

        if (disguise == null)
        {
            // The plain stone plate is the block model itself, and there is nothing to add to it.
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getBlockModelShaper().getBlockModel(disguise);
        List<BakedQuad> top = model.getQuads(disguise, Direction.UP, RandomSource.create(42L));
        TextureAtlasSprite sprite = top.isEmpty() ? model.getParticleIcon() : top.get(0).getSprite();

        // The tint of the top face of that block: a grass block is grey without it and green with it.
        int tint = minecraft.getBlockColors().getColor(disguise, trigger.getLevel(), trigger.getBlockPos(), 1);

        float red = 1.0F;
        float green = 1.0F;
        float blue = 1.0F;

        if (tint != -1)
        {
            red = (tint >> 16 & 0xFF) / 255.0F;
            green = (tint >> 8 & 0xFF) / 255.0F;
            blue = (tint & 0xFF) / 255.0F;
        }

        // The sprite lives in the block atlas, so the geometry is drawn with the atlas bound and the sprite's own
        // coordinates - and it is drawn without culling, because a plate is a hand made box and not a quad of a model.
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));

        plate(consumer, poseStack.last().pose(), sprite, red, green, blue, packedLight, packedOverlay);
    }

    /** The box of the plate: its top face and its four sides, all wearing the top sprite of the disguise. */
    private static void plate(VertexConsumer consumer, Matrix4f matrix, TextureAtlasSprite sprite, float red,
            float green, float blue, int light, int overlay)
    {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        // Up.
        quad(consumer, matrix, new float[] { MIN, TOP, MIN, MIN, TOP, MAX, MAX, TOP, MAX, MAX, TOP, MIN },
                new float[] { u0, v1, u0, v0, u1, v0, u1, v1 }, 0.0F, 1.0F, 0.0F, red, green, blue, light, overlay);

        // North and south: the thin edge of the plate, wearing the same picture.
        quad(consumer, matrix, new float[] { MIN, 0.0F, MIN, MIN, TOP, MIN, MAX, TOP, MIN, MAX, 0.0F, MIN },
                new float[] { u0, v1, u0, v0, u1, v0, u1, v1 }, 0.0F, 0.0F, -1.0F, red, green, blue, light, overlay);
        quad(consumer, matrix, new float[] { MAX, 0.0F, MAX, MAX, TOP, MAX, MIN, TOP, MAX, MIN, 0.0F, MAX },
                new float[] { u0, v1, u0, v0, u1, v0, u1, v1 }, 0.0F, 0.0F, 1.0F, red, green, blue, light, overlay);

        // West and east.
        quad(consumer, matrix, new float[] { MIN, 0.0F, MAX, MIN, TOP, MAX, MIN, TOP, MIN, MIN, 0.0F, MIN },
                new float[] { u0, v1, u0, v0, u1, v0, u1, v1 }, -1.0F, 0.0F, 0.0F, red, green, blue, light, overlay);
        quad(consumer, matrix, new float[] { MAX, 0.0F, MIN, MAX, TOP, MIN, MAX, TOP, MAX, MAX, 0.0F, MAX },
                new float[] { u0, v1, u0, v0, u1, v0, u1, v1 }, 1.0F, 0.0F, 0.0F, red, green, blue, light, overlay);
    }

    /** One quad of four positions (x, y, z each) with four uv pairs, in the order of the vertex list. */
    private static void quad(VertexConsumer consumer, Matrix4f matrix, float[] positions, float[] uvs, float normalX,
            float normalY, float normalZ, float red, float green, float blue, int light, int overlay)
    {
        for (int vertex = 0; vertex < 4; vertex++)
        {
            consumer.vertex(matrix, positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2])
                    .color(red, green, blue, 1.0F)
                    .uv(uvs[vertex * 2], uvs[vertex * 2 + 1])
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(normalX, normalY, normalZ)
                    .endVertex();
        }
    }
}
