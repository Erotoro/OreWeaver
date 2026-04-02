package dev.erotoro.oreweaver.task;

import dev.erotoro.oreweaver.OreWeaverPlugin;
import dev.erotoro.oreweaver.config.PluginConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class OreWeaverTask {

    private final OreWeaverPlugin plugin;
    private final Player player;
    private final Block originBlock;
    private final ItemStack tool;
    private final Set<Material> aliasGroup;
    private final int maxBlocks;

    public OreWeaverTask(
        OreWeaverPlugin plugin,
        Player player,
        Block originBlock,
        Material originType,
        ItemStack tool,
        int maxBlocks
    ) {
        this.plugin = plugin;
        this.player = player;
        this.originBlock = originBlock;
        this.tool = tool.clone();
        this.maxBlocks = maxBlocks;
        this.aliasGroup = plugin.getPluginConfig().getAliasGroup(originType);
    }

    public void execute() {
        List<Block> toBreak = collectBlocks();

        if (toBreak.isEmpty()) {
            plugin.getPlayerData().endProcessing(player);
            return;
        }

        PluginConfig cfg = plugin.getPluginConfig();
        AtomicInteger brokenBlocks = new AtomicInteger();
        AtomicInteger nextIndex = new AtomicInteger();
        AtomicBoolean finished = new AtomicBoolean();
        processNextBlock(toBreak, cfg, nextIndex, brokenBlocks, finished);
    }

    /**
     * BFS is preferable here because it expands outward from the originally mined block in rings.
     * That means when {@code maxBlocks} cuts the search short, we keep the closest connected
     * blocks first instead of following one deep branch the way DFS would.
     */
    private List<Block> collectBlocks() {
        final int MAX_SEARCH_RADIUS = 16;

        List<Block> result = new ArrayList<>();
        Set<Long> visited = new HashSet<Long>();
        Deque<Block> queue = new ArrayDeque<>();

        int originX = originBlock.getX();
        int originY = originBlock.getY();
        int originZ = originBlock.getZ();

        visited.add(encodeLocation(originX, originY, originZ));

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }

                    int nx = originX + dx;
                    int ny = originY + dy;
                    int nz = originZ + dz;

                    if (Math.abs(nx - originX) > MAX_SEARCH_RADIUS
                        || Math.abs(ny - originY) > MAX_SEARCH_RADIUS
                        || Math.abs(nz - originZ) > MAX_SEARCH_RADIUS) {
                        continue;
                    }

                    if (ny < originBlock.getWorld().getMinHeight()
                        || ny > originBlock.getWorld().getMaxHeight()) {
                        continue;
                    }

                    long encoded = encodeLocation(nx, ny, nz);
                    if (!visited.add(encoded)) {
                        continue;
                    }

                    Block neighbor = originBlock.getRelative(dx, dy, dz);
                    if (aliasGroup.contains(neighbor.getType())) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        while (!queue.isEmpty() && result.size() < maxBlocks) {
            Block current = queue.poll();
            result.add(current);

            int currentX = current.getX();
            int currentY = current.getY();
            int currentZ = current.getZ();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }

                        int nx = currentX + dx;
                        int ny = currentY + dy;
                        int nz = currentZ + dz;

                        if (Math.abs(nx - originX) > MAX_SEARCH_RADIUS
                            || Math.abs(ny - originY) > MAX_SEARCH_RADIUS
                            || Math.abs(nz - originZ) > MAX_SEARCH_RADIUS) {
                            continue;
                        }

                        if (ny < current.getWorld().getMinHeight()
                            || ny > current.getWorld().getMaxHeight()) {
                            continue;
                        }

                        long encoded = encodeLocation(nx, ny, nz);
                        if (!visited.add(encoded)) {
                            continue;
                        }

                        Block neighbor = current.getRelative(dx, dy, dz);
                        if (aliasGroup.contains(neighbor.getType())) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }

        return result;
    }

    private static long encodeLocation(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private void processNextBlock(
        List<Block> toBreak,
        PluginConfig cfg,
        AtomicInteger nextIndex,
        AtomicInteger brokenBlocks,
        AtomicBoolean finished
    ) {
        plugin.getScheduler().runForEntity(player, () -> {
            if (!player.isOnline()) {
                finishExecution(cfg, brokenBlocks.get(), finished);
                return;
            }

            int index = nextIndex.getAndIncrement();
            if (index >= toBreak.size()) {
                finishExecution(cfg, brokenBlocks.get(), finished);
                return;
            }

            ItemStack currentTool = player.getInventory().getItemInMainHand();
            if (currentTool.getType() != tool.getType()) {
                finishExecution(cfg, brokenBlocks.get(), finished);
                return;
            }

            ItemStack breakTool = currentTool.clone();
            Block block = toBreak.get(index);

            plugin.getScheduler().runAtLocation(block.getLocation(), () -> {
                boolean broken = breakBlock(block, breakTool);
                plugin.getScheduler().runForEntity(player, () -> {
                    if (!player.isOnline()) {
                        finishExecution(cfg, brokenBlocks.get(), finished);
                        return;
                    }

                    if (broken) {
                        brokenBlocks.incrementAndGet();
                        if (cfg.isUseDurability()) {
                            ItemStack liveTool = player.getInventory().getItemInMainHand();
                            if (liveTool.getType() != tool.getType()) {
                                finishExecution(cfg, brokenBlocks.get(), finished);
                                return;
                            }
                            applyDurability(liveTool);
                        }
                    }

                    processNextBlock(toBreak, cfg, nextIndex, brokenBlocks, finished);
                });
            });
        });
    }

    private void finishExecution(PluginConfig cfg, int brokenCount, AtomicBoolean finished) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }

        plugin.getScheduler().runForEntity(player, () -> {
            try {
                if (player.isOnline()
                    && brokenCount > 0
                    && cfg.getHungerPerBlock() > 0.0D
                    && !player.hasPermission("oreweaver.bypass.hunger")) {
                    double exhaustion = cfg.getHungerPerBlock() * brokenCount;
                    player.setExhaustion((float) (player.getExhaustion() + exhaustion));
                }

                if (brokenCount > 0) {
                    plugin.getPlayerData().recordUse(player);
                }
            } finally {
                plugin.getPlayerData().endProcessing(player);
            }
        });
    }

    private boolean breakBlock(Block block, ItemStack breakTool) {
        if (!aliasGroup.contains(block.getType())) {
            return false;
        }

        return block.breakNaturally(breakTool);
    }

    private void applyDurability(ItemStack item) {
        if (item.getType().getMaxDurability() == 0) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }

        Enchantment unbreaking = null;
        NamespacedKey unbreakingKey = NamespacedKey.minecraft("unbreaking");
        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            if (enchantment != null && enchantment.getKey().equals(unbreakingKey)) {
                unbreaking = enchantment;
                break;
            }
        }
        int unbreakingLevel = unbreaking == null ? 0 : item.getEnchantmentLevel(unbreaking);
        if (unbreakingLevel > 0
            && Math.random() < unbreakingLevel / (unbreakingLevel + 1.0D)) {
            return;
        }

        int currentDamage = damageable.getDamage();
        int maxDurability = item.getType().getMaxDurability();
        int newDamage = currentDamage + 1;

        if (newDamage >= maxDurability) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return;
        }

        damageable.setDamage(newDamage);
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
    }
}
