package spiderqueenhungrycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.cocoon.CocoonEntity;

/**
 * Deliberately simple cocoon shape: three stacked boxes with a bulging middle. There is one model for
 * every kind of prey, the size difference comes from the entity scale in the renderer.
 *
 * <p>The model fills exactly one block on every axis, so scaling it by the visual size of the cocoon
 * yields exactly that many blocks. The wider middle bulge is what makes a cocoon look like a wrapped
 * up creature instead of a thin pillar.
 */
public class CocoonModel extends EntityModel<CocoonEntity>
{
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "cocoon"), "main");

    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 64;

    private final ModelPart root;

    public CocoonModel(ModelPart root)
    {
        this.root = root;
    }

    /** Builds the shape once, reused for every cocoon in the world. */
    public static LayerDefinition createBodyLayer()
    {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Lower taper, bulging middle and upper taper. Sixteen model units are one block, and the
        // widest point reaches a full block, so a scale of one means exactly one block.
        root.addOrReplaceChild("bottom",
                CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, 0.0F, -7.0F, 14.0F, 5.0F, 14.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 19).addBox(-8.0F, 5.0F, -8.0F, 16.0F, 6.0F, 16.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("top",
                CubeListBuilder.create().texOffs(0, 41).addBox(-7.0F, 11.0F, -7.0F, 14.0F, 5.0F, 14.0F),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(CocoonEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch)
    {
        // Static shape, the shaking after a hit is applied by the renderer.
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay,
            float red, float green, float blue, float alpha)
    {
        this.root.render(pose, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
