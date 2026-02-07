/**
 * Prunes values based on Global Residual Analysis.
 * Logic: Total Pips available - Sum of all 'SUM' Region Targets = Required Pips for 'Non-Sum' cells.
 */
public final class PreResidualValueForcing {

    public static boolean DEBUG = false;

    /**
     * Entry point for residual-based domain reduction.
     * @return count of values eliminated from cell domains.
     */
    public static int apply(PipsGame game) {
        ResidualState state = calculateResidualState(game);

        if (DEBUG) {
            System.out.printf("[Residual] Total Pips: %d | Sum Targets: %d | Residual Capacity: %d%n",
                    state.totalPips, state.totalSumTargets, state.residualCapacity);
        }

        if (state.residualCapacity < 0) {
            throw new IllegalStateException("Logic Error: Total sum targets exceed total available pips.");
        }

        return pruneImpossibleResidualValues(game, state);
    }

    /**
     * Data class to hold the results of the board-wide pip audit.
     */
    static class ResidualState {
        int totalPips;
        int totalSumTargets;
        int residualCapacity;
    }

    private static ResidualState calculateResidualState(PipsGame game) {
        ResidualState state = new ResidualState();

        // 1. Calculate "Supply": Every pip on every domino provided in the game
        for (Domino domino : game.dominoes) {
            state.totalPips += domino.a + domino.b;
        }

        // 2. Calculate "Reserved Demand": Pips that MUST go into 'sum' regions
        for (Region region : game.regions) {
            if (region.constraintTypeId == PipsConstants.TYPE_SUM) {
                state.totalSumTargets += region.target;
            }
        }

        // 3. The "Residual": Pips that MUST be distributed among all other types of regions
        state.residualCapacity = state.totalPips - state.totalSumTargets;
        return state;
    }

    /**
     * Iterates through all non-sum cells and prunes values that are 
     * mathematically incompatible with the global residual.
     */
    private static int pruneImpossibleResidualValues(PipsGame game, ResidualState state) {
        int totalEliminated = 0;

        int pipsAlreadyUsedInNonSum = getPipsUsedByFixedNonSumCells(game);
        int remainingResidualToFill = state.residualCapacity - pipsAlreadyUsedInNonSum;
        int emptyNonSumCellCount = countEmptyNonSumCells(game);

        if (DEBUG) {
            System.out.printf("[Residual] Remaining Cells: %d | Remaining Residual: %d%n",
                    emptyNonSumCellCount, remainingResidualToFill);
        }

        if (remainingResidualToFill < 0) {
            throw new IllegalStateException("Residual underflow: Non-sum assignments exceed available residual.");
        }

        // Check every empty cell that does NOT belong to a 'sum' region
        for (int r = 0; r < game.rows; r++) {
            for (int c = 0; c < game.cols; c++) {
                Cell cell = game.cells[r][c];
                if (cell == null || cell.value >= 0) continue;

                Region region = game.regions.get(cell.regionId);

                if (region.constraintTypeId == PipsConstants.TYPE_SUM) continue;

                // How many other non-sum cells must we account for?
                int otherCellsCount = emptyNonSumCellCount - 1;

                for (int v = PipsConstants.MIN_PIP; v <= PipsConstants.MAX_PIP; v++) {
                    // Bitwise check: Is bit v set (forbidden)?
                    if ((cell.forbiddenMask & (1 << v)) != 0) continue;

                    // Hull Consistency check
                    int minPotentialTotal = v + (otherCellsCount * PipsConstants.MIN_PIP);
                    int maxPotentialTotal = v + (otherCellsCount * PipsConstants.MAX_PIP);

                    if (minPotentialTotal > remainingResidualToFill || maxPotentialTotal < remainingResidualToFill) {
                        // Bitwise set: Mark bit v as forbidden
                        cell.forbiddenMask |= (1 << v);
                        totalEliminated++;

                        if (DEBUG) {
                            System.out.printf("[Residual] Pruned %d at (%d,%d) - Out of global residual bounds.%n", v, r, c);
                        }
                    }
                }
            }
        }
        return totalEliminated;
    }

    // --- Helper Logic for Bookkeeping ---

    private static int countEmptyNonSumCells(PipsGame game) {
        int count = 0;
        for (int r = 0; r < game.rows; r++) {
            for (int c = 0; c < game.cols; c++) {
                Cell cell = game.cells[r][c];
                if (cell != null && cell.value < 0) {
                    Region region = game.regions.get(cell.regionId);
                    if (region.constraintTypeId != PipsConstants.TYPE_SUM) count++;
                }
            }
        }
        return count;
    }

    private static int getPipsUsedByFixedNonSumCells(PipsGame game) {
        int sum = 0;
        for (int r = 0; r < game.rows; r++) {
            for (int c = 0; c < game.cols; c++) {
                Cell cell = game.cells[r][c];
                if (cell != null && cell.value >= 0) {
                    Region region = game.regions.get(cell.regionId);
                    if (region.constraintTypeId !=PipsConstants.TYPE_SUM) sum += cell.value;
                }
            }
        }
        return sum;
    }
}