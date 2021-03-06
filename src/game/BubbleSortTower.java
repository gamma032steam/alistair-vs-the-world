package game;

import org.newdawn.slick.Image;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.geom.Vector2f;

import control.World;

public class BubbleSortTower extends Tower {
    public BubbleSortTower(float x, float y, Type type, World world) throws SlickException {
        super(x, y, type, world);
    }

    @Override
    protected void shoot(Vector2f dir) throws SlickException {
        world.addProjectile(new Bubble(world, getX(), getY()));
    }
    
    private static class Bubble extends Projectile {
        private static final int DAMAGE = 1;
        private static final float SCALE_INCR = 0.015f, SCALE_MAX = 0.7f;
        
        public Bubble(World world, float startX, float startY) throws SlickException {
            super(world, startX, startY, new Vector2f(0, 0), new Image(Projectile.SPRITE_PATH + "bubble-original.png"), DAMAGE);
            setScale(.1f);
        }
        
        @Override
        public void advance() {
            super.advance();
            setScale(getScale() + SCALE_INCR);
        }
        
        @Override
        public boolean isDead() {
            return super.isDead() || getScale() >= SCALE_MAX;
        }

        @Override
        public void pop() {
            getWorld().play("bubblepop");
        }
    }
}
