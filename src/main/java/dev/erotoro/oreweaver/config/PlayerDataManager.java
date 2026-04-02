package dev.erotoro.oreweaver.config;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores per-player runtime state in thread-safe collections so Folia region threads can
 * read and update player flags without external synchronization.
 */
public final class PlayerDataManager {

    private final Set<UUID> toggledOff = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastUsed = new ConcurrentHashMap<>();
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    /**
     * Returns whether the player has OreWeaver manually disabled.
     *
     * <p>Thread-safety: this read is safe because {@code toggledOff} is a concurrent set backed by
     * {@link ConcurrentHashMap}, so concurrent reads and writes do not require explicit locking.
     *
     * @param player player to inspect
     * @return {@code true} if OreWeaver is disabled for this player
     */
    public boolean isToggleDisabled(Player player) {
        return toggledOff.contains(player.getUniqueId());
    }

    /**
     * Toggles OreWeaver state for the player.
     *
     * <p>Thread-safety: the add/remove operations are safe because {@code toggledOff} is a
     * concurrent set backed by {@link ConcurrentHashMap}. The return value reflects the state after
     * this method's own atomic set operation.
     *
     * @param player player whose toggle state should change
     * @return {@code true} if OreWeaver is now enabled, {@code false} if it is now disabled
     */
    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        if (toggledOff.remove(id)) {
            return true;
        }

        toggledOff.add(id);
        return false;
    }

    /**
     * Returns the remaining cooldown in seconds for the player.
     *
     * <p>Thread-safety: this method is safe because {@code lastUsed} is a
     * {@link ConcurrentHashMap}, so reading the timestamp while other threads update it does not
     * corrupt state. The calculation uses the retrieved snapshot value only.
     *
     * @param player player being checked
     * @param cooldownTicks cooldown length in ticks
     * @return remaining cooldown in seconds, or {@code 0.0} when no cooldown is active
     */
    public double getRemainingCooldown(Player player, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            return 0.0;
        }

        Long last = lastUsed.get(player.getUniqueId());
        if (last == null) {
            return 0.0;
        }

        long cooldownMs = cooldownTicks * 50L;
        long elapsedMs = System.currentTimeMillis() - last;
        long remainingMs = cooldownMs - elapsedMs;
        return remainingMs > 0L ? remainingMs / 1000.0D : 0.0D;
    }

    /**
     * Records that the player has just used OreWeaver.
     *
     * <p>Thread-safety: this write is safe because {@code lastUsed} is a
     * {@link ConcurrentHashMap}, which supports concurrent updates without explicit synchronization.
     *
     * @param player player whose last-use timestamp should be updated
     */
    public void recordUse(Player player) {
        lastUsed.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Marks the player as currently being processed by OreWeaver.
     *
     * <p>Thread-safety: this is safe because {@code processing} is a concurrent set backed by
     * {@link ConcurrentHashMap}. {@code add} is atomic for the UUID key, so it works as an
     * anti-recursion guard across concurrent calls.
     *
     * @param player player to mark as processing
     * @return {@code false} if the player was already processing, otherwise {@code true}
     */
    public boolean beginProcessing(Player player) {
        return processing.add(player.getUniqueId());
    }

    /**
     * Clears the player's processing flag after OreWeaver work completes.
     *
     * <p>Thread-safety: this removal is safe because {@code processing} is a concurrent set backed
     * by {@link ConcurrentHashMap}, so concurrent clears and checks remain safe without locks.
     *
     * @param player player whose processing flag should be cleared
     */
    public void endProcessing(Player player) {
        processing.remove(player.getUniqueId());
    }

    /**
     * Removes every cached entry for the player from every internal collection.
     *
     * <p>Thread-safety: each removal is safe because all backing collections are concurrent
     * collections built on {@link ConcurrentHashMap}, so cleanup can run safely alongside reads or
     * updates on other threads.
     *
     * @param player player whose cached state should be removed
     */
    public void cleanup(Player player) {
        UUID id = player.getUniqueId();
        toggledOff.remove(id);
        lastUsed.remove(id);
        processing.remove(id);
    }
}
