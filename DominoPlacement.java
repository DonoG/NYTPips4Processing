/**
 * Represents a specific placement of a domino on the grid.
 * This class links a physical domino to its spatial coordinates and values.
 *
 * Caches cell and region references to eliminate repeated
 * array lookups during solving
 */

class DominoPlacement {
    // Reference index to the original Domino object in the master list/set
    int dominoIndex;

    // Coordinates for the first half of the domino (for example, row 1, column 1)
    int r1, c1;

    // Coordinates for the second half of the domino (for example, row 2, column 2)
    int r2, c2;

    // The specific pip values assigned to each coordinate
    // Note: v1/v2 might differ from domino.a/b depending on rotation/flip
    int v1, v2;

    // These are set once during construction and reused throughout solving
    Cell cell1, cell2;
    Region region1, region2;
    boolean sameRegion;

    /**
     * Constructs a placement record for the solver.
     * NOTE: Cell and region caching must be done via setCachedReferences() after construction.
     */

    DominoPlacement(int dominoIndex,
                    int r1, int c1, int v1,
                    int r2, int c2, int v2) {
        this.dominoIndex = dominoIndex;
        this.r1 = r1;
        this.c1 = c1;
        this.v1 = v1;
        this.r2 = r2;
        this.c2 = c2;
        this.v2 = v2;
    }

    /**
     * Caches cell and region references for fast access during search.
     * This MUST be called before the placement is used in the solver.
     */

    void setCachedReferences(Cell c1, Cell c2, Region r1, Region r2) {
        this.cell1 = c1;
        this.cell2 = c2;
        this.region1 = r1;
        this.region2 = r2;
        this.sameRegion = (c1.regionId == c2.regionId);
    }

    /**
     * Returns a human-readable summary of the placement.
     * Example: "Domino 5 (2,3) @ (1,1)-(1,2)"
     */

    @Override
    public String toString() {
        return "Domino " + dominoIndex +
                " (" + v1 + "," + v2 + ")" +
                " @ (" + r1 + "," + c1 + ")-(" + r2 + "," + c2 + ")";
    }
}