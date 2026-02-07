/**
 * Represents an individual cell within the puzzle grid.
 */

public class Cell {
    public int r, c;
    public int value = -1;
    public int regionId;
    public int forbiddenMask = 0;

    public Cell(int r, int c, int regionId) {
        this.r = r;
        this.c = c;
        this.regionId = regionId;
    }
}