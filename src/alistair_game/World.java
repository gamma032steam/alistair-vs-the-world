package alistair_game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.newdawn.slick.Color;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.Image;
import org.newdawn.slick.Input;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.geom.Vector2f;

/**
 * Handles all the game logic for a level. Created by App.
 */
class World {
    private int w, h, tSize, gridW, gridH, sidebarW;
    private float startx, starty, enemySpeed = 1f;
    private int health = 100, waveNum = 1;
    private long timer = 0;
    private Tile alistair;
    private Tower myTower; // Tower currently being placed
    
    /** 2D array of tiles for each grid cell */
    private Tile[][] tiles;
    /** List of waves, each with set of spawn instructions */
    private List<Wave> waves;
    /** Enemy path. 3D array for x-coord, y-coord and direction for enemy to move move */
    private int[][][] path;
    /** List of enemies in crder of creation (oldest first) */
    private List<Enemy> enemies = new LinkedList<>();
    /** List of all projectiles */
    private List<Projectile> projectiles = new LinkedList<>();
    /** List of all towers */
    private List<Tower> towers = new LinkedList<>();

    private static Image[] tileset;
    private static String[] tile_names;
    private static final int ALISTAIR_INDEX = 2;
    static {
        // Initialise tileset and tile names
        // Meaning of integers in level file
        tile_names = new String[3]; // TODO: add all this to a file (?)
        tile_names[0] = "wall";
        tile_names[1] = "path";
        tile_names[2] = "alistair";

        // Assign ints to images
        tileset = new Image[tile_names.length];
        try {
            for (int i = 0; i < tileset.length; i++) {
                tileset[i] = new Image("assets\\sprites\\tiles\\" + tile_names[i] + ".png");
            }
        } catch (SlickException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create the world.
     * @param w Map width
     * @param h Map height
     * @param tSize Side length of each tile in pixels
     * @param startx Enemy origin (x-axis)
     * @param starty Enemy origin (y-axis)
     * @param level Map layout
     * @param waves Data on waves and enemy spawn timing
     */
    World(int w, int h, int tSize, int sidebarW, float startx, float starty, int[][] level, ArrayList<Wave> waves) {
        this.w = w;
        this.h = h;
        this.tSize = tSize;
        this.gridW = (w-sidebarW)/tSize;
        this.gridH = h/tSize;
        this.startx = startx;
        this.starty = starty;
        this.waves = waves;
        this.sidebarW = sidebarW;

        // Initialise tile sprites from level + tileset
        tiles = new Tile[gridW][gridH];
        for (int x = 0; x < level.length; x++) {
            for (int y = 0; y < level[x].length; y++) {
                int i = level[x][y];
                tiles[x][y] = new Tile((x + 0.5f) * tSize, (y + 0.5f) * tSize, tileset[i], tile_names[i]);
                if (i == ALISTAIR_INDEX) {
                    alistair = tiles[x][y];
                }
            }
        }

        // Traverse the path and store direction values in a grid
        path = new int[gridW][gridH][2];
        int x = toGrid(startx), y = toGrid(starty);
        int i = defaultDir(x);
        int j = defaultDir(y);
        while (x < 0 || x >= gridW || y < 0 || y >= gridH) {
            x += i;
            y += j;
        }
        while (toPos(x) != alistair.getX() || toPos(y) != alistair.getY()) {
            // Move along the path
            // Check if we've hit a wall yet
            if (x + i < 0 || x + i >= gridW || y + j < 0 || y + j >= gridH || tiles[x + i][y + j].isWall()) {
                // OK, try turning left (anti-clockwise)
                int old_i = i;
                i = j;
                j = -old_i;

                // Check again
                if (tiles[x + i][y + j].isWall()) {
                    // Failed, turn right then (need to do a 180)
                    i = -i;
                    j = -j;
                }
            }
            // Update x, y and our direction
            path[x][y][0] = i;
            path[x][y][1] = j;
            x += i;
            y += j;
        }

        // Intro sound
        AudioController.play("intro");
    }

    /**
     * Deals with user input e.g. pressing Esc to return to main menu.
     * @param Obtained from App's GameContainer
     * @return A string for an action to take. (Empty string by default).
     */
    String processInput(Input input) {
        if (input.isKeyPressed(Input.KEY_ESCAPE)) {
            return "Exit";
        }
        return "";
    }
    
    /**
     * Keeps track of the time (in ms) from the start of the wave. Spawns enemies
     * and projectiles accordingly.
     * @param delta ms from last tick
     */
    void tick(int delta) {
        timer += delta;

        // Enemy spawning (based on the current wave)
        if (waveNum-1 < waves.size()) {
            Wave w = waves.get(waveNum-1);
            String enemy = w.trySpawn(timer);
            if (enemy != "") {
                spawnEnemy(startx, starty, enemy);
                if (w.isFinished()) {
                    newWave();
                }
            }
        }

        // Tower shots
        for (Tower t : towers) {
            if (t.countDown(delta)) {
                t.shoot(this);
            }
        }
    }

    /** Call every time a new wave starts */
    void newWave() {
        waveNum++;
        timer = 0;
        for (Tower t : towers) {
            t.waveReset();
        }
    }

    /** Create a new enemy at the given position */
    void spawnEnemy(float x, float y, String name) {
        Vector2f v = new Vector2f(defaultDir(x), defaultDir(y)).scale(enemySpeed);
        enemies.add(new Enemy(x, y, v, name));
    }

    /** Update enemy positons */
    void moveEnemies() {
        Iterator<Enemy> itr = enemies.iterator();
        while (itr.hasNext()) {
            Enemy e = itr.next();
            e.advance(enemySpeed, this);
            if (e.checkCollision(alistair)) {
                takeDamage(e.getDamage());
                itr.remove();
            }
        }
    }

    /** Update projectile positions */
    void moveProjectiles() {
        Iterator<Projectile> itr = projectiles.iterator();
        while (itr.hasNext()) {
            Projectile p = itr.next();
            p.advance();
            if (p.isOffScreen(w, h)) {
                itr.remove();
            }

            // Hitting enemies
            Iterator<Enemy> eItr = enemies.iterator();
            while (eItr.hasNext()) {
                Enemy e = eItr.next();
                if (p.checkCollision(e)) {
                    e.takeDamage(p.getDamage(), eItr);
                    itr.remove();
                    break;
                }
            }
        }
    }

    /** Handles placing towers */
    void processTowers(int mousex, int mousey, boolean clicked) {
        // If we're placing a tower, move it to the mouse position
        if (isPlacingTower()) {
            myTower.setColor(Color.white);
            myTower.teleport((float) mousex, (float) mousey);

            // Red if out of game bounds
            if (!inGridBounds(mousex/tSize, mousey/tSize)) {
                System.out.println(timer + "Out of bounds!");
                myTower.setColor(Color.red);
            }

            // Set the tower to be red if it's touching a non-wall tile or tower
            outer:
            for (Tile[] column : tiles) {
                for (Tile tile : column) {
                    if (!tile.isWall() && tile.checkCollision(myTower)) {
                        myTower.setColor(Color.red);
                        break outer;
                    }
                }
            }
            outer:
            for (Tower t : towers) {
                if (t.checkCollision(myTower)) {
                    myTower.setColor(Color.red);
                    break outer;
                }
            }
                
            // If the user clicked and it's not colliding with anything, place it
            if (clicked && myTower.getColor() == Color.white) {
                myTower.place(toPos(toGrid(mousex)), toPos(toGrid(mousey)));
                towers.add(myTower);
                myTower = null;
            }
        }
    }

    /** Create a new tower at the given position */
    void newTower(float xpos, float ypos) {
        try {
            myTower = new Tower(xpos, ypos, new Image("assets\\sprites\\alistair32.png"), 3000);
        } catch (SlickException e) {
            e.printStackTrace();
        }
    }

    /** Handles tower selection from the sidebar */
    void towerSelect(int mousex, int mousey, Boolean clicked) {
        // TODO: Some better mapping here, maybe use sprites and add an isClicked() method
        boolean inXRange = mousex >= w-(sidebarW/2)-(32/2) && mousex <= w-(sidebarW/2)+(32/2);
        boolean inYRange = mousey >= 100 && mousey <= 132;
        if (clicked && inXRange && inYRange) {
            newTower(mousex, mousey);
        }
    }

    /** Draw game interface */
    void drawGUI(Graphics g) {
        // Sidebar
        g.setColor(Color.darkGray);
        g.fillRect(w-sidebarW,0,sidebarW,h);

        g.setColor(Color.white);
        // Display Alistair's health
        Util.writeCentered(g, Integer.toString(health), alistair.getX(), alistair.getY());
        // Wave number
        Util.writeCentered(g, "Wave: " + waveNum,w-(sidebarW/2), 20);
        // Basic tower
        try {
            Image alistairTower = new Image("assets//sprites//alistair32.png");
            alistairTower.draw(w-(sidebarW/2)-(32/2), 100);
        } catch (SlickException e) {
            e.printStackTrace();
        }
    }

    void renderTiles() {
        for (Tile[] column : tiles) {
            for (Tile t : column) {
                t.drawSelf();
            }
        }
    }

    void renderEnemies() {
        for (Enemy e : enemies) {
            e.drawSelf();
        }
    }

    void renderTowers(Graphics g) {
        for (Tower t : towers) {
            t.drawSelf();
        }
        if (myTower != null) {
            myTower.drawSelf();
            myTower.drawRange(g);
        }
    }

    void renderProjectiles() {
        for (Projectile p : projectiles) {
            p.drawSelf();
        }
    }

    /** Make alistair take damage
     * @param damage Health reduction, <=100
     * */
    void takeDamage(int damage) {
        health -= damage;
        if (health <= 0) {
            System.out.println("we ded");
            AudioController.play("gameover");
            // TODO: add handling for game overs (SEGFAULTS!)
        }
    }

    /** Converts from literal position to position on grid */
    int toGrid(float pos) {
        // Choose closest grid position
        return Math.round((pos - tSize / 2) / tSize);
    }

    /** Converts from grid position to literal coordinates */
    float toPos(int gridval) {
        return gridval * tSize + tSize / 2;
    }

    /** Check coordinate against game boundaries */
    boolean inGridBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < gridW && y < gridH;
    }

    /** Returns a grid direction to point inwards from the current position */
    int defaultDir(int gridval) {
        return gridval < 0 ? 1 : (gridval >= gridW ? -1 : 0);
    }

    /** Returns a literal direction to point inwards */
    float defaultDir(float pos) {
        return pos < 0 ? 1 : (pos >= gridW * tSize ? -1 : 0);
    }

    boolean isPlacingTower() {
        return myTower != null;
    }

    /** Create a new projectile
     * @param x Initial x-coord
     * @param y Initial y-coord
     * @param vec Initial movement vector
     * @param im Sprite image
     */
    void newProjectile(float x, float y, Vector2f vec, Image im) {
        projectiles.add(new Projectile(x, y, vec, im));
    }

    void setWaves(ArrayList<Wave> waves) {
        this.waves = waves;
    }

    int getGridWidth() { return gridW; }
    int getGridHeight() { return gridH; }
    int getTileSize() { return tSize; }
    Tile getTile(int x, int y) { return tiles[x][y]; }
    int getPathXDir(int x, int y) { return path[x][y][0]; }
    int getPathYDir(int x, int y) { return path[x][y][1]; }
    List<Enemy> getEnemies() { return Collections.unmodifiableList(enemies); }
    List<Projectile> getProjectiles() { return Collections.unmodifiableList(projectiles); }
}
