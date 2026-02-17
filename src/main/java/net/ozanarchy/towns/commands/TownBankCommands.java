package net.ozanarchy.towns.commands;

import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.events.MemberEvents;
import net.ozanarchy.towns.gui.BankGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class TownBankCommands implements CommandExecutor, TabCompleter {
    private final MemberEvents mEvents;
    private final BankGui gui;
    private String prefix = Utils.prefix();
    private String noPerm = messagesConfig.getString("messages.nopermission");
    private String incorrectUsage = messagesConfig.getString("messages.incorrectusage");

    public TownBankCommands(MemberEvents mEvents, BankGui gui) {
        this.mEvents = mEvents;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        // Bank actions are player-only.
        if(!(sender instanceof Player p)) return true;
        if(!p.hasPermission("oztowns.commands.bank") || !p.hasPermission("oztowns.commands")){
            p.sendMessage(Utils.getColor(prefix + noPerm));
            return true;
        }
        if(args.length < 1){
            // Open bank GUI when no subcommand is provided.
            gui.openGui(p);
            return true;
        }
        // Route bank subcommands.
        switch (args[0].toLowerCase()) {
            case "gui" -> {
                gui.openGui(p);
                return true;
            }
            case "deposit" -> {
                if(!(p.hasPermission("oztowns.commands.bank.deposit"))){
                    p.sendMessage(Utils.getColor(prefix + noPerm));
                    return true;
                }
                mEvents.giveTownMoney(p, args);
                return true;
            }
            case "withdraw" -> {
                if(!(p.hasPermission("oztowns.commands.bank.withdraw"))){
                    p.sendMessage(Utils.getColor(prefix + noPerm));
                    return true;
                }
                mEvents.withdrawTownMoney(p, args);
                return true;
            }
            case "balance", "bal" -> {
                if(!(p.hasPermission("oztowns.commands.bank.balance"))){
                    p.sendMessage(Utils.getColor(prefix + noPerm));
                    return true;
                }
                mEvents.townBalance(p);
                return true;
            }
            case "help", "commands" -> {
                if(!(p.hasPermission("oztowns.commands.bank.help"))){
                    p.sendMessage(Utils.getColor(prefix + noPerm));
                    return true;
                }
                helpCommand(p);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }

        if (args.length == 1) {
            // First-argument suggestions for /townbank.
            List<String> subCommands = Arrays.asList("deposit", "withdraw", "balance", "bal", "help", "commands", "gui");
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    private void helpCommand(Player p){
        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.bankhelpmenu")));
        for (String line : messagesConfig.getStringList("bankhelp")){
            p.sendMessage(Utils.getColor(line));
        }
    }
}
