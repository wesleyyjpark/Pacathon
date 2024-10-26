package com.buaisociety.pacman.maze;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector2d;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import com.buaisociety.pacman.entity.Direction;
import com.buaisociety.pacman.entity.Entity;
import com.buaisociety.pacman.entity.FruitEntity;
import com.buaisociety.pacman.entity.GhostEntity;
import com.buaisociety.pacman.entity.GhostState;
import com.buaisociety.pacman.entity.PacmanEntity;

public class Searcher {

    /**
     * Stores the result of the BFS search, including the found tile, the distance from the start tile, and the direction.
     */
    public static class SearchResult {
        private final Tile tile;
        private final int distance;
        private final Direction direction; // Add direction field

        public SearchResult(Tile tile, int distance, Direction direction) { // Update constructor
            this.tile = tile;
            this.distance = distance;
            this.direction = direction; // Set direction
        }

        public Tile getTile() {
            return tile;
        }

        public int getDistance() {
            return distance;
        }

        public Direction getDirection() { // Add getter for direction
            return direction;
        }
    }

    /**
     * Performs a BFS search in each of the four directions to find tiles matching the predicate, containing a FruitEntity, or being a tunnel.
     *
     * @param startTile The starting tile for the BFS.
     * @param predicate The predicate to test each tile.
     * @return A Map containing the first matching tile and distance for each direction.
     */
    public static Map<Direction, SearchResult> findTileInAllDirections(@NotNull Tile startTile, @NotNull Predicate<Tile> predicate) {
        Map<Direction, SearchResult> results = new EnumMap<>(Direction.class);

        for (Direction direction : Direction.values()) {
            SearchResult result = findTileWithBFS(startTile, tile -> {
                // Check if the tile matches the predicate or contains a FruitEntity
                boolean matchesPredicate = predicate.test(tile);
                boolean containsFruit = startTile.getMaze().getEntities().stream()
                    .anyMatch(entity -> entity instanceof FruitEntity && entity.getTilePosition().equals(tile.getPosition()));
                return matchesPredicate || containsFruit;
            }, direction);

            if (result != null) {
                results.put(result.getDirection(), result);
            }
        }

        return results;
    }

    /**
     * Performs a BFS to find the closest tile in the specified direction that matches the predicate.
     *
     * @param startTile The starting tile for the BFS.
     * @param predicate The predicate to test each tile.
     * @param direction The initial direction for the search.
     * @return The SearchResult containing the tile, distance, and direction, or null if no matching tile is found.
     */
    public static SearchResult findTileWithBFS(@NotNull Tile startTile, @NotNull Predicate<Tile> predicate, @NotNull Direction direction) {
        Queue<Vector2ic> queue = new ArrayDeque<>();
        Set<Vector2ic> visited = new HashSet<>();
        Queue<Integer> distances = new ArrayDeque<>();

        Vector2i startPosition = new Vector2i(startTile.getPosition()).add(getDirectionOffset(direction));
        queue.add(startPosition);
        visited.add(startPosition);
        distances.add(1);

        while (!queue.isEmpty()) {
            Vector2ic currentPos = queue.poll();
            int currentDistance = distances.poll();
            Tile currentTile = startTile.getMaze().getTile(new Vector2i(currentPos));

            if (predicate.test(currentTile)) {
                return new SearchResult(currentTile, currentDistance, direction); // Return direction
            }

            // Enqueue neighboring tiles only in the initial search direction
            for (Vector2i neighbor : getNeighborsInDirection(new Vector2i(currentPos), direction)) {
                Tile neighborTile = startTile.getMaze().getTile(neighbor);
                if (neighborTile.getState() == TileState.WALL) continue;

                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                    distances.add(currentDistance + 1);
                }
            }
        }

        return null;  // No tile matching the predicate was found
    }

    /**
     * Returns the offset vector based on the initial direction.
     *
     * @param direction The direction for the offset.
     * @return The vector offset for moving in that direction.
     */
    public static Vector2i getDirectionOffset(Direction direction) {
        return switch (direction) {
            case UP -> new Vector2i(0, 1);
            case DOWN -> new Vector2i(0, -1);
            case LEFT -> new Vector2i(-1, 0);
            case RIGHT -> new Vector2i(1, 0);
        };
    }

    /**
     * Returns neighboring tiles based on the given initial direction.
     *
     * @param position The tile position for which to get neighbors.
     * @param direction The primary direction for the search.
     * @return A list of neighboring tile positions limited to the specified direction.
     */
    public static List<Vector2i> getNeighborsInDirection(Vector2i position, Direction direction) {
        List<Vector2i> neighbors = new ArrayList<>();
        Vector2i offset = getDirectionOffset(direction);
        neighbors.add(new Vector2i(position.x + offset.x, position.y + offset.y));
        return neighbors;
    }

    /**
     * Checks if there are any ghosts nearby within a certain distance.
     * If power pellets are eaten (indicated by score increase), ghosts will be scared and move towards them.
     *
     * @param pacman The PacmanEntity to check against.
     * @param distance The maximum distance to check for ghosts.
     * @return A pair containing a boolean indicating if a ghost is nearby and the direction towards or away from the ghost.
     */
    public static Pair<Boolean, Direction> isGhostNearby(PacmanEntity pacman, double distance) {
        Maze maze = pacman.getMaze();
        int currentScore = maze.getLevelManager().getScore(); // Get the current score

        // Temporary variable to store the previous score
        int previousScore = currentScore; // Store the current score as previous for the next check

        // Check if the score has increased by 50
        boolean hasEatenPowerPellet = (currentScore - previousScore) >= 50;

        GhostEntity nearestGhost = findNearestGhost(pacman);
        if (nearestGhost != null) {
            double ghostDistance = nearestGhost.getPosition().distance(pacman.getPosition());
            if (ghostDistance <= distance) {
                // If Pacman has eaten a power pellet, return direction towards the ghost
                if (hasEatenPowerPellet && nearestGhost.getState() == GhostState.FRIGHTENED) {
                    return new Pair<>(true, getDirectionTo(pacman.getPosition(), nearestGhost.getPosition()));
                } else if (nearestGhost.getState() != GhostState.FRIGHTENED) {
                    // Return direction away from the ghost
                    return new Pair<>(true, getDirectionAway(pacman.getPosition(), nearestGhost.getPosition()));
                }
            }
        }
        return new Pair<>(false, null); // No ghosts nearby
    }

    /**
     * Finds the nearest ghost to Pacman.
     *
     * @param pacman The PacmanEntity to check against.
     * @return The nearest GhostEntity, or null if no ghosts are present.
     */
    public static GhostEntity findNearestGhost(PacmanEntity pacman) {
        Maze maze = pacman.getMaze();
        GhostEntity nearestGhost = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : maze.getEntities()) {
            if (entity instanceof GhostEntity ghost) {
                double distance = ghost.getPosition().distance(pacman.getPosition());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    nearestGhost = ghost;
                }
            }
        }
        return nearestGhost;
    }

    /**
     * Gets the direction towards a target position.
     *
     * @param pacmanPosition The position of Pacman.
     * @param targetPosition The position of the target (ghost).
     * @return The direction towards the target.
     */
    public static Direction getDirectionTo(Vector2d pacmanPosition, Vector2d targetPosition) {
        double dx = targetPosition.x() - pacmanPosition.x();
        double dy = targetPosition.y() - pacmanPosition.y();

        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? Direction.RIGHT : Direction.LEFT;
        } else {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        }
    }

    /**
     * Gets the direction away from a target position.
     *
     * @param pacmanPosition The position of Pacman.
     * @param targetPosition The position of the target (ghost).
     * @return The direction away from the target.
     */
    public static Direction getDirectionAway(Vector2d pacmanPosition, Vector2d targetPosition) {
        double dx = pacmanPosition.x() - targetPosition.x();
        double dy = pacmanPosition.y() - targetPosition.y();

        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? Direction.LEFT : Direction.RIGHT;
        } else {
            return dy > 0 ? Direction.DOWN : Direction.UP;
        }
    }

}
