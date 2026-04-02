package dev.erotoro.oreweaver.listener;

import dev.erotoro.oreweaver.OreWeaverPlugin;
import dev.erotoro.oreweaver.config.PlayerDataManager;
import dev.erotoro.oreweaver.config.PluginConfig;
import dev.erotoro.oreweaver.task.OreWeaverTask;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.logging.Level;

public final class OreWeaverListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final OreWeaverPlugin plugin;

    public OreWeaverListener(OreWeaverPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        PluginConfig cfg = plugin.getPluginConfig();
        PlayerDataManager pd = plugin.getPlayerData();

        if (!pd.beginProcessing(player)) {
            return;
        }

        try {
            if (!player.hasPermission("oreweaver.use")) {
                pd.endProcessing(player);
                return;
            }

            GameMode gameMode = player.getGameMode();
            if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) {
                pd.endProcessing(player);
                return;
            }

            if (!cfg.isWorldAllowed(block.getWorld().getName())) {
                pd.endProcessing(player);
                return;
            }

            ItemStack tool = player.getInventory().getItemInMainHand();
            if (tool.getType().isAir()) {
                pd.endProcessing(player);
                return;
            }

            Material blockType = block.getType();
            if (!cfg.canMine(tool.getType(), blockType)) {
                pd.endProcessing(player);
                return;
            }

            switch (cfg.getActivationMode()) {
                case SNEAK:
                    if (!player.isSneaking()) {
                        pd.endProcessing(player);
                        return;
                    }
                    break;
                case TOGGLED:
                    if (pd.isToggleDisabled(player)) {
                        pd.endProcessing(player);
                        return;
                    }
                    break;
                case ALWAYS:
                    break;
            }

            if (!player.hasPermission("oreweaver.bypass.cooldown")) {
                double remaining = pd.getRemainingCooldown(player, cfg.getCooldownTicks());
                if (remaining > 0.0D) {
                    pd.endProcessing(player);
                    String seconds = String.format(Locale.ROOT, "%.1f", remaining);
                    String message = cfg.formatMessage(cfg.getMsgCooldown(), "<seconds>", seconds);
                    player.sendActionBar(MINI.deserialize(message));
                    return;
                }
            }

            if (!player.hasPermission("oreweaver.bypass.hunger")) {
                if (cfg.getHungerPerBlock() > 0.0D && player.getFoodLevel() < cfg.getMinimumFoodLevel()) {
                    pd.endProcessing(player);
                    String message = cfg.formatMessage(cfg.getMsgTooHungry());
                    player.sendActionBar(MINI.deserialize(message));
                    return;
                }
            }

            int maxBlocks = player.hasPermission("oreweaver.bypass.maxblocks")
                ? cfg.getServerMaxBlocks()
                : cfg.getMaxBlocks();

            Material originType = block.getType();
            OreWeaverTask task = new OreWeaverTask(plugin, player, block, originType, tool, maxBlocks);
            task.execute();
        } catch (Exception exception) {
            plugin.getLogger().log(
                Level.SEVERE,
                "[OreWeaver] Unexpected error while handling block break for " + player.getName(),
                exception
            );
            pd.endProcessing(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPlayerData().cleanup(event.getPlayer());
    }
}
