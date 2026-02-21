package net.ozanarchy.towns.events;

import net.ozanarchy.chestlock.ChestLockPlugin;
import net.ozanarchy.chestlock.lock.LockInfo;
import net.ozanarchy.chestlock.lock.LockService;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.handlers.DatabaseHandler;
import net.ozanarchy.towns.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class AdminEvents {
    private final TownsPlugin plugin;
    private final DatabaseHandler db;
    private final String prefix = Utils.adminPrefix();

    public AdminEvents(DatabaseHandler db, TownsPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
    }

    // ==========================================
    // ADMIN UTILITIES
    // ==========================================

    public void reload(CommandSender sender) {
        plugin.reloadAllConfigs();
        sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.reloaded")));
    }

    // ==========================================
    // TOWN ADMIN ACTIONS
    // ==========================================

    public void deleteTown(CommandSender sender, String townName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.townnotfound")));
                return;
            }

            Location oldSpawn = db.getTownSpawn(townId);
            if (oldSpawn != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Block block = oldSpawn.getBlock();
                    unlockChestLock(block);
                    if (block.getType() == Material.LODESTONE) {
                        block.setType(Material.AIR);
                    }
                });
            }

            db.deleteClaim(townId);
            db.deleteMembers(townId);
            db.deleteTownBank(townId);
            db.deleteTown(townId);

            sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.towndeleted").replace("{town}", townName)));
        });
    }

    public void setSpawn(Player player, String townName) {
        Location loc = player.getLocation();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                player.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.townnotfound")));
                return;
            }

            Location oldLoc = db.getTownSpawn(townId);
            if (oldLoc != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Block oldBlock = oldLoc.getBlock();
                    unlockChestLock(oldBlock);
                    if (oldBlock.getType() == Material.LODESTONE) {
                        oldBlock.setType(Material.AIR);
                    }
                });
            }

            double saveX = loc.getBlockX() + 0.5;
            double saveY = loc.getBlockY();
            double saveZ = loc.getBlockZ() + 0.5;

            db.updateTownSpawn(townId, loc.getWorld().getName(), saveX, saveY, saveZ);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Block block = loc.getBlock();
                block.setType(Material.LODESTONE);
                loc.getWorld().save();
                player.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.spawnsuccess").replace("{town}", townName)));
            });
        });
    }

    public void removeSpawn(CommandSender sender, String townName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.townnotfound")));
                return;
            }

            Location oldSpawn = db.getTownSpawn(townId);
            if (oldSpawn != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Block block = oldSpawn.getBlock();
                    unlockChestLock(block);
                    if (block.getType() == Material.LODESTONE) {
                        block.setType(Material.AIR);
                    }
                });
            }

            db.updateTownSpawn(townId, null, 0, 0, 0);
            db.resetTownCreationTime(townId);

            sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.spawnremoved").replace("{town}", townName)));
        });
    }

    public void addMember(CommandSender sender, String townName, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.townnotfound")));
                return;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            if (!target.isOnline() && !target.hasPlayedBefore()) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.playernotfound")));
                return;
            }

            UUID targetId = target.getUniqueId();
            Integer targetTown = db.getPlayerTownId(targetId);
            if (targetTown != null) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.alreadyintown")));
                return;
            }

            boolean success = db.addMember(townId, targetId, "MEMBER");
            if (!success) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.addmemberfailed")));
                return;
            }

            sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.addedmember")
                    .replace("{player}", target.getName() == null ? playerName : target.getName())
                    .replace("{town}", townName)));

            if (target.isOnline()) {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    onlineTarget.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.joinedtown")));
                }
            }
        });
    }

    public void removeMember(CommandSender sender, String townName, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.townnotfound")));
                return;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            if (!target.isOnline() && !target.hasPlayedBefore()) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.playernotfound")));
                return;
            }

            UUID targetId = target.getUniqueId();
            Integer targetTown = db.getPlayerTownId(targetId);
            if (targetTown == null || !targetTown.equals(townId)) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.notintown")));
                return;
            }
            if(db.isMayor(targetId, townId)){
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.cannotremovemayor")));
                return;
            }
            boolean success = db.removeMember(targetId, townId);
            if (!success) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.removememberfailed")));
                return;
            }

            sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.removedmember")
                    .replace("{player}", target.getName() == null ? playerName : target.getName())
                    .replace("{town}", townName)));

            if (target.isOnline()) {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    onlineTarget.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.kickedfromtown")));
                }
            }
        });
    }

    public void setMayor(CommandSender sender, String townName, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.townnotfound")));
                return;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            if (!target.isOnline() && !target.hasPlayedBefore()) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.playernotfound")));
                return;
            }

            UUID targetId = target.getUniqueId();
            Integer targetTown = db.getPlayerTownId(targetId);
            if (targetTown == null || !targetTown.equals(townId)) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.notintown")));
                return;
            }

            boolean success = db.setMayor(targetId, townId);
            if (!success) {
                sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.setmayorfail")));
                return;
            }

            sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.setmayor")
                    .replace("{player}", target.getName() == null ? playerName : target.getName())
                    .replace("{town}", townName)));

            if (target.isOnline()) {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    onlineTarget.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.newmayor")));
                }
            }
        });
    }

    private void unlockChestLock(Block block) {
        if (block == null) return;

        if (!(Bukkit.getPluginManager().getPlugin("OminousChestLock") instanceof ChestLockPlugin chestLockPlugin)) {
            return;
        }

        LockService lockService = chestLockPlugin.getLockService();
        if (lockService == null) return;

        LockInfo lockInfo = lockService.getLockInfo(block);
        if (lockInfo != null) {
            lockService.unlock(block, lockInfo.keyName());
        }
    }

    public void spawnTeleport(Player p, String townName){
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.townnotfound")));
                return;
            }

            Location spawn = db.getTownSpawn(townId);

            if(spawn == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.spawnnotfound")));
                });
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () ->{
                p.teleport(db.getTownSpawn(townId));
                p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.teleported")
                    .replace("{town}", townName)));
            });
        });
    }
}
