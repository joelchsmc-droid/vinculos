package vinculos.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vinculos.BondManager;

/**
 * Un Warden nunca se enoja con un jugador que tiene afecto máximo con algún Warden.
 * (Si algún nombre cambiara en otra versión, el mod igual funciona: hay un respaldo en VinculosMod.)
 */
@Mixin(Warden.class)
public abstract class WardenMixin {

	@Inject(method = "increaseAngerAt(Lnet/minecraft/world/entity/Entity;IZ)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void vinculos$noAnger(Entity entity, int amount, boolean listening, CallbackInfo ci) {
		if (entity instanceof Player player && BondManager.isWardenAlly(player.getUUID())) {
			ci.cancel();
		}
	}

	@Inject(method = "canTargetEntity", at = @At("HEAD"), cancellable = true, require = 0)
	private void vinculos$notATarget(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player player && BondManager.isWardenAlly(player.getUUID())) {
			cir.setReturnValue(false);
		}
	}
}
