package vinculos;

import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

public class VinculosMod implements ModInitializer {
	public static final String MOD_ID = "vinculos";

	// ---- Probabilidades (baja a propósito) ----
	/** Chance de que el Warden se incomode al darle la primera flor. */
	public static final float FAIL_START = 0.10f;
	/** Chance de que se incomode al regalarle una flor. */
	public static final float FAIL_GIFT = 0.06f;
	/** Chance de que se incomode al acariciarlo. */
	public static final float FAIL_PET = 0.06f;
	/** Si se incomoda, chance de que además te ataque. */
	public static final float ATTACK_CHANCE = 0.30f;
	/** Cuánto afecto se pierde al incomodarlo. */
	public static final int FAIL_PENALTY = 8;

	private static final int PINK = 0xFF69B4;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> BondManager.load(saveFile(server)));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> BondManager.save(saveFile(server)));

		// Click derecho en un Warden: con una flor empieza el vínculo; con cualquier cosa (si ya hay vínculo) abre el menú.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (!(player instanceof ServerPlayer sp) || !(entity instanceof Warden warden)) return InteractionResult.PASS;

			ItemStack held = sp.getItemInHand(hand);
			boolean flower = held.is(ItemTags.FLOWERS);
			boolean bonded = BondManager.hasBond(sp.getUUID(), warden.getUUID());
			if (!flower && !bonded) return InteractionResult.PASS;

			String bubble;
			if (!bonded) {
				if (!sp.isCreative()) held.shrink(1);
				if (sp.getRandom().nextFloat() < FAIL_START) {
					upset(sp, warden, "Al Warden le incomodó que te acercaras así...");
					return InteractionResult.SUCCESS;
				}
				BondManager.startBond(sp.getUUID(), warden.getUUID());
				bubble = "Hmm... hueles a flores. Puedes quedarte.";
			} else {
				int value = BondManager.getAffection(sp.getUUID(), warden.getUUID());
				bubble = BondMenu.Moods.statusLine(value);
			}

			// Mientras el menú está abierto el Warden se calma (y el menú te protege del daño).
			calm(warden, sp);
			final String finalBubble = bubble;
			sp.openMenu(new SimpleMenuProvider(
					(id, inv, p) -> new BondMenu(id, inv, sp, warden, finalBubble),
					Component.literal("♥ Vínculo con el Warden ♥").withStyle(s -> s.withColor(PINK))));
			return InteractionResult.SUCCESS;
		});

		// Sin daño mientras el menú está abierto, y sin daño de Wardens si eres aliado.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player)) return true;
			if (player.containerMenu instanceof BondMenu) return false;
			if (source.getEntity() instanceof Warden && BondManager.isWardenAlly(player.getUUID())) return false;
			return true;
		});

		// Respaldo: los Wardens cercanos olvidan a los aliados aunque el mixin no aplique.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				boolean menuOpen = player.containerMenu instanceof BondMenu;
				if (!menuOpen && !BondManager.isWardenAlly(player.getUUID())) continue;
				if (!(player.level() instanceof ServerLevel level)) continue;
				for (Warden warden : level.getEntitiesOfClass(Warden.class, player.getBoundingBox().inflate(40))) {
					calm(warden, player);
				}
			}
			if (server.getTickCount() % 6000 == 0) BondManager.save(saveFile(server));
		});
	}

	/** El Warden se incomoda: pierdes afecto y, con poca probabilidad, te ataca. */
	public static void upset(ServerPlayer player, Warden warden, String text) {
		player.closeContainer();
		if (BondManager.hasBond(player.getUUID(), warden.getUUID())) {
			BondManager.addAffection(player.getUUID(), warden.getUUID(), -FAIL_PENALTY, true);
		}
		boolean attacks = player.getRandom().nextFloat() < ATTACK_CHANCE;
		player.sendSystemMessage(Component.literal(attacks ? text + " ¡Se enojó!" : text)
				.withStyle(s -> s.withColor(attacks ? 0xFF5555 : PINK)));
		if (attacks) {
			warden.increaseAngerAt(player, 100, true);
		}
	}

	private static void calm(Warden warden, ServerPlayer player) {
		warden.clearAnger(player);
		if (warden.getTarget() == player) {
			warden.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			warden.setTarget(null);
		}
	}

	private static Path saveFile(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("vinculos.txt");
	}
}
