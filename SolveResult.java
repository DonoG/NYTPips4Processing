import java.util.List;

/**
 * Encapsulates the outcome of a puzzle-solving attempt.
 * This class serves as a Data Transfer Object (DTO) containing the game state,
 * any discovered solutions, and performance metrics.
 */
public class SolveResult {

    /**
     * Represents the various outcomes of a solve attempt.
     */
    public enum Status {
        SOLVED,   // At least one valid solution was found
        UNSOLVED, // Search completed but no valid solution exists
        MISSING,  // The requested puzzle date/ID could not be found
        ERROR     // An unexpected exception occurred during solving
    }

    // --- Result Data ---
    public Status status;
    public PipsGame game;
    public List<List<Object>> solutions; // List of placements forming the solution(s)
    public String message;               // Error or informational message

    // --- Performance Metrics ---
    public long nodesExplored;           // Total states visited in the search tree
    public double solveTimeMillis;       // Time taken to execute the solver

    // --- Static Factory Methods ---

    /**
     * Creates a result for a completed search.
     * * @param g     The game instance used for the solve.
     * @param sols  The list of solutions found (may be empty).
     * @param nodes The number of nodes processed.
     * @param time  The execution time in milliseconds.
     * @return A SolveResult with either SOLVED or UNSOLVED status.
     */
    public static SolveResult success(PipsGame g, List<List<Object>> sols, long nodes, double time) {
        SolveResult r = new SolveResult();
        // If the solver finished but the solution list is empty, it's UNSOLVED
        r.status = (sols == null || sols.isEmpty()) ? Status.UNSOLVED : Status.SOLVED;
        r.game = g;
        r.solutions = sols;
        r.nodesExplored = nodes;
        r.solveTimeMillis = time;
        return r;
    }

    /**
     * Creates a result indicating that the puzzle data was unavailable.
     * * @param msg Descriptive message regarding the missing data.
     * @return A SolveResult with MISSING status.
     */
    public static SolveResult missing(String msg) {
        SolveResult r = new SolveResult();
        r.status = Status.MISSING;
        r.message = msg;
        return r;
    }

    /**
     * Creates a result indicating a failure or error during processing.
     */
    public static SolveResult error(String msg) {
        SolveResult r = new SolveResult();
        r.status = Status.ERROR;
        r.message = msg;
        return r;
    }
}