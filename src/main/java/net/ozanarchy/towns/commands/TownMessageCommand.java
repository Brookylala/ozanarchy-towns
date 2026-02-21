package net.ozanarchy.towns.commands;

import static net.ozanarchy.towns.TownsPlugin.config;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.handlers.DatabaseHandler;
import net.ozanarchy.towns.util.Utils;

public class TownMessageCommand implements CommandExecutor{
    private final TownsPlugin plugin;
    private final DatabaseHandler db;
    private String prefix = Utils.prefix();

    public TownMessageCommand(TownsPlugin plugin, DatabaseHandler db){
        this.plugin = plugin;
        this.db = db;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command arg1, String arg2, String[] args) {
        if(!(sender instanceof Player)){
            Bukkit.getLogger().info("Town messaging is only for players.");
            return true;
        };
        
        Player p = (Player) sender;

        townChat(p, args);

        return true;
    }

    public void townChat(Player p, String[] args){
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(p.getUniqueId());

            if(townId == null){
                Bukkit.getScheduler().runTask(plugin, () ->{
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }
            
            List<UUID> townMemberUUID = db.getTownMembers(townId).stream()
                    .map(DatabaseHandler.TownMember::getUuid)
                    .toList();

            StringBuilder messageBuilder = new StringBuilder();
            for (int i = 0; i < args.length; i++){
                messageBuilder.append(args[i]).append(" ");
            }

            String msg = messageBuilder.toString().trim();
            
            for (UUID uuid : townMemberUUID) {
                Player member = Bukkit.getPlayer(uuid);
                if(member != null && member.isOnline()){
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        member.sendMessage(Utils.getColor(config.getString("townusercolor") + p.getDisplayName() + "&f&l>&7 " + msg));
                    });
                }
            }
        });
    }
    
}
