package dao;

import databaseConnect.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * DAO for managing game history and scores
 */
public class GameHistoryDAO {
    
    /**
     * Save PvP game result to database
     * @param username player name
     * @param goldEarned gold earned
     * @param kills number of monsters killed
     * @param pointsEarned points added to leaderboard
     * @return true if successful
     */
    public boolean savePvpGameResult(String username, int goldEarned, int kills, int pointsEarned) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;
        
        try {
            // Create table if not exists
            createGameHistoryTableIfNotExists(conn);
            
            String query = "INSERT INTO game_history (username, game_mode, score, kills, points_earned, played_at) " +
                          "VALUES (?, 'pvp', ?, ?, ?, NOW())";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setInt(2, goldEarned);
            stmt.setInt(3, kills);
            stmt.setInt(4, pointsEarned);
            
            int rows = stmt.executeUpdate();
            stmt.close();
            
            // Update PvP stats
            updatePvpStats(conn, username, goldEarned, kills);
            
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }
    
    /**
     * Save Maze game result to database
     * @param username player name
     * @param score score
     * @param coinsCollected coins collected
     * @param won whether player won
     * @param pointsEarned points added to leaderboard
     * @return true if successful
     */
    public boolean saveMazeGameResult(String username, int score, int coinsCollected, boolean won, int pointsEarned) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;
        
        try {
            // Create table if not exists
            createGameHistoryTableIfNotExists(conn);
            
            String query = "INSERT INTO game_history (username, game_mode, score, coins_collected, won, points_earned, played_at) " +
                          "VALUES (?, 'maze', ?, ?, ?, ?, NOW())";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setInt(2, score);
            stmt.setInt(3, coinsCollected);
            stmt.setBoolean(4, won);
            stmt.setInt(5, pointsEarned);
            
            int rows = stmt.executeUpdate();
            stmt.close();
            
            // Update Maze stats
            updateMazeStats(conn, username, score, coinsCollected, won);
            
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    /**
     * Save Monster Hunt game result to database
     * @param username player name
     * @param score score obtained
     * @param pointsEarned points added to leaderboard
     * @return true if successful
     */
    public boolean saveHuntGameResult(String username, int score, int pointsEarned) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;
        
        try {
            // Create table if not exists
            createGameHistoryTableIfNotExists(conn);
            
            String query = "INSERT INTO game_history (username, game_mode, score, coins_collected, points_earned, played_at) " +
                          "VALUES (?, 'hunt', ?, ?, ?, NOW())";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setInt(2, score);
            stmt.setInt(3, score); // In hunt, score = coins
            stmt.setInt(4, pointsEarned);
            
            int rows = stmt.executeUpdate();
            stmt.close();
            
            // For now, we don't have specific hunt stats table, but we count it towards total coins
            // We could add updateHuntStats later if needed
            
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }
    
    /**
     * Create game_history table if not exists
     */
    private void createGameHistoryTableIfNotExists(Connection conn) throws SQLException {
        String createTableQuery = 
            "CREATE TABLE IF NOT EXISTS game_history (" +
            "  id INT AUTO_INCREMENT PRIMARY KEY," +
            "  username VARCHAR(50) NOT NULL," +
            "  game_mode VARCHAR(20) NOT NULL," +
            "  score INT DEFAULT 0," +
            "  kills INT DEFAULT 0," +
            "  coins_collected INT DEFAULT 0," +
            "  won BOOLEAN DEFAULT FALSE," +
            "  points_earned INT DEFAULT 0," +
            "  played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "  INDEX idx_username (username)," +
            "  INDEX idx_game_mode (game_mode)" +
            ")";
        PreparedStatement stmt = conn.prepareStatement(createTableQuery);
        stmt.executeUpdate();
        stmt.close();
        
        // Create player_stats table if not exists
        String createStatsQuery = 
            "CREATE TABLE IF NOT EXISTS player_stats (" +
            "  username VARCHAR(50) PRIMARY KEY," +
            "  total_pvp_games INT DEFAULT 0," +
            "  total_pvp_gold INT DEFAULT 0," +
            "  total_pvp_kills INT DEFAULT 0," +
            "  highest_pvp_gold INT DEFAULT 0," +
            "  total_maze_games INT DEFAULT 0," +
            "  total_maze_wins INT DEFAULT 0," +
            "  total_maze_score INT DEFAULT 0," +
            "  highest_maze_score INT DEFAULT 0," +
            "  total_coins_collected INT DEFAULT 0," +
            "  last_played TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ")";
        stmt = conn.prepareStatement(createStatsQuery);
        stmt.executeUpdate();
        stmt.close();
    }
    
    /**
     * Update PvP stats for player
     */
    private void updatePvpStats(Connection conn, String username, int goldEarned, int kills) throws SQLException {
        // Kiểm tra xem đã có record chưa
        String checkQuery = "SELECT * FROM player_stats WHERE username = ?";
        PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
        checkStmt.setString(1, username);
        ResultSet rs = checkStmt.executeQuery();
        
        if (rs.next()) {
            // Update existing record
            int currentHighest = rs.getInt("highest_pvp_gold");
            int newHighest = Math.max(currentHighest, goldEarned);
            
            String updateQuery = "UPDATE player_stats SET " +
                "total_pvp_games = total_pvp_games + 1, " +
                "total_pvp_gold = total_pvp_gold + ?, " +
                "total_pvp_kills = total_pvp_kills + ?, " +
                "highest_pvp_gold = ? " +
                "WHERE username = ?";
            PreparedStatement updateStmt = conn.prepareStatement(updateQuery);
            updateStmt.setInt(1, goldEarned);
            updateStmt.setInt(2, kills);
            updateStmt.setInt(3, newHighest);
            updateStmt.setString(4, username);
            updateStmt.executeUpdate();
            updateStmt.close();
        } else {
            // Insert new record
            String insertQuery = "INSERT INTO player_stats (username, total_pvp_games, total_pvp_gold, total_pvp_kills, highest_pvp_gold) " +
                "VALUES (?, 1, ?, ?, ?)";
            PreparedStatement insertStmt = conn.prepareStatement(insertQuery);
            insertStmt.setString(1, username);
            insertStmt.setInt(2, goldEarned);
            insertStmt.setInt(3, kills);
            insertStmt.setInt(4, goldEarned);
            insertStmt.executeUpdate();
            insertStmt.close();
        }
        
        rs.close();
        checkStmt.close();
    }
    
    /**
     * Update Maze stats for player
     */
    private void updateMazeStats(Connection conn, String username, int score, int coinsCollected, boolean won) throws SQLException {
        // Check if record already exists
        String checkQuery = "SELECT * FROM player_stats WHERE username = ?";
        PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
        checkStmt.setString(1, username);
        ResultSet rs = checkStmt.executeQuery();
        
        if (rs.next()) {
            // Update existing record
            int currentHighest = rs.getInt("highest_maze_score");
            int newHighest = Math.max(currentHighest, score);
            
            String updateQuery = "UPDATE player_stats SET " +
                "total_maze_games = total_maze_games + 1, " +
                "total_maze_wins = total_maze_wins + ?, " +
                "total_maze_score = total_maze_score + ?, " +
                "highest_maze_score = ?, " +
                "total_coins_collected = total_coins_collected + ? " +
                "WHERE username = ?";
            PreparedStatement updateStmt = conn.prepareStatement(updateQuery);
            updateStmt.setInt(1, won ? 1 : 0);
            updateStmt.setInt(2, score);
            updateStmt.setInt(3, newHighest);
            updateStmt.setInt(4, coinsCollected);
            updateStmt.setString(5, username);
            updateStmt.executeUpdate();
            updateStmt.close();
        } else {
            // Insert new record
            String insertQuery = "INSERT INTO player_stats (username, total_maze_games, total_maze_wins, total_maze_score, highest_maze_score, total_coins_collected) " +
                "VALUES (?, 1, ?, ?, ?, ?)";
            PreparedStatement insertStmt = conn.prepareStatement(insertQuery);
            insertStmt.setString(1, username);
            insertStmt.setInt(2, won ? 1 : 0);
            insertStmt.setInt(3, score);
            insertStmt.setInt(4, score);
            insertStmt.setInt(5, coinsCollected);
            insertStmt.executeUpdate();
            insertStmt.close();
        }
        
        rs.close();
        checkStmt.close();
    }
    
    /**
     * Get player stats
     */
    public String getPlayerStats(String username) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return null;
        
        try {
            String query = "SELECT * FROM player_stats WHERE username = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                StringBuilder sb = new StringBuilder();
                sb.append("PvP Games: ").append(rs.getInt("total_pvp_games"));
                sb.append(", PvP Kills: ").append(rs.getInt("total_pvp_kills"));
                sb.append(", Best PvP: ").append(rs.getInt("highest_pvp_gold"));
                sb.append(", Maze Games: ").append(rs.getInt("total_maze_games"));
                sb.append(", Maze Wins: ").append(rs.getInt("total_maze_wins"));
                sb.append(", Best Maze: ").append(rs.getInt("highest_maze_score"));
                
                rs.close();
                stmt.close();
                return sb.toString();
            }
            
            rs.close();
            stmt.close();
            return "No stats found";
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        } finally {
            try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }
    
    /**
     * Get top 10 highest scores by game mode
     */
    public String getTopScores(String gameMode, int limit) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return null;
        
        try {
            String query = "SELECT username, MAX(score) as best_score FROM game_history " +
                          "WHERE game_mode = ? GROUP BY username ORDER BY best_score DESC LIMIT ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, gameMode);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            
            StringBuilder sb = new StringBuilder();
            int rank = 1;
            while (rs.next()) {
                sb.append(rank++).append(". ")
                  .append(rs.getString("username")).append(": ")
                  .append(rs.getInt("best_score")).append("\n");
            }
            
            rs.close();
            stmt.close();
            return sb.toString();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        } finally {
            try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }
}
