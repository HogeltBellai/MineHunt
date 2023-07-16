package ru.hogeltbellai.minehunt.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;
import ru.hogeltbellai.minehunt.MineHunt;

public class ReloadCommand implements CommandExecutor {
	
    private final MineHunt plugin;

    public ReloadCommand(MineHunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player) sender;
        if (!player.hasPermission("minehunt.reload")) {
            player.sendMessage("У вас нет разрешения на перезагрузку плагина.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.loadCooldownsFromConfig();
            plugin.loadLimitsFromConfig();
            plugin.loadMoneyFromConfig();
            player.sendMessage(ChatColor.GREEN + "Конфигурация плагина успешно перезагружена.");
        } else {
            player.sendMessage("Плагин разработан им HogeltBellai");
            player.sendMessage("С любовью :з");
        }

        return true;
    }
}
