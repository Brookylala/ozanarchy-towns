package net.ozanarchy.towns.handlers;

import net.ozanarchy.towns.TownsPlugin;
import org.bukkit.Chunk;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseHandler {
    private final TownsPlugin plugin;

    public DatabaseHandler(TownsPlugin plugin) {
        this.plugin = plugin;
    }

    public static class TownMember {
        private final UUID uuid;
        private final String role;

        public TownMember(UUID uuid, String role) {
            this.uuid = uuid;
            this.role = role;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getRole() {
            return role;
        }
    }

    // ==========================================
    // CHUNK CLAIMS
    // ==========================================

    /**
     * Checks if a specific chunk is claimed by any town.
     */
    public boolean getChunkClaimed(Chunk chunk){
        String sql = """
            SELECT 1 FROM claims
            WHERE world=? AND chunkx=? AND chunkz=?
            LIMIT 1
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, chunk.getWorld().getName());
            stmt.setInt(2, chunk.getX());
            stmt.setInt(3, chunk.getZ());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Saves a new claim for a town.
     */
    public void saveClaim(Chunk chunk, int townID){
        String sql = """
            INSERT INTO claims (world, chunkx, chunkz, town_id)
            VALUES (?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, chunk.getWorld().getName());
            stmt.setInt(2, chunk.getX());
            stmt.setInt(3, chunk.getZ());
            stmt.setInt(4, townID);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the town ID associated with a specific chunk.
     */
    public Integer getChunkTownId(Chunk chunk) {
        String sql = """
            SELECT town_id FROM claims
            WHERE world=? AND chunkx=? AND chunkz=?
            LIMIT 1
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, chunk.getWorld().getName());
            stmt.setInt(2, chunk.getX());
            stmt.setInt(3, chunk.getZ());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("town_id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Removes a claim for a specific chunk and town.
     */
    public boolean unClaimChunk(Chunk chunk, int townId){
        String sql = """
                DELETE FROM claims
                WHERE world=? AND chunkx=? AND chunkz=? AND town_id=?
                LIMIT 1
                """;
        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, chunk.getWorld().getName());
            stmt.setInt(2, chunk.getX());
            stmt.setInt(3, chunk.getZ());
            stmt.setInt(4, townId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Deletes all claims associated with a town.
     */
    public void deleteClaim(int townId){
        String sql = "DELETE FROM claims WHERE town_id=?";

        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setInt(1, townId);
            stmt.executeUpdate();
        } catch (SQLException e){
            e.printStackTrace();
        }
    }

    /**
     * Checks if a town has any claimed chunks.
     */
    public boolean hasClaims(int townId) {
        String sql = "SELECT 1 FROM claims WHERE town_id = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Checks if a chunk is adjacent to any chunks already claimed by the town.
     */
    public boolean isAdjacentClaim(int townId, Chunk chunk) {
        String sql = """
            SELECT 1 FROM claims
            WHERE town_id = ? AND world = ? AND (
                (chunkx = ? AND ABS(chunkz - ?) = 1) OR
                (chunkz = ? AND ABS(chunkx - ?) = 1)
            )
            LIMIT 1
        """;
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.setString(2, chunk.getWorld().getName());
            stmt.setInt(3, chunk.getX());
            stmt.setInt(4, chunk.getZ());
            stmt.setInt(5, chunk.getZ());
            stmt.setInt(6, chunk.getX());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Loads all claims from the database into a map (used for caching).
     */
    public Map<String, Integer> loadAllClaims(){
        Map<String, Integer> claims = new ConcurrentHashMap<>();
        String sql = "SELECT world, chunkx, chunkz, town_id FROM claims";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            ResultSet rs = stmt.executeQuery();
            while (rs.next()){
                String world = rs.getString("world");
                int x = rs.getInt("chunkx");
                int z = rs.getInt("chunkz");
                int townId = rs.getInt("town_id");

                claims.put(world + ":" + x + ":" + z, townId);
            }
        }catch (SQLException e){
            e.printStackTrace();
        }
        return claims;
    }

    // ==========================================
    // TOWN MANAGEMENT
    // ==========================================

    /**
     * Creates a new town in the database.
     */
    public int createTown(String name, UUID mayor, String world, double x, double y, double z) throws SQLException {
        String sql = "INSERT INTO towns (name, mayor_uuid, world, spawn_x, spawn_y, spawn_z, created_at) VALUES (?, ?, ?, ?, ?, ?, NOW())";

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, mayor.toString());
            stmt.setString(3, world);
            stmt.setDouble(4, x);
            stmt.setDouble(5, y);
            stmt.setDouble(6, z);
            stmt.executeUpdate();

            try(ResultSet rs = stmt.getGeneratedKeys()) {
                if(rs.next()){
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create town");
    }

    /**
     * Deletes a town from the database.
     */
    public void deleteTown(int townId){
        String sql = "DELETE FROM towns WHERE id=?";

        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if a town with the given name exists.
     */
    public boolean townExists(String name){
        String sql = "SELECT 1 FROM towns WHERE name = ? LIMIT 1";

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()){
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Gets the name of a town by its ID.
     */
    public String getTownName(int townId) {
        String sql = """
            SELECT name
            FROM towns
            WHERE id = ?
            LIMIT 1
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Gets the town ID by name (case-insensitive).
     */
    public Integer getTownIdByName(String name) {
        String sql = "SELECT id FROM towns WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Gets a list of towns that do not have a spawn point set.
     */
    public ResultSet getTownsWithoutSpawn() throws SQLException {
        String sql = "SELECT id, name FROM towns WHERE world IS NULL OR world = '' OR world = 'null'";
        return plugin.getConnection().prepareStatement(sql).executeQuery();
    }

    /**
     * Gets the age of a town in seconds since its creation.
     */
    public long getTownAgeInSeconds(int townId) {
        String sql = "SELECT TIMESTAMPDIFF(SECOND, created_at, NOW()) as age FROM towns WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("age");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Updates the spawn location for a town.
     */
    public void updateTownSpawn(int townId, String world, double x, double y, double z) {
        String sql = "UPDATE towns SET world = ?, spawn_x = ?, spawn_y = ?, spawn_z = ? WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setDouble(2, x);
            stmt.setDouble(3, y);
            stmt.setDouble(4, z);
            stmt.setInt(5, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Resets the town creation time to now (used for the spawn setting grace period).
     */
    public void resetTownCreationTime(int townId) {
        String sql = "UPDATE towns SET created_at = NOW() WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the spawn location of a town.
     */
    public org.bukkit.Location getTownSpawn(int townId) {
        String sql = "SELECT world, spawn_x, spawn_y, spawn_z FROM towns WHERE id = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String worldName = rs.getString("world");
                    if (worldName == null) return null;
                    org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                    if (world == null) return null;
                    return new org.bukkit.Location(world, rs.getDouble("spawn_x"), rs.getDouble("spawn_y"), rs.getDouble("spawn_z"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Renames the town.
     */
    public void renameTown(int townId, String name){
        String sql = "UPDATE towns SET name = ? WHERE id = ?";
        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setString(1, name);
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch(SQLException e){
            e.printStackTrace();
        }
    }

    // ==========================================
    // MEMBER MANAGEMENT
    // ==========================================

    /**
     * Adds a player as a member of a town.
     */
    public boolean addMember(int townId, UUID uuid, String role){
        String sql = """
                INSERT INTO town_members (town_id, uuid, role)
                VALUES (?, ?, ?)
                """;

        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.setString(2, uuid.toString());
            stmt.setString(3, role);
            return stmt.executeUpdate() > 0;
        }catch (SQLException e){
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Gets the town ID for a specific player.
     */
    public Integer getPlayerTownId(UUID uuid) {
        String sql = """
            SELECT town_id
            FROM town_members
            WHERE uuid = ?
            LIMIT 1
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("town_id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Checks if a player is a member of a town.
     */
    public boolean isMember(UUID uuid, int townId) {
        String sql = """
            SELECT 1 FROM town_members
            WHERE uuid = ? AND town_id = ?
            LIMIT 1
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, townId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Sets the role of a player in a town.
     */
    public boolean setRole(UUID uuid, int townId, String role){
        String sql = """
            UPDATE town_members
            SET role=?
            WHERE uuid=? AND town_id=?
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setString(1, role);
            stmt.setString(2, uuid.toString());
            stmt.setInt(3, townId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Checks if a player has the 'MEMBER' rank in a town.
     */
    public boolean isMemberRank(UUID uuid, int townId){
        String sql = """
            SELECT 1 FROM town_members
            WHERE uuid=? AND town_id=? AND role='MEMBER'
            LIMIT 1
        """;

        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, townId);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Removes a player from a town.
     */
    public boolean removeMember(UUID uuid, int townId){
        String sql = "DELETE FROM town_members WHERE uuid=? AND town_id=?";

        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, townId);
            return stmt.executeUpdate() > 0;
        } catch(SQLException e){
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Gets a list of members for a town ordered by role.
     */
    public java.util.List<TownMember> getTownMembers(int townId) {
        java.util.List<TownMember> members = new java.util.ArrayList<>();
        String sql = """
            SELECT uuid, role
            FROM town_members
            WHERE town_id = ?
            ORDER BY FIELD(role, 'MAYOR', 'OFFICER', 'MEMBER'), uuid
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String uuidStr = rs.getString("uuid");
                    String role = rs.getString("role");
                    try {
                        members.add(new TownMember(UUID.fromString(uuidStr), role));
                    } catch (IllegalArgumentException ignored) {
                        // Skip malformed UUIDs
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return members;
    }

    /**
     * Checks if a player is the mayor of a town.
     */
    public boolean isMayor(UUID uuid, int townId) {
        String sql = """
            SELECT 1 FROM town_members
            WHERE uuid=? AND town_id=? AND role='MAYOR'
            LIMIT 1
        """;

        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, townId);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** 
     * Sets a new mayor for a town.
     */
    public boolean setMayor(UUID uuid, int townId) {
        String sql = """
            UPDATE town_members
            SET role='MAYOR'
            WHERE uuid=? AND town_id=?
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, townId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Checks if a player is a town administrator (Mayor or Officer).
     */
    public boolean isTownAdmin(UUID uuid, int townId){
        String sql = """
                SELECT 1 FROM town_members
                WHERE uuid=? AND town_id=? AND role IN ('MAYOR','OFFICER')
                LIMIT 1
                """;
        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, townId);

            return stmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Deletes all members associated with a town.
     */
    public void deleteMembers(int townId){
        String sql = "DELETE FROM town_members WHERE town_id=?";

        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setInt(1, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if a player has permission to build/interact in a specific chunk.
     */
    public boolean canBuild(UUID uuid, Chunk chunk){
        String sql = """
            SELECT 1
            FROM claims c
            JOIN town_members m ON c.town_id = m.town_id
            WHERE c.world=? AND c.chunkx=? AND c.chunkz=?
            AND m.uuid=?
            LIMIT 1
        """;
        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, chunk.getWorld().getName());
            stmt.setInt(2, chunk.getX());
            stmt.setInt(3, chunk.getZ());
            stmt.setString(4, uuid.toString());

            return stmt.executeQuery().next();
        } catch (SQLException e){
            e.printStackTrace();
        }

        return false;
    }

    // ==========================================
    // BANKING AND UPKEEP
    // ==========================================

    /**
     * Creates a new bank account for a town.
     */
    public void createTownBank(int townId){
        String sql = "INSERT INTO town_bank (town_id) VALUES (?)";

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, townId);
            stmt.executeUpdate();
        } catch (SQLException e){
            e.printStackTrace();
        }
    }

    /**
     * Deletes a town's bank account.
     */
    public void deleteTownBank(int townId){
        String sql = "DELETE FROM town_bank WHERE town_id=?";

        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setInt(1, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Transfers all money from one town bank to another.
     */
    public void transferBankBalance(int fromTownId, int toTownId) {
        double balance = getTownBalance(fromTownId);
        if (balance > 0) {
            depositTownMoney(toTownId, balance);
            withdrawTownMoney(fromTownId, balance);
        }
    }

    /**
     * Deposits money into a town's bank account.
     */
    public boolean depositTownMoney(int townId, double amount) {
        String sql = "UPDATE town_bank SET balance = balance + ? WHERE town_id=?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, townId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Withdraws money from a town's bank account (checks for sufficient funds).
     */
    public boolean withdrawTownMoney(int townId, double amount){
        String sql = """
                UPDATE town_bank
                SET balance = balance - ?
                WHERE town_id=? AND balance >=?
                """;
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setDouble(1, amount);
            stmt.setInt(2, townId);
            stmt.setDouble(3, amount);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Gets the current balance of a town's bank.
     */
    public double getTownBalance(int townId){
        String sql = "SELECT balance FROM town_bank WHERE town_id=? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("balance");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Increases the daily upkeep cost for a town.
     */
    public void increaseUpkeep(int townId, double amount){
        String sql = "UPDATE town_bank SET upkeep_cost = upkeep_cost + ? WHERE town_id=?";
        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Decreases the daily upkeep cost for a town.
     */
    public void decreaseUpkeep(int townId, double amount){
        String sql = "UPDATE town_bank SET upkeep_cost = upkeep_cost - ? WHERE town_id=?";
        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Updates the timestamp of the last upkeep payment.
     */
    public void updateLastUpkeep(int townId) {
        String sql = "UPDATE town_bank SET last_upkeep = NOW() WHERE town_id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the current daily upkeep cost for a town.
     */
    public double getTownUpkeep(int townId) {
        String sql = "SELECT upkeep_cost FROM town_bank WHERE town_id=? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("upkeep_cost");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Gets a list of towns that are due for an upkeep payment.
     */
    public ResultSet getTownsNeedingUpkeep() throws SQLException {
        String sql = """
        SELECT tb.town_id, tb.balance, tb.upkeep_cost, tb.last_upkeep
        FROM town_bank tb
        WHERE UNIX_TIMESTAMP(NOW()) - UNIX_TIMESTAMP(tb.last_upkeep) >= tb.upkeep_interval
    """;
        return plugin.getConnection().prepareStatement(sql).executeQuery();
    }
}
