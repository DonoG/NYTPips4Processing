/**
 * Represents a physical domino tile with two numeric sides (pips).
 */
public class Domino {

    public final int a, b;    // The values on each end of the domino.
    public final boolean symmetric; // do the pips values on this domino match?

    /**
     * Constructor to initialize a domino tile.
     * @param a The value on the first side.
     * @param b The value on the second side.
     */

    public Domino(int a, int b) {
        this.a = a;
        this.b = b;
        this.symmetric = a==b;
    }

    /**
     * Returns a string representation of the domino, e.g., "3-5".
     * Useful for debugging and printing results to the console.
     */

    @Override
    public String toString() {
        return a + "-" + b;
    }
}