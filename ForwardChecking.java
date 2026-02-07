import java.util.List;

/**
 * Implements forward checking logic for a Pips-based constraint satisfaction game.
 * This class validates if a given game state is still solvable based on remaining
 * resources and regional constraints.
 */
public final class ForwardChecking {

    /**
     * Determines if the current game state is feasible.
     * * @param game          The current state of the Pips game.
     * @param solutionDepth The current depth in the search tree (used to throttle expensive checks).
     * @return true if the state is potentially solvable; false if a dead-end is detected.
     */
    public static boolean feasible(PipsGame game, int solutionDepth) {
        final int totalSlots = game.remainingSumSlots;
        final int totalTarget = game.remainingSumTarget;

        // 1. Basic Boundary Checks
        if (totalSlots == 0) return totalTarget == 0;
        if (totalSlots < 0 || totalTarget < 0) return false;

        final int[] pipSupply = game.pipSupply;
        final int minP = PipsConstants.MIN_PIP;
        final int maxP = PipsConstants.MAX_PIP;

        long totalSupply = 0;
        int maxSingleSupply = 0;
        int activeKinds = 0;
        int firstP = -1;
        int lastP = -1;

// 2. Aggregate Pip Statistics
        boolean allEven = true;
        boolean allOdd = true;

        for (int p = minP; p <= maxP; p++) {
            int count = pipSupply[p];
            if (count > 0) {
                totalSupply += count;
                if (count > maxSingleSupply) maxSingleSupply = count;

                if (firstP == -1) firstP = p;
                lastP = p;
                activeKinds++;

                // --- NEW PARITY LOGIC ---
                if ((p & 1) == 0) allOdd = false;  // Using bitwise & 1 is a tiny bit faster than % 2
                else allEven = false;
            }
        }

        // Fail if we don't have enough physical pips
        if (totalSupply < totalSlots) return false;

        // If all pips are even, target must be even.
        if (allEven && (totalTarget & 1) != 0) return false;

        // If all pips are odd, target parity must match slot count parity.
        // (Odd + Odd = Even, Odd + Odd + Odd = Odd)
        if (allOdd && (totalTarget & 1) != (totalSlots & 1)) return false;

        // 3. Global Constraint Validation
        if (activeKinds == 2) {
            // Check if the target sum falls within the theoretical range of the two available pips
            long minPossible = (long) firstP * totalSlots;
            long maxPossible = (long) lastP * totalSlots;
            if (totalTarget < minPossible || totalTarget > maxPossible) {
                return false;
            }
        } else if (activeKinds == 1) {
            // Only one pip type remains; target must be exactly (pip value * slots)
            if ((long) firstP * totalSlots != totalTarget) {
                return false;
            }
        }

        // Only perform the expensive sum check at deeper levels of the search tree
        if (solutionDepth >= 2) {
            if (!isSumPossible(pipSupply, totalSlots, totalTarget, minP, maxP)) {
                return false;
            }
        }

// 4. Regional Constraint Validation
        final List<Region> regions = game.regions;
        final int regionCount = regions.size();

        for (int i = 0; i < regionCount; i++) {
            Region g = regions.get(i);
            int remaining = g.remainingSlots;

            if (remaining <= 0) continue; // Skip if region is already full

            int constraintType = g.constraintTypeId;

            // Handle "All Equal" constraints: All pips in this region must be the same value
            if (constraintType == PipsConstants.TYPE_ALL_EQUAL) {
                if (g.filledCount > 0) {
                    // If some slots are filled, remaining pips must match the requiredValue
                    if (pipSupply[g.requiredValue] < remaining) {
                        return false;
                    }
                } else {
                    // If empty, at least one pip type must have enough supply to fill the region
                    if (maxSingleSupply < remaining) {
                        return false;
                    }
                }
            }
            // Handle "Sum" constraints: Region must sum to a specific value
            else if (constraintType == PipsConstants.TYPE_SUM) {
                // --- NEW CHEAP BOUNDARY CHECKS ---
                // Fast failure: Check if the max/min possible values can even reach the target
                if ((long) lastP * remaining < g.remainingTarget) return false;
                if ((long) firstP * remaining > g.remainingTarget) return false;

                // Only perform the expensive greedy search if the simple math passes
                if (!isSumPossible(pipSupply, remaining, g.remainingTarget, minP, maxP)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Greedy check to see if a target sum is achievable given current supplies.
     * Calculates the absolute minimum and maximum possible sums using available pips.
     */
    private static boolean isSumPossible(int[] supply, int slots, int target, int minP, int maxP) {
        // Calculate Minimum Possible Sum (Greedy: use smallest pips first)
        int minSum = 0;
        int leftMin = slots;

        for (int p = minP; p <= maxP && leftMin > 0; p++) {
            int available = supply[p];
            if (available > 0) {
                int take = Math.min(available, leftMin);
                minSum += take * p;
                leftMin -= take;
            }
        }

        if (minSum > target) return false;

        // Calculate Maximum Possible Sum (Greedy: use largest pips first)
        int maxSum = 0;
        int leftMax = slots;

        for (int p = maxP; p >= minP && leftMax > 0; p--) {
            int available = supply[p];
            if (available > 0) {
                int take = Math.min(available, leftMax);
                maxSum += take * p;
                leftMax -= take;
            }
        }

        return maxSum >= target;
    }
}