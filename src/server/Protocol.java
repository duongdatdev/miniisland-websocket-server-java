package server;

/**
 * This class is responsible for creating the protocol for the server.
 * It is used to create packets that are sent between the server and the client.
 * The packets are used to communicate information about the game state between the server and the client.
 */
public class Protocol {

    private String message = "";

    /**
     * Creates a new instance of Protocol
     */
    public Protocol() {
    }

    /**
     * IDPacket
     * @param id the id of the player
     */
    public String IDPacket(int id) {
        message = "ID" + id;
        return message;
    }

    /**
     * IDPacket
     * @param id the id of the player
     * @param username the username of the player
     */
    public String IDPacket(int id, String username) {
        message = "ID" + id + "," + username;
        return message;
    }

    /**
     * NewClientPacket
     * @param x the x coordinate of the player
     * @param y the y coordinate of the player
     * @param dir the direction of the player
     * @param id the id of the player
     */
    public String NewClientPacket(int x, int y, int dir, int id) {
        message = "NewClient" + x + "," + y + "-" + dir + "|" + id;
        return message;
    }

    /**
     * NewClientPacket
     * @param username the username of the player
     * @param x the x coordinate of the player
     * @param y the y coordinate of the player
     * @param dir the direction of the player
     * @param id the id of the player
     * @param map the map of the player
     */
    public String NewClientPacket(String username, int x, int y, int dir, int id, String map) {
        message = "NewClient" + username + "," + x + "-" + y + "|" + dir + "!" + id + "#" + map;
        return message;
    }

    /**
     * LoginPacket
     * @param status the status of the login (success or failed)
     * @param msg the message of the login
     * @return the message
     */
    public String LoginPacket(String status,String msg) {
        message = "Login," + status + "," + msg;
        return message;
    }

    /**
     * RegisterPacket
     * @param status the status of the register (success or failed)
     * @param msg the message of the register
     * @return the message
     */
    public String registerPacket(String status, String msg) {
        message = "Register," + status + "," + msg;
        return message;
    }

    /**
     * LeaderBoardPacket
     * @param msg the message of the leaderboard
     * @return the message
     */
    public String leaderBoardPacket(String msg) {
        message = "Leaderboard" + msg;
        return message;
    }

    /**
     * MazeMapPacket
     * @param msg the message of the update point
     * @return the message
     */
    public String mazeMapPacket(String msg) {
        message = "Maze" + msg;
        return message;
    }

    public String teleportPacket(String username, String map, int x, int y) {
        message = "TeleportMap," + username + "," + map + "," + x + "," + y;
        return message;
    }

    public String removePlayerPacket(String username) {
        message = "Exit" + username;
        return message;
    }
    
    // ============== Skin Shop Protocols ==============
    
    /**
     * Send list of skins in shop
     */
    public String skinsListPacket(java.util.List<dao.ShopDAO.SkinItem> skins) {
        StringBuilder sb = new StringBuilder("SkinsList");
        for (dao.ShopDAO.SkinItem skin : skins) {
            sb.append(",").append(skin.toProtocolString());
        }
        return sb.toString();
    }
    
    /**
     * Send player's coins
     */
    public String playerCoinsPacket(int coins) {
        return "PlayerCoins," + coins;
    }
    
    /**
     * Send buy result
     */
    public String buyResultPacket(boolean success, String msg, int newBalance) {
        return "BuyResult," + (success ? "success" : "failed") + "," + msg + "," + newBalance;
    }
    
    /**
     * Send list of player's skins
     */
    public String playerSkinsPacket(java.util.List<dao.ShopDAO.PlayerSkin> skins) {
        StringBuilder sb = new StringBuilder("PlayerSkins");
        for (dao.ShopDAO.PlayerSkin skin : skins) {
            sb.append(",").append(skin.toProtocolString());
        }
        return sb.toString();
    }
    
    /**
     * Send equipped skin
     */
    public String equippedSkinPacket(String skinFolder) {
        return "EquippedSkin," + skinFolder;
    }

    /**
     * UpdatePacket
     * @param username the username of the player
     * @param x the x coordinate of the player
     * @param y the y coordinate of the player
     * @param dir the direction of the player
     * @return the message
     */
    public String UpdatePacket(String username, int x, int y, int dir) {
        message = "Update," + username + "," + x + "," + y + "," + dir;
        return message;
    }
}
