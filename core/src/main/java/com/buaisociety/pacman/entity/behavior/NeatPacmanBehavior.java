package com.buaisociety.pacman.entity.behavior;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.buaisociety.pacman.entity.Direction;
import com.buaisociety.pacman.entity.Entity;
import com.buaisociety.pacman.entity.FruitEntity;
import com.buaisociety.pacman.entity.PacmanEntity;
import com.buaisociety.pacman.maze.Searcher;
import com.buaisociety.pacman.maze.Tile;
import com.buaisociety.pacman.maze.TileState;
import com.cjcrafter.neat.Client;

public class NeatPacmanBehavior implements Behavior {

    private final @NotNull Client client;
    private @Nullable PacmanEntity pacman;

    // Score modifiers help us maintain "multiple pools" of points.
    // This is great for training, because we can take away points from
    // specific pools of points instead of subtracting from all.
    private int scoreModifier = 0;
    private int lastScore = 0; // Add this line to declare lastScore
    private int numberUpdatesSinceLastScore = 0; // Add this line to declare numberUpdatesSinceLastScore


    public NeatPacmanBehavior(@NotNull Client client) {
        this.client = client;
    }

    /**
     * Returns the desired direction that the entity should move towards.
     *
     * @param entity the entity to get the direction for
     * @return the desired direction for the entity
     */
    @NotNull
    @Override
    public Direction getDirection(@NotNull Entity entity) {
        if (pacman == null) {
            pacman = (PacmanEntity) entity;
        }

        // SPECIAL TRAINING CONDITIONS
        int newScore = pacman.getMaze().getLevelManager().getScore();
        if (newScore > lastScore) {
            lastScore = newScore;
            scoreModifier += 10;
            numberUpdatesSinceLastScore = 0;
        }

        if (numberUpdatesSinceLastScore++ > 60 * 10){
            pacman.kill();
            return Direction.UP;
        }
        if (numberUpdatesSinceLastScore > 10 * 10){
            scoreModifier -= 1;
        }
        int scoreDifference = newScore-lastScore;
        if (scoreDifference == 100 || scoreDifference == 300 || scoreDifference == 500 || scoreDifference == 700 ||
        scoreDifference == 1000 || scoreDifference == 2000 || scoreDifference == 3000 || scoreDifference == 5000) {
            scoreModifier += 20;
        }

        // TODO: Make changes here to help with your training...
        // END OF SPECIAL TRAINING CONDITIONS

        // We are going to use these directions a lot for different inputs. Get them all once for clarity and brevity
        Direction forward = pacman.getDirection();
        Direction left = pacman.getDirection().left();
        Direction right = pacman.getDirection().right();
        Direction behind = pacman.getDirection().behind();

        // Input nodes 1, 2, 3, and 4 show if the pacman can move in the forward, left, right, and behind directions
        boolean canMoveForward = pacman.canMove(forward);
        boolean canMoveLeft = pacman.canMove(left);
        boolean canMoveRight = pacman.canMove(right);
        boolean canMoveBehind = pacman.canMove(behind);

        // boolean if fruit exists or not
        boolean fruitExists = false;
        for (Entity mazeEntity : pacman.getMaze().getEntities()){
            if (mazeEntity instanceof FruitEntity) {
                fruitExists = true;
            }
        }

        //BFS 
        Tile currentTile = pacman.getMaze().getTile(pacman.getTilePosition());
        Map<Direction, Searcher.SearchResult> nearestPellets = Searcher.findTileInAllDirections(currentTile, tile -> tile.getState() == TileState.PELLET);
        Map<Direction, Searcher.SearchResult> nearestFruits = Searcher.findTileInAllDirections(currentTile, tile -> 
            pacman.getMaze().getEntities().stream()
                .anyMatch(e -> e instanceof FruitEntity && e.getTilePosition().equals(tile.getPosition()))
        );

        // Determine the direction with the closest pellet
        Direction closestPelletDirection = null;
        int closestPelletDistance = Integer.MAX_VALUE;

        for (Map.Entry<Direction, Searcher.SearchResult> entry : nearestPellets.entrySet()) {
            Searcher.SearchResult result = entry.getValue();
            if (result != null && result.getDistance() < closestPelletDistance) {
                closestPelletDistance = result.getDistance();
                closestPelletDirection = entry.getKey();
            }
        }

        // Determine the direction with the closest fruit
        Direction closestFruitDirection = null;
        int closestFruitDistance = Integer.MAX_VALUE;

        for (Map.Entry<Direction, Searcher.SearchResult> entry : nearestFruits.entrySet()) {
            Searcher.SearchResult result = entry.getValue();
            if (result != null && result.getDistance() < closestFruitDistance) {
                closestFruitDistance = result.getDistance();
                closestFruitDirection = entry.getKey();
            }
        }

        // BFS to find the nearest tunnel
        Map<Direction, Searcher.SearchResult> nearestTunnels = Searcher.findTileInAllDirections(currentTile, tile -> tile.getState() == TileState.TUNNEL);

        // Determine the direction with the closest tunnel
        Direction closestTunnelDirection = null;
        int closestTunnelDistance = Integer.MAX_VALUE;

        for (Map.Entry<Direction, Searcher.SearchResult> entry : nearestTunnels.entrySet()) {
            Searcher.SearchResult result = entry.getValue();
            if (result != null && result.getDistance() < closestTunnelDistance) {
                closestTunnelDistance = result.getDistance();
                closestTunnelDirection = entry.getKey();
            }
        }

        float[] inputs = new float[8]; // Adjust size based on the number of inputs
        inputs[0] = canMoveForward ? 1f : 0f; // Forward
        inputs[1] = canMoveLeft ? 1f : 0f;    // Left
        inputs[2] = canMoveRight ? 1f : 0f;   // Right
        inputs[3] = canMoveBehind ? 1f : 0f;  // Behind
        inputs[4] = closestPelletDirection != null ? switch (closestPelletDirection) {
            case UP -> 1f;
            case LEFT -> 2f;
            case RIGHT -> 3f;
            case DOWN -> 4f;
        } : 0f;
        inputs[5] = fruitExists ? 1f : 0f;
        inputs[6] = closestFruitDirection != null ? switch (closestFruitDirection) {
            case UP -> 1f;
            case LEFT -> 2f;
            case RIGHT -> 3f;
            case DOWN -> 4f;
        } : 0f;
        inputs[7] = closestTunnelDirection != null ? switch (closestTunnelDirection) {
            case UP -> 1f;
            case LEFT -> 2f;
            case RIGHT -> 3f;
            case DOWN -> 4f;
        } : 0f;

        // Use the closest direction for the output
        float[] outputs = client.getCalculator().calculate(inputs).join();


        int index = 0;
        float max = outputs[0];
        for (int i = 1; i < outputs.length; i++) {
            if (outputs[i] > max) {
                max = outputs[i];
                index = i;
            }
        }

        // Determine the new direction
        Direction newDirection;

        // Prioritize tunnels if a direction to it is available
        if (closestTunnelDirection != null) {
            newDirection = closestTunnelDirection;
        } else if (closestFruitDirection != null) {
            newDirection = closestFruitDirection;
        } else if (closestPelletDirection != null) {
            newDirection = closestPelletDirection;
        } else {
            newDirection = switch (index) {
                case 0 -> pacman.getDirection();
                case 1 -> pacman.getDirection().left();
                case 2 -> pacman.getDirection().right();
                case 3 -> pacman.getDirection().behind();
                default -> throw new IllegalStateException("Unexpected value: " + index);
            };
        }

        // Special training condition: Increase scoreModifier when moving towards tunnels
        if (newDirection == closestTunnelDirection) {
            scoreModifier += 15; // Increase the scoreModifier when moving towards a tunnel
        } else if (newDirection == closestFruitDirection) {
            scoreModifier += 20;
        } else if (newDirection == closestPelletDirection) {
            scoreModifier += 10;
        } else {
            scoreModifier -= 1;
        }

        if (pacman.canMove(newDirection)) {
            pacman.move(newDirection, 1.0, true);
        }

        client.setScore(pacman.getMaze().getLevelManager().getScore() + scoreModifier);
        return newDirection;

        












    }

    @Override
    public void render(@NotNull SpriteBatch batch) {
        // TODO: You can render debug information here
        /*
        if (pacman != null) {
            DebugDrawing.outlineTile(batch, pacman.getMaze().getTile(pacman.getTilePosition()), Color.RED);
            DebugDrawing.drawDirection(batch, pacman.getTilePosition().x() * Maze.TILE_SIZE, pacman.getTilePosition().y() * Maze.TILE_SIZE, pacman.getDirection(), Color.RED);
        }
         */
    }
}
