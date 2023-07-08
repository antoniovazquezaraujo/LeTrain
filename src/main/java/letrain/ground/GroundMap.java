package letrain.ground;

import letrain.map.Point;
import letrain.visitor.Visitor;

public class GroundMap implements letrain.map.GroundMap{
    public static final int GROUND = 0;
    public static final int MOUNTAIN = 1;
	private static final int FIXED_VIRTUAL_WIDTH = 1000;
    private static int SMOOTHING_STEPS = 5;    
    private int[][] terrain;
    private int width;
    private int height;

    public void update(int startX, int startY, int width, int height){
        this.height = height;
        this.width = width;
        initializeBlock(startX,startY, width, height);
        for (int i = 0; i < SMOOTHING_STEPS; i++) {
            smoothBlock(startX, startY, width, height);
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (terrain[startX+x][startY+y] == 1) {
                    set(new Point(x, y), MOUNTAIN);
                }else{
                    set(new Point(x, y), GROUND);
                }
            }
        }
    }

    private void initializeBlock(int startX, int startY, int width, int height) {       
        terrain = new int[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                long seed = ((x+startX) * FIXED_VIRTUAL_WIDTH) + y+startY;  
                long nextDouble = ((seed * 1103515245L + 12345L) & 0x7fffffffL) % (seed+1);
                terrain[x][y] = nextDouble < (seed/3) ? 1 : 0;
            }
        }
    }

    private void smoothBlock(int startX, int startY, int width, int height) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (terrain[x][y] == 0) {
                    int wallNeighbors = countWallNeighborsInBounds(x, y, startX, startY, width, height);
                    terrain[x][y] = wallNeighbors >= 5 ? 1 : 0;
                }
            }
        }
    }

    private int countWallNeighborsInBounds(int x, int y, int startX, int startY, int width, int height) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    count += terrain[nx][ny];
                } else {
                    // Consider out-of-bounds as walls
                    count++;
                }
            }
        }
        return count;
    }

	@Override
	public void accept(Visitor visitor) {
        Ground ground = new Ground();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                ground.setPosition(new Point(x, y));
                ground.setType(terrain[x][y]== 0? Ground.GroundType.GROUND : Ground.GroundType.MOUNTAIN);
                visitor.visitGround(ground);
            }
        }
	}

	@Override
	public int getWidth() {
        return this.height;
	}

	@Override
	public int getHeight() {
        return this.width;
	}

	@Override
	public int get(int x, int y) {
        return terrain[x][y];
	}

	@Override
	public int get(Point pos) {
        return terrain[pos.getX()][pos.getY()];
	}

	@Override
	public void set(int x, int y, int value) {
        terrain[x][y] = value;
	}

	@Override
	public void set(Point pos, int value) {
        terrain[pos.getX()][pos.getY()] = value;
	}

}