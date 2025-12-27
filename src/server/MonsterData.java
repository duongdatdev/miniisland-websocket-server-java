package server;

import java.util.Random;

/**
 * Server-side monster state for synchronization.
 * This class tracks monster position, health, and AI on the server
 * to ensure all clients see the same monster behavior.
 */
public class MonsterData {
    public int id;
    public int x, y;
    public int health;
    public int maxHealth;
    public int type; // 0=SLIME, 1=GOBLIN, 2=ORC, 3=BOSS
    public int damage;
    public int goldReward;
    public int speed;
    public boolean alive = true;
    
    // AI state
    private int moveDirection = 1;
    private int moveTimer = 0;
    private int moveDuration = 60;
    private Random random = new Random();
    
    // Map bounds (playable area for hunt map)
    private static final int MIN_BOUND = 528;  // Tile 11 * 48
    private static final int MAX_BOUND = 1824; // Tile 38 * 48
    
    public MonsterData(int id, int type, int x, int y) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        
        // Set stats based on type
        // Note: speed is pixels per 33ms tick (30 FPS), so multiply by ~30 for approx pixels per second
        switch (type) {
            case 0: // SLIME
                maxHealth = 30;
                damage = 5;
                goldReward = 10;
                speed = 3;  // ~90 pixels/sec
                break;
            case 1: // GOBLIN
                maxHealth = 50;
                damage = 10;
                goldReward = 25;
                speed = 4; // ~120 pixels/sec
                break;
            case 2: // ORC
                maxHealth = 100;
                damage = 20;
                goldReward = 50;
                speed = 2;  // ~60 pixels/sec
                break;
            case 3: // BOSS
                maxHealth = 300;
                damage = 30;
                goldReward = 200;
                speed = 2;  // ~60 pixels/sec
                break;
            default:
                maxHealth = 30;
                damage = 5;
                goldReward = 10;
                speed = 3;
        }
        this.health = maxHealth;
        this.moveDirection = random.nextInt(4) + 1;
        this.moveDuration = random.nextInt(60) + 30; // 1-3 seconds at 30 FPS
    }
    
    /**
     * Update AI movement - called every server tick
     */
    /**
     * Update AI movement - called every server tick
     * @param players List of players to potentially chase
     */
    public void updateAI(java.util.List<ClientInfo> players) {
        if (!alive) return;
        
        // Find nearest player
        ClientInfo target = null;
        double minDst = 300.0; // Vision range
        
        if (players != null) {
            for (ClientInfo p : players) {
                 if (p != null && p.isAlive && "hunt".equals(p.getMap())) {
                     double dst = Math.sqrt(Math.pow(x - p.getX(), 2) + Math.pow(y - p.getY(), 2));
                     if (dst < minDst) {
                         minDst = dst;
                         target = p;
                     }
                 }
            }
        }
        
        if (target != null) {
            // Chase logic
            double dx = target.getX() - x;
            double dy = target.getY() - y;
            double length = Math.sqrt(dx * dx + dy * dy);
            
            if (length > 0) {
                // Move towards player
                x += (int) (dx / length * speed);
                y += (int) (dy / length * speed);
                
                // Update internal direction for potential animation sync (if needed later)
                if (Math.abs(dx) > Math.abs(dy)) {
                    moveDirection = dx > 0 ? 4 : 3;
                } else {
                    moveDirection = dy > 0 ? 1 : 2;
                }
            }
        } else {
            // Idle / Random movement logic
            moveTimer++;
            if (moveTimer >= moveDuration) {
                moveTimer = 0;
                moveDirection = random.nextInt(4) + 1;
                moveDuration = random.nextInt(60) + 30; // 1-3 seconds at 30 FPS
            }
            
            int newX = x;
            int newY = y;
            
            switch (moveDirection) {
                case 1: // Down
                    newY += speed;
                    break;
                case 2: // Up
                    newY -= speed;
                    break;
                case 3: // Left
                    newX -= speed;
                    break;
                case 4: // Right
                    newX += speed;
                    break;
            }
            
            x = newX;
            y = newY;
        }
        
        // Clamp to map bounds (account for monster size ~48px)
        x = Math.max(MIN_BOUND, Math.min(x, MAX_BOUND - 48));
        y = Math.max(MIN_BOUND, Math.min(y, MAX_BOUND - 48));
        
        // Change direction if hitting boundary (only for random move, chase ignores this for fluid sliding along wall)
        if (target == null && (x == MIN_BOUND || x == MAX_BOUND - 48 || y == MIN_BOUND || y == MAX_BOUND - 48)) {
            moveDirection = random.nextInt(4) + 1;
        }
    }
    
    /**
     * Take damage from a bullet
     * @param dmg damage amount
     * @return gold reward if monster died, 0 otherwise
     */
    public int takeDamage(int dmg) {
        if (!alive) return 0;
        
        health -= dmg;
        if (health <= 0) {
            health = 0;
            alive = false;
            return goldReward;
        }
        return 0;
    }
    
    /**
     * Get monster type name for protocol
     */
    public String getTypeName() {
        switch (type) {
            case 0: return "SLIME";
            case 1: return "GOBLIN";
            case 2: return "ORC";
            case 3: return "BOSS";
            default: return "SLIME";
        }
    }
    
    @Override
    public String toString() {
        return "MonsterData{id=" + id + ", type=" + getTypeName() + 
               ", pos=(" + x + "," + y + "), health=" + health + "/" + maxHealth + "}";
    }
}
