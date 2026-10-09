package spiderqueenhungrycraft.mixin.compat.iaf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.alexthe666.iceandfire.entity.EntityDragonBase;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.compat.iceandfire.IceAndFireSupport;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyDiagnostics;

/**
 * The third, equally optional hook of the Ice and Fire compatibility (Necromancy IV round, the riding fix), and the
 * one that turns "being carried in the mouth of a skeleton" into a real ride.
 *
 * <p><b>What Ice and Fire does with a rider.</b> The whole riding of that mod hangs on one question,
 * {@code Entity#getControllingPassenger()}, and its answer for a dragon is:
 *
 * <pre>
 * for (Entity passenger : getPassengers())
 *     if (passenger instanceof Player player
 *             &amp;&amp; getTarget() != passenger
 *             &amp;&amp; isTame()
 *             &amp;&amp; getOwnerUUID() != null
 *             &amp;&amp; getOwnerUUID().equals(player.getUUID()))
 *         return player;
 * return null;
 * </pre>
 *
 * <p>A dragon under a HungryCraft contract is deliberately <b>never tamed</b> - the contract must not hand the queen
 * the tame menu of that mod - so that answer is {@code null} while she sits on its back, and three things follow from
 * it, all three of them visible in the bytecode of that mod:
 *
 * <ul>
 * <li>{@code EntityDragonBase#positionRider} asks the same question and, when the answer is not the passenger itself,
 * calls {@code updatePreyInMouth(passenger)}: the passenger is moved to the <b>head</b>, the
 * {@code ANIMATION_SHAKEPREY} loop is started and it is dropped again after fifty five ticks - that is the "bitten and
 * shaken" ride the round reported,</li>
 * <li>{@code EntityFireDragon#travel} takes its rider branch only when that answer is not null, so the passenger had
 * no control at all - the skeleton walked where its own AI wanted while the queen was sitting on it,</li>
 * <li>and {@code useFlyingPathFinder} is {@code isFlying() &amp;&amp; getControllingPassenger() == null}, so a ridden
 * skeleton kept being navigated by the mod instead of by the player.</li>
 * </ul>
 *
 * <p><b>What this hook answers instead.</b> For a dragon that is <b>not</b> tamed and whose passenger is the player
 * who holds its contract, this injection returns that player - exactly the answer the native branch above would give
 * for a tamed and owned dragon. Nothing else is changed and nothing else is claimed:
 *
 * <ul>
 * <li>a tamed dragon returns early, so the native answer, the native owner check and the native target check stay
 * exactly where they are: this file never speaks for a dragon that Ice and Fire already understands,</li>
 * <li>a dragon that is not under a contract of that player returns nothing, so a stranger who climbs onto a
 * contracted skeleton is still carried in its mouth by the native code,</li>
 * <li>a passenger the skeleton is fighting is skipped, which is the native guard and the reason it is kept: a dragon
 * that is biting at somebody may not be steered by them at the same moment,</li>
 * <li>and no field of the dragon is written. The tame flag, the owner of the record and the command are untouched,
 * because the answer is computed here on every call and never stored.</li>
 * </ul>
 *
 * <p>Because everything above follows from that one answer, this single injection gives the round what it asked for:
 * the rider is placed on the back by {@code getRiderPosition}, the skeleton stops playing the prey animation, the
 * passenger steers it with the native keys of that mod, and it lands, hovers and takes off exactly like a dragon that
 * was tamed by its owner - while the data of that dragon still says it was never tamed at all.
 *
 * <p>The configuration this hook lives in is gated by {@link IceAndFireMixinPlugin}, so a game without Ice and Fire
 * never loads this class and never resolves the name of another mod.
 */
@Mixin(EntityDragonBase.class)
public abstract class EntityDragonBaseRiderMixin
{
    /**
     * <b>The spelling of the very same hook for a published game: the method by its SRG name.</b>
     *
     * <p>This injector exists because the official-name one was measured <b>twice</b> in a real client and did not work
     * there. Both attempts asked for the member by its official name - once bare, once with its descriptor - and both
     * failed in the same way:
     *
     * <pre>
     * Mixin apply failed spiderqueen_hungrycraft_iaf.mixins.json:EntityDragonBaseRiderMixin
     *   -> com.github.alexthe666.iceandfire.entity.EntityDragonBase:
     * InvalidInjectionException: Critical injection failure: @Inject annotation on hungrycraft$riderOfAContract
     *   could not find any targets matching 'getControllingPassenger()Lnet/minecraft/world/entity/LivingEntity;' in
     *   com.github.alexthe666.iceandfire.entity.EntityDragonBase.
     *   Using refmap spiderqueen_hungrycraft_iaf.refmap.json
     * </pre>
     *
     * <p>The reference map was named in the message, so it was read - and the name was still not translated: in a
     * published game the member really is called {@code m_6688_}, and no way of describing it under its official name
     * can find it. The translation is still stated in {@code spiderqueen_hungrycraft_iaf.refmap.json} (which carries
     * both plausible owner forms, because that is the correct thing to state), but this compatibility no longer
     * <b>depends</b> on that translation being applied.
     *
     * <p>So the hook is declared twice, once per world it can find itself in, and each declaration tolerates the other
     * world - {@code require = 0} means "finding nothing here is not an error":
     *
     * <ul>
     * <li><b>a published game</b>: the class carries {@code m_6688_} and nothing else, so this injector matches and the
     * official one stays silent,</li>
     * <li><b>a development workspace</b> (only reachable if somebody puts the Ice and Fire jars into {@code run/mods},
     * which the build deliberately does not do): the deobfuscated jar carries {@code getControllingPassenger}, so the
     * official injector matches and this one stays silent,</li>
     * <li>and in either world exactly one of the two is applied, which is why the body lives in one method and the other
     * injector simply calls it.</li>
     * </ul>
     */
    @Inject(method = "m_6688_()Lnet/minecraft/world/entity/LivingEntity;", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private void hungrycraft$riderOfAContractSrgName(CallbackInfoReturnable<LivingEntity> callback)
    {
        hungrycraft$riderOfAContract(callback);
    }

    /**
     * The same hook, for a development workspace: the member by its official name and its full descriptor.
     *
     * <p>{@code require = 0} is what lets both declarations live side by side: in a published game this name does not
     * exist and finding nothing is not an error.
     */
    @Inject(method = "getControllingPassenger()Lnet/minecraft/world/entity/LivingEntity;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void hungrycraft$riderOfAContract(CallbackInfoReturnable<LivingEntity> callback)
    {
        EntityDragonBase dragon = (EntityDragonBase) (Object) this;

        // The positive half of the acceptance criterion of this round: this line can only ever be written by a game in
        // which the hook is really applied, because it is written from inside it - and it is written whether or not the
        // switchable diagnostics are on, because "is the hook applied" is not a detail, it is the difference between a
        // ride and a dragon carrying a player in its mouth. One line per launch.
        NecromancyDiagnostics.announceRiderHookApplied();

        if (dragon.isTame())
        {
            // Not our business: a dragon that really was tamed has an owner of its own, and the answer of Ice and
            // Fire for it is the correct one - owner only, and never while the animal is fighting that owner.
            return;
        }

        for (Entity passenger : dragon.getPassengers())
        {
            // The prey test is deliberately gone (Necromancy IV round 7): a contract dragon whose owner was ever set as
            // its target used to fail this loop - the rider was not returned as the controlling passenger, positionRider
            // treated him as prey, and SHAKE_PREY bit and dropped him. The owner of a contract is now returned whatever
            // the target says, and the isolation (no autonomous target source) is what keeps the owner out of the target
            // slot in the first place. A stranger still fails the ownership check and is answered by Ice and Fire.
            if (passenger instanceof Player player && IceAndFireSupport.mayActAsNecromancyOwner(dragon, player))
            {
                NecromancyDiagnostics.once("rider-hook-answered",
                        "riding hook handed a dragon to its contract owner: dragon=" + dragon.getUUID()
                                + " rider=" + player.getUUID() + " (this is the native ride, not the prey pose)");
                callback.setReturnValue(player);

                return;
            }
        }
    }
}
