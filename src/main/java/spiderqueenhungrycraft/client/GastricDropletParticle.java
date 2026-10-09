package spiderqueenhungrycraft.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import spiderqueenhungrycraft.devour.DevourTuning;

/**
 * A droplet of the dark green digestive fluid (Phase 8E2A-POLISH-2).
 *
 * <p>This is the particle that carries the fluid whenever it is not a full vomit: it is thrown out of the mouth
 * of a beast that snaps at something, and it drips off a creature while the stomach digests it. It flies, the
 * gravity of the game pulls it down, the air slows it down, and it is gone the moment it reaches the ground -
 * no puddle, no decal, just a splash of liquid that disappears.
 *
 * <p>The sprite set is the one of the vanilla dust particle, so a droplet and the dust cloud of a regurgitation
 * are literally the same drawing in the very same colour. No texture is added by this mod.
 */
@OnlyIn(Dist.CLIENT)
public class GastricDropletParticle extends TextureSheetParticle
{
    /** How much the air slows a droplet down every tick. */
    public static final double DRAG = 0.965D;

    /** How much of its speed a droplet keeps while it lies on the ground. */
    public static final double GROUND_DRAG = 0.7D;

    /** Gravity of the saliva of a devour, in blocks per tick squared. */
    public static final float SALIVA_GRAVITY = 0.055F;

    /** Gravity of a droplet of a digestion: a little gentler, a droplet drips rather than falls. */
    public static final float DRIP_GRAVITY = 0.045F;

    /** The life of a droplet: a limited number of ticks, with a little chance in it. */
    public static final int LIFETIME_MIN = 12;
    public static final int LIFETIME_MAX = 24;

    private final SpriteSet sprites;

    protected GastricDropletParticle(ClientLevel level, double x, double y, double z, double dx, double dy,
            double dz, SpriteSet sprites, float minScale, float maxScale, float gravity)
    {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;

        float scale = minScale + this.random.nextFloat() * (maxScale - minScale);
        this.quadSize = 0.1F * scale;
        this.setSize(0.02F, 0.02F);

        // The very colour of the fluid of a regurgitation, so all three read as one liquid.
        this.setColor(DevourTuning.FLUID_RED, DevourTuning.FLUID_GREEN, DevourTuning.FLUID_BLUE);

        // A short life of the game, like any splash: a droplet is not a piece of scenery.
        this.lifetime = LIFETIME_MIN + this.random.nextInt(LIFETIME_MAX - LIFETIME_MIN + 1);
        this.gravity = gravity;
        this.friction = 0.92F;
        this.hasPhysics = true;
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType()
    {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick()
    {
        // The sprite set is walked through with the age, exactly like the dust of the game does it.
        this.setSpriteFromAge(this.sprites);

        if (this.age++ >= this.lifetime)
        {
            this.remove();
            return;
        }

        // Air resistance, then the fall: the droplet flies forward, slows down and drops towards the floor.
        this.xd *= DRAG;
        this.zd *= DRAG;
        this.yd *= DRAG;
        this.yd -= (double) this.gravity;

        this.move(this.xd, this.yd, this.zd);

        if (this.onGround)
        {
            // The ground swallows the rest of the droplet: it slides a little and fades away.
            this.yd = 0.0D;
            this.xd *= GROUND_DRAG;
            this.zd *= GROUND_DRAG;
            this.alpha -= 0.15F * (1.0F + this.random.nextFloat());
            this.quadSize *= 0.9F;

            if (this.alpha <= 0.05F)
            {
                this.remove();
            }
        }
    }

    /** Provider of the saliva a beast throws out of its mouth: the larger of the two ranges. */
    @OnlyIn(Dist.CLIENT)
    public static class SalivaProvider implements ParticleProvider<SimpleParticleType>
    {
        private final SpriteSet sprites;

        public SalivaProvider(SpriteSet sprites)
        {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double dx, double dy, double dz)
        {
            return new GastricDropletParticle(level, x, y, z, dx, dy, dz, this.sprites, 1.4F, 1.8F, 0.055F);
        }
    }

    /** Provider of the droplets of a digestion: a little smaller and a little slower. */
    @OnlyIn(Dist.CLIENT)
    public static class DripProvider implements ParticleProvider<SimpleParticleType>
    {
        private final SpriteSet sprites;

        public DripProvider(SpriteSet sprites)
        {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double dx, double dy, double dz)
        {
            return new GastricDropletParticle(level, x, y, z, dx, dy, dz, this.sprites, 1.2F, 1.6F, 0.045F);
        }
    }
}
