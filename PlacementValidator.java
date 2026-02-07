import java.util.ArrayList;
import java.util.List;

/**
 * Validates domino placements against regional constraints.
 * Provides logic for both initial DLX graph building (static feasibility)
 * and real-time validation during solver execution.
 */
public class PlacementValidator {

    /**
     * Top-level check to see if a specific domino placement satisfies the rules
     * of every region it touches.
     * * @param p The domino placement (position and values)
     * @param game The current state of the game
     * @return true if the placement is valid for all affected regions
     */
    public static boolean isPlacementValidForRegions(DominoPlacement p, PipsGame game) {
        // Map the two ends of the domino to game cells
        Cell cell1 = game.cells[p.r1][p.c1];
        Cell cell2 = game.cells[p.r2][p.c2];

        boolean isSameRegion = cell1.regionId == cell2.regionId;

        // Identify regions that need to be re-evaluated
        List<Region> affectedRegions = new ArrayList<>();
        affectedRegions.add(game.regions.get(cell1.regionId));
        if (!isSameRegion) {
            affectedRegions.add(game.regions.get(cell2.regionId));
        }

        for (Region region : affectedRegions) {
            int deltaSum = 0;
            int deltaCount = 0;
            int valForRegion1 = 0; // Value of the first domino end relative to this region
            int valForRegion2 = 0; // Value of the second domino end relative to this region

            // If the region contains the first end of the domino
            if (region == game.regions.get(cell1.regionId)) {
                deltaSum += p.v1;
                deltaCount++;
                valForRegion1 = p.v1;

                // If both ends are in this same region
                if (isSameRegion) {
                    deltaSum += p.v2;
                    deltaCount++;
                    valForRegion2 = p.v2;
                }
            }

            // If the region contains only the second end of the domino
            if (!isSameRegion && region == game.regions.get(cell2.regionId)) {
                deltaSum += p.v2;
                deltaCount++;
                valForRegion1 = p.v2;
            }

            // Perform the mathematical feasibility check
            if (!regionFeasible(region, deltaSum, deltaCount, valForRegion1, valForRegion2, isSameRegion, cell1, cell2)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Logic used during DLX graph construction to determine if a specific
     * node (placement) is fundamentally possible within a region.
     */
    public static boolean regionFeasible(
            Region r,
            int deltaSum,
            int deltaCount,
            int v1,
            int v2,
            boolean sameRegion,
            Cell c1,
            Cell c2
    ) {
        // Calculate potential state
        int currentSum = deltaSum;
        int currentCount = deltaCount;
        int remainingCells = r.cells.size() - currentCount;

        // Basic safety check
        if (remainingCells < 0) return false;

        // Checks if the two halves of the domino itself violate region rules
        if (sameRegion) {
            if (r.constraintTypeId ==PipsConstants.TYPE_ALL_EQUAL && v1 != v2) return false;
            if (r.constraintTypeId ==PipsConstants.TYPE_NOT_EQUAL && v1 == v2) return false;
        }

        // Checks if the new domino contradicts pips already fixed on the board
        for (Cell c : r.cells) {
            if (c != null && c.value >= 0) {
                if (r.constraintTypeId ==PipsConstants.TYPE_ALL_EQUAL){
                    if (v1 != c.value) return false;
                    if (sameRegion && v2 != c.value) return false;
                }
                    if (r.constraintTypeId ==PipsConstants.TYPE_NOT_EQUAL){
                    if (v1 == c.value) return false;
                    if (sameRegion && v2 == c.value) return false;
                }
            }
        }

        // Calculates the min/max possible sum
        int minPotential = currentSum + (remainingCells * PipsConstants.MIN_PIP);
        int maxPotential = currentSum + (remainingCells * PipsConstants.MAX_PIP);

        switch (r.constraintTypeId) {
            case PipsConstants.TYPE_SUM: // "sum":
                if (minPotential > r.target) return false;  // We're already too high
                if (maxPotential < r.target) return false;  // We can't reach high enough
                if (remainingCells == 0 && currentSum != r.target) return false;
                break;

            case PipsConstants.TYPE_LESS_THAN: //  "lessThan":
                if (minPotential >= r.target) return false;
                if (remainingCells == 0 && currentSum >= r.target) return false;
                break;

            case PipsConstants.TYPE_GREATER_THAN: // "greaterThan":
                if (maxPotential <= r.target) return false;
                if (remainingCells == 0 && currentSum <= r.target) return false;
                break;

            default:
                // "allEqual", "notEqual", and "empty" handled above or require no hull check
                return true;
        }

        return true;
    }

    /**
     * Final validation check called after a DLX choice is made
     * to confirm the board remains in a valid state.
     */
    public static boolean checkLastPlacement(DominoPlacement p, PipsGame game) {

        int regionIdx1 = p.region1.id;
        int regionIdx2 = p.region2.id;

        Region r1 = p.region1;
        Region r2 = p.region2;

        // If both cells aren't in any constraint regions (rare), the placement is valid by default
        if (regionIdx1 < 0 && regionIdx2 < 0) return true;

        // Validate the region containing the first end
        if (regionIdx1 >= 0) {
            if (!validateRegionState(r1, game, p)) return false;
        }

        // Validate the second region only if it's different from the first
        if (regionIdx2 >= 0 && regionIdx2 != regionIdx1) {
            return validateRegionState(r2, game, p);
        }

        return true;
    }

    /**
     * Internal helper to scan a region and verify all values (existing + new)
     * satisfy the region's specific constraint.
     */
    private static boolean validateRegionState(Region r, PipsGame game, DominoPlacement p) {
        int runningSum = 0;
        int countFilled = 0;
        List<Integer> regionValues = new ArrayList<>();

        Cell placementCell1 = game.cells[p.r1][p.c1];
        Cell placementCell2 = game.cells[p.r2][p.c2];

        // Aggregate all values currently in the region
        for (Cell c : r.cells) {
            int v = -1;
            if (c == placementCell1) v = p.v1;
            else if (c == placementCell2) v = p.v2;
            else if (c.value >= 0) v = c.value;

            if (v != -1) {
                // VBA-style Fail Fast on masks
                if (((c.forbiddenMask >> v) & 1) != 0) return false;

                regionValues.add(v);
                runningSum += v;
                countFilled++;
            }
        }

        int emptyCount = r.cells.size() - countFilled;
        int minExtraPotential = emptyCount * PipsConstants.MIN_PIP;
        int maxExtraPotential = emptyCount * PipsConstants.MAX_PIP;

        switch (r.constraintTypeId) {
            case PipsConstants.TYPE_ALL_EQUAL:
                if (regionValues.isEmpty()) return true;
                int first = regionValues.get(0);
                // .stream() is Java 8, so this part is actually fine!
                return regionValues.stream().allMatch(v -> v == first);

            case PipsConstants.TYPE_NOT_EQUAL:
                // Check for duplicates
                int regionSize = regionValues.size();
                for (int i = 0; i < regionSize; i++) {
                    for (int j = i + 1; j < regionSize; j++) {
                        if (regionValues.get(i).equals(regionValues.get(j))) {
                            return false;
                        }
                    }
                }
                return true;

            case PipsConstants.TYPE_SUM:
                if (runningSum > r.target) return false;
                if (runningSum + maxExtraPotential < r.target) return false;
                return emptyCount != 0 || runningSum == r.target;

            case PipsConstants.TYPE_LESS_THAN:
                if (emptyCount == 0 && runningSum >= r.target) return false;
                return runningSum + minExtraPotential < r.target;

            case PipsConstants.TYPE_GREATER_THAN:
                if (emptyCount == 0 && runningSum <= r.target) return false;
                return runningSum + maxExtraPotential > r.target;

            default:
                return true;
        }
    }
}