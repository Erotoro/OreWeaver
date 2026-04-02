package dev.erotoro.oreweaver.listener;

import dev.erotoro.oreweaver.OreWeaverPlugin;
import dev.erotoro.oreweaver.scheduler.SchedulerAdapter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class OreWeaverCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final OreWeaverPlugin plugin;

    public OreWeaverCommand(OreWeaverPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "toggle" -> handleToggle(sender);
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender);
            default -> {
                sendHelp(sender, label);
                yield true;
            }
        };
    }

    private boolean handleToggle(CommandSender sender) {
        if (!sender.hasPermission("oreweaver.command.toggle")) {
            sendMessage(sender, plugin.getPluginConfig().getMsgNoPermission());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sendMessage(sender, plugin.getPluginConfig().getMsgPlayerOnly());
            return true;
        }

        boolean enabled = plugin.getPlayerData().toggle(player);
        String template = enabled
            ? plugin.getPluginConfig().getMsgToggledOn()
            : plugin.getPluginConfig().getMsgToggledOff();
        sendMessage(player, template);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("oreweaver.command.reload")) {
            sendMessage(sender, plugin.getPluginConfig().getMsgNoPermission());
            return true;
        }

        plugin.getPluginConfig().reload();
        sendMessage(sender, plugin.getPluginConfig().getMsgReloadSuccess());
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        if (!sender.hasPermission("oreweaver.command.info")) {
            sendMessage(sender, plugin.getPluginConfig().getMsgNoPermission());
            return true;
        }

        String platform = SchedulerAdapter.isFolia() ? "Folia" : "Paper/Spigot";
        sendMessage(
            sender,
            plugin.getPluginConfig().getMsgInfo(),
            "<version>", plugin.getPluginConfig().getVersion(),
            "<platform>", platform
        );
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        if (sender.hasPermission("oreweaver.command.toggle")) {
            sendMessage(sender, plugin.getPluginConfig().getMsgHelpToggle(), "<label>", label);
        }
        if (sender.hasPermission("oreweaver.command.info")) {
            sendMessage(sender, plugin.getPluginConfig().getMsgHelpInfo(), "<label>", label);
        }
        if (sender.hasPermission("oreweaver.command.reload")) {
            sendMessage(sender, plugin.getPluginConfig().getMsgHelpReload(), "<label>", label);
        }
    }

    private void sendMessage(CommandSender sender, String template, String... pairs) {
        sender.sendMessage(MINI.deserialize(plugin.getPluginConfig().formatMessage(template, pairs)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Arrays.asList("toggle", "info", "reload").stream()
                .filter(option -> option.startsWith(input))
                .filter(option -> sender.hasPermission("oreweaver.command." + option))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
