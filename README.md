NYT Pips Batch Solver
A high-performance automated solver for NYT Pips puzzles using the Dancing Links (DLX) algorithm for exact cover problems. This tool downloads and saves locally each JSON files from the NYT site, and allows for batch processing of historical puzzles, range-based solving, and shows statistics as puzzles solve.

As of 7 February 2026, it is able to find a soluton for around ~500 NYT puzzles in well under a second on my machine. Otheriwse, it takes around 4-5 seconds to explore all possible solutions for ~500 puzzles

The most stubborn puzzles are from 15 September 2025 and 28 October 2025 - due to the large search space. I'd hoped to be able get all solutions of these puzzles < 1 sec each, but haven't quite been able to hit that

🚀 Features
DLX Algorithm Implementation

Batch Processing: Solve puzzles by specific dates, date ranges, or difficulty levels (easy, medium, hard).

Multithreaded Execution: Uses Processing's thread() to keep the UI responsive while the solver runs in the background.

Detailed Metrics: Reports nodes visited, solve time (ms), and total solution counts for every puzzle.

🛠 Requirements
Processing 4.0+: 

📂 Project Structure
To run this sketch, ensure all provided .java files are in your sketchbook folder

🖥 Usage
Basic Setup
By default, the solver is configured to run in setup() via a background thread. You can modify the batchSolvePips() method to target specific puzzles:

// Example: Solve a specific range of dates
SolveOptions options = SolveOptions.Builder
    .forRange("2025-08-18", "2025-12-31")
    .difficulty("hard")
    .maxSolutions(1)
    .build();

Then call solveBatch(options);

Visual Feedback
The Processing window provides real-time status updates:

Grey Screen: Solver is currently processing (check the console for live logs).

Green Screen: Batch complete.

Red Screen: Batch complete, but some puzzles were missing or hit errors.

📊 Logic & Performance
The solver treats the Pips puzzle as an Exact Cover problem. Each domino placement is a row in a sparse matrix, and each cell/domino requirement is a column.

Beyond DLX, the solver uses some pre-pruning and some look-ahead logic to fail fast on impossible branches.

📝 License
This project is for educational and research purposes. All puzzle data is property of the New York Times.
