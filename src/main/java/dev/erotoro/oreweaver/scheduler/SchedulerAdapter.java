package dev.erotoro.oreweaver.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Small scheduler abstraction for running work on Folia and on classic Bukkit schedulers
 * without depending on external libraries.
 */
public final class SchedulerAdapter {

    private static final boolean FOLIA;

    static {
        boolean foliaDetected;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            foliaDetected = true;
        } catch (ClassNotFoundException ignored) {
            foliaDetected = false;
        }
        FOLIA = foliaDetected;
    }

    private final JavaPlugin plugin;

    /**
     * Creates a scheduler adapter for the owning plugin.
     *
     * <p>Thread-safety: construction is safe from any thread because this constructor only stores
     * the plugin reference and does not access thread-confined world state or schedule work.
     *
     * @param plugin owning plugin instance
     */
    public SchedulerAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Schedules work on the thread that is safe for the supplied location.
     *
     * <p>Thread-safety: on Folia, the runnable is dispatched through the region scheduler for the
     * given location, so location-confined world access inside {@code task} is safe for that
     * region. On Paper/Spigot, the runnable is dispatched onto the main server thread via the
     * Bukkit scheduler.
     *
     * @param location location whose owning thread should execute the task
     * @param task work to execute
     */
    public void runAtLocation(Location location, Runnable task) {
        if (FOLIA) {
            plugin.getServer().getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    /**
     * Schedules asynchronous work that must not touch thread-confined Bukkit world state unless it
     * is rescheduled back onto a safe server thread first.
     *
     * <p>Thread-safety: on Folia, the runnable is executed by the async scheduler. On
     * Paper/Spigot, it is executed by Bukkit's asynchronous scheduler. In both cases the caller is
     * responsible for ensuring the runnable itself is thread-safe.
     *
     * @param task asynchronous work to execute
     */
    public void runAsync(Runnable task) {
        if (FOLIA) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    /**
     * Schedules work on the thread that is safe for the supplied entity.
     *
     * <p>Thread-safety: on Folia, the runnable is dispatched through the entity scheduler so entity
     * state such as inventory, exhaustion, sounds, and online checks can be accessed safely inside
     * {@code task}. On Paper/Spigot, the runnable is dispatched onto the main server thread.
     *
     * @param entity entity whose owning thread should execute the task
     * @param task work to execute
     */
    public void runForEntity(Entity entity, Runnable task) {
        if (FOLIA) {
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), () -> { });
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    /**
     * Returns whether the current runtime is Folia.
     *
     * <p>Thread-safety: this method is thread-safe because it returns a cached immutable value that
     * is computed once during class initialization.
     *
     * @return {@code true} when running on Folia, otherwise {@code false}
     */
    public static boolean isFolia() {
        return FOLIA;
    }
}
