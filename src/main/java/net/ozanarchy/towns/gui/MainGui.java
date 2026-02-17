package net.ozanarchy.towns.gui;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.util.SkullCreator;
import net.ozanarchy.towns.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static net.ozanarchy.towns.TownsPlugin.guiConfig;

public class MainGui implements Listener {
    private final TownsPlugin plugin;

    public MainGui(TownsPlugin plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player player) {
        String title = Utils.getColor(guiConfig.getString("title", "&8Town Management"));
        int size = guiConfig.getInt("size", 27);
        Inventory inv = Bukkit.createInventory(null, size, title);

        ConfigurationSection items = guiConfig.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection itemSection = items.getConfigurationSection(key);
                if (itemSection == null) continue;

                int slot = itemSection.getInt("slot");
                String materialName = itemSection.getString("material", "PAPER");
                String texture = itemSection.getString("texture");
                String name = Utils.getColor(itemSection.getString("name", " "));
                List<String> lore = itemSection.getStringList("lore");
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(Utils.getColor(line));
                }

                ItemStack item;
                if (materialName.equals("PLAYER_HEAD") && texture != null && !texture.isEmpty()) {
                    item = SkullCreator.itemFromBase64(texture);
                } else {
                    try {
                        Material material = Material.valueOf(materialName);
                        item = new ItemStack(material);
                    } catch (IllegalArgumentException e) {
                        item = new ItemStack(Material.PAPER);
                    }
                }

                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(name);
                    meta.setLore(coloredLore);
                    item.setItemMeta(meta);
                }

                inv.setItem(slot, item);
            }
        }

        applyFiller(inv, guiConfig);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = Utils.getColor(guiConfig.getString("title", "&8Town Management"));
        if (event.getView().getTitle().equals(title)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            ConfigurationSection items = guiConfig.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ConfigurationSection itemSection = items.getConfigurationSection(key);
                    if (itemSection == null) continue;

                    if (event.getSlot() == itemSection.getInt("slot")) {
                        String command = itemSection.getString("command");
                        if (command != null && !command.isEmpty()) {
                            player.closeInventory();
                            player.performCommand(command);
                        }
                        break;
                    }
                }
            }
        }
    }

    private void applyFiller(Inventory inv, ConfigurationSection root) {
        if (root == null) return;
        ConfigurationSection filler = root.getConfigurationSection("filler");
        if (filler == null) return;

        String materialName = filler.getString("material", "GRAY_STAINED_GLASS_PANE");
        String texture = filler.getString("texture");
        String name = Utils.getColor(filler.getString("name", " "));
        List<String> lore = filler.getStringList("lore");
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(Utils.getColor(line));
        }

        ItemStack fillerItem;
        if (materialName.equals("PLAYER_HEAD") && texture != null && !texture.isEmpty()) {
            fillerItem = SkullCreator.itemFromBase64(texture);
        } else {
            try {
                Material material = Material.valueOf(materialName);
                fillerItem = new ItemStack(material);
            } catch (IllegalArgumentException e) {
                fillerItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            }
        }

        ItemMeta meta = fillerItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(coloredLore);
            fillerItem.setItemMeta(meta);
        }

        boolean fill = filler.getBoolean("fill", false);
        List<Integer> slots = filler.getIntegerList("slots");

        if (fill) {
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack existing = inv.getItem(i);
                if (existing == null || existing.getType() == Material.AIR) {
                    inv.setItem(i, fillerItem.clone());
                }
            }
        }

        for (Integer slot : slots) {
            if (slot == null) continue;
            if (slot < 0 || slot >= inv.getSize()) continue;
            inv.setItem(slot, fillerItem.clone());
        }
    }
}
