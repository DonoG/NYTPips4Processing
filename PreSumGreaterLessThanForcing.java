import java.util.*;

/**
 * Prunes values from Sum, GreaterThan, and LessThan regions by analyzing
 * global pip supply and regional constraints.
 */
public final class PreSumGreaterLessThanForcing {

    public static boolean DEBUG = false;
    private static final int MASK_0_6 = 0x7F; // Binary 1111111 (bits 0 through 6)

    public static int apply(PipsGame game) {
        // --- PASS 1: INITIAL HEURISTIC PASS ---
        int totalPruned = runHeuristicPass(game);

        // --- PREPARE FOR PASS 2: TIGHTEN SUPPLY ---
        int[] restrictedInventory = game.pipSupply.clone();
        updateInventoryForForcedSingles(game, restrictedInventory);

        // --- PASS 2: COMBINATORIAL PASS ---
        totalPruned += runCombinatorialPass(game, restrictedInventory);

        return totalPruned;
    }

    /**
     * Pass 1: Uses fast min/max boundary logic to prune impossible values.
     */
    private static int runHeuristicPass(PipsGame game) {
        int prunedCount = 0;
        int[] localInventory = game.pipSupply.clone();

        for (Region region : game.regions) {
            for (Cell cell : region.cells) {
                if (cell.value < 0 && isNakedSingle(cell)) {
                    int singleVal = getSingleValue(cell);
                    if (localInventory[singleVal] > 0) {
                        localInventory[singleVal]--;
                    }
                }
            }
        }

        for (Region region : game.regions) {
            List<Cell> targetCells = new ArrayList<>();
            int currentRegionSum = 0;
            for (Cell cell : region.cells) {
                if (cell.value >= 0) currentRegionSum += cell.value;
                else targetCells.add(cell);
            }

            if (targetCells.isEmpty()) continue;

            int remainingTarget = region.target - currentRegionSum;
            int emptySlots = targetCells.size();

            for (Cell cell : targetCells) {
                boolean cellIsForced = isNakedSingle(cell);
                int forcedVal = cellIsForced ? getSingleValue(cell) : -1;

                for (int v = PipsConstants.MIN_PIP; v <= PipsConstants.MAX_PIP; v++) {
                    // Bitwise check: Is bit v set?
                    if ((cell.forbiddenMask & (1 << v)) != 0) continue;

                    boolean wasRestored = false;
                    if (cellIsForced && v == forcedVal) {
                        localInventory[v]++;
                        wasRestored = true;
                    }

                    boolean isFeasible = false;
                    if (localInventory[v] > 0) {
                        localInventory[v]--;
                        isFeasible = checkValueFeasibility(v, emptySlots, remainingTarget, region.constraintTypeId, localInventory);
                        localInventory[v]++;
                    }

                    if (!isFeasible) {
                        // Bitwise set: Set bit v to 1
                        cell.forbiddenMask |= (1 << v);
                        prunedCount++;
                        if (DEBUG) {
                            System.out.printf("[Heuristic] Pruned %d at (%d,%d) | Type: %s%n", v, cell.r, cell.c, region.constraintTypeId);
                        }
                    }

                    if (wasRestored) {
                        localInventory[v]--;
                    }
                }
            }
        }
        return prunedCount;
    }

    /**
     * Identifies Naked and Hidden singles to reserve their pips in the inventory.
     */
    private static void updateInventoryForForcedSingles(PipsGame game, int[] inventory) {
        if (DEBUG) System.out.println("--- Updating Supply for Singles ---");
        Set<Cell> accountedCells = new HashSet<>();

        for (Region region : game.regions) {
            for (Cell cell : region.cells) {
                if (cell.value < 0 && isNakedSingle(cell)) {
                    if (accountedCells.contains(cell)) continue;
                    int v = getSingleValue(cell);
                    if (inventory[v] > 0) {
                        inventory[v]--;
                        accountedCells.add(cell);
                    }
                }
            }

            if (region.constraintTypeId ==PipsConstants.TYPE_SUM) {
                for (int v = PipsConstants.MIN_PIP; v <= PipsConstants.MAX_PIP; v++) {
                    Cell hiddenCandidate = null;
                    int possibleSlotCount = 0;
                    for (Cell cell : region.cells) {
                        // Bitwise check: Is bit v clear?
                        if (cell.value < 0 && (cell.forbiddenMask & (1 << v)) == 0) {
                            possibleSlotCount++;
                            hiddenCandidate = cell;
                        }
                    }

                    if (possibleSlotCount == 1 && !accountedCells.contains(hiddenCandidate)) {
                        if (inventory[v] > 0) {
                            inventory[v]--;
                            accountedCells.add(hiddenCandidate);
                        }
                    }
                }
            }
        }
    }

    /**
     * Pass 2: Uses recursion to verify if a valid combination actually exists.
     */
    private static int runCombinatorialPass(PipsGame game, int[] inventory) {
        int prunedCount = 0;
        for (Region region : game.regions) {

            if (region.constraintTypeId !=PipsConstants.TYPE_SUM) continue;

            List<Cell> emptyCells = new ArrayList<>();
            int currentRegionSum = 0;
            for (Cell cell : region.cells) {
                if (cell.value >= 0) currentRegionSum += cell.value;
                else emptyCells.add(cell);
            }

            if (emptyCells.isEmpty() || emptyCells.size() >= 6) continue;

            int remainingTarget = region.target - currentRegionSum;

            for (Cell targetCell : emptyCells) {
                for (int v = PipsConstants.MIN_PIP; v <= PipsConstants.MAX_PIP; v++) {
                    // Bitwise check: Is bit v set?
                    if ((targetCell.forbiddenMask & (1 << v)) != 0) continue;

                    if (!checkByPermutation(targetCell, v, emptyCells, remainingTarget, region.constraintTypeId, inventory)) {
                        // Bitwise set: Set bit v to 1
                        targetCell.forbiddenMask |= (1 << v);
                        prunedCount++;
                    }
                }
            }
        }
        return prunedCount;
    }

    private static boolean checkByPermutation(Cell targetCell, int val, List<Cell> regionCells, int target, int type, int[] inventory) {
        int[] workingInventory = inventory.clone();

        for (Cell cell : regionCells) {
            if (isNakedSingle(cell)) {
                workingInventory[getSingleValue(cell)]++;
            }
        }

        if (workingInventory[val] <= 0) return false;
        workingInventory[val]--;

        List<Cell> otherCells = new ArrayList<>(regionCells);
        otherCells.remove(targetCell);

        return canSatisfyRecursively(0, val, otherCells, target, type, workingInventory);
    }

    private static boolean canSatisfyRecursively(int index, int runningSum, List<Cell> others, int target, int type, int[] inventory) {
        if (index == others.size()) {

            switch (type) {
                case PipsConstants.TYPE_SUM:
                    return runningSum == target;

                case PipsConstants.TYPE_GREATER_THAN:
                    return runningSum > target;

                case PipsConstants.TYPE_LESS_THAN:
                    return runningSum < target;

                default:
                    return true;
            }
        }

        Cell current = others.get(index);
        if (type == PipsConstants.TYPE_SUM && runningSum > target) return false;

        for (int v = PipsConstants.MIN_PIP; v <= PipsConstants.MAX_PIP; v++) {
            // Bitwise check: Is bit v clear?
            if ((current.forbiddenMask & (1 << v)) == 0 && inventory[v] > 0) {
                inventory[v]--;
                if (canSatisfyRecursively(index + 1, runningSum + v, others, target, type, inventory)) {
                    inventory[v]++;
                    return true;
                }
                inventory[v]++;
            }
        }
        return false;
    }

    // --- UTILITIES ---

    /**
     * A "Naked Single" exists if exactly one bit in the 0-6 range is clear (0).
     */
    private static boolean isNakedSingle(Cell c) {
        // Invert mask and isolate bits 0-6. One set bit means one value is allowed.
        int allowedBits = (~c.forbiddenMask) & MASK_0_6;
        return Integer.bitCount(allowedBits) == 1;
    }

    /**
     * Returns the pip value of the only allowed bit.
     */
    private static int getSingleValue(Cell c) {
        int allowedBits = (~c.forbiddenMask) & MASK_0_6;
        if (allowedBits == 0) return -1;
        // numberOfTrailingZeros returns the index of the first '1' bit
        return Integer.numberOfTrailingZeros(allowedBits);
    }

    private static boolean checkValueFeasibility(int v, int totalSlots, int target, int type, int[] inventory) {
        int remainingSlots = totalSlots - 1;

        if (remainingSlots == 0) {
            switch (type) {
                case PipsConstants.TYPE_SUM:
                    return (v == target);
                case PipsConstants.TYPE_GREATER_THAN:
                    return (v > target);
                case PipsConstants.TYPE_LESS_THAN:
                    return (v < target);
                default:
                    return true;
            }
        }

        int neededFromOthers = target - v;
        int minOthers = getFiniteBound(remainingSlots, inventory, true);
        int maxOthers = getFiniteBound(remainingSlots, inventory, false);

        switch (type) {
            case PipsConstants.TYPE_SUM:
                return (neededFromOthers >= minOthers && neededFromOthers <= maxOthers);
            case PipsConstants.TYPE_GREATER_THAN:
                return (v + maxOthers > target);
            case PipsConstants.TYPE_LESS_THAN:
                return (v + minOthers < target);
            default:
                return true;
        }
    }

    private static int getFiniteBound(int count, int[] inventory, boolean findMin) {
        int sum = 0, remainingToFill = count;
        if (findMin) {
            for (int val = 0; val <= 6 && remainingToFill > 0; val++) {
                int take = Math.min(remainingToFill, inventory[val]);
                sum += (take * val);
                remainingToFill -= take;
            }
        } else {
            for (int val = 6; val >= 0 && remainingToFill > 0; val--) {
                int take = Math.min(remainingToFill, inventory[val]);
                sum += (take * val);
                remainingToFill -= take;
            }
        }
        return remainingToFill > 0 ? (findMin ? 1000 : -1000) : sum;
    }
}