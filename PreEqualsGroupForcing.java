
/**
 * A pre-solver optimization class that prunes impossible values from 'allEqual' regions.
 * It uses the global pip supply to determine if a specific value can still satisfy
 * the requirements of an entire region.
 */

public final class PreEqualsGroupForcing {

    public static boolean DEBUG = false;

    /**
     * Scans all 'allEqual' regions and marks values as forbidden if they cannot
     * possibly fill the region based on current board state and global supply.
     * * @param game The current game state containing regions and pip supply.
     * @return The number of values successfully pruned from cell domains.
     */
    public static int apply(PipsGame game) {
        int totalPrunedCount = 0;

        for (Region region : game.regions) {
            // This logic only applies to groups where every cell must have the same value.
            if(region.constraintTypeId!=PipsConstants.TYPE_ALL_EQUAL){
                continue;
            }

            // Audit the current state of the region
            int emptyCellCount = 0;
            int[] valuesAlreadyInRegion = new int[PipsConstants.MAX_PIP + 1];
            int fixedValueFound = -1;

            for (Cell cell : region.cells) {
                if (cell.value >= 0) {
                    valuesAlreadyInRegion[cell.value]++;
                    fixedValueFound = cell.value;
                } else {
                    emptyCellCount++;
                }
            }

            // If the region is already full, no pruning is needed here.
            if (emptyCellCount == 0) {
                continue;
            }

            // Global Supply Validation (Supply vs. Demand)
            for (int v = PipsConstants.MIN_PIP; v <= PipsConstants.MAX_PIP; v++) {
                int availableInInventory = game.pipSupply[v];
                int alreadyPlacedInThisRegion = valuesAlreadyInRegion[v];
                int totalAvailableForThisRegion = availableInInventory + alreadyPlacedInThisRegion;

                // Demand: region.cells.size() | Supply: totalAvailableForThisRegion
                if (totalAvailableForThisRegion < region.cells.size()) {
                    // Prune value 'v' from all empty cells in this region
                    for (Cell cell : region.cells) {
                        // Bitwise check: Is bit v NOT set?
                        if (cell.value < 0 && (cell.forbiddenMask & (1 << v)) == 0) {
                            // Bitwise set: Mark bit v as forbidden
                            cell.forbiddenMask |= (1 << v);
                            totalPrunedCount++;

                            if (DEBUG) {
                                System.out.printf("[AllEqual] Pruned %d at (%d,%d) - Need %d but only %d exist.%n",
                                        v, cell.r, cell.c, region.cells.size(), totalAvailableForThisRegion);
                            }
                        }
                    }
                }
            }

            // Intra-Region Consistency
            // If one cell is fixed, every other value is forbidden for the remaining empty cells.
            if (fixedValueFound != -1) {
                for (int v = PipsConstants.MIN_PIP; v <= PipsConstants.MAX_PIP; v++) {
                    if (v == fixedValueFound) continue;

                    for (Cell cell : region.cells) {
                        // Bitwise check: Is bit v NOT set?
                        if (cell.value < 0 && (cell.forbiddenMask & (1 << v)) == 0) {
                            cell.forbiddenMask |= (1 << v);
                            totalPrunedCount++;
                        }
                    }
                }
            }
        }

        return totalPrunedCount;
    }
}