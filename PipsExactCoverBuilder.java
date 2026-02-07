/**
 * Builds the DLX Exact Cover matrix using array-based indices.
 */

public class PipsExactCoverBuilder {

    private final PipsGame game;
    private final DLXSolver solver;

    // Use primitive int arrays to store column indices for speed
    private int[][] cellCols;
    private int[] dominoCols;

    private int placementCount = 0;

    public int getPlacementCount() {
        return placementCount;
    }

    public PipsExactCoverBuilder(PipsGame game) {
        this.game = game;
        // Safe starting point; the solver will grow if needed
        int initialCols = (game.rows * game.cols) + game.dominoes.size() + 10;
        int initialNodes = initialCols * 4;
        this.solver = new DLXSolver(game, initialNodes, initialCols);
    }

    public DLXSolver build() {
        buildColumns();
        buildRows();
        return solver;
    }

    private void buildColumns() {
        int rows = game.rows;
        int cols = game.cols;

        cellCols = new int[rows][cols];
        dominoCols = new int[game.dominoes.size()];

        // Cell Constraints
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (game.cells[r][c] != null) {
                    cellCols[r][c] = solver.addColumn("CELL:" + r + "," + c);
                }
            }
        }

        // Domino Constraints
        int totalDominoes = game.dominoes.size();
        for (int i = 0; i < totalDominoes; i++) {
            Domino d = game.dominoes.get(i);
            dominoCols[i] = solver.addColumn("DOMINO_" + i + "_" + d.a + "-" + d.b);
        }

    }

    private void buildRows() {
        TilingChecks.performGeometricVetoScan(game);

        int totalDominoes = game.dominoes.size();

        // Loop through Dominoes
        for (int i = 0; i < totalDominoes; i++) {
            Domino d = game.dominoes.get(i);


            // Loop through the Grid
            for (int r = 1; r < game.rows; r++) {
                for (int c = 1; c < game.cols; c++) {

                    // Horizontal
                    if (c + 1 < game.cols && !TilingChecks.horizontalVeto[r][c]) {
                        tryPlacement(i, d, r, c, r, c + 1);
                    }

                    // Vertical
                    if (r + 1 < game.rows && !TilingChecks.verticalVeto[r][c]) {
                        tryPlacement(i, d, r, c, r + 1, c);
                    }
                }
            }
        }
    }

    private void tryPlacement(int dominoIndex, Domino d, int r1, int c1, int r2, int c2) {
        Cell cell1 = game.cells[r1][c1];
        Cell cell2 = game.cells[r2][c2];

        if (cell1 == null || cell2 == null) return;

        if (canActuallyHold(cell1, d.a) && canActuallyHold(cell2, d.b)) {
            DominoPlacement p1 = new DominoPlacement(dominoIndex, r1, c1, d.a, r2, c2, d.b);
            // Cache references immediately after construction
            p1.setCachedReferences(cell1, cell2, game.regions.get(cell1.regionId), game.regions.get(cell2.regionId));
            if (PlacementValidator.isPlacementValidForRegions(p1, game)) {
                addPlacement(p1);
            }
        }

        if (!d.symmetric) {
            if (canActuallyHold(cell1, d.b) && canActuallyHold(cell2, d.a)) {
                DominoPlacement p2 = new DominoPlacement(dominoIndex, r1, c1, d.b, r2, c2, d.a);
                // Cache references immediately after construction
                p2.setCachedReferences(cell1, cell2, game.regions.get(cell1.regionId), game.regions.get(cell2.regionId));
                if (PlacementValidator.isPlacementValidForRegions(p2, game)) {
                    addPlacement(p2);
                }
            }
        }
    }

    private boolean canActuallyHold(Cell c, int value) {
        if (c.value >= 0) return c.value == value;
        int forced = getForcedValue(c);
        if (forced != -1) return forced == value;
        return (c.forbiddenMask & (1 << value)) == 0;
    }

    private int getForcedValue(Cell c) {
        int allowedBits = (~c.forbiddenMask) & 0x7F;
        if (Integer.bitCount(allowedBits) == 1) {
            return Integer.numberOfTrailingZeros(allowedBits);
        }
        return -1;
    }

    private void addPlacement(DominoPlacement p) {

        placementCount++;

        /*if (placementCount <= 10) {
            System.out.printf("CELL:%d,%d | CELL:%d,%d | DOMINO:%s" + "\n",
                    p.r1, p.c1, p.r2, p.c2, p.v1 + "-" + p.v2);
        }*/

        int[] colArray = {cellCols[p.r1][p.c1], cellCols[p.r2][p.c2], dominoCols[p.dominoIndex]};

        solver.addRow(p, colArray);
    }
}
