package com.github.q11hackermans.slakeoverflow_server;

import com.github.q11hackermans.slakeoverflow_server.connections.ServerConnection;
import com.github.q11hackermans.slakeoverflow_server.constants.ConnectionType;
import com.github.q11hackermans.slakeoverflow_server.constants.Direction;
import com.github.q11hackermans.slakeoverflow_server.constants.FieldState;
import com.github.q11hackermans.slakeoverflow_server.game.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class GameSession {
    private final List<Snake> snakeList;
    private final List<Item> itemList;
    private final int borderX;
    private final int borderY;
    private final int fovsizeX;
    private final int fovsizeY;

    public GameSession(int x, int y) {
        this.snakeList = new ArrayList<>();
        this.itemList = new ArrayList<>();
        this.borderX = x;
        this.borderY = y;
        this.fovsizeX = 50;
        this.fovsizeY = 50;
    }

    // TICK
    public void tick() {
        // CHECK IF SNAKE IS ALIVE
        this.checkSnakes();

        // RUNNING TICK ON SNAKES
        for(Snake snake : this.snakeList) {
            snake.tick();
        }
        this.spawnFood(this.calcFoodSpawnTries());

        // SENDING PLAYERDATA TO SNAKES
        for(Snake snake : this.snakeList) {
            try {
                snake.getConnection().getDataIOStreamHandler().writeUTF(this.getSendablePlayerData(snake));
            } catch(Exception e) {
                SlakeoverflowServer.getServer().getLogger().warning("CONNECTION", "Error while sending data to " + snake.getConnection().getClientId());
                snake.getConnection().getClient().close();
            }
        }

        // ADD NEW SNAKES
        this.addNewSnakes();
    }

    /**
     * Return a number to use in the spawnFood function depending on the player-count
     */
    private int calcFoodSpawnTries() {
        if (randomIntInRange(1, 3) == 1) {
            return (int) (1 + Math.round(0.2 * snakeList.size()));
        }
        return 0;
    }

    /**
     * Tries X-times to spawn food with the value of 1-3 if the field is free
     * @param tries Times the Server tries to spawn food
     */
    private void spawnFood(int tries){
        for (int i = tries; i > 0; i--) {
            int[] rPos = this.posInRandomPlayerFov();
            int posX = rPos[0];
            int posY = rPos[1];

            if (isFree(posX, posY)) {
                this.itemList.add(new Food(posX, posY, new Random().nextInt(SlakeoverflowServer.getServer().getConfigManager().getConfig().getMaxFoodValue() - SlakeoverflowServer.getServer().getConfigManager().getConfig().getMinFoodValue()) + SlakeoverflowServer.getServer().getConfigManager().getConfig().getMinFoodValue()));
            }
        }
    }

    /**
     * Tries to spawn a SuperFood with this value at this field
     *
     * @param value Value of the super food to be spawned
     * @param posX  Position X where the super-food is spawned
     * @param posY  Position Y where the super-food is spawned
     */
    public void spawnSuperFoodAt(int value, int posX, int posY) {
        if (isFree(posX, posY)) {
            this.itemList.add(new SuperFood(posX, posY, value));
        }
    }

    /**
     * Returns a random number on the fields x-axis
     *
     * @return Random x coordinate
     */
    private int randomPosX() {
        return (int) ((Math.random() * ((this.borderX - 1) - 1)) + 1);
    }

    /**
     * Returns a random number on the fields y-axis
     * @return Random y coordinate
     */
    private int randomPosY(){
        return (int) ((Math.random() * ((this.borderY - 1) - 1)) + 1);
    }

    private void checkSnakes() {
        this.snakeList.removeIf(snake -> !snake.isAlive());
    }

    private void addNewSnakes() {
        for(ServerConnection connection : SlakeoverflowServer.getServer().getConnectionList()) {
            if(connection.isConnected() && connection.getConnectionType() == ConnectionType.PLAYER && this.getSnakeOfConnection(connection) == null) {
                int posX = this.randomPosX();
                int posY = this.randomPosY();

                int length = SlakeoverflowServer.getServer().getConfigManager().getConfig().getDefaultSnakeLength();

                int count = 3;
                while(count > 0) {
                    if(this.isAreaFree(posX, posY, length*2)) {
                        this.snakeList.add(new Snake(connection, posX, posY, Direction.NORTH, length, this));
                        count = 0;
                        break;
                    } else {
                        count --;
                    }
                }
            }
        }
    }

    /**
     * This method returns the final String (in JSONObject format) which is ready for sending to the player.
     * @param snake The snake
     * @return String (in JSONObject format)
     */
    private String getSendablePlayerData(Snake snake) {
        JSONObject playerData = new JSONObject();
        playerData.put("cmd", "playerdata");
        playerData.put("fovx", this.fovsizeX);
        playerData.put("fovy", this.fovsizeY);

        JSONArray fields = new JSONArray();

        int minY = snake.getPosY() - this.fovsizeY;
        int maxY = snake.getPosY() + this.fovsizeY;

        if(minY < 0) {
            minY = 0;
        }
        if(maxY > this.borderY) {
            maxY = this.borderY;
        }

        int relativey = -1;
        for(int iy = minY-1; iy <= maxY; iy++) {
            int minX = snake.getPosX() - this.fovsizeX;
            int maxX = snake.getPosX() + this.fovsizeX;

            if(minX < 0) {
                minX = 0;
            }
            if(maxX > this.borderX) {
                maxX = this.borderX;
            }

            int relativex = -1;
            for(int ix = minX-1; ix <= maxX; ix++) {
                if(ix == -1) {
                    fields.put(this.createCoordsJSONArray(false, ix, iy, FieldState.BORDER));
                } else if(ix == this.borderX) {
                    fields.put(this.createCoordsJSONArray(false, ix, iy, FieldState.BORDER));
                } else if(iy == -1) {
                    fields.put(this.createCoordsJSONArray(false, ix, iy, FieldState.BORDER));
                } else if(iy == this.borderY) {
                    fields.put(this.createCoordsJSONArray(false, ix, iy, FieldState.BORDER));
                } else {
                    GameObject field = this.getField(ix, iy);
                    if(field instanceof Snake) {
                        if(field == snake) {
                            if(Arrays.equals(field.getPos(), new int[]{ix, iy})) {
                                fields.put(this.createCoordsJSONArray(false, ix, iy, FieldState.PLAYER_HEAD_OWN));
                            } else {
                                fields.put(this.createCoordsJSONArray(false, ix, iy, FieldState.PLAYER_BODY_OWN));
                            }
                        } else {
                            if(Arrays.equals(field.getPos(), new int[]{ix, iy})) {
                                fields.put(this.createCoordsJSONArray(false, ix, iy, FieldState.PLAYER_HEAD_OTHER));
                            } else {
                                fields.put(this.createCoordsJSONArray(false, ix, iy, FieldState.PLAYER_BODY_OTHER));
                            }
                        }
                    } else if(field instanceof Item) {
                        if(field instanceof Food) {
                            fields.put(this.createCoordsJSONArray(false, ix, iy, FieldState.ITEM_FOOD));
                        } else if(field instanceof SuperFood) {
                            fields.put(this.createCoordsJSONArray(false, ix, iy, FieldState.ITEM_SUPER_FOOD));
                        } else {
                            // DO NOTHING
                            //fields.put(this.createCoordsJSONArray(false, ix, iy, FieldState.ITEM_UNKNOWN));
                        }
                    } else {
                        // DO NOTHING
                        //fields.put(this.createCoordsJSONArray(false, ix, iy, FieldState.EMPTY));
                    }
                }

                relativex++;
            }

            relativey++;
        }

        playerData.put("fields", fields);

        return playerData.toString();
    }

    /**
     * Creates a JSONArray [0,0,0,0]
     * 1. Value: absolute/relative
     * 2. Value: X-Coordinates
     * 3. Value: Y-Coordinates
     * 4. Value: FieldState
     * @param relative Absolute/relative coordinates
     * @param x X-Coordinates
     * @param y Y-Coordinates
     * @param fieldState FieldState
     * @return JSONArray
     */
    private JSONArray createCoordsJSONArray(boolean relative, int x, int y, int fieldState) {
        JSONArray jsonArray = new JSONArray();
        if(relative) {
            jsonArray.put(1);
        } else {
            jsonArray.put(0);
        }
        jsonArray.put(x);
        jsonArray.put(y);
        jsonArray.put(fieldState);
        return jsonArray;
    }

    // FIELD MANAGEMENT

    /**
     * Returns true if the specified field is free
     *
     * @param posX Position X
     * @param posY Position Y
     * @return boolean
     */
    public boolean isFree(int posX, int posY) {
        return this.getField(posX, posY) == null;
    }

    /**
     * Returns an integer array with a position within a random players FOV or [-1|-1] if there is no free field within the FOV or no player.
     *
     * @return int[]
     */
    private int[] posInRandomPlayerFov() {
        if (snakeList.size() > 0) {
            Snake rSnake = snakeList.get(randomIntInRange(0, snakeList.size() - 1));
            int[] randomSnakePos = new int[]{rSnake.getPosX(), rSnake.getPosY()};

            for (int i = 0; i < 20; i++) {
                int rPosX = this.randomIntInRange(randomSnakePos[0] - Math.round(this.fovsizeX / 2), randomSnakePos[0] + Math.round(this.fovsizeX / 2));
                int rPosY = this.randomIntInRange(randomSnakePos[1] - Math.round(this.fovsizeY / 2), randomSnakePos[1] + Math.round(this.fovsizeY / 2));
                if (isFree(rPosX, rPosY)) {
                    return new int[]{rPosX, rPosY};
                }
            }
        }
        return new int[]{-1, -1};
    }

    /**
     * Returns if a specific area is free.
     * The x and y coordinates are the center field coordinates.
     *
     * @param x    center field x
     * @param y    center field y
     * @param area area
     * @return boolean
     */
    public boolean isAreaFree(int x, int y, int area) {
        if(x < 0 || x >= this.borderX) {
            return false;
        }
        if(y < 0 || y >= this.borderY) {
            return false;
        }

        int left = x - Math.round(area/2);
        int top = y - Math.round(area/2);

        if(left < 0) {
            left = 0;
        }
        if(top < 0) {
            top = 0;
        }

        int right = x + Math.round(area/2);
        int bottom = y + Math.round(area/2);

        if(right >= this.borderX) {
            right = this.borderX - 1;
        }
        if(bottom >= this.borderY) {
            bottom = this.borderY - 1;
        }

        for(int iy = top; iy < bottom; iy++) {
            for(int ix = left; ix < right; ix++) {
                if(!this.isFree(ix, iy)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns true if the specified field is not another player
     * @param posX Position X
     * @param posY Position Y
     * @return boolean
     */
    public boolean isOtherPlayerFree(int posX, int posY, Snake snake) {
        GameObject fieldObject = this.getField(posX,posY);
        return (!(fieldObject instanceof Snake) || fieldObject == snake);
    }

    /**
     * Returns if the specified field is a food item
     * @param posX Position X
     * @param posY Position Y
     * @return boolean (true = is a food item)
     */
    public boolean hasApple(int posX, int posY) {
        GameObject gameObject = this.getField(posX, posY);
        return (gameObject instanceof Food);
    }

    /**
     * Returns the food value if food is on the specified field.
     * Returns 0 if there is no food on the specified field.
     *
     * @param posX Position X
     * @param posy Position Y
     * @return int (!=0 when food, =0 when no food)
     */
    public int getFoodValue(int posX, int posy) {
        GameObject gameObject = this.getField(posX, posy);
        if (gameObject instanceof Food) {
            return ((Food) gameObject).getFoodValue();
        } else if (gameObject instanceof SuperFood) {
            return ((SuperFood) gameObject).getValue();
        } else {
            return 0;
        }
    }

    /**
     * Returns the GameObject that is on the specified field.
     * If the specified field is empty, this will return null.
     * @param posX Position X
     * @param posY Position Y
     * @return GameObject
     */
    public GameObject getField(int posX, int posY) {
        for(Snake snake : this.snakeList) {
            if(snake.getPosX() == posX && snake.getPosY() == posY) {
                return snake;
            } else {
                for(int[] pos : snake.getBodyPositions()) {
                    if(pos[0] == posX && pos[1] == posY) {
                        return snake;
                    }
                }
            }
        }
        for (Item item : this.itemList) {
            if (item.getPosX() == posX && item.getPosY() == posY) {
                return item;
            }
        }
        return null;
    }

    /**
     * Returns a random int from 1 to the upper bound
     *
     * @param upperBound upper bound of the random int
     * @return int
     */
    private int randomIntInRange(int lowerBound, int upperBound) throws IllegalArgumentException {

        if (lowerBound >= upperBound) {
            IllegalArgumentException e = new IllegalArgumentException("The upper bound is smaller than the lower bound");
            SlakeoverflowServer.getServer().getLogger().warning("RANDOM-INT-GENERATOR", "Exception: " + e);
            throw e;
        }
        return new Random().nextInt((upperBound - lowerBound) + 1) + lowerBound;
    }

    /**
     * Get the world border
     * The world border is from X: 0-getBorder()[0], Y: 0-getBorder()[1].
     *
     * @return int[]{posX, posY}
     */
    public int[] getBorder() {
        return new int[]{this.borderX, this.borderY};
    }

    // SNAKE MANAGEMENT

    /**
     * Returns the snake of a specific player
     * @param connection Player
     * @return Snake
     */
    public Snake getSnakeOfConnection(ServerConnection connection) {
        for(Snake snake : this.snakeList) {
            if(snake.getConnection() == connection) {
                return snake;
            }
        }
        return null;
    }

    /**
     * Get a copy of the snake list.
     * @return copy of snake list
     */
    public List<Snake> getSnakeList() {
        return List.copyOf(this.snakeList);
    }

    /**
     * Get a copy of the item list.
     * @return copy of item list
     */
    public List<Item> getItemList() {
        return List.copyOf(this.itemList);
    }
}
