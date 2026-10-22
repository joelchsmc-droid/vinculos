package vinculos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Guarda el afecto de cada jugador hacia cada mob (se guarda en un archivo dentro del mundo).
 */
public final class BondManager {
	public static final int MAX_AFFECTION = 100;

	private static final Map<UUID, Map<UUID, Integer>> AFFECTION = new HashMap<>();
	/** Jugadores con afecto máximo con algún Warden: los demás Wardens los ignoran. */
	private static final Set<UUID> WARDEN_ALLIES = new HashSet<>();

	private BondManager() {}

	public static boolean hasBond(UUID player, UUID mob) {
		return AFFECTION.getOrDefault(player, Map.of()).containsKey(mob);
	}

	public static int getAffection(UUID player, UUID mob) {
		return AFFECTION.getOrDefault(player, Map.of()).getOrDefault(mob, 0);
	}

	public static void startBond(UUID player, UUID mob) {
		AFFECTION.computeIfAbsent(player, k -> new HashMap<>()).putIfAbsent(mob, 0);
	}

	/** Suma afecto (puede ser negativo) y devuelve el nuevo valor. */
	public static int addAffection(UUID player, UUID mob, int amount, boolean isWarden) {
		Map<UUID, Integer> map = AFFECTION.computeIfAbsent(player, k -> new HashMap<>());
		int value = Math.max(0, Math.min(MAX_AFFECTION, map.getOrDefault(mob, 0) + amount));
		map.put(mob, value);
		if (isWarden && value >= MAX_AFFECTION) {
			WARDEN_ALLIES.add(player);
		}
		return value;
	}

	public static boolean isWardenAlly(UUID player) {
		return WARDEN_ALLIES.contains(player);
	}

	// ---- Guardado en disco (archivo de texto simple) ----

	public static void load(Path file) {
		AFFECTION.clear();
		WARDEN_ALLIES.clear();
		if (!Files.exists(file)) return;
		try {
			for (String line : Files.readAllLines(file)) {
				String[] p = line.trim().split(" ");
				try {
					if (p.length == 4 && p[0].equals("B")) {
						AFFECTION.computeIfAbsent(UUID.fromString(p[1]), k -> new HashMap<>())
								.put(UUID.fromString(p[2]), Integer.parseInt(p[3]));
					} else if (p.length == 2 && p[0].equals("A")) {
						WARDEN_ALLIES.add(UUID.fromString(p[1]));
					}
				} catch (IllegalArgumentException ignored) {
					// línea dañada: se ignora
				}
			}
		} catch (IOException e) {
			System.err.println("[Vinculos] No se pudo leer " + file + ": " + e.getMessage());
		}
	}

	public static void save(Path file) {
		List<String> lines = new ArrayList<>();
		AFFECTION.forEach((player, mobs) ->
				mobs.forEach((mob, value) -> lines.add("B " + player + " " + mob + " " + value)));
		WARDEN_ALLIES.forEach(player -> lines.add("A " + player));
		try {
			Files.write(file, lines);
		} catch (IOException e) {
			System.err.println("[Vinculos] No se pudo guardar " + file + ": " + e.getMessage());
		}
	}
}
