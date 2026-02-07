import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Immutable configuration class for the Pips solver.
 * Enforces date boundaries: August 18, 2025, to Today + 30 days.
 */
public class SolveOptions {
    private final List<String> dates;
    private final int solutionsToShow;
    private final String difficulty;
    private final long maxSolutions;

    private SolveOptions(Builder builder) {
        this.dates = applyDateConstraints(builder.dates);
        this.solutionsToShow = builder.solutionsToShow;
        this.difficulty = builder.difficulty;
        this.maxSolutions = builder.maxSolutions;
    }

    /**
     * Filters input dates to the valid window and prints a warning if any are discarded.
     */
    private static List<String> applyDateConstraints(List<String> inputDates) {
        if (inputDates == null || inputDates.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDate minDate = LocalDate.of(2025, 8, 18);
        LocalDate maxDate = LocalDate.now().plusDays(30);

        // Process the dates into a concrete list first to compare sizes
        List<String> filteredDates = inputDates.stream()
                .map(String::trim)
                .distinct()
                .map(LocalDate::parse)
                .filter(d -> !d.isBefore(minDate) && !d.isAfter(maxDate))
                .sorted()
                .map(LocalDate::toString)
                .collect(Collectors.toList());

        // Check if any dates were removed (comparing unique input count vs filtered count)
        long uniqueInputCount = inputDates.stream().map(String::trim).distinct().count();

        if (filteredDates.size() < uniqueInputCount) {
            System.out.println("WARNING: Some requested dates were outside the valid range ("
                    + minDate + " to " + maxDate + ") and have been removed.");
            System.out.println("Requested unique dates: " + uniqueInputCount);
            System.out.println("Accepted dates:         " + filteredDates.size());
        }

        return Collections.unmodifiableList(filteredDates);
    }

    // --- Getters ---
    public List<String> getDates() { return dates; }
    public int getSolutionsToShow() { return solutionsToShow; }
    public String getDifficulty() { return difficulty; }
    public long getMaxSolutions() { return maxSolutions; }

    /**
     * Fluent Builder class to simplify the creation of SolveOptions.
     */
    public static class Builder {
        private List<String> dates;
        private int solutionsToShow = 0;
        private String difficulty = "hard";
        private long maxSolutions = PipsConstants.MAX_SOLUTIONS;

        public Builder(List<String> dates) {
            this.dates = new ArrayList<>(dates);
        }

        public static Builder forSingleDate(String date) {
            return new Builder(Collections.singletonList(date));
        }

        public static Builder forRange(String start, String end) {
            return new Builder(generateRange(start, end));
        }

        public static Builder forDates(List<String> dates) {
            return new Builder(dates);
        }

        private static List<String> generateRange(String start, String end) {
            LocalDate d1 = LocalDate.parse(start.trim());
            LocalDate d2 = LocalDate.parse(end.trim());
            List<String> range = new ArrayList<>();
            while (!d1.isAfter(d2)) {
                range.add(d1.toString());
                d1 = d1.plusDays(1);
            }
            return range;
        }

        public Builder solutionsToShow(int count) { this.solutionsToShow = count; return this; }
        public Builder difficulty(String diff) { this.difficulty = (diff != null) ? diff.toLowerCase().trim() : "hard"; return this; }
        public Builder maxSolutions(long max) { this.maxSolutions = max; return this; }

        public SolveOptions build() {
            return new SolveOptions(this);
        }
    }
}