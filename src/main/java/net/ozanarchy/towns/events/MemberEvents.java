package net.ozanarchy.towns.events;

import net.ozanarchy.ozanarchyEconomy.api.EconomyAPI;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.handlers.ChunkHandler;
import net.ozanarchy.towns.util.ChunkVisuals;
import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.handlers.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.ozanarchy.towns.TownsPlugin.config;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class MemberEvents implements Listener {
    private static final long INVITE_TIMEOUT_SECONDS = 30L;

    private static class TownInvite {
        private final UUID inviterUuid;
        private final int townId;
        private final long expiresAt;
        private final BukkitTask timeoutTask;

        private TownInvite(UUID inviterUuid, int townId, long expiresAt, BukkitTask timeoutTask) {
            this.inviterUuid = inviterUuid;
            this.townId = townId;
            this.expiresAt = expiresAt;
            this.timeoutTask = timeoutTask;
        }
    }

    private final DatabaseHandler db;
    private final TownsPlugin plugin;
    private final EconomyAPI economy;
    private final ChunkHandler chunkCache;
    private final Map<UUID, TownInvite> pendingInvites = new ConcurrentHashMap<>();
    private final String prefix = Utils.prefix();
    private final String noPerm = messagesConfig.getString("messages.nopermission");
    private final String incorrectUsage = messagesConfig.getString("messages.incorrectusage");

    public MemberEvents(DatabaseHandler data, TownsPlugin plugin, EconomyAPI economy, ChunkHandler chunkCache){
        this.db = data;
        this.plugin = plugin;
        this.economy = economy;
        this.chunkCache = chunkCache;
    }

    // ==========================================
    // MEMBER ADD / REMOVE
    // ==========================================

    /**
     * Sends a town invite to a player.
     */
    public void addMember(Player requester, String[] args){
        if(args.length < 2){
            requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteusage", incorrectUsage)));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null){
            requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotfound")));
            return;
        }

        UUID requesterUUID = requester.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
           Integer townId = db.getPlayerTownId(requesterUUID);

           if (townId == null){
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
               });
               return;
           }
           if (!db.isTownAdmin(requesterUUID, townId)) {
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nottownadmin")));
               });
               return;
           }
           if (db.getPlayerTownId(targetUUID) != null){
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playeralreadyintown")));
               });
               return;
           }

           String townName = db.getTownName(townId);
           Bukkit.getScheduler().runTask(plugin, () -> sendInvite(requester, target, townId, townName));
        });
    }

    /**
     * Accepts a pending town invite.
     */
    public void acceptInvite(Player target) {
        UUID targetUUID = target.getUniqueId();
        TownInvite invite = pendingInvites.remove(targetUUID);

        if (invite == null || invite.expiresAt < System.currentTimeMillis()) {
            target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nopendinginvite")));
            return;
        }

        invite.timeoutTask.cancel();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (db.getPlayerTownId(targetUUID) != null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playeralreadyintown"))));
                return;
            }

            Integer inviterTown = db.getPlayerTownId(invite.inviterUuid);
            if (inviterTown == null || inviterTown != invite.townId || !db.isTownAdmin(invite.inviterUuid, invite.townId)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteinvalid"))));
                return;
            }

            boolean success = db.addMember(invite.townId, targetUUID, "MEMBER");
            String townName = db.getTownName(invite.townId);

            if (success){
                db.increaseUpkeep(inviterTown, config.getDouble("towns.addedmemberupkeep"));

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player inviter = Bukkit.getPlayer(invite.inviterUuid);
                        target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteacceptedtarget")
                            .replace("{town}", townName != null ? townName : "Unknown")));
                        if (inviter != null) {
                            inviter.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteacceptedrequester")
                                .replace("{player}", target.getName())));
                        }
                });
            }
        });
    }

    /**
     * Denies a pending town invite.
     */
    public void denyInvite(Player target) {
        UUID targetUUID = target.getUniqueId();
        TownInvite invite = pendingInvites.remove(targetUUID);

        if (invite == null || invite.expiresAt < System.currentTimeMillis()) {
            target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nopendinginvite")));
            return;
        }

        invite.timeoutTask.cancel();

        target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invitedeniedtarget")));
        Player inviter = Bukkit.getPlayer(invite.inviterUuid);
        if (inviter != null) {
            inviter.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invitedeniedrequester")
                    .replace("{player}", target.getName())));
        }
    }

    /**
     * Removes a player from the town (kick).
     */
    public void removeMember(Player requester, String[] args){
        if(args.length <2){
            requester.sendMessage(Utils.getColor(prefix + incorrectUsage));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null){
            requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotfound")));
            return;
        }

        UUID requesterUUID = requester.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
           Integer townId = db.getPlayerTownId(requesterUUID);

           if (townId == null){
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
               });
               return;
           }
           if (!db.isTownAdmin(requesterUUID, townId)) {
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nottownadmin")));
               });
               return;
           }
           Integer targetTown = db.getPlayerTownId(targetUUID);
           if (targetTown == null || !targetTown.equals(townId)){
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotinyourtown")));
               });
               return;
           }
           if(db.isMayor(targetUUID, townId)) {
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.cantremovemayor")));
               });
               return;
           }

           boolean success = db.removeMember(targetUUID, townId);
           Bukkit.getScheduler().runTask(plugin, () -> {
              if(success){
                  requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.removedmember").replace("{player}", target.getName())));
                  target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.kickedfromtown")));
                  return;
              } else {
                  requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.removememberfailed")));
              }
           });
           db.decreaseUpkeep(townId, config.getDouble("towns.refundedmemberupkeep"));
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        UUID targetUUID = e.getPlayer().getUniqueId();
        TownInvite invite = pendingInvites.remove(targetUUID);
        if (invite != null) {
            invite.timeoutTask.cancel();
        }
    }

    /**
     * Allows a player to leave their current town.
     */
    public void leaveTown(Player p){
        UUID uuid = p.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->{
           Integer townId = db.getPlayerTownId(uuid);
            if (townId == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }
            if(db.isMayor(uuid, townId)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.mayorcantleave")));
                });
                return;
            }

            boolean success = db.removeMember(uuid, townId);

            Bukkit.getScheduler().runTask(plugin, () ->{
               if (success) {
                   p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.lefttown")));
               } else {
                   p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.leavefailed")));
                   return;
               }
            });
            db.decreaseUpkeep(townId, config.getDouble("towns.refundedmemberupkeep"));
        });
    }

    // ==========================================
    // PLAYER RANKS
    // ==========================================

    /**
     * Promotes a member to Officer.
     */
    public void promotePlayer(Player requester, String[] args){
        if(args.length <2){
            requester.sendMessage(Utils.getColor(prefix + incorrectUsage));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotfound")));
            return;
        }

        UUID requesterUUID = requester.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
           Integer townId = db.getPlayerTownId(requesterUUID);
            if (townId == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }
            if (!db.isMayor(requesterUUID, townId)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notmayor")));
                });
                return;
            }
            Integer targetTown = db.getPlayerTownId(targetUUID);
            if (targetTown == null || !targetTown.equals(townId)){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotinyourtown")));
                });
                return;
            }
            if(!db.isMemberRank(targetUUID, townId)){
                Bukkit.getScheduler().runTask(plugin, () ->{
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.cantpromote")));
                });
                return;
            }
            boolean success = db.setRole(targetUUID, townId, "OFFICER");
            Bukkit.getScheduler().runTask(plugin, () -> {
                if(success){
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.promotedmember").replace("{player}", target.getName())));
                    target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.promotedtarget")));
                } else {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.promotefailed")));
                }
            });
        });
    }

    /**
     * Demotes an Officer to Member.
     */
    public void demotePlayer(Player requester, String[] args){
        if(args.length <2){
            requester.sendMessage(Utils.getColor(prefix + incorrectUsage));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotfound")));
            return;
        }

        UUID requesterUUID = requester.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(requesterUUID);
            if (townId == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }
            if (!db.isMayor(requesterUUID, townId)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notmayor")));
                });
                return;
            }
            if (requesterUUID.equals(targetUUID)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.cantdemoteself")))
                );
                return;
            }
            Integer targetTown = db.getPlayerTownId(targetUUID);
            if (targetTown == null || !targetTown.equals(townId)){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotinyourtown")));
                });
                return;
            }
            if(db.isMemberRank(targetUUID, townId)){
                Bukkit.getScheduler().runTask(plugin, () ->{
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.cantdemote")));
                });
                return;
            }
            boolean success = db.setRole(targetUUID, townId, "MEMBER");
            Bukkit.getScheduler().runTask(plugin, () -> {
                if(success){
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.demotedmember").replace("{player}", target.getName())));
                    target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.demotedtarget")));
                } else {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.demotefailed")));
                }
            });
        });
    }

    public void transferMayor(Player p, String[] args) {
        if(args.length < 2){
            p.sendMessage(Utils.getColor(prefix + incorrectUsage));
            return;
        }

        Player target = (Player) Bukkit.getPlayer(args[1]);
    
        if (target == null || !target.isOnline()){
            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotfound")));
            return;
        }
        
        UUID targetUUID = target.getUniqueId();
        UUID requesterUUID = p.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int townId = db.getPlayerTownId(requesterUUID);
            int targetTownId = db.getPlayerTownId(targetUUID);
            String townName = db.getTownName(townId);
            if(targetTownId != townId){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotinyourtown")));
                });
                return;
            }
            if(!db.isMayor(requesterUUID, townId)){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notmayor")));
                });
                return;
            }
            if (requesterUUID.equals(targetUUID)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.cantpromote")))
                );
                return;
            }
            Boolean setRole = db.setRole(requesterUUID, townId, "OFFICER");
            Boolean setMayor = db.setMayor(targetUUID, townId);
            if(setMayor && setRole){
                Bukkit.getScheduler().runTask(plugin, () ->{
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.mayortransfered").replace("{town}", townName).replace("{player}", target.getName())));
                    target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.newtownmayor").replace("{town}", townName)));
                });
                return;
            } else {
                Bukkit.getScheduler().runTask(plugin, () ->{
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.databaseerror")));
                });
                return;
            }

        });
    }

    // ==========================================
    // TOWN BANK
    // ==========================================

    /**
     * Deposits money into the town bank.
     */
    public void giveTownMoney(Player p, String[] args){
        if(args.length < 2){
            p.sendMessage(Utils.getColor(prefix + incorrectUsage));
            return;
        }
        double amount = Double.parseDouble(args[1]);
        if(amount < 1){
            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invalidamount")));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(p.getUniqueId());
            if(townId == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }
            economy.remove(p.getUniqueId(), amount, success ->{
                if(success){
                    db.depositTownMoney(townId, amount);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.depositwealth").replace("{amount}", String.valueOf(amount))));
                    });
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notenough")));
                    });
                }
            });
        });
    }

    /**
     * Withdraws money from the town bank (Officers and Mayors only).
     */
    public void withdrawTownMoney(Player p, String[] args){
        if(args.length < 2){
            p.sendMessage(Utils.getColor(prefix + incorrectUsage));
            return;
        }
        double amount = Double.parseDouble(args[1]);
        if(amount < 1){
            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invalidamount")));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(p.getUniqueId());
            if(townId == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }
            boolean isAdmin = db.isTownAdmin(p.getUniqueId(), townId);
            if(isAdmin){
                economy.add(p.getUniqueId(), amount);
                db.withdrawTownMoney(townId, amount);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.withdrawwealth").replace("{amount}", String.valueOf(amount))));
                });
            }
        });
    }

    /**
     * Shows the current balance of the town bank.
     */
    public void townBalance(Player p){
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(p.getUniqueId());
            if (townId == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }
            double bal = db.getTownBalance(townId);
            Bukkit.getScheduler().runTask(plugin, () ->{
               p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.bankbalance").replace("{balance}", String.valueOf(bal))));
            });
        });
    }

    // ==========================================
    // VISUALS
    // ==========================================

    /**
     * Enables a temporary particle visualizer to show chunk boundaries and ownership.
     */
    public void chunkVisualizer(Player p){
        int duration = config.getInt("visualizer.duration");
        int timer = duration * 2;
        String ownParticle = config.getString("visualizer.own");
        String wildParticle = config.getString("visualizer.wild");
        String enemyParticle = config.getString("visualizer.enemy");

        Particle own, wild, enemy;
        try {
            own = Particle.valueOf(ownParticle.toUpperCase());
            wild = Particle.valueOf(wildParticle.toUpperCase());
            enemy = Particle.valueOf(enemyParticle.toUpperCase());

            // Check if any particle requires data but we aren't equipped to handle it beyond DUST
            validateParticle(own);
            validateParticle(wild);
            validateParticle(enemy);

        } catch (IllegalArgumentException e) {
            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invalidparticle")));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer playerTown = db.getPlayerTownId(p.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.visualizerenabled").replace("{duration}", String.valueOf(duration))));
                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (ticks++ >= timer || !p.isOnline()) {
                            cancel();
                            return;
                        }

                        Chunk center = p.getLocation().getChunk();

                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                Chunk c = center.getWorld().getChunkAt(
                                        center.getX() + dx,
                                        center.getZ() + dz
                                );

                                Integer chunkTown = chunkCache.getTownId(c);

                                if (chunkTown == null) {
                                    ChunkVisuals.showChunk(p, c, wild);
                                } else if (chunkTown.equals(playerTown)) {
                                    ChunkVisuals.showChunk(p, c, own);
                                } else {
                                    ChunkVisuals.showChunk(p, c, enemy);
                                }
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 10L);
            });
        });
    }

    /**
     * Validates if a particle can be used in the visualizer.
     */
    private void validateParticle(Particle particle) {
        if (particle == null) return;
        Class<?> dataClass = particle.getDataType();
        if (dataClass != Void.class && dataClass != Particle.DustOptions.class) {
            throw new IllegalArgumentException("Unsupported particle data type: " + dataClass.getName());
        }
    }

    private void sendInvite(Player requester, Player target, int townId, String townName) {
        TownInvite previous = pendingInvites.remove(target.getUniqueId());
        if (previous != null) {
            previous.timeoutTask.cancel();
        }

        long expiresAt = System.currentTimeMillis() + (INVITE_TIMEOUT_SECONDS * 1000L);
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TownInvite current = pendingInvites.get(target.getUniqueId());
            if (current == null || current.expiresAt != expiresAt) {
                return;
            }

            pendingInvites.remove(target.getUniqueId());
            if (target.isOnline()) {
                target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteexpiredtarget")));
            }
            if (requester.isOnline()) {
                requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteexpiredrequester")
                        .replace("{player}", target.getName())));
            }
        }, INVITE_TIMEOUT_SECONDS * 20L);

        pendingInvites.put(target.getUniqueId(), new TownInvite(requester.getUniqueId(), townId, expiresAt, timeoutTask));

        requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invitesent")
                .replace("{player}", target.getName())
                .replace("{seconds}", String.valueOf(INVITE_TIMEOUT_SECONDS))));

        target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invitereceived")
                .replace("{player}", requester.getName())
                .replace("{town}", townName != null ? townName : "Unknown")
                .replace("{seconds}", String.valueOf(INVITE_TIMEOUT_SECONDS))));
        target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteactions")));
    }
}
