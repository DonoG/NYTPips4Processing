import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dancing Links solver
 */

public class DLXSolver {

    private final PipsGame game;
    private long nodesVisited;
    private boolean forwardCheck;
    private long maxSolutions;

    // Parallel arrays representing the matrix nodes
    private int[] L, R, U, D, C;
    private int[] size;
    private Object[] rowDataArray;
    private int nodeCount;
    private final int root = 0;
    private String[] columnNames;

    private int[] solution;
    private int solutionDepth;
    private final List<List<Object>> solutions;

    public DLXSolver(PipsGame game, int initialMaxNodes, int initialMaxCols) {
        this.game = game;
        L = new int[initialMaxNodes];
        R = new int[initialMaxNodes];
        U = new int[initialMaxNodes];
        D = new int[initialMaxNodes];
        C = new int[initialMaxNodes];
        rowDataArray = new Object[initialMaxNodes];
        columnNames = new String[initialMaxCols + 1];

        size = new int[initialMaxCols + 1];

        L[root] = R[root] = root;
        nodeCount = 1;


        solution = new int[5];
        solutionDepth = 0;
        solutions = new ArrayList<>();
        nodesVisited = 0;
    }

    public long getNodesVisited() {
        return nodesVisited;
    }

    public void useForwardCheck(boolean b) {
        forwardCheck = b;
    }

    public void setSolutionLimit(long s) {
        maxSolutions = s;
    }

    private void ensureCapacity() {
        if (nodeCount >= L.length) {
            int newSize = L.length * 2;
            L = Arrays.copyOf(L, newSize);
            R = Arrays.copyOf(R, newSize);
            U = Arrays.copyOf(U, newSize);
            D = Arrays.copyOf(D, newSize);
            C = Arrays.copyOf(C, newSize);
            rowDataArray = Arrays.copyOf(rowDataArray, newSize);
        }
    }

    // Ensure solution array capacity
    private void ensureSolutionCapacity() {
        if (solutionDepth >= solution.length) {
            solution = Arrays.copyOf(solution, solution.length * 2);
        }
    }

    public int addColumn(String name) {
        ensureCapacity();
        int c = nodeCount++;

        if (c >= size.length) {
            size = Arrays.copyOf(size, size.length * 2);
            columnNames = Arrays.copyOf(columnNames, size.length * 2);  // ADD THIS
        }

        L[c] = L[root];
        R[c] = root;
        R[L[root]] = c;
        L[root] = c;

        U[c] = D[c] = c;
        C[c] = c;
        size[c] = 0;
        columnNames[c] = name;
        return c;
    }

    public void addRow(Object rowData, int... columns) {
        int first = -1;

        for (int colIdx : columns) {
            ensureCapacity();
            int n = nodeCount++;
            C[n] = colIdx;
            rowDataArray[n] = rowData;

            // Vertical insert
            D[n] = colIdx;
            U[n] = U[colIdx];
            D[U[colIdx]] = n;
            U[colIdx] = n;
            size[colIdx]++;

            // Horizontal insert
            if (first == -1) {
                first = n;
                L[n] = R[n] = n;
            } else {
                R[n] = first;
                L[n] = L[first];
                R[L[first]] = n;
                L[first] = n;
            }
        }
    }

    public List<List<Object>> solve() {
        search();
        return solutions;
    }

    private boolean search() {

        if (solutions.size() >= maxSolutions) {
            return true;
        }

        if (R[root] == root) {
            // Pre-allocate list with exact size
            List<Object> sol = new ArrayList<>(solutionDepth);
            for (int i = 0; i < solutionDepth; i++) {
                sol.add(rowDataArray[solution[i]]);
            }
            solutions.add(sol);

            return solutions.size() >= maxSolutions;
        }

        /*
        if (nodesVisited % 100000 == 0) {
            System.out.println("Currently " + solutions.size() + " solutions and " + nodesVisited + " nodes");
        }*/

        nodesVisited++;

        int c = chooseColumn();

        cover(c);

        for (int r = D[c]; r != c; r = D[r]) {
            DominoPlacement p = (DominoPlacement) rowDataArray[r];

            if (!PlacementValidator.checkLastPlacement(p, game)) continue;

            game.applyPlacement(p);

            if (forwardCheck) {
                if (!ForwardChecking.feasible(game, solutions.size())) {
                    game.undoPlacement(p);
                    continue;
                }
            }

            ensureSolutionCapacity();
            solution[solutionDepth++] = r;

            for (int j = R[r]; j != r; j = R[j]) {
                cover(C[j]);
            }

            if (search()) return true;

            for (int j = L[r]; j != r; j = L[j]) {
                uncover(C[j]);
            }

            solutionDepth--;
            game.undoPlacement(p);
        }

        uncover(c);

        return false;
    }

    private int chooseColumn() {
        int min = Integer.MAX_VALUE;
        int best = -1;

        int current = R[root];

        while (current != root) {
            int currentSize = size[current];

            // Immediate return on empty column
            if (currentSize == 0) return current;

            if (currentSize < min) {
                min = currentSize;
                best = current;

                // Early exit if we find size 1
                if (min == 1) return best;
            }

            current = R[current];
        }
        return best;
    }

    private void cover(int c) {
        // Remove column header
        int lc = L[c];
        int rc = R[c];
        R[lc] = rc;
        L[rc] = lc;

        for (int i = D[c]; i != c; i = D[i]) {
            for (int j = R[i]; j != i; j = R[j]) {
                int uj = U[j];
                int dj = D[j];
                int cj = C[j];

                D[uj] = dj;
                U[dj] = uj;
                size[cj]--;
            }
        }
    }

    private void uncover(int c) {
        for (int i = U[c]; i != c; i = U[i]) {
            for (int j = L[i]; j != i; j = L[j]) {
                int cj = C[j];
                int uj = U[j];
                int dj = D[j];

                size[cj]++;
                D[uj] = j;
                U[dj] = j;
            }
        }

        // Restore column header
        int lc = L[c];
        int rc = R[c];
        R[lc] = c;
        L[rc] = c;
    }
}