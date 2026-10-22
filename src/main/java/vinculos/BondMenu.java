package vinculos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * El menú rosita: un inventario de 3 filas donde cada objeto es un botón.
 * Fila de arriba: globo de diálogo. Fila del medio: acciones. Fila de abajo: barra de afecto.
 */
public class BondMenu extends ChestMenu {
	private static final int PINK = 0xFF69B4;
	private static final int PINK_LIGHT = 0xFFB6D9;
	private static final int GRAY = 0xAAAAAA;

	private static final int SLOT_BUBBLE = 4;
	private static final int SLOT_GIFT = 10;
	private static final int SLOT_PET = 11;
	private static final int SLOT_MOB = 13;
	private static final int SLOT_STATUS = 15;
	private static final int SLOT_CLOSE = 16;
	private static final int BAR_START = 18;

	private static final Map<UUID, Long> LAST_PET = new HashMap<>();

	private final Container box;
	private final Warden warden;
	private final ServerPlayer owner;
	private String bubble;

	public BondMenu(int containerId, Inventory inventory, ServerPlayer owner, Warden warden, String bubble) {
		this(containerId, inventory, new SimpleContainer(27), owner, warden, bubble);
	}

	private BondMenu(int containerId, Inventory inventory, Container box, ServerPlayer owner, Warden warden, String bubble) {
		super(MenuType.GENERIC_9x3, containerId, inventory, box, 3);
		this.box = box;
		this.owner = owner;
		this.warden = warden;
		this.bubble = bubble;
		refresh();
	}

	public Warden getWarden() {
		return warden;
	}

	// ---------- Clicks ----------

	@Override
	public void clicked(int slot, int button, ClickType type, Player player) {
		// Todo está bloqueado: nadie puede sacar ni mover objetos del menú.
		if (!(player instanceof ServerPlayer sp)) return;

		if (slot == SLOT_CLOSE) {
			sp.closeContainer();
			return;
		}
		if (slot == SLOT_GIFT) {
			if (gift(sp)) return; // el menú se cerró (se enojó)
		} else if (slot == SLOT_PET) {
			if (pet(sp)) return;
		} else if (slot == SLOT_STATUS) {
			bubble = Moods.statusLine(affection());
		} else {
			return;
		}
		refresh();
		broadcastChanges();
	}

	@Override
	public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return warden.isAlive() && player.distanceToSqr(warden) < 100.0;
	}

	// ---------- Acciones ----------

	/** Devuelve true si el menú se cerró porque el Warden se incomodó. */
	private boolean gift(ServerPlayer sp) {
		Inventory inv = sp.getInventory();
		int found = -1;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(ItemTags.FLOWERS)) {
				found = i;
				break;
			}
		}
		if (found < 0) {
			bubble = "Necesitas una flor para regalarme...";
			return false;
		}
		ItemStack flower = inv.getItem(found);
		boolean favorite = net.minecraft.core.registries.BuiltInRegistries.ITEM
				.getKey(flower.getItem()).getPath().contains("eyeblossom");
		if (!sp.isCreative()) flower.shrink(1);

		if (sp.getRandom().nextFloat() < VinculosMod.FAIL_GIFT) {
			VinculosMod.upset(sp, warden, "Al Warden no le gustó ese regalo...");
			return true;
		}
		raise(sp, favorite ? 12 : 6, favorite ? "¡Esa flor me encanta!" : null);
		return false;
	}

	private boolean pet(ServerPlayer sp) {
		long now = sp.level().getGameTime();
		if (now - LAST_PET.getOrDefault(sp.getUUID(), -100L) < 20) {
			return false; // muy rápido: se ignora
		}
		LAST_PET.put(sp.getUUID(), now);

		if (sp.getRandom().nextFloat() < VinculosMod.FAIL_PET) {
			VinculosMod.upset(sp, warden, "Al Warden no le gustó que lo tocaras...");
			return true;
		}
		raise(sp, 3, null);
		return false;
	}

	private void raise(ServerPlayer sp, int amount, String specialLine) {
		int before = affection();
		int after = BondManager.addAffection(sp.getUUID(), warden.getUUID(), amount, true);
		if (sp.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HEART, warden.getX(), warden.getY() + 2.4, warden.getZ(),
					5, 0.6, 0.4, 0.6, 0.0);
		}
		if (before < BondManager.MAX_AFFECTION && after >= BondManager.MAX_AFFECTION) {
			bubble = "Eres especial para mí. Los otros Wardens ya no te harán daño.";
			sp.sendSystemMessage(Component.literal("♥ ¡Afecto máximo! Los otros Wardens ya no te ven como una amenaza.")
					.withStyle(s -> s.withColor(PINK)));
		} else {
			bubble = specialLine != null ? specialLine : Moods.line(after, sp.getRandom().nextInt(100));
		}
	}

	private int affection() {
		return BondManager.getAffection(owner.getUUID(), warden.getUUID());
	}

	// ---------- Dibujo del menú ----------

	private void refresh() {
		int value = affection();
		ItemStack pane = named(Items.PINK_STAINED_GLASS_PANE, " ", PINK_LIGHT);

		for (int i = 0; i < 27; i++) box.setItem(i, pane.copy());

		box.setItem(SLOT_BUBBLE, named(Items.PAPER, "“" + bubble + "”", 0xFFFFFF,
				"Estado: " + Moods.mood(value)));
		box.setItem(SLOT_GIFT, named(Items.POPPY, "Regalar una flor", PINK,
				"Usa 1 flor de tu inventario.", "Las flores de ojo le gustan más."));
		box.setItem(SLOT_PET, named(Items.FEATHER, "Acariciar", PINK,
				"Sube un poco el afecto."));
		box.setItem(SLOT_MOB, named(Items.SCULK_CATALYST, "Warden", PINK,
				"Estado: " + Moods.mood(value), "Afecto: " + value + "/" + BondManager.MAX_AFFECTION));
		box.setItem(SLOT_STATUS, named(Items.BOOK, "Ver estado", PINK,
				"Pregúntale cómo se siente."));
		box.setItem(SLOT_CLOSE, named(Items.BARRIER, "Cerrar", GRAY));

		int filled = Math.round(value / (float) BondManager.MAX_AFFECTION * 9);
		for (int i = 0; i < 9; i++) {
			boolean on = i < filled;
			box.setItem(BAR_START + i, on
					? named(Items.PINK_DYE, "♥  Afecto " + value + "/" + BondManager.MAX_AFFECTION, PINK)
					: named(Items.GRAY_DYE, "♡  Afecto " + value + "/" + BondManager.MAX_AFFECTION, GRAY));
		}
	}

	private static ItemStack named(Item item, String name, int color, String... lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal(name).withStyle(s -> s.withColor(color).withItalic(false)));
		if (lore.length > 0) {
			stack.set(DataComponents.LORE, new ItemLore(
					java.util.Arrays.stream(lore)
							.<Component>map(l -> Component.literal(l).withStyle(s -> s.withColor(GRAY).withItalic(false)))
							.toList()));
		}
		return stack;
	}

	/** Frases del globo de diálogo según el afecto. */
	static final class Moods {
		static String mood(int v) {
			if (v >= 100) return "Te adora ♥";
			if (v >= 80) return "Enamorado";
			if (v >= 60) return "Cariñoso";
			if (v >= 40) return "Tranquilo";
			if (v >= 20) return "Curioso";
			return "Desconfiado";
		}

		static String statusLine(int v) {
			return "Me siento " + mood(v).toLowerCase() + ". (" + v + "/" + BondManager.MAX_AFFECTION + ")";
		}

		private static final List<String> LOW = List.of("...", "Hmm.", "No me mires tanto.", "¿Qué quieres?");
		private static final List<String> MID = List.of("Eso estuvo bien.", "Puedes quedarte cerca.", "Me gusta tu olor... a flores.", "Hmm... gracias.");
		private static final List<String> HIGH = List.of("Me haces sentir tranquilo.", "Quédate conmigo.", "Contigo no necesito oír nada más.", "Eres mi persona favorita.");

		static String line(int v, int roll) {
			List<String> pool = v >= 60 ? HIGH : v >= 30 ? MID : LOW;
			return pool.get(roll % pool.size());
		}
	}
}
