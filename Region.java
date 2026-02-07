import java.util.ArrayList;
import java.util.List;

/**
 * Represents a specific area on the game board subject to a single constraint.
 * A Region tracks its own cells and maintains a running state of its progress
 * toward satisfying its specific rule (Sum, All Equal, etc.).
 */
public class Region {

    // --- Static Definition (Set during initialization) ---

    /** Unique identifier for the region. */
    public int id;

    /** * Encodes the rule type (e.g., SUM, ALL_EQUAL, LESS_THAN).
     * Integer comparisons are significantly faster than String comparisons in solvers.
     */
    public int constraintTypeId;

    /** The target value to satisfy the constraint (e.g., the specific sum required). */
    public int target;

    /** The list of grid cells that belong to this specific region. */
    public List<Cell> cells = new ArrayList<>();


    // --- Dynamic State (Updated during backtracking/search) ---

    /** Number of cells in this region that have not yet been assigned a value. */
    public int remainingSlots;

    /** * For SUM constraints: The remaining value needed to reach the target.
     * Updated dynamically as cells are filled.
     */
    public int remainingTarget;

    /** Total number of cells currently occupied in this region. */
    public int filledCount;

    /** * Used for constraints like ALL_EQUAL.
     * Stores the value of the first pip placed; all subsequent pips must match this.
     * Defaults to -1 when no pips are placed.
     */
    public int requiredValue = -1;
}