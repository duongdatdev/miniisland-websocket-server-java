package server;

import dao.GameHistoryDAO;
import dao.ShopDAO;
import map.MazeGen;
import service.PlayerService;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebSocket-based game server for Mini Island 2D
 * This server handles client connections via WebSocket instead of TCP Socket
 */
public class WebSocketGameServer extends WebSocketServer {

    private ArrayList<ClientInfo> playerOnline;
    private Map<WebSocket, String> connectionAuthMap; // Track authentication per connection
    private Protocol protocol;
    private PlayerService playerService;
    private GameHistoryDAO gameHistoryDAO;
    private ShopDAO shopDAO;

    // Monster Hunt
    private java.util.Timer huntTimer;
    private int huntTimeRemaining = 60;
    private boolean huntActive = false;
    private Map<String, Integer> huntScores = new HashMap<>();

    // Monster synchronization - use ConcurrentHashMap for thread safety
    private Map<Integer, MonsterData> huntMonsters = new java.util.concurrent.ConcurrentHashMap<>();
    private int nextMonsterId = 1;
    private int monsterUpdateTick = 0;
    private java.util.Timer monsterTimer; // Fast timer for monster movement

    //Maze gen
    private MazeGen mazeGen = new MazeGen(10, 20);
    private boolean winMaze = true;

    public WebSocketGameServer(int port) {
        super(new InetSocketAddress(port));
        playerOnline = new ArrayList<ClientInfo>();
        connectionAuthMap = new HashMap<>();
        protocol = new Protocol();
        playerService = new PlayerService();
        gameHistoryDAO = new GameHistoryDAO();
        shopDAO = new ShopDAO();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("New connection from " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Connection closed: " + conn.getRemoteSocketAddress());
        
        // Find and remove player associated with this connection
        String username = connectionAuthMap.get(conn);
        if (username != null) {
            for (ClientInfo player : playerOnline) {
                if (player != null && player.getUsername().equals(username)) {
                    playerOnline.remove(player);
                    broadcastMessage("Exit" + username);
                    break;
                }
            }
            connectionAuthMap.remove(conn);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            handleMessage(conn, message);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error handling message: " + message);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket error: " + ex.getMessage());
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket Game Server started successfully!");
    }

    private void handleMessage(WebSocket conn, String sentence) {
        int defaultX = 1645;
        int defaultY = 754;

        if (sentence.startsWith("Login")) {
            handleLogin(conn, sentence);
        } else if (sentence.startsWith("Register")) {
            handleRegister(conn, sentence);
        } else if (sentence.startsWith("Hello")) {
            handleHello(conn, sentence, defaultX, defaultY);
        } else if (sentence.startsWith("Update")) {
            handleUpdate(sentence);
        } else if (sentence.startsWith("TeleportToMap")) {
            handleTeleportToMap(sentence);
        } else if (sentence.startsWith("EnterMaze")) {
            handleEnterMaze(sentence);
        } else if (sentence.startsWith("WinMaze")) {
            handleWinMaze(sentence);
        } else if (sentence.startsWith("BulletCollision")) {
            handleBulletCollision(sentence);
        } else if (sentence.startsWith("Respawn")) {
            handleRespawn(sentence);
        } else if (sentence.startsWith("Chat")) {
            broadcastMessage(sentence);
        } else if (sentence.startsWith("Shot")) {
            broadcastMessage(sentence);
        } else if (sentence.startsWith("Remove")) {
            handleRemove(sentence);
        } else if (sentence.startsWith("Exit")) {
            handleExit(sentence);
        } else if (sentence.startsWith("Exit Auth")) {
            handleExitAuth(conn);
        } else if (sentence.startsWith("GET_ITEMS")) {
            handleGetItems(conn);
        } else if (sentence.startsWith("BUY_ITEM")) {
            handleBuyItem(conn, sentence);
        } else if (sentence.startsWith("ScoreBattleEnd")) {
            handleScoreBattleEnd(conn, sentence);
        } else if (sentence.startsWith("MazeEnd")) {
            handleMazeEnd(conn, sentence);
        } else if (sentence.startsWith("Shop,")) {
            handleShopRequest(conn, sentence);
        } else if (sentence.startsWith("SpawnMonster")) {
            broadcastToMap("hunt", sentence);
        } else if (sentence.startsWith("MonsterDead")) {
            handleMonsterDead(sentence);
        } else if (sentence.startsWith("MonsterHit")) {
            handleMonsterHit(sentence);
        } else if (sentence.startsWith("BulletUpdate")) {
            broadcastToMap("hunt", sentence);
        } else if (sentence.startsWith("ScoreUpdate")) {
            handleScoreUpdate(sentence);
        }
    }

    private void handleLogin(WebSocket conn, String sentence) {
        String[] parts = sentence.split(",");
        String username = parts[1];
        String password = parts[2];

        String result = playerService.login(username, password);
        String msg = result.substring(result.indexOf('|') + 1, result.length());

        // Check if user is already logged in
        for (ClientInfo clientInfo : playerOnline) {
            if (clientInfo != null && clientInfo.getUsername().equals(username)) {
                sendToClient(conn, protocol.LoginPacket("Failed", "User already logged in"));
                return;
            }
        }

        if (result.startsWith("Success")) {
            System.out.println("Login Success: " + username);
            sendToClient(conn, protocol.LoginPacket("Success", msg));
        } else {
            System.out.println("Login Failed: " + username);
            sendToClient(conn, protocol.LoginPacket("Failed", msg));
        }
    }

    private void handleRegister(WebSocket conn, String sentence) {
        String[] parts = sentence.split(",");
        String username = parts[1];
        String password = parts[2];
        String email = parts[3];

        String result = playerService.register(username, password, email);
        int posResult = result.indexOf('|');
        String msg = result.substring(posResult + 1, result.length());

        if (result.startsWith("Success")) {
            sendToClient(conn, protocol.registerPacket("Success", msg));
            System.out.println("Register Success: " + username);
        } else {
            sendToClient(conn, protocol.registerPacket("Failed", msg));
            System.out.println("Register Failed: " + username);
        }
    }

    private void handleHello(WebSocket conn, String sentence, int defaultX, int defaultY) {
        String username = sentence.substring(5, sentence.length());
        
        // Store the authenticated username for this connection
        connectionAuthMap.put(conn, username);
        
        // Auto-give default skin to user if they don't have any
        shopDAO.giveDefaultSkin(username);

        sendToClient(conn, protocol.IDPacket(playerOnline.size() + 1, username));
        
        // Get player's equipped skin
        String equippedSkin = shopDAO.getEquippedSkin(username);
        
        // Send NewClient with skin info to other players
        broadcastMessage(protocol.NewClientPacket(username, defaultX, defaultY, -1, playerOnline.size() + 1, "lobby"));
        
        // Broadcast new player's skin so other players can see it
        broadcastMessage("ChangeSkin," + username + "," + equippedSkin);

        System.out.println(protocol.leaderBoardPacket(playerService.leaderBoard()));
        sendToClient(conn, protocol.leaderBoardPacket(playerService.leaderBoard()));

        sendAllClientsInMap(conn, "lobby");
        
        // Send other players' skins to the new player
        for (ClientInfo player : playerOnline) {
            if (player != null && !player.getUsername().equals(username)) {
                String playerSkin = shopDAO.getEquippedSkin(player.getUsername());
                sendToClient(conn, "ChangeSkin," + player.getUsername() + "," + playerSkin);
            }
        }

        playerOnline.add(new ClientInfo(conn, username, defaultX, defaultY, -1, "lobby"));
    }

    private void handleUpdate(String sentence) {
        String[] parts = sentence.split(",");
        String username = parts[1];
        int x = Integer.parseInt(parts[2]);
        int y = Integer.parseInt(parts[3]);
        int dir = Integer.parseInt(parts[4]);

        playerOnline.stream()
                .filter(player -> player.getUsername().equals(username))
                .findFirst()
                .ifPresent(player -> {
                    player.setPosX(x);
                    player.setPosY(y);
                    player.setDirection(dir);
                });

        playerOnline.stream()
                .filter(player -> !player.getUsername().equals(username))
                .forEach(player -> sendToClient(player.getWebSocket(), sentence));
    }

    private void handleTeleportToMap(String sentence) {
        String[] parts = sentence.split(",");
        String username = parts[1];
        String map = parts[2];
        int x = Integer.parseInt(parts[3]);
        int y = Integer.parseInt(parts[4]);

        ClientInfo p = null;
        for (ClientInfo player : playerOnline) {
            if (player != null && player.getUsername().equals(username)) {
                player.setPosX(x);
                player.setPosY(y);
                player.setMap(map);
                p = player;
                break;
            }
        }

        broadcastMessage(protocol.NewClientPacket(username, x, y, -1, playerOnline.size() + 1, p.getMap()));
        sendAllClientsInMap(p.getWebSocket(), map);
        
        // === FIX: Sync Skins on Teleport ===
        // 1. Send this player's skin to everyone else in the new map
        String mySkin = shopDAO.getEquippedSkin(username);
        for (ClientInfo player : playerOnline) {
            if (player != null && !player.getUsername().equals(username) && player.getMap().equals(map)) {
                sendToClient(player.getWebSocket(), "ChangeSkin," + username + "," + mySkin);
            }
        }
        
        // 2. Send skins of everyone in the new map to this player
        for (ClientInfo player : playerOnline) {
            if (player != null && !player.getUsername().equals(username) && player.getMap().equals(map)) {
                String otherSkin = shopDAO.getEquippedSkin(player.getUsername());
                sendToClient(p.getWebSocket(), "ChangeSkin," + player.getUsername() + "," + otherSkin);
            }
        }
        // ===================================

        broadcastMessage(sentence);
        
        // Monster Hunt Timer Logic
        if (map.equals("hunt")) {
            startHuntTimer();
            
            // Send all existing monsters to the new player
            for (MonsterData m : huntMonsters.values()) {
                if (m.alive) {
                    sendToClient(p.getWebSocket(), "SpawnMonster," + m.id + "," + m.type + "," + m.x + "," + m.y);
                }
            }
            
            // Also send current time and wave
            if (huntActive) {
                sendToClient(p.getWebSocket(), "HuntTime," + huntTimeRemaining);
                int wave = (180 - huntTimeRemaining) / 45 + 1;
                sendToClient(p.getWebSocket(), "HuntWave," + wave);
            }
        } else {
            // Check if anyone is left in hunt
            boolean anyInHunt = false;
            for (ClientInfo player : playerOnline) {
                if (player.getMap().equals("hunt")) {
                    anyInHunt = true;
                    break;
                }
            }
            if (!anyInHunt) {
                stopHuntTimer();
            }
        }
    }

    private void handleEnterMaze(String sentence) {
        String username = sentence.substring(9);

        ClientInfo p = null;
        for (ClientInfo player : playerOnline) {
            if (player != null && player.getUsername().equals(username)) {
                p = player;
                player.setMap("Loading");
                break;
            }
        }
        if (winMaze) {
            mazeGen = new MazeGen(10, 20);
            mazeGen.solve();
            winMaze = false;
        }

        assert p != null;
        sendToClient(p.getWebSocket(), protocol.mazeMapPacket(mazeGen.toString()));
    }

    private void handleWinMaze(String sentence) {
        String username = sentence.substring(7);

        ClientInfo p = null;
        for (ClientInfo player : playerOnline) {
            if (player != null && player.getUsername().equals(username)) {
                p = player;
                player.setMap("lobby");
                break;
            }
        }

        assert p != null;

        broadcastMessage(protocol.NewClientPacket(username, 1645, 754, -1, playerOnline.size() + 1, p.getMap()));
        playerService.updatePoint(username, 50);
        sendLeaderBoardToAllClient();
        sendAllClientsInMap(p.getWebSocket(), "lobby");
        teleportAllPlayerInMapToMap("maze", "lobby");
        winMaze = true;
    }

    private void handleBulletCollision(String sentence) {
        String[] parts = sentence.split(",");
        String playerShot = parts[1];
        String playerHit = parts[2];

        for (ClientInfo player : playerOnline) {
            if (player != null && player.getUsername().equals(playerHit) && player.isAlive) {
                playerService.updatePoint(playerShot, 10);
                playerService.updatePoint(playerHit, -10);
                sendLeaderBoardToAllClient();
                broadcastMessage(sentence);
                break;
            }
        }
        for (ClientInfo player : playerOnline) {
            if (player != null && player.getUsername().equals(playerHit)) {
                player.isAlive = false;
                break;
            }
        }
    }

    private void handleRespawn(String sentence) {
        String username = sentence.substring(7);

        for (ClientInfo player : playerOnline) {
            if (player != null && player.getUsername().equals(username)) {
                player.isAlive = true;
                break;
            }
        }
    }

    private void handleRemove(String sentence) {
        int id = Integer.parseInt(sentence.substring(6));
        broadcastMessage(sentence);
        playerOnline.remove(id);
    }

    private void handleExit(String sentence) {
        String username = sentence.substring(4);
        System.out.println("Exit: " + username);

        for (ClientInfo player : playerOnline) {
            if (player != null && player.getUsername().equals(username)) {
                playerOnline.remove(player);
                connectionAuthMap.remove(player.getWebSocket());
                break;
            }
        }
        broadcastMessage(sentence);
    }

    private void handleExitAuth(WebSocket conn) {
        connectionAuthMap.remove(conn);
        conn.close();
    }
    
    /**
     * Handle Score Battle end - add points to leaderboard and save to database
     */
    private void handleScoreBattleEnd(WebSocket conn, String sentence) {
        String[] parts = sentence.split(",");
        String username = parts[1];
        int finalScore = Integer.parseInt(parts[2]);
        int kills = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
        
        // Calculate points to add to leaderboard (10% of gold earned)
        int pointsToAdd = finalScore / 10;
        
        // Calculate coins to add (gold earned + kill bonus)
        int coinsToAdd = finalScore + (kills * 5); // Full gold earned + 5 coins per kill
        
        if (pointsToAdd > 0) {
            playerService.updatePoint(username, pointsToAdd);
        }
        
        // Always add coins if player earned any
        if (coinsToAdd > 0) {
            shopDAO.addCoins(username, coinsToAdd);
        }
        
        // Save to database
        gameHistoryDAO.savePvpGameResult(username, finalScore, kills, pointsToAdd);
        
        System.out.println("ScoreBattle End - " + username + ": +" + pointsToAdd + " points, +" + coinsToAdd + " coins (from " + finalScore + " gold, " + kills + " kills)");
        sendLeaderBoardToAllClient();
        
        // Send updated coin balance to client
        sendToClient(conn, protocol.playerCoinsPacket(shopDAO.getPlayerCoins(username)));
    }
    
    /**
     * Handle Maze end - add points to leaderboard and save to database
     */
    private void handleMazeEnd(WebSocket conn, String sentence) {
        String[] parts = sentence.split(",");
        String username = parts[1];
        int score = Integer.parseInt(parts[2]);
        int coinsCollected = Integer.parseInt(parts[3]);
        boolean won = parts[4].equals("1");
        
        // Calculate points: win +50, points from score (5% of score)
        int pointsToAdd = score / 20;
        if (won) {
            pointsToAdd += 50; // Bonus for winning maze
        }
        
        // Calculate coins: collected coins + win bonus
        int coinsToAdd = coinsCollected;
        if (won) {
            coinsToAdd += 25; // Bonus coins for winning
        }
        
        if (pointsToAdd > 0) {
            playerService.updatePoint(username, pointsToAdd);
        }
        
        // Always add coins if player earned any
        if (coinsToAdd > 0) {
            shopDAO.addCoins(username, coinsToAdd);
        }
        
        // Save to database
        gameHistoryDAO.saveMazeGameResult(username, score, coinsCollected, won, pointsToAdd);
        
        System.out.println("Maze End - " + username + ": +" + pointsToAdd + " points, +" + coinsToAdd + " coins (score: " + score + ", coins: " + coinsCollected + ", won: " + won + ")");
        sendLeaderBoardToAllClient();
        
        // Send updated coin balance to client
        sendToClient(conn, protocol.playerCoinsPacket(shopDAO.getPlayerCoins(username)));
    }

    private void handleGetItems(WebSocket conn) {
        List<String> items = fetchItemsFromDatabase();
        for (String item : items) {
            sendToClient(conn, item);
        }
        sendToClient(conn, ""); // End of items
    }

    private void handleBuyItem(WebSocket conn, String sentence) {
        String[] parts = sentence.split(",");
        int userId = Integer.parseInt(parts[1]);
        int itemId = Integer.parseInt(parts[2]);
        String result = buyItem(userId, itemId);
        sendToClient(conn, result);
    }

    private List<String> fetchItemsFromDatabase() {
        List<String> items = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/game_shop", "root", "password");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id, name, price FROM items")) {

            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                int price = resultSet.getInt("price");
                items.add(id + ": " + name + " - " + price + " coins");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    private String buyItem(int userId, int itemId) {
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/game_shop", "root", "password")) {
            PreparedStatement checkBalanceStmt = connection.prepareStatement("SELECT balance FROM users WHERE id = ?");
            checkBalanceStmt.setInt(1, userId);
            ResultSet balanceResult = checkBalanceStmt.executeQuery();
            if (balanceResult.next()) {
                int balance = balanceResult.getInt("balance");

                PreparedStatement getItemPriceStmt = connection.prepareStatement("SELECT price FROM items WHERE id = ?");
                getItemPriceStmt.setInt(1, itemId);
                ResultSet priceResult = getItemPriceStmt.executeQuery();
                if (priceResult.next()) {
                    int price = priceResult.getInt("price");

                    if (balance >= price) {
                        PreparedStatement updateBalanceStmt = connection.prepareStatement("UPDATE users SET balance = balance - ? WHERE id = ?");
                        updateBalanceStmt.setInt(1, price);
                        updateBalanceStmt.setInt(2, userId);
                        updateBalanceStmt.executeUpdate();

                        PreparedStatement addItemStmt = connection.prepareStatement("INSERT INTO userItems (user_id, item_id) VALUES (?, ?)");
                        addItemStmt.setInt(1, userId);
                        addItemStmt.setInt(2, itemId);
                        addItemStmt.executeUpdate();

                        return "Purchase successful!";
                    } else {
                        return "Insufficient balance!";
                    }
                } else {
                    return "Item not found!";
                }
            } else {
                return "User not found!";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Purchase failed!";
        }
    }

    public void teleportAllPlayerInMapToMap(String map, String map2) {
        for (ClientInfo player : playerOnline) {
            if (player.getMap().equals(map)) {
                player.setMap(map2);
                sendToClient(player.getWebSocket(), protocol.teleportPacket(player.getUsername(), map2, 1645, 754));
                broadcastMessage(protocol.NewClientPacket(player.getUsername(), 1645, 754, -1, playerOnline.size() + 1, player.getMap()));
                sendAllClientsInMap(player.getWebSocket(), map2);
            }
        }
    }

    public void broadcastMessage(String message) {
        for (ClientInfo clientInfo : playerOnline) {
            if (clientInfo != null && clientInfo.getWebSocket() != null && clientInfo.getWebSocket().isOpen()) {
                sendToClient(clientInfo.getWebSocket(), message);
            }
        }
    }

    public void sendToClient(WebSocket conn, String message) {
        if (conn != null && conn.isOpen()) {
            conn.send(message);
        }
    }

    public void sendLeaderBoardToAllClient() {
        for (ClientInfo clientInfo : playerOnline) {
            if (clientInfo != null && clientInfo.getWebSocket() != null) {
                sendLeaderBoardToClient(clientInfo.getWebSocket());
            }
        }
    }

    public void sendLeaderBoardToClient(WebSocket conn) {
        sendToClient(conn, protocol.leaderBoardPacket(playerService.leaderBoard()));
    }

    public void sendAllClientsInMap(WebSocket conn, String map) {
        for (int i = 0; i < playerOnline.size(); i++) {
            if (playerOnline.get(i) != null && playerOnline.get(i).getMap().equals(map)) {
                String username = playerOnline.get(i).getUsername();
                int x = playerOnline.get(i).getX();
                int y = playerOnline.get(i).getY();
                int dir = playerOnline.get(i).getDir();
                sendToClient(conn, protocol.NewClientPacket(username, x, y, dir, i + 1, map));
            }
        }
    }

    public ArrayList<ClientInfo> getPlayerOnline() {
        return playerOnline;
    }

    public void startServer() {
        start();
    }

    public void stopServer() throws IOException, InterruptedException {
        stop();
    }

    // Shop handling - Skin Shop
    private void handleShopRequest(WebSocket conn, String sentence) {
        String[] parts = sentence.split(",");
        if (parts.length < 2) return;
        
        String action = parts[1];
        String username = connectionAuthMap.get(conn);
        
        if (username == null) {
            sendToClient(conn, "Shop,Error,Not logged in");
            return;
        }
        
        switch (action) {
            case "GetSkins":
                // Get all skins
                List<ShopDAO.SkinItem> skins = shopDAO.getAllSkins();
                sendToClient(conn, protocol.skinsListPacket(skins));
                break;
                
            case "GetCoins":
                // Get player's coins
                int coins = shopDAO.getPlayerCoins(username);
                sendToClient(conn, protocol.playerCoinsPacket(coins));
                break;
                
            case "Buy":
                // Buy a skin: Shop,Buy,skinId
                if (parts.length >= 3) {
                    try {
                        int skinId = Integer.parseInt(parts[2]);
                        String result = shopDAO.buySkin(username, skinId);
                        String[] resultParts = result.split("\\|");
                        boolean success = resultParts[0].equals("Success");
                        String message = resultParts.length > 1 ? resultParts[1] : result;
                        int newBalance = shopDAO.getPlayerCoins(username);
                        sendToClient(conn, protocol.buyResultPacket(success, message, newBalance));
                        
                        // If purchase successful, send updated player skins list
                        if (success) {
                            List<ShopDAO.PlayerSkin> playerSkins = shopDAO.getPlayerSkins(username);
                            sendToClient(conn, protocol.playerSkinsPacket(playerSkins));
                        }
                    } catch (NumberFormatException e) {
                        sendToClient(conn, protocol.buyResultPacket(false, "Invalid skin ID", 0));
                    }
                }
                break;
                
            case "GetMySkins":
                // Get player's owned skins
                List<ShopDAO.PlayerSkin> playerSkins = shopDAO.getPlayerSkins(username);
                sendToClient(conn, protocol.playerSkinsPacket(playerSkins));
                break;
                
            case "Equip":
                // Equip skin: Shop,Equip,skinId
                if (parts.length >= 3) {
                    try {
                        int skinId = Integer.parseInt(parts[2]);
                        String result = shopDAO.equipSkin(username, skinId);
                        String[] resultParts = result.split("\\|");
                        boolean success = resultParts[0].equals("Success");
                        
                        if (success) {
                            String skinFolder = resultParts[1];
                            sendToClient(conn, protocol.equippedSkinPacket(skinFolder));
                            // Broadcast to others that this player changed skin
                            broadcastMessage("ChangeSkin," + username + "," + skinFolder);
                        } else {
                            sendToClient(conn, "Shop,Error," + resultParts[1]);
                        }
                    } catch (NumberFormatException e) {
                        sendToClient(conn, "Shop,Error,Invalid skin ID");
                    }
                }
                break;
                
            case "GetEquipped":
                // Get equipped skin folder
                String equippedFolder = shopDAO.getEquippedSkin(username);
                sendToClient(conn, protocol.equippedSkinPacket(equippedFolder));
                break;
                
            default:
                sendToClient(conn, "Shop,Error,Unknown action");
                break;
        }
    }

    // === Monster Hunt Helper Methods ===
    
    private void startHuntTimer() {
        if (huntActive) return;
        huntActive = true;
        huntTimeRemaining = 60;
        huntScores.clear();
        huntMonsters.clear();
        nextMonsterId = 1;
        monsterUpdateTick = 0;
        
        // Slow timer for game time and spawning (every 1 second)
        huntTimer = new java.util.Timer();
        huntTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                if (huntTimeRemaining > 0) {
                    huntTimeRemaining--;
                    // Broadcast time to all players in "hunt" map
                    broadcastToMap("hunt", "HuntTime," + huntTimeRemaining);
                    
                    // Calculate and broadcast wave (every 45 seconds = 1 wave)
                    int wave = (180 - huntTimeRemaining) / 45 + 1;
                    broadcastToMap("hunt", "HuntWave," + wave);
                    
                    // Server-side Monster Spawning
                    if (huntTimeRemaining % 3 == 0 && huntMonsters.size() < 15) { // Spawn every 3 seconds, max 15 monsters
                        int x = 528 + (int)(Math.random() * 1296); // Within playable bounds
                        int y = 528 + (int)(Math.random() * 1296);
                        int type = (int)(Math.random() * 3); 
                        int id = nextMonsterId++;
                        
                        // Track monster on server
                        MonsterData monster = new MonsterData(id, type, x, y);
                        huntMonsters.put(id, monster);
                        
                        broadcastToMap("hunt", "SpawnMonster," + id + "," + type + "," + x + "," + y);
                    }
                    
                    // Remove dead monsters from tracking
                    huntMonsters.entrySet().removeIf(e -> !e.getValue().alive);
                } else {
                    // End game
                    broadcastToMap("hunt", "HuntEnd");
                    stopHuntTimer();
                }
            }
        }, 1000, 1000);
        
        // Fast timer for monster AI and position broadcasts (every 33ms = ~30 FPS)
        monsterTimer = new java.util.Timer();
        monsterTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                if (!huntActive) return;
                
                // Update all monster AI (server-side movement)
                for (MonsterData m : huntMonsters.values()) {
                    if (m.alive) {
                        m.updateAI(playerOnline);
                    }
                }
                
                // Broadcast monster positions every tick for smooth movement
                for (MonsterData m : huntMonsters.values()) {
                    if (m.alive) {
                        broadcastToMap("hunt", "MonsterUpdate," + m.id + "," + m.x + "," + m.y + "," + m.health);
                    }
                }
            }
        }, 33, 33); // Run every 33ms = ~30 FPS
    }
    
    private void stopHuntTimer() {
        if (huntTimer != null) {
            huntTimer.cancel();
            huntTimer = null;
        }
        if (monsterTimer != null) {
            monsterTimer.cancel();
            monsterTimer = null;
        }
        huntActive = false;
        huntMonsters.clear();
        nextMonsterId = 1;
    }
    
    private void broadcastToMap(String mapName, String message) {
        for (ClientInfo player : playerOnline) {
            if (player != null && player.getMap().equals(mapName)) {
                sendToClient(player.getWebSocket(), message);
            }
        }
    }
    
    /**
     * Handle monster hit from client - process damage server-side
     * Format: MonsterHit,monsterId,damage,shooterUsername
     */
    private void handleMonsterHit(String sentence) {
        String[] parts = sentence.split(",");
        if (parts.length < 4) return;
        
        try {
            int monsterId = Integer.parseInt(parts[1]);
            int damage = Integer.parseInt(parts[2]);
            String shooter = parts[3];
            
            MonsterData monster = huntMonsters.get(monsterId);
            if (monster == null || !monster.alive) return;
            
            // Apply damage and check for death
            int goldReward = monster.takeDamage(damage);
            
            // Broadcast health update to all clients
            broadcastToMap("hunt", "MonsterUpdate," + monster.id + "," + monster.x + "," + monster.y + "," + monster.health);
            
            if (goldReward > 0) {
                // Monster died - remove from tracking and broadcast death
                huntMonsters.remove(monsterId);
                broadcastToMap("hunt", "MonsterDead," + monsterId + "," + shooter + "," + goldReward);
                
                // Update shooter's score on server
                int currentScore = huntScores.getOrDefault(shooter, 0);
                huntScores.put(shooter, currentScore + goldReward);
                broadcastHuntLeaderboard();
                
                System.out.println("[HUNT] " + shooter + " killed monster #" + monsterId + " for " + goldReward + " gold");
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid MonsterHit packet: " + sentence);
        }
    }
    
    private void handleMonsterDead(String sentence) {
        // MonsterDead,monsterId,killerName,points
        String[] parts = sentence.split(",");
        if (parts.length < 4) return;
        
        // Score is now handled by ScoreUpdate packet to support client-side combos/buffs
        // String killer = parts[2];
        // int points = Integer.parseInt(parts[3]);
        // huntScores.put(killer, huntScores.getOrDefault(killer, 0) + points);
        // broadcastHuntLeaderboard();
        
        broadcastToMap("hunt", sentence);
    }
    
    private void handleScoreUpdate(String sentence) {
        String[] parts = sentence.split(",");
        if (parts.length < 3) return;
        
        String username = parts[1];
        int score = Integer.parseInt(parts[2]);
        
        huntScores.put(username, score);
        broadcastHuntLeaderboard();
    }
    
    private void broadcastHuntLeaderboard() {
        StringBuilder sb = new StringBuilder("HuntLeaderboard");
        // Sort by score descending
        huntScores.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> sb.append(",").append(entry.getKey()).append(":").append(entry.getValue()));
            
        broadcastToMap("hunt", sb.toString());
    }
}
