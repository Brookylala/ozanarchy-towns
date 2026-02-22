package net.ozanarchy.towns;

import net.ozanarchy.ozanarchyEconomy.api.EconomyAPI;
import net.ozanarchy.towns.commands.TownBankCommands;
import net.ozanarchy.towns.commands.TownMessageCommand;
import net.ozanarchy.towns.commands.TownsCommand;
import net.ozanarchy.towns.commands.AdminCommands;
import net.ozanarchy.towns.events.AdminEvents;
import net.ozanarchy.towns.events.LockPickListener;
import net.ozanarchy.towns.events.MemberEvents;
import net.ozanarchy.towns.events.ProtectionListener;
import net.ozanarchy.towns.events.TownEvents;
import net.ozanarchy.towns.handlers.ChunkHandler;
import net.ozanarchy.towns.handlers.DatabaseHandler;
import net.ozanarchy.towns.handlers.PermissionManager;
import net.ozanarchy.towns.util.TownsPlaceholder;
import net.ozanarchy.towns.gui.MainGui;
import net.ozanarchy.towns.gui.BankGui;
import net.ozanarchy.towns.gui.MembersGui;
import net.ozanarchy.towns.gui.MemberPermissionMenu;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.Map;

public final class TownsPlugin extends JavaPlugin {
    private Connection connection;
    private String host;
    private String database;
    private String username;
    private String password;
    private int port;
    public static FileConfiguration config;
    public static FileConfiguration guiConfig;
    public static FileConfiguration messagesConfig;
    public static FileConfiguration hologramsConfig;
    private EconomyAPI economy;
    private final ChunkHandler chunkCache = new ChunkHandler();
    private PermissionManager permissionManager;

    @Override
    public void onEnable() {
        initializeConfigs();
        economy = Bukkit.getServicesManager().load(EconomyAPI.class);
        if (economy == null) {
            getLogger().severe("Economy API not found! Disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        DatabaseHandler db = new DatabaseHandler(this);
        permissionManager = new PermissionManager(this);
        TownEvents townEvents = new TownEvents(db, permissionManager, this, economy, chunkCache);
        MemberEvents memberEvents = new MemberEvents(db, permissionManager, this, economy, chunkCache);
        AdminEvents adminEvents = new AdminEvents(db, this);

        MainGui mainGui = new MainGui(this);
        BankGui bankGui = new BankGui(this);
        MembersGui membersGui = new MembersGui(this, db, permissionManager);
        registerCommands(db, townEvents, memberEvents, adminEvents, mainGui, bankGui, membersGui);
        registerEvents(db, townEvents, memberEvents, mainGui, bankGui, membersGui);

        setupMySql();
        createTables();

        registerPlaceholders(db);
        scheduleChunkCacheReload();
        scheduleUpkeepHandler(db, townEvents);
        scheduleSpawnReminderAndDeletion(db, townEvents);
    }

    private void initializeConfigs() {
        config = getConfig();
        config.options().copyDefaults(true);
        saveDefaultConfig();
        guiConfig = loadYamlConfig("gui.yml");
        messagesConfig = loadYamlConfig("messages.yml");
        hologramsConfig = loadYamlConfig("holograms.yml");
        applyCacheSettings();
    }

    private void registerCommands(DatabaseHandler db, TownEvents townEvents, MemberEvents memberEvents, AdminEvents adminEvents,
                                  MainGui mainGui, BankGui bankGui, MembersGui membersGui) {
        TownsCommand townsCommand = new TownsCommand(db, townEvents, memberEvents, mainGui, membersGui);
        getCommand("towns").setExecutor(townsCommand);
        getCommand("towns").setTabCompleter(townsCommand);

        TownBankCommands townBankCommands = new TownBankCommands(memberEvents, bankGui);
        getCommand("townbank").setExecutor(townBankCommands);
        getCommand("townbank").setTabCompleter(townBankCommands);

        AdminCommands adminCommands = new AdminCommands(adminEvents);
        getCommand("townadmin").setExecutor(adminCommands);
        getCommand("townadmin").setTabCompleter(adminCommands);

        if (config.getBoolean("townmessages")) {
            getCommand("tm").setExecutor(new TownMessageCommand(this, db));
        }
    }

    private void registerEvents(DatabaseHandler db, TownEvents townEvents, MemberEvents memberEvents,
                                MainGui mainGui, BankGui bankGui, MembersGui membersGui) {
        getServer().getPluginManager().registerEvents(townEvents, this);
        getServer().getPluginManager().registerEvents(memberEvents, this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this, db, chunkCache, permissionManager), this);
        getServer().getPluginManager().registerEvents(new LockPickListener(this, db, economy), this);
        getServer().getPluginManager().registerEvents(mainGui, this);
        getServer().getPluginManager().registerEvents(bankGui, this);
        getServer().getPluginManager().registerEvents(membersGui, this);
        getServer().getPluginManager().registerEvents(new MemberPermissionMenu(this, db, permissionManager, null), this);
    }

    private void registerPlaceholders(DatabaseHandler db) {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TownsPlaceholder(this, db).register();
            getLogger().info("PlaceholderAPI Enabled");
        }
    }

    private void scheduleChunkCacheReload() {
        reloadChunkCache();
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::reloadChunkCache, 300L, 300L);
    }

    private void scheduleUpkeepHandler(DatabaseHandler db, TownEvents townEvents) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try (ResultSet rs = db.getTownsNeedingUpkeep()) {
                while (rs.next()) {
                    int townId = rs.getInt("town_id");
                    double cost = rs.getDouble("upkeep_cost");
                    boolean paid = db.withdrawTownMoney(townId, cost);
                    if (paid) {
                        db.updateLastUpkeep(townId);
                        townEvents.notifyTown(townId, messagesConfig.getString("messages.upkeepsuccess").replace("{cost}", String.valueOf(cost)));
                    } else {
                        townEvents.notifyTown(townId, messagesConfig.getString("messages.upkeepoverdue"));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, 0L, 20 * 60 * 15);
    }

    private void scheduleSpawnReminderAndDeletion(DatabaseHandler db, TownEvents townEvents) {
        long maxAgeMinutes = config.getLong("spawn-reminder.max-age-minutes", 60);
        long intervalMinutes = config.getLong("spawn-reminder.reminder-interval-minutes", 5);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try (ResultSet rs = db.getTownsWithoutSpawn()) {
                while (rs.next()) {
                    int townId = rs.getInt("id");
                    String townName = rs.getString("name");
                    long ageSeconds = db.getTownAgeInSeconds(townId);
                    long ageMinutes = ageSeconds / 60;

                    if (ageMinutes >= maxAgeMinutes) {
                        Bukkit.getScheduler().runTask(this, () -> {
                            townEvents.abandonTown(townId);
                            getLogger().info("Town " + townName + " deleted for not setting spawn within " + maxAgeMinutes + " minutes.");
                        });
                    } else if (ageMinutes > 0 && ageMinutes % intervalMinutes == 0 && ageSeconds % 60 < 20) {
                        String msg = messagesConfig.getString("messages.setspawnreminder")
                                .replace("{minutes}", String.valueOf(maxAgeMinutes - ageMinutes));
                        townEvents.notifyTown(townId, msg);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, 20 * 30L, 20 * 60L);
    }

    public void setupMySql() {
        host = config.getString("mysql.host");
        port = config.getInt("mysql.port");
        username = config.getString("mysql.username");
        password = config.getString("mysql.password");
        database = config.getString("mysql.database");

        try {
            connectMySql();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private synchronized void connectMySql() throws SQLException, ClassNotFoundException {
        if (connection != null && !connection.isClosed()) return;

        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = "jdbc:mysql://" + this.host + ":" + this.port + "/" + this.database
                + "?useSSL=false&allowPublicKeyRetrieval=true&tcpKeepAlive=true&connectTimeout=5000&socketTimeout=10000";
        setConnection(DriverManager.getConnection(url, this.username, this.password));
        getLogger().info("MYSQL Connected Successfully");
    }

    /**
     * Creates the necessary MySQL tables if they don't already exist.
     */
    public void createTables() {
        try (Statement stmt = getConnection().createStatement()) {
            // Towns table: Stores basic town information
            String towns = "CREATE TABLE IF NOT EXISTS towns (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," + // Unique town ID
                    "name VARCHAR(32) UNIQUE," +           // Town name (must be unique)
                    "mayor_uuid VARCHAR(36)," +            // UUID of the town's mayor
                    "world VARCHAR(32)," +                 // World where the town spawn is located
                    "spawn_x DOUBLE," +                    // X coordinate of the town spawn
                    "spawn_y DOUBLE," +                    // Y coordinate of the town spawn
                    "spawn_z DOUBLE," +                    // Z coordinate of the town spawn
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" + // When the town was created
                    ")";
            stmt.executeUpdate(towns);

            // Claims table: Stores chunk ownership information
            String claims = "CREATE TABLE IF NOT EXISTS claims (" +
                    "world VARCHAR(32)," +                 // World of the claimed chunk
                    "chunkx INT," +                        // X coordinate of the chunk
                    "chunkz INT," +                        // Z coordinate of the chunk
                    "town_id INT," +                       // ID of the town that owns this chunk
                    "PRIMARY KEY (world, chunkx, chunkz)," +
                    "FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE" + // Automatically delete claims when a town is deleted
                    ")";
            stmt.executeUpdate(claims);

            // Town Members table: Stores players and their roles within towns
            String members = "CREATE TABLE IF NOT EXISTS town_members (" +
                    "town_id INT NOT NULL," +              // ID of the town the player belongs to
                    "uuid VARCHAR(36) NOT NULL," +         // UUID of the player
                    "role ENUM('MAYOR', 'OFFICER', 'MEMBER') NOT NULL," + // Player's rank/role
                    "PRIMARY KEY (uuid)," +                // Each player can only be in one town (primary key on UUID)
                    "INDEX (town_id)" +                    // Index for faster lookups by town
                    ")";
            stmt.executeUpdate(members);

            // Town Bank table: Stores economic data for each town
            String townBank = "CREATE TABLE IF NOT EXISTS town_bank (" +
                    "town_id INT NOT NULL," +              // ID of the town
                    "balance DOUBLE NOT NULL DEFAULT 0," + // Current bank balance
                    "last_upkeep TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," + // Last time upkeep was paid
                    "upkeep_interval INT NOT NULL DEFAULT 86400," + // Time between upkeep payments (in seconds)
                    "upkeep_cost DOUBLE NOT NULL DEFAULT 0.0," +    // Cost of the upkeep payment
                    "FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE" + // Automatically delete bank when a town is deleted
                    ")";
            stmt.executeUpdate(townBank);

            // Member Permissions table: Stores individual player permissions within towns
            String memberPermissions = "CREATE TABLE IF NOT EXISTS town_member_permissions (" +
                    "town_id INT NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "permission_node VARCHAR(50) NOT NULL," +
                    "value BOOLEAN NOT NULL DEFAULT FALSE," +
                    "PRIMARY KEY (town_id, player_uuid, permission_node)," +
                    "FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE" +
                    ")";
            stmt.executeUpdate(memberPermissions);

            // Database migrations: ensure legacy tables have the required columns
            try {
                stmt.executeUpdate("ALTER TABLE towns ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
            } catch (SQLException ignored) {}

            getLogger().info("Tables checked/created successfully.");
        } catch (SQLException e) {
            getLogger().severe("Error creating tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException ignored) {
                    }
                }
                connection = null;
                connectMySql();
            }
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to establish MySQL connection", e);
        }
        return connection;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    // Chunk Reloader
    public void reloadChunkCache() {
        DatabaseHandler db = new DatabaseHandler(this);
        Map<String, Integer> claims = db.loadAllClaims();
        chunkCache.setAll(claims);
    }

    private FileConfiguration loadYamlConfig(String fileName) {
        File configFile = new File(getDataFolder(), fileName);
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource(fileName, false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        return yaml;
    }

    public void reloadAllConfigs() {
        reloadConfig();
        config = getConfig();
        guiConfig = loadYamlConfig("gui.yml");
        messagesConfig = loadYamlConfig("messages.yml");
        hologramsConfig = loadYamlConfig("holograms.yml");
        applyCacheSettings();
    }

    private void applyCacheSettings() {
        long ttlSeconds = config.getLong("cache.ttl-seconds", 15L);
        DatabaseHandler.setCacheTtlSeconds(ttlSeconds);
        PermissionManager.setCacheTtlSeconds(ttlSeconds);
    }

    @Override
    public void onDisable() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}


