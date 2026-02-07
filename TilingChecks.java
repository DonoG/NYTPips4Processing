import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Performs geometric analysis on the grid to identify initial invalid placements
 * that would make the puzzle unsolvable due to "stranded" cells.
 */
public class TilingChecks {

    // Bitmaps to store whether a specific coordinate is a forbidden starting point for a domino
    public static boolean[][] horizontalVeto;
    public static boolean[][] verticalVeto;

    // Toggle for detailed console output during pre-scanning
    private static final boolean DEBUG = false;

    /**
     * Scans the grid to flag placements that are geometrically impossible.
     * Run prior to building the DLX matrix to prune the candidate list.
     * * @param game The current game state containing the grid dimensions and cell data.
     */
    public static void performGeometricVetoScan(PipsGame game) {
        horizontalVeto = new boolean[game.rows][game.cols];
        verticalVeto = new boolean[game.rows][game.cols];
        int count = 0;

        for (int r = 0; r < game.rows; r++) {
            for (int c = 0; c < game.cols; c++) {

                // Evaluate Horizontal Placement: (r, c) and (r, c + 1)
                if (c + 1 < game.cols) {
                    if (isPlacementDeadly(game, r, c, r, c + 1, "HORIZ")) {
                        horizontalVeto[r][c] = true;
                        count++;
                    }
                }

                // Evaluate Vertical Placement: (r, c) and (r + 1, c)
                if (r + 1 < game.rows) {
                    if (isPlacementDeadly(game, r, c, r + 1, c, "VERT")) {
                        verticalVeto[r][c] = true;
                        count++;
                    }
                }
            }
        }

        if (DEBUG) {
            System.out.println("Geometric Pre-scan complete. Total placements vetoed: " + count);
        }
    }

    /**
     * Core Logic: Determines if placing a domino at the specified coordinates
     * leaves any adjacent empty cell with zero available pairing options.
     */
    private static boolean isPlacementDeadly(PipsGame game, int r1, int c1, int r2, int c2, String type) {
        // Retrieve all unique cells adjacent to the proposed domino footprint
        List<int[]> neighbors = getUniqueAdjacent(game, r1, c1, r2, c2);

        for (int[] pos : neighbors) {
            int nr = pos[0];
            int nc = pos[1];

            // Only analyze neighbors that are currently unoccupied (value < 0)
            if (game.cells[nr][nc] != null && game.cells[nr][nc].value < 0) {

                int exits = 0;
                int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

                // Check all 4 directions around the neighbor cell to find a potential partner
                for (int[] d : dirs) {
                    int pr = nr + d[0]; // Partner row
                    int pc = nc + d[1]; // Partner col

                    if (pr >= 0 && pr < game.rows && pc >= 0 && pc < game.cols) {
                        Cell partner = game.cells[pr][pc];

                        // A partner is valid if it's empty and NOT one of the two cells
                        // we are currently "occupying" with our test domino.
                        if (partner != null && partner.value < 0) {
                            boolean isSelf = (pr == r1 && pc == c1) || (pr == r2 && pc == c2);
                            if (!isSelf) {
                                exits++;
                            }
                        }
                    }
                }

                // If the neighbor cell has 0 exits (neighbors) left to pair with,
                // the proposed placement is "deadly" as it guarantees an unsolvable state.
                if (exits == 0) {
                    if (DEBUG) {
                        System.out.printf("[VETO] %s at (%d,%d)-(%d,%d) strands cell at (%d,%d)%n",
                                type, r1, c1, r2, c2, nr, nc);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Identifies all unique coordinates neighboring the two cells of a potential domino placement.
     */
    private static List<int[]> getUniqueAdjacent(PipsGame game, int r1, int c1, int r2, int c2) {
        Set<String> visited = new HashSet<>();
        List<int[]> unique = new ArrayList<>();
        int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        int[][] cells = {{r1, c1}, {r2, c2}};

        for (int[] c : cells) {
            for (int[] d : dirs) {
                int nr = c[0] + d[0];
                int nc = c[1] + d[1];

                if (nr >= 0 && nr < game.rows && nc >= 0 && nc < game.cols) {
                    // Exclude the domino's own footprint from its neighbor list
                    boolean isSelf = (nr == r1 && nc == c1) || (nr == r2 && nc == c2);
                    if (!isSelf) {
                        String key = nr + "," + nc;
                        if (!visited.contains(key)) {
                            visited.add(key);
                            unique.add(new int[]{nr, nc});
                        }
                    }
                }
            }
        }
        return unique;
    }
}