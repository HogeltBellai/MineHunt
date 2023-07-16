package ru.hogeltbellai.minehunt;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import ru.hogeltbellai.minehunt.commands.ReloadCommand;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MineHunt extends JavaPlugin implements Listener {
	
    private Economy economy;
    private FileConfiguration config;
    private File configFile;
    private Map<Material, Integer> cooldowns;
    private Map<Material, Integer> limits;
    private Map<UUID, Map<Material, Integer>> playerBlockCounts;
    private Map<UUID, Map<Material, Long>> playerCooldowns;
    private Map<Material, Double> moneyMap;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().warning("Не удалось инициализировать Vault. Плагин будет отключен.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    	
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        
        config = getConfig();
        cooldowns = new HashMap<>();
        limits = new HashMap<>();
        playerBlockCounts = new HashMap<>();
        playerCooldowns = new HashMap<>();
        moneyMap = new HashMap<>();
        loadCooldownsFromConfig();
        loadLimitsFromConfig();
        loadMoneyFromConfig();
        
        getCommand("minehunt").setExecutor(new ReloadCommand(this));
        
        getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().info("=======================================");
        getLogger().info("=                                     =");
        getLogger().info("=               MineHunt              =");
        getLogger().info("=      С любовью от HogeltBella       =");
        getLogger().info("=                                     =");
        getLogger().info("=======================================");
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        }
        return (economy != null);
    }

    public void loadCooldownsFromConfig() {
        config = getConfig();
        cooldowns.clear();

        for (String materialString : config.getConfigurationSection("cooldowns").getKeys(false)) {
            Material material = Material.getMaterial(materialString);
            int cooldown = config.getInt("cooldowns." + materialString);
            cooldowns.put(material, cooldown);
        }
    }

    public void loadLimitsFromConfig() {
        config = getConfig();
        limits.clear();

        for (String materialString : config.getConfigurationSection("limits").getKeys(false)) {
            Material material = Material.getMaterial(materialString);
            int limit = config.getInt("limits." + materialString);
            limits.put(material, limit);
        }
    }
    
    public void loadMoneyFromConfig() {
        config = getConfig();
        moneyMap.clear();

        ConfigurationSection moneySection = config.getConfigurationSection("money");
        if (moneySection != null) {
            for (String materialString : moneySection.getKeys(false)) {
                Material material = Material.getMaterial(materialString);
                double money = config.getDouble("money." + materialString);
                moneyMap.put(material, money);
            }
        }
    }

    private void setPlayerCooldown(UUID playerId, Material material) {
        long currentTime = System.currentTimeMillis();
        Map<Material, Long> playerCooldownMap = playerCooldowns.getOrDefault(playerId, new HashMap<>());
        playerCooldownMap.put(material, currentTime);
        playerCooldowns.put(playerId, playerCooldownMap);
    }

    private boolean hasPlayerCooldownExpired(UUID playerId, Material material) {
        long currentTime = System.currentTimeMillis();
        Map<Material, Long> playerCooldownMap = playerCooldowns.getOrDefault(playerId, new HashMap<>());
        if (!playerCooldownMap.containsKey(material))
            return true;
        long lastCooldownTime = playerCooldownMap.get(material);
        int cooldown = cooldowns.get(material);
        return currentTime - lastCooldownTime >= cooldown * 1000L;
    }

    private void increasePlayerBlockCount(UUID playerId, Material material) {
        Map<Material, Integer> playerBlockCountMap = playerBlockCounts.getOrDefault(playerId, new HashMap<>());
        int blockCount = playerBlockCountMap.getOrDefault(material, 0);
        playerBlockCountMap.put(material, blockCount + 1);
        playerBlockCounts.put(playerId, playerBlockCountMap);

        int currentBlockCount = getPlayerBlockCount(playerId, material);
        int limit = limits.getOrDefault(material, -1);
        if (limit != -1 && currentBlockCount >= limit) {
            setPlayerCooldown(playerId, material);
            playerBlockCounts.get(playerId).put(material, 0);
        }
    }

    private int getPlayerBlockCount(UUID playerId, Material material) {
        Map<Material, Integer> playerBlockCountMap = playerBlockCounts.getOrDefault(playerId, new HashMap<>());
        return playerBlockCountMap.getOrDefault(material, 0);
    }
    
    private void initializePlayerBlockCounts(UUID playerId) {
        if (!playerBlockCounts.containsKey(playerId)) {
            playerBlockCounts.put(playerId, new HashMap<>());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Material material = event.getBlock().getType();
        ItemStack item = player.getInventory().getItemInMainHand();
        double money = moneyMap.getOrDefault(material, 0.0);
        
        if (item.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }
        
        initializePlayerBlockCounts(playerId);

        if (!hasPlayerCooldownExpired(playerId, material)) {
            long currentTime = System.currentTimeMillis();
            long lastCooldownTime = playerCooldowns.get(playerId).get(material);
            int cooldown = cooldowns.get(material);
            long remainingTime = (lastCooldownTime + (cooldown * 1000L)) - currentTime;
            int secondsRemaining = (int) Math.ceil(remainingTime / 1000.0);
            
            String cooldownMessage = config.getString("messages.cooldown");
            String cooldownActionbar = config.getString("actionbar.cooldown");
            if(config.getBoolean("messages.cooldown-message-allow")) {
                cooldownMessage = cooldownMessage.replace("{time}", String.valueOf(secondsRemaining));
                cooldownMessage = ChatColor.translateAlternateColorCodes('&', cooldownMessage);
                cooldownMessage = cooldownMessage.replace("{material}", material.name());
            	player.sendMessage(cooldownMessage);
            }
            if(config.getBoolean("actionbar.cooldown-actionbar-allow")) {
            	cooldownActionbar = cooldownActionbar.replace("{time}", String.valueOf(secondsRemaining));
            	cooldownActionbar = ChatColor.translateAlternateColorCodes('&', cooldownActionbar);
            	cooldownActionbar = cooldownActionbar.replace("{material}", material.name());
            	player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(cooldownActionbar));
            }
        } else {
            int limit = limits.getOrDefault(material, -1);
            if (limit != -1) {
                int blockCount = getPlayerBlockCount(playerId, material);
                if (blockCount >= limit) {
                    setPlayerCooldown(playerId, material);
                    playerBlockCounts.get(playerId).put(material, 0);
                    economy.depositPlayer(player, money);

                    String moneyMessage = config.getString("messages.break");
                    moneyMessage = moneyMessage.replace("{money}", String.valueOf(money));
                    moneyMessage = ChatColor.translateAlternateColorCodes('&', moneyMessage);
                    moneyMessage = moneyMessage.replace("{material}", material.name());
                    player.sendMessage(moneyMessage);
                } else {
                    increasePlayerBlockCount(playerId, material);
                    economy.depositPlayer(player, money);
                    String moneyMessage = config.getString("messages.break");
                    moneyMessage = moneyMessage.replace("{money}", String.valueOf(money));
                    moneyMessage = ChatColor.translateAlternateColorCodes('&', moneyMessage);
                    moneyMessage = moneyMessage.replace("{material}", material.name());
                    player.sendMessage(moneyMessage);
                }
            }
        }
    }
}