import processing.core.PApplet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main entry point using the Processing framework
 * to batch solve NYP pips puzzles
 */

public class Main extends PApplet {

    PipsJSON pips;
    boolean isProcessing = true; // Initial state - used to track thread that solves puzzles
    boolean solvingIssues = false;
    String batchOutput = "";


    public void settings() {
        size(640, 480, P2D);
    }

    public void setup() {
        thread("batchSolvePips");
    }

    public void draw() {
        background(40); // Dark grey background
        fill(255);      // White text
        textAlign(CENTER, CENTER);
        textSize(16);

        String instructions = "";

        if (isProcessing) {
            // Simple on-screen message
            text("NYT Pips solver running...", width / 2, height / 2 - 20);
            fill(200);
            textSize(14);
            text("Check the console for full output", width / 2, height / 2 + 10);
            text("First time running may be slow, as it downloads and locally saves puzzles", width / 2, height / 2 + 40);
        } else {

            fill(100, 255, 100); // Green for finished
            if(solvingIssues) fill(255, 0,0); // Red if any issues

            text("NYT Pips solver finished", width / 2, height / 2 - 20);
            textSize(14);
            fill(200);
            instructions += "\nCheck the console for full batch output";
            instructions += "\nEdit batchSolvePips() for different solving options";
            text(batchOutput + instructions, width / 2, height / 2 + 50);
        }
    }

    public void batchSolvePips() {

        String today = LocalDate.now().toString();

        // Example - solve by date range
        SolveOptions allPuzzlesAllSolutions = SolveOptions.Builder.forRange("2025-08-18", today).difficulty("all").build();

        // Example - solve by date range
        SolveOptions allPuzzlesSingleSolution = SolveOptions.Builder.forRange("2025-08-18", today).difficulty("all").maxSolutions(1).build();

        // Single date
        SolveOptions TodaysHardPuzzle = SolveOptions.Builder.forSingleDate(today).difficulty("hard") // options here are easy, medium, hard - or can just use 'all'
                .solutionsToShow(1) // show first solution
                //.maxSolutions(1) // stops after finding set # of solutions
                .build();

        // batch solve puzzles by specific dates
        // these are examples of puzzles that have high maximum solutions
        // DLX takes some time to traverse and find all possible solutions - i.e. the 15 September 2025 has around 2.764m solutions
        List<String> hardPuzzleDates = new ArrayList<>(Arrays.asList("2025-09-15", "2025-10-28", "2025-10-20", "2025-10-18"));
        SolveOptions LargeSearchPuzzles = SolveOptions.Builder.forDates(hardPuzzleDates).difficulty("hard").build();

        //solveBatch(allPuzzlesAllSolutions);
        //solveBatch(allPuzzlesSingleSolution);
        //solveBatch(TodaysHardPuzzle);
        solveBatch(LargeSearchPuzzles);

        isProcessing = false;
    }

    public SolveResult solveSingle(String date, String difficulty, long maxSolutions) {

        PipsGame game = pips.getPipsGame(date, difficulty);
        if (game == null) return SolveResult.missing("File not found: " + date + "_" + difficulty);

        long start = System.nanoTime();

        //Some pre-checks to help prune puzzles
        PreEqualsGroupForcing.apply(game);
        PreSumGreaterLessThanForcing.apply(game);
        PreResidualValueForcing.apply(game);

        PipsExactCoverBuilder builder = new PipsExactCoverBuilder(game);
        DLXSolver solver = builder.build();
        //System.out.println("DLX Build Complete. Total Placements (Matrix Rows): " + builder.getPlacementCount());

        solver.useForwardCheck(true);
        solver.setSolutionLimit(maxSolutions);

        List<List<Object>> solutions = solver.solve();

        long end = System.nanoTime();
        double solveTime = (end - start) / 1_000_000.;
        long totalNodes = solver.getNodesVisited();

        return SolveResult.success(game, solutions, totalNodes, solveTime / 1000.);
    }

    /**
     * For debugging - iterates through the game board and prints any values that have been
     * marked as forbidden by the bitmask logic.
     */
    /**
     * Iterates through the game board and prints any values marked as forbidden.
     * Includes null-checks to prevent the "cell is null" error.
     */

    public static void printAllForbiddenValues(PipsGame game) {
        if (game == null || game.cells == null) {
            System.out.println("Error: Game or cell grid is null.");
            return;
        }

        System.out.println("=== FORBIDDEN VALUES REPORT ===");
        int problemCells = 0;

        for (int r = 0; r < game.rows; r++) {
            for (int c = 0; c < game.cols; c++) {
                Cell cell = game.cells[r][c];

                // --- NULL CHECK ---
                if (cell == null) continue;

                int mask = cell.forbiddenMask;
                // Bits 0-6 cover pip values 0 to 6
                int allowedBits = (~mask) & 0x7F;

                // Check if there are any restrictions
                if ((mask & 0x7F) != 0) {
                    List<Integer> forbidden = new ArrayList<>();
                    List<Integer> allowed = new ArrayList<>();

                    for (int v = 0; v <= 6; v++) {
                        if (((mask >> v) & 1) != 0) forbidden.add(v);
                        else allowed.add(v);
                    }

                    System.out.printf("Cell (%d, %d) [Region %d]: Forbidden %s | Allowed %s%n", r, c, cell.regionId, forbidden, allowed);

                    // FLAG CRITICAL ERROR: No values possible for this cell
                    if (allowed.isEmpty()) {
                        System.err.printf("   !!! DEAD END: Cell (%d,%d) has NO possible values!%n", r, c);
                        problemCells++;
                    }
                }
            }
        }

        if (problemCells > 0) {
            System.err.printf("SUMMARY: Found %d cells that are impossible to fill.%n", problemCells);
        }
        System.out.println("===============================");
    }

    /**
     * Manages the iteration over multiple dates/difficulties and logs results.
     * Uses tab delimiters (\t) for aligned console output.
     */

    public void solveBatch(SolveOptions options) {
        // Extract everything from the object
        List<String> dates = options.getDates();
        int solutionsToShow = options.getSolutionsToShow();
        String inputDifficulty = options.getDifficulty();
        long maxSolutions = options.getMaxSolutions();

        if (maxSolutions <= 0) {
            batchOutput = "Exiting as maxSolutions must be greater than 0.";
            println(batchOutput);
            return;
        }

        pips = new PipsJSON(this);

        String[] difficulties;
        if (inputDifficulty.trim().equalsIgnoreCase("all")) {
            difficulties = new String[]{"easy", "medium", "hard"};
        } else {
            List<String> validList = new ArrayList<>();
            String[] rawDifficulties = inputDifficulty.split(",");
            for (String raw : rawDifficulties) {
                String d = raw.trim().toLowerCase();
                if (d.equals("easy") || d.equals("medium") || d.equals("hard")) {
                    validList.add(d);
                }
            }
            difficulties = validList.toArray(new String[0]);
        }

        int total = 0, solved = 0, unsolved = 0, missing = 0, errors = 0;
        long totalNodesVisited = 0;
        long batchStart = System.nanoTime();

        // Header for table output - define the widths once so they are easy to adjust
        String rowFormat = "%-14s  %-12s  %-12s  %-12s  %-12s  %10s%n";
        System.out.printf(rowFormat, "STATUS", "DATE", "DIFFICULTY", "SOLUTIONS", "NODES", "TIME (secs)");
        System.out.println(repeatChar('-', 84));

        for (String date : dates) {
            for (String difficulty : difficulties) {
                total++;
                try {
                    SolveResult result = solveSingle(date, difficulty, maxSolutions);
                    totalNodesVisited += result.nodesExplored;

                    switch (result.status) {
                        case SOLVED:
                            solved++;
                            System.out.printf(rowFormat, "SOLVED", date, difficulty, result.solutions.size(), result.nodesExplored, String.format("%.4f", result.solveTimeMillis));
                            //println(date + "\t" + result.solutions.size() + "\t" + String.format("%.4f", (double)result.solveTimeMillis));

                            for (int i = 0; i < solutionsToShow && i < result.solutions.size(); i++) {
                                println("\nSolution #" + (i + 1) + "\n");
                                applySolutionToGrid(result.game, result.solutions.get(i));
                                println(getGridString(result.game));
                            }
                            break;

                        case UNSOLVED:
                            unsolved++;

                            System.out.printf((rowFormat) + "%n", "UNSOLVED", date, difficulty, 0, result.nodesExplored, result.solveTimeMillis);
                            break;

                        case MISSING:
                            missing++;

                            System.out.printf((rowFormat) + "%n", "MISSING", date, difficulty, "--", "--", "--");
                            break;

                        case ERROR:
                            errors++;
                            System.out.printf((rowFormat) + "%n", "ERROR", date, difficulty, "--", "--", "--");
                            break;
                    }
                } catch (Exception ex) {
                    errors++;
                    println("CRASH\t" + date + "\t" + difficulty + "\t" + ex.getMessage());
                }
            }
        }

        double totalTimeSec = (System.nanoTime() - batchStart) / 1_000_000_000.0;
        batchOutput = "\n--- BATCH SUMMARY ---";
        batchOutput += String.format("\nTotal: %d | Solved: %d | Unsolved: %d | Missing: %d | Errors: %d", total, solved, unsolved, missing, errors);
        batchOutput += String.format("\nTotal nodes visited: %,d", totalNodesVisited);
        batchOutput += String.format("\nTotal time: %.2f seconds", totalTimeSec);
        println(batchOutput);

        if (errors > 0 || missing >0 || unsolved > 0) solvingIssues = true; //for formatting results

    }

    public static String getGridString(PipsGame game) {
        StringBuilder sb = new StringBuilder();
        for (int r = 1; r < game.rows; r++) {
            for (int c = 1; c < game.cols; c++) {
                Cell cell = game.cells[r][c];
                if (cell == null) sb.append("   ");
                else if (cell.value < 0) sb.append(" _ ");
                else sb.append(" ").append(cell.value).append(" ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public void applySolutionToGrid(PipsGame game, List<Object> solution) {
        for (Object o : solution) {
            DominoPlacement p = (DominoPlacement) o;
            game.cells[p.r1][p.c1].value = p.v1;
            game.cells[p.r2][p.c2].value = p.v2;
        }
    }

    private String repeatChar(char c, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        PApplet.main("Main");
    }
}