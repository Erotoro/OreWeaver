package dev.erotoro.oreweaver;

import dev.erotoro.oreweaver.config.PlayerDataManager;
import dev.erotoro.oreweaver.config.PluginConfig;
import dev.erotoro.oreweaver.listener.OreWeaverCommand;
import dev.erotoro.oreweaver.listener.OreWeaverListener;
import dev.erotoro.oreweaver.scheduler.SchedulerAdapter;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class OreWeaverPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private PlayerDataManager playerData;
    private SchedulerAdapter scheduler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("lang/lang_en.yml", false);
        saveResource("lang/lang_ru.yml", false);
        saveResource("lang/lang_ua.yml", false);
        this.pluginConfig = new PluginConfig(this);
        this.playerData = new PlayerDataManager();
        this.scheduler = new SchedulerAdapter(this);

        getServer().getPluginManager().registerEvents(new OreWeaverListener(this), this);

        PluginCommand command = getCommand("oreweaver");
        if (command == null) {
            getLogger().severe("Command 'oreweaver' missing from plugin.yml");
        } else {
            OreWeaverCommand handler = new OreWeaverCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        int pluginId = 30533;
        Metrics metrics = new Metrics(this, pluginId);
        metrics.addCustomChart(
            new SimplePie("platform", () -> SchedulerAdapter.isFolia() ? "Folia" : "Paper/Spigot")
        );

        getLogger().info(
            "OreWeaver v" + getPluginMeta().getVersion()
                + (SchedulerAdapter.isFolia() ? " [Folia]" : " [Paper/Spigot]")
        );
    }

    @Override
    public void onDisable() {
        getLogger().info("OreWeaver stopped.");
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public PlayerDataManager getPlayerData() {
        return playerData;
    }

    public SchedulerAdapter getScheduler() {
        return scheduler;
    }
}
