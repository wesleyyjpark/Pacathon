package com.buaisociety.pacman.entity.behavior;
import com.buaisociety.pacman.maze.Searcher;
import com.buaisociety.pacman.maze.Tile;
import com.buaisociety.pacman.maze.TileState;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.buaisociety.pacman.maze.Maze;
import com.buaisociety.pacman.sprite.DebugDrawing;
import com.cjcrafter.neat.Client;
import com.buaisociety.pacman.entity.Direction;
import com.buaisociety.pacman.entity.Entity;
import com.buaisociety.pacman.entity.GhostEntity;
import com.buaisociety.pacman.entity.GhostState;
import com.buaisociety.pacman.entity.PacmanEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Map;
import com.buaisociety.pacman.maze.Pair;
import java.util.Optional;

public class NeatPacmanBehavior implements Behavior {

    private final @NotNull Client client;
    private @Nullable PacmanEntity pacman;

    // Score modifiers help us maintain "multiple pools" of points.
    // This is great for training, because we can take away points from
    // specific pools of points instead of subtracting from all.
    private int scoreModifier = 0;
    private int lastScore = 0; // Add this line to declare lastScore
    private int numberUpdatesSinceLastScore = 0; // Add this line to declare numberUpdatesSinceLastScore
    Direction newDirection;

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

        //check if ghost is nearby
        Pair<Boolean, Direction> ghostInfo = Searcher.isGhostNearby(pacman, 4.0); // Check within a distance of 4.0
        boolean ghostNearby = ghostInfo.getKey();
        Direction ghostDirection = ghostInfo.getValue();


        

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

        if (ghostNearby) {
            // If the ghost is frightened, move towards it; otherwise, move away
            Optional<GhostEntity> frightenedGhost = pacman.getMaze().getEntities().stream()
                .filter(e -> e instanceof GhostEntity)
                .map(e -> (GhostEntity) e) // Cast to GhostEntity
                .filter(ghost -> ghost.getState() == GhostState.FRIGHTENED)
                .findFirst();
            if (frightenedGhost.isPresent()) {
                // Move towards the frightened ghost
                Direction directionTowardsGhost = Searcher.getDirectionTo(pacman.getPosition(), frightenedGhost.get().getPosition());
                newDirection = directionTowardsGhost; // Update newDirection to move towards the frightened ghost
            } else {
                // Move away from the ghost
                newDirection = Searcher.getDirectionAway(pacman.getPosition(), pacman.getMaze().getEntities().stream()
                    .filter(e -> e instanceof GhostEntity)
                    .map(e -> (GhostEntity) e) // Cast to GhostEntity
                    .filter(ghost -> ghost.getState() == GhostState.CHASE) // Change to NORMAL state
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No normal ghost found"))
                    .getPosition());
            }
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


        //BFS 
        Tile currentTile = pacman.getMaze().getTile(pacman.getTilePosition());
        Map<Direction, Searcher.SearchResult> nearestPellets = Searcher.findTileInAllDirections(currentTile, tile -> tile.getState() == TileState.PELLET);

        // Determine the direction with the closest pellet
        Direction closestDirection = null;
        int closestDistance = Integer.MAX_VALUE;

        for (Map.Entry<Direction, Searcher.SearchResult> entry : nearestPellets.entrySet()) {
            Searcher.SearchResult result = entry.getValue();
            if (result != null && result.getDistance() < closestDistance) {
                closestDistance = result.getDistance();
                closestDirection = entry.getKey();
            }
        }

        float[] inputs = new float[5]; // Adjust size based on the number of inputs
        inputs[0] = canMoveForward ? 1f : 0f; // Forward
        inputs[1] = canMoveLeft ? 1f : 0f;    // Left
        inputs[2] = canMoveRight ? 1f : 0f;   // Right
        inputs[3] = canMoveBehind ? 1f : 0f;  // Behind
        inputs[4] = closestDirection != null ? switch (closestDirection) {
            case UP -> 1f;    // Closest direction is UP
            case LEFT -> 2f;  // Closest direction is LEFT
            case RIGHT -> 3f; // Closest direction is RIGHT
            case DOWN -> 4f;  // Closest direction is DOWN
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

        newDirection = closestDirection != null ? closestDirection : switch (index) {
            case 0 -> pacman.getDirection();
            case 1 -> pacman.getDirection().left();
            case 2 -> pacman.getDirection().right();
            case 3 -> pacman.getDirection().behind();
            default -> throw new IllegalStateException("Unexpected value: " + index);
        };

        if (newDirection == closestDirection) {
            scoreModifier += 10; // Adjust the increment value as needed
        } else {
            scoreModifier -= 1; // Penalize for not moving towards the closest direction
        }

        if (pacman.canMove(newDirection)) {
            pacman.move(newDirection, 1.0, true); // Adjust the double and boolean values as needed
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
