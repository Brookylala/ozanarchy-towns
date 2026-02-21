package net.ozanarchy.towns.events;

import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.handlers.ChunkHandler;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.handlers.DatabaseHandler;
import org.bukkit.Material;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.InventoryHolder;

import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class ProtectionListener implements Listener {
    private final DatabaseHandler db;
    private final ChunkHandler chunkCache;

    public ProtectionListener(DatabaseHandler db, ChunkHandler chunkCache) {
        this.db = db;
        this.chunkCache = chunkCache;
    }

    /**
     * Backwards-compatible constructor that may be called with the plugin instance first.
     * The plugin parameter is unused but accepted for compatibility with older calls.
     */
    public ProtectionListener(TownsPlugin plugin, DatabaseHandler db, ChunkHandler chunkCache) {
        this(db, chunkCache);
    }

    /**
     * Prevents players from breaking only the active town-spawn Lodestone.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreakBlock(BlockBreakEvent e){
        if (e.getBlock().getType() == Material.LODESTONE) {
            Integer townId = chunkCache.getTownId(e.getBlock().getChunk());
            if (townId != null) {
                Location townSpawn = db.getTownSpawn(townId);
                if (townSpawn != null && townSpawn.getBlock().equals(e.getBlock())) {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.cannotbreaktownlodestone")));
                    return;
                }
            }
        }
        protection(e.getPlayer(), e.getBlock().getChunk(), e);
    }

    /**
     * Prevents placing blocks that would obstruct the clear sky above a town spawn.
     * Also handles general land protection.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlaceBlock(BlockPlaceEvent e){
        // Check if placing a block above a town spawn
        Block placedBlock = e.getBlock();
        Location loc = placedBlock.getLocation();
        
        // Optimize: only check if the chunk is claimed
        Integer townId = chunkCache.getTownId(placedBlock.getChunk());
        if (townId != null) {
            Location spawnLoc = db.getTownSpawn(townId);
            if (spawnLoc != null && spawnLoc.getWorld().equals(loc.getWorld()) && 
                spawnLoc.getBlockX() == loc.getBlockX() && spawnLoc.getBlockZ() == loc.getBlockZ() &&
                loc.getBlockY() > spawnLoc.getBlockY()) {
                
                if (placedBlock.getType().isOccluding()) {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.spawnskyrestricted")));
                    return;
                }
            }
        }
        
        protection(e.getPlayer(), e.getBlock().getChunk(), e);
    }

    /**
     * Handles block interactions (right/left click) and enforces land protection.
     * Specifically allows interaction with the town Lodestone for non-members (for raiding).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block block = e.getClickedBlock();
            if (block != null) {
                Material type = block.getType();
                // Specifically allow interaction with the town Lodestone for non-members (for raiding).
                if (type == Material.LODESTONE) {
                    // Check if this Lodestone is a town spawn
                    Integer townId = chunkCache.getTownId(block.getChunk());
                    if (townId != null) {
                        Location townSpawn = db.getTownSpawn(townId);
                        if (townSpawn != null && townSpawn.getBlock().equals(block)) {
                            // If it's the town spawn, check if the player is a member of this town
                            Integer playerTownId = db.getPlayerTownId(e.getPlayer().getUniqueId());
                            if (townId.equals(playerTownId)) {
                                e.setCancelled(true);
                                e.getPlayer().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.cannotraidowntown")));
                                return;
                            }
                            return; // Explicitly allow interaction with the town Lodestone for non-members
                        }
                    }
                }
                
                // Allow interacting (opening) containers regardless of town protection
                if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    if (block.getState() instanceof InventoryHolder) {
                        return;
                    }
                }
            }
        }
        if (e.getClickedBlock() != null) {
            protection(e.getPlayer(), e.getClickedBlock().getChunk(), e);
        }
    }

    /**
     * Prevents unauthorized access to containers within claimed chunks.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractContainer(InventoryOpenEvent e){
        if (!(e.getPlayer() instanceof Player p)) return;
        InventoryHolder holder = e.getInventory().getHolder();
        Block block = null;

        if (holder instanceof Container c) {
            block = c.getBlock();
        } else if (holder instanceof DoubleChest dc) {
            block = dc.getLocation().getBlock();
        }
        
        if (block != null) {
            // Allow opening all container inventories regardless of town protection
            if (block.getState() instanceof InventoryHolder) {
                return;
            }
            
            // Lodestone is not a container, but we allow opening any virtual inventory attached to it
            if (block.getType() == Material.LODESTONE) {
                Integer tId = chunkCache.getTownId(block.getChunk());
                if (tId != null) {
                    Location townSpawn = db.getTownSpawn(tId);
                    if (townSpawn != null && townSpawn.getBlock().equals(block)) {
                        return; // Allow opening "containers" on the town Lodestone
                    }
                }
            }
        }
        
        if (block == null) return;

        Chunk chunk = block.getChunk();

        Integer townId = chunkCache.getTownId(chunk);
        if (townId == null) return;

        if (p.hasPermission("oztowns.admin.protectionbypass")) return;

        boolean allowed = db.canBuild(p.getUniqueId(), chunk);

        if (allowed) return;


        e.setCancelled(true);
        p.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.missingcontainerperm")));
    }

    /**
     * Prevents entities (like TNT or Creepers) from exploding blocks in claimed chunks.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(block -> chunkCache.getTownId(block.getChunk()) != null);
    }

    /**
     * Prevents blocks (like TNT) from exploding blocks in claimed chunks.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(block -> chunkCache.getTownId(block.getChunk()) != null);
    }

    /**
     * General land protection logic. Checks if the player is allowed to build/interact.
     */
    private void protection(Player p, Chunk chunk, Cancellable event){
        if (p.hasPermission("oztowns.admin.protectionbypass")) return;

        Integer claimTown = chunkCache.getTownId(chunk);
        if (claimTown == null) return;
        Integer playerTown = db.getPlayerTownId(p.getUniqueId());
        
        // Only allow Town Admins (Mayor/Officers) to build in their own town
        boolean allowed = playerTown != null &&
                            db.isTownAdmin(p.getUniqueId(), playerTown) &&
                            claimTown.equals(playerTown);
        if(!allowed) {
            event.setCancelled(true);
            p.sendMessage(Utils.getColor(Utils.prefix() +
                    messagesConfig.getString("messages.missinginteractperm")));
        }
    }
}
