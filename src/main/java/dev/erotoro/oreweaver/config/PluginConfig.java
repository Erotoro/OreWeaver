package dev.erotoro.oreweaver.config;

import dev.erotoro.oreweaver.OreWeaverPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads and validates plugin configuration.
 *
 * <p>The config is normalized once during reload so runtime checks stay O(1).
 */
public final class PluginConfig {

    public enum ActivationMode { SNEAK, ALWAYS, TOGGLED }

    private static final ActivationMode DEFAULT_ACTIVATION_MODE = ActivationMode.SNEAK;
    private static final int DEFAULT_MAX_BLOCKS = 64;
    private static final int DEFAULT_SERVER_MAX_BLOCKS = 256;
    private static final int DEFAULT_COOLDOWN_TICKS = 0;
    private static final boolean DEFAULT_USE_DURABILITY = true;
    private static final double DEFAULT_HUNGER_PER_BLOCK = 0.025D;
    private static final int DEFAULT_MINIMUM_FOOD_LEVEL = 6;
    private static final boolean DEFAULT_DEBUG = false;
    private static final String DEFAULT_LANGUAGE = "en";

    private static final String DEFAULT_MSG_PREFIX = "<gray>[<green>VM</green>]</gray> ";
    private static final String DEFAULT_MSG_TOGGLED_ON = "<prefix><green>OreWeaver enabled.";
    private static final String DEFAULT_MSG_TOGGLED_OFF = "<prefix><red>OreWeaver disabled.";
    private static final String DEFAULT_MSG_NO_PERMISSION = "<prefix><red>No permission.";
    private static final String DEFAULT_MSG_PLAYER_ONLY = "<prefix><red>This subcommand can only be used by players.";
    private static final String DEFAULT_MSG_COOLDOWN = "<prefix><yellow>Please wait <seconds>s.";
    private static final String DEFAULT_MSG_TOO_HUNGRY = "<prefix><yellow>You are too hungry for OreWeaver.";
    private static final String DEFAULT_MSG_RELOAD_SUCCESS = "<prefix><green>Config reloaded.";
    private static final String DEFAULT_MSG_INFO = "<prefix><gray>OreWeaver <green>v<version></green> | <yellow><platform></yellow>";
    private static final String DEFAULT_MSG_HELP_TOGGLE = "<gray>/<label> toggle</gray> - toggle OreWeaver";
    private static final String DEFAULT_MSG_HELP_INFO = "<gray>/<label> info</gray> - show plugin info";
    private static final String DEFAULT_MSG_HELP_RELOAD = "<gray>/<label> reload</gray> - reload config";

    private final OreWeaverPlugin plugin;

    private ActivationMode activationMode;
    private int maxBlocks;
    private int serverMaxBlocks;
    private int cooldownTicks;
    private boolean useDurability;
    private double hungerPerBlock;
    private int minimumFoodLevel;
    private boolean debug;

    private Set<String> disabledWorlds;
    private Set<String> enabledWorlds;

    private Map<Material, Set<Material>> toolGroups;
    private Map<Material, Set<Material>> blockToAliasGroup;

    private String language;
    private String msgPrefix;
    private String msgToggledOn;
    private String msgToggledOff;
    private String msgNoPermission;
    private String msgPlayerOnly;
    private String msgCooldown;
    private String msgTooHungry;
    private String msgReloadSuccess;
    private String msgInfo;
    private String msgHelpToggle;
    private String msgHelpInfo;
    private String msgHelpReload;

    public PluginConfig(OreWeaverPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reloads config from disk and rebuilds all runtime lookup structures.
     */
    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        activationMode = readActivationMode(cfg);
        maxBlocks = clamp(cfg.getInt("max-blocks", DEFAULT_MAX_BLOCKS), 1, 1024, "max-blocks");
        serverMaxBlocks = clamp(
            cfg.getInt("server-max-blocks", DEFAULT_SERVER_MAX_BLOCKS),
            maxBlocks,
            4096,
            "server-max-blocks"
        );
        cooldownTicks = clamp(
            cfg.getInt("cooldown-ticks", DEFAULT_COOLDOWN_TICKS),
            0,
            20 * 60,
            "cooldown-ticks"
        );
        useDurability = cfg.getBoolean("use-durability", DEFAULT_USE_DURABILITY);
        hungerPerBlock = clampDouble(
            cfg.getDouble("hunger-per-block", DEFAULT_HUNGER_PER_BLOCK),
            0.0D,
            100.0D,
            "hunger-per-block"
        );
        minimumFoodLevel = clamp(
            cfg.getInt("minimum-food-level", DEFAULT_MINIMUM_FOOD_LEVEL),
            0,
            20,
            "minimum-food-level"
        );
        debug = cfg.getBoolean("debug", DEFAULT_DEBUG);

        disabledWorlds = Collections.unmodifiableSet(new HashSet<>(cfg.getStringList("disabled-worlds")));
        enabledWorlds = Collections.unmodifiableSet(new HashSet<>(cfg.getStringList("enabled-worlds")));

        Map<String, Set<Material>> aliases = buildAliases(cfg);
        blockToAliasGroup = buildAliasIndex(aliases);
        toolGroups = buildToolGroups(cfg, aliases);

        language = readLanguage(cfg);
        loadMessages();

        if (debug) {
            plugin.getLogger().info(
                "[DEBUG] Config loaded: " + toolGroups.size() + " tool groups, "
                    + blockToAliasGroup.size() + " alias mappings, language=" + language
            );
        }
    }

    private String readLanguage(FileConfiguration cfg) {
        String rawValue = cfg.getString("language", DEFAULT_LANGUAGE);
        String normalized = rawValue == null ? DEFAULT_LANGUAGE : rawValue.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("en") && !normalized.equals("ru") && !normalized.equals("ua")) {
            warn("Unknown language '" + rawValue + "', using " + DEFAULT_LANGUAGE);
            return DEFAULT_LANGUAGE;
        }
        return normalized;
    }

    private ActivationMode readActivationMode(FileConfiguration cfg) {
        String rawValue = cfg.getString("activation-mode");
        if (rawValue == null) {
            warn("Missing value for activation-mode, using " + DEFAULT_ACTIVATION_MODE);
            return DEFAULT_ACTIVATION_MODE;
        }

        try {
            return ActivationMode.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            warn("Unknown activation-mode '" + rawValue + "', using " + DEFAULT_ACTIVATION_MODE);
            return DEFAULT_ACTIVATION_MODE;
        }
    }

    private Map<String, Set<Material>> buildAliases(FileConfiguration cfg) {
        ConfigurationSection aliasSection = cfg.getConfigurationSection("block-aliases");
        if (aliasSection == null) {
            warn("Missing config section 'block-aliases', using empty defaults");
            return Collections.emptyMap();
        }

        Map<String, Set<Material>> aliases = new HashMap<>();
        for (String aliasName : aliasSection.getKeys(false)) {
            List<String> materialNames = aliasSection.getStringList(aliasName);
            Set<Material> materials = new LinkedHashSet<>();

            for (String materialName : materialNames) {
                Material material = Material.matchMaterial(materialName);
                if (material == null) {
                    warn("Unknown material '" + materialName + "' in block-aliases." + aliasName + ", skipping");
                    continue;
                }
                materials.add(material);
            }

            if (!materials.isEmpty()) {
                aliases.put(aliasName.toLowerCase(Locale.ROOT), Collections.unmodifiableSet(new LinkedHashSet<>(materials)));
            }
        }

        return Collections.unmodifiableMap(new HashMap<>(aliases));
    }

    private Map<Material, Set<Material>> buildAliasIndex(Map<String, Set<Material>> aliases) {
        Map<Material, Set<Material>> aliasIndex = new HashMap<>();

        for (Set<Material> aliasGroup : aliases.values()) {
            Set<Material> immutableGroup = Collections.unmodifiableSet(new LinkedHashSet<>(aliasGroup));
            for (Material material : immutableGroup) {
                aliasIndex.put(material, immutableGroup);
            }
        }

        return Collections.unmodifiableMap(new HashMap<>(aliasIndex));
    }

    private Map<Material, Set<Material>> buildToolGroups(
        FileConfiguration cfg,
        Map<String, Set<Material>> aliases
    ) {
        ConfigurationSection toolSection = cfg.getConfigurationSection("tool-groups");
        if (toolSection == null) {
            warn("Missing config section 'tool-groups', using empty defaults");
            return Collections.emptyMap();
        }

        Map<Material, Set<Material>> builtToolGroups = new HashMap<>();

        for (String toolName : toolSection.getKeys(false)) {
            Material tool = Material.matchMaterial(toolName);
            if (tool == null) {
                warn("Unknown material '" + toolName + "' in tool-groups, skipping");
                continue;
            }

            Set<Material> allowedBlocks = new LinkedHashSet<>();
            for (String reference : toolSection.getStringList(toolName)) {
                Set<Material> aliasGroup = aliases.get(reference.toLowerCase(Locale.ROOT));
                if (aliasGroup != null) {
                    allowedBlocks.addAll(aliasGroup);
                    continue;
                }

                Material block = Material.matchMaterial(reference);
                if (block == null) {
                    warn("Unknown material or alias '" + reference + "' in tool-groups." + toolName + ", skipping");
                    continue;
                }
                allowedBlocks.add(block);
            }

            if (!allowedBlocks.isEmpty()) {
                builtToolGroups.put(tool, Collections.unmodifiableSet(new LinkedHashSet<>(allowedBlocks)));
            }
        }

        return Collections.unmodifiableMap(new HashMap<>(builtToolGroups));
    }

    private void loadMessages() {
        ConfigurationSection messages = loadLanguageSection(language);
        if (messages == null) {
            warn("Missing language file section 'messages' for '" + language + "', using hardcoded defaults");
            setDefaultMessages();
            return;
        }

        msgPrefix = messages.getString("prefix", DEFAULT_MSG_PREFIX);
        msgToggledOn = messages.getString("toggled-on", DEFAULT_MSG_TOGGLED_ON);
        msgToggledOff = messages.getString("toggled-off", DEFAULT_MSG_TOGGLED_OFF);
        msgNoPermission = messages.getString("no-permission", DEFAULT_MSG_NO_PERMISSION);
        msgPlayerOnly = messages.getString("player-only", DEFAULT_MSG_PLAYER_ONLY);
        msgCooldown = messages.getString("cooldown", DEFAULT_MSG_COOLDOWN);
        msgTooHungry = messages.getString("too-hungry", DEFAULT_MSG_TOO_HUNGRY);
        msgReloadSuccess = messages.getString("reload-success", DEFAULT_MSG_RELOAD_SUCCESS);
        msgInfo = messages.getString("info", DEFAULT_MSG_INFO);
        msgHelpToggle = messages.getString("help-toggle", DEFAULT_MSG_HELP_TOGGLE);
        msgHelpInfo = messages.getString("help-info", DEFAULT_MSG_HELP_INFO);
        msgHelpReload = messages.getString("help-reload", DEFAULT_MSG_HELP_RELOAD);
    }

    private ConfigurationSection loadLanguageSection(String code) {
        String resourceName = "lang/lang_" + code + ".yml";
        File langFile = new File(plugin.getDataFolder(), resourceName);
        try {
            YamlConfiguration langConfig;

            if (langFile.isFile()) {
                try (InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(langFile),
                    StandardCharsets.UTF_8
                )) {
                    langConfig = YamlConfiguration.loadConfiguration(reader);
                }
            } else {
                try (InputStream stream = plugin.getResource(resourceName)) {
                    if (stream == null) {
                        warn("Missing bundled language file '" + resourceName + "'");
                        return null;
                    }
                    langConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)
                    );
                }
            }

            ConfigurationSection messages = langConfig.getConfigurationSection("messages");
            if (messages == null) {
                warn("Missing config section 'messages' in '" + resourceName + "'");
            }
            return messages;
        } catch (Exception exception) {
            warn("Failed to load language file '" + resourceName + "', using defaults");
            return null;
        }
    }

    private void setDefaultMessages() {
        msgPrefix = DEFAULT_MSG_PREFIX;
        msgToggledOn = DEFAULT_MSG_TOGGLED_ON;
        msgToggledOff = DEFAULT_MSG_TOGGLED_OFF;
        msgNoPermission = DEFAULT_MSG_NO_PERMISSION;
        msgPlayerOnly = DEFAULT_MSG_PLAYER_ONLY;
        msgCooldown = DEFAULT_MSG_COOLDOWN;
        msgTooHungry = DEFAULT_MSG_TOO_HUNGRY;
        msgReloadSuccess = DEFAULT_MSG_RELOAD_SUCCESS;
        msgInfo = DEFAULT_MSG_INFO;
        msgHelpToggle = DEFAULT_MSG_HELP_TOGGLE;
        msgHelpInfo = DEFAULT_MSG_HELP_INFO;
        msgHelpReload = DEFAULT_MSG_HELP_RELOAD;
    }

    private int clamp(int value, int min, int max, String fieldName) {
        if (value < min) {
            warn("Field '" + fieldName + "' out of range (" + value + "), clamped to " + min);
            return min;
        }
        if (value > max) {
            warn("Field '" + fieldName + "' out of range (" + value + "), clamped to " + max);
            return max;
        }
        return value;
    }

    private double clampDouble(double value, double min, double max, String fieldName) {
        if (value < min) {
            warn("Field '" + fieldName + "' out of range (" + value + "), clamped to " + min);
            return min;
        }
        if (value > max) {
            warn("Field '" + fieldName + "' out of range (" + value + "), clamped to " + max);
            return max;
        }
        return value;
    }

    private void warn(String message) {
        plugin.getLogger().warning("[Config] " + message);
    }

    public boolean canMine(Material tool, Material block) {
        Set<Material> allowedBlocks = toolGroups.get(tool);
        return allowedBlocks != null && allowedBlocks.contains(block);
    }

    public Set<Material> getAliasGroup(Material block) {
        Set<Material> aliasGroup = blockToAliasGroup.get(block);
        return aliasGroup != null ? aliasGroup : Collections.singleton(block);
    }

    public boolean isWorldAllowed(String worldName) {
        if (disabledWorlds.contains(worldName)) {
            return false;
        }
        if (!enabledWorlds.isEmpty()) {
            return enabledWorlds.contains(worldName);
        }
        return true;
    }

    public String formatMessage(String template, String... pairs) {
        String formatted = template.replace("<prefix>", msgPrefix);
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            formatted = formatted.replace(pairs[index], pairs[index + 1]);
        }
        return formatted;
    }

    public ActivationMode getActivationMode() { return activationMode; }
    public int getMaxBlocks() { return maxBlocks; }
    public int getServerMaxBlocks() { return serverMaxBlocks; }
    public int getCooldownTicks() { return cooldownTicks; }
    public boolean isUseDurability() { return useDurability; }
    public double getHungerPerBlock() { return hungerPerBlock; }
    public int getMinimumFoodLevel() { return minimumFoodLevel; }
    public boolean isDebug() { return debug; }
    public String getLanguage() { return language; }
    public String getMsgToggledOn() { return msgToggledOn; }
    public String getMsgToggledOff() { return msgToggledOff; }
    public String getMsgNoPermission() { return msgNoPermission; }
    public String getMsgPlayerOnly() { return msgPlayerOnly; }
    public String getMsgCooldown() { return msgCooldown; }
    public String getMsgTooHungry() { return msgTooHungry; }
    public String getMsgReloadSuccess() { return msgReloadSuccess; }
    public String getMsgInfo() { return msgInfo; }
    public String getMsgHelpToggle() { return msgHelpToggle; }
    public String getMsgHelpInfo() { return msgHelpInfo; }
    public String getMsgHelpReload() { return msgHelpReload; }
    public String getVersion() { return plugin.getPluginMeta().getVersion(); }
}
