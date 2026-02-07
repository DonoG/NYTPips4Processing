/**
 * Global configuration constants for the Pips puzzle solver.
 */

public class PipsConstants {

    // The range of numeric values (dots) allowed on the dominoes
    public static final int MIN_PIP = 0; // The lowest value (blank)
    public static final int MAX_PIP = 6; // The highest value (standard double-six set)

    // integers for constraint types
    public static final int TYPE_EMPTY     = 0;
    public static final int TYPE_SUM       = 1;
    public static final int TYPE_ALL_EQUAL = 2;
    public static final int TYPE_NOT_EQUAL = 3;
    public static final int TYPE_LESS_THAN = 4;
    public static final int TYPE_GREATER_THAN = 5;

    // Limits number of solutions that can be returned
    public static final int MAX_SOLUTIONS = 999999999;

    // The local directory name where puzzle JSON files are saved or loaded from
    public static final String GAME_FOLDER = "Pips Games";

    // The base URL for fetching daily puzzle data from the New York Times API
    public static final String NYT_JSON = "https://www.nytimes.com/svc/pips/v1/";
}