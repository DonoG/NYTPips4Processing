import java.util.*;
import static processing.core.PApplet.max;

/**
 * Manages the core state and logic for the Pips puzzle game.
 * Tracks grid layout, regional constraints, and the available supply of dominoes.
 */
public class PipsGame {
    // Identity and Metadata
    public int id;
    public String backendId;
    public String[] constructors;
    public String JSONSolution = "";

    // Global Pip Supply Tracking
    public int activePipsMask = 0; // Bitmask representing which pip values are still available
    public long totalPipSum = 0;   // Running sum of all available pip values
    int[] pipSupply = new int[PipsConstants.MAX_PIP + 1]; // Count of each specific pip value

    // Global Constraint Tracking
    public int remainingSumSlots;  // Total empty slots across all 'SUM' type regions
    public int remainingSumTarget; // The total remaining value needed to satisfy all 'SUM' regions

    // Grid Components
    int rows;
    int cols;
    Cell[][] cells;
    ArrayList<Region> regions = new ArrayList<>();
    ArrayList<Domino> dominoes = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Initialization Logic
    // -------------------------------------------------------------------------

    /**
     * Builds the 2D grid array based on the cells contained within the regions.
     */
    void finaliseGrid() {
        int maxR = 0, maxC = 0;
        for (Region r : regions) {
            for (Cell c : r.cells) {
                maxR = max(maxR, c.r);
                maxC = max(maxC, c.c);
            }
        }
        rows = maxR + 1;
        cols = maxC + 1;
        cells = new Cell[rows][cols];

        for (Region r : regions) {
            for (Cell c : r.cells) {
                cells[c.r][c.c] = c;
            }
        }
    }

    /**
     * Populates the initial pip supply based on the list of available dominoes.
     */
    void initialSupply() {
        Arrays.fill(pipSupply, 0);
        activePipsMask = 0;
        totalPipSum = 0;
        for (Domino d : this.dominoes) {
            addPipToTracking(d.a);
            addPipToTracking(d.b);
        }
    }

    private void addPipToTracking(int v) {
        if (pipSupply[v] == 0) {
            activePipsMask |= (1 << v); // Set bit if this is the first pip of this value
        }
        pipSupply[v]++;
        totalPipSum += v;
    }

    private void removePipFromTracking(int v) {
        pipSupply[v]--;
        if (pipSupply[v] == 0) {
            activePipsMask &= ~(1 << v); // Clear bit if no pips of this value remain
        }
        totalPipSum -= v;
    }

    /**
     * Resets the regional states and calculates the global sum targets.
     */
    void initialSumState() {
        remainingSumSlots = 0;
        remainingSumTarget = 0;

        for (Region r : regions) {
            r.remainingSlots = r.cells.size();
            r.remainingTarget = r.target;
            r.filledCount = 0;
            r.requiredValue = -1;

            if (r.constraintTypeId == PipsConstants.TYPE_SUM) {
                remainingSumSlots += r.remainingSlots;
                remainingSumTarget += r.remainingTarget;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Solver Support Methods (Apply/Undo)
    // -------------------------------------------------------------------------

    /**
     * Updates game state when a domino is placed on the grid.
     * @param p The placement details containing cells, values, and regions.
     */
    public void applyPlacement(DominoPlacement p) {
        // 1. Remove used pips from the global supply
        removePipFromTracking(p.v1);
        removePipFromTracking(p.v2);

        Region r1 = p.region1;
        Region r2 = p.region2;

        // 2. Update Cell 1 and its Region
        p.cell1.value = p.v1;
        if (r1.constraintTypeId == PipsConstants.TYPE_SUM) {
            this.remainingSumSlots--;
            this.remainingSumTarget -= p.v1;
        }
        r1.remainingSlots--;
        r1.remainingTarget -= p.v1;
        r1.filledCount++;

        // If 'ALL EQUAL' constraint, the first placement defines the required value
        if (r1.constraintTypeId == PipsConstants.TYPE_ALL_EQUAL && r1.filledCount == 1) {
            r1.requiredValue = p.v1;
        }

        // 3. Update Cell 2 and its Region
        p.cell2.value = p.v2;

        if (p.sameRegion) {
            // Optimization: Avoid double-fetching region if domino is entirely within one region
            if (r1.constraintTypeId == PipsConstants.TYPE_SUM) {
                this.remainingSumSlots--;
                this.remainingSumTarget -= p.v2;
            }
            r1.remainingSlots--;
            r1.remainingTarget -= p.v2;
            r1.filledCount++;
        } else {
            // Update the second region independently
            if (r2.constraintTypeId == PipsConstants.TYPE_SUM) {
                this.remainingSumSlots--;
                this.remainingSumTarget -= p.v2;
            }
            r2.remainingSlots--;
            r2.remainingTarget -= p.v2;
            r2.filledCount++;

            if (r2.constraintTypeId == PipsConstants.TYPE_ALL_EQUAL && r2.filledCount == 1) {
                r2.requiredValue = p.v2;
            }
        }
    }

    /**
     * Reverts game state to the point before a specific placement.
     * Used for backtracking in the solver.
     */
    public void undoPlacement(DominoPlacement p) {
        // 1. Restore pips to the global supply
        addPipToTracking(p.v1);
        addPipToTracking(p.v2);

        Region r1 = p.region1;
        Region r2 = p.region2;

        // 2. Revert Cell 2 and its Region
        if (p.sameRegion) {
            if (r1.constraintTypeId == PipsConstants.TYPE_SUM) {
                this.remainingSumSlots++;
                this.remainingSumTarget += p.v2;
            }
            r1.remainingSlots++;
            r1.remainingTarget += p.v2;
            r1.filledCount--;
        } else {
            if (r2.constraintTypeId == PipsConstants.TYPE_SUM) {
                this.remainingSumSlots++;
                this.remainingSumTarget += p.v2;
            }
            r2.remainingSlots++;
            r2.remainingTarget += p.v2;
            r2.filledCount--;

            // If region becomes empty, reset the required value for 'ALL EQUAL'
            if (r2.constraintTypeId == PipsConstants.TYPE_ALL_EQUAL && r2.filledCount == 0) {
                r2.requiredValue = -1;
            }
        }
        p.cell2.value = -1;

        // 3. Revert Cell 1 and its Region
        if (r1.constraintTypeId == PipsConstants.TYPE_SUM) {
            this.remainingSumSlots++;
            this.remainingSumTarget += p.v1;
        }
        r1.remainingSlots++;
        r1.remainingTarget += p.v1;
        r1.filledCount--;

        if (r1.constraintTypeId == PipsConstants.TYPE_ALL_EQUAL && r1.filledCount == 0) {
            r1.requiredValue = -1;
        }
        p.cell1.value = -1;
    }
}