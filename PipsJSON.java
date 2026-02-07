import processing.core.PApplet;
import processing.data.JSONArray;
import processing.data.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * PipsJSON handles the fetching, caching, and parsing of NYT Pips puzzle data.
 * It manages the transition from raw JSON to the internal PipsGame model.
 */
public class PipsJSON {

    private final PApplet app;
    private final File cacheDirectory;

    public PipsJSON(PApplet app) {
        this.app = app;
        this.cacheDirectory = new File(app.sketchPath(PipsConstants.GAME_FOLDER));
        ensureDirectoryExists(cacheDirectory);
    }

    private static void ensureDirectoryExists(File folder) {
        if (!folder.exists()) folder.mkdirs();
    }

    private static String readUTF8File(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Entry point: retrieves puzzle data from local cache or remote URL and parses it.
     */
    public PipsGame getPipsGame(String dateStr, String difficulty) {
        String jsonContent = fetchJsonContent(dateStr, difficulty);
        return parsePipsGame(jsonContent, difficulty);
    }

    /**
     * Checks local storage for a cached puzzle file before attempting a download.
     */
    private String fetchJsonContent(String dateStr, String difficulty) {
        String fileName = String.format("NYT_Pips_%s_%s.json", dateStr, difficulty);
        File cacheFile = new File(cacheDirectory, fileName);

        if (cacheFile.exists()) {
            String cachedData = readUTF8File(cacheFile);
            // Verify data is not corrupted or effectively null
            if (cachedData != null && !cachedData.trim().equals("null")) {
                return cachedData;
            }
        }

        return downloadAndCacheJSON(dateStr, difficulty, cacheFile);
    }

    /**
     * Downloads puzzle data from NYT and saves it to the local cache.
     */
    private String downloadAndCacheJSON(String dateStr, String difficulty, File targetFile) {
        String url = PipsConstants.NYT_JSON + dateStr + ".json";

        try {
            JSONObject jsonResponse = app.loadJSONObject(url);

            if (jsonResponse == null || jsonResponse.size() == 0) {
                System.err.println("Failed to retrieve or empty JSON from: " + url);
                return null;
            }

            // Save the raw JSON for future offline use
            app.saveJSONObject(jsonResponse, targetFile.getAbsolutePath());
            return jsonResponse.toString();

        } catch (Exception e) {
            System.err.println("Network or I/O Error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Core logic to map JSON fields to the PipsGame object.
     */
    private PipsGame parsePipsGame(String jsonStr, String difficulty) {
        if (jsonStr == null || jsonStr.isEmpty()) return null;

        JSONObject root = app.parseJSONObject(jsonStr);
        if (root == null || !root.hasKey(difficulty)) return null;

        JSONObject gameData = root.getJSONObject(difficulty);
        PipsGame game = new PipsGame();

        // Basic Metadata
        game.id = gameData.getInt("id", -1);
        game.backendId = gameData.getString("backendId", "");

        // Handle optional solution array
        JSONArray solutionArray = gameData.hasKey("solution") ? gameData.getJSONArray("solution") : null;

        // Parse list of puzzle creators
        game.constructors = parseStringOrObjectArray(gameData, "constructors");

        // Parse Dominoes (the pieces available for the puzzle)
        JSONArray dominoArray = gameData.getJSONArray("dominoes");
        game.dominoes.clear();

        for (int i = 0; i < dominoArray.size(); i++) {
            JSONArray pair = dominoArray.getJSONArray(i);
            if (pair.size() == 2) {
                game.dominoes.add(new Domino(pair.getInt(0), pair.getInt(1)));
            }
        }

        // Sort dominoes by total pip count for easier UI/Solver handling
        game.dominoes.sort((d1, d2) -> Integer.compare(d1.a + d1.b, d2.a + d2.b));

        // Parse Regions (the constraints placed on specific grid cells)
        parseRegions(game, gameData);

        // Finalize state if solution exists
        if (solutionArray != null) {
            game.JSONSolution = buildSolutionGridString(solutionArray, dominoArray);
        }

        game.finaliseGrid();
        game.initialSupply(); // Initial pip inventory
        game.initialSumState(); // Status of region sums

        return game;
    }

    /**
     * Iterates through the 'regions' array and maps JSON logic to internal enums/types.
     */
    private void parseRegions(PipsGame game, JSONObject gameData) {
        JSONArray regionsArray = gameData.getJSONArray("regions");
        game.regions.clear();

        for (int i = 0; i < regionsArray.size(); i++) {
            JSONObject regionJson = regionsArray.getJSONObject(i);
            Region region = new Region();
            region.id = i;

            // Map NYT constraint names to ID based constants
            String rawType = regionJson.getString("type", "empty").toLowerCase();

            switch (rawType) {
                case "equals":
                    region.constraintTypeId = PipsConstants.TYPE_ALL_EQUAL;
                    break;
                case "unequal":
                    region.constraintTypeId = PipsConstants.TYPE_NOT_EQUAL;
                    break;
                case "sum":
                    region.constraintTypeId = PipsConstants.TYPE_SUM;
                    break;
                case "less":
                    region.constraintTypeId = PipsConstants.TYPE_LESS_THAN;
                    break;
                case "greater":
                    region.constraintTypeId = PipsConstants.TYPE_GREATER_THAN;
                    break;
                default:
                    region.constraintTypeId = PipsConstants.TYPE_EMPTY;
                    break;
            }

            region.target = regionJson.getInt("target", 0);

            // Map 0-indexed JSON coordinates to 1-indexed Game coordinates
            JSONArray indices = regionJson.getJSONArray("indices");
            for (int j = 0; j < indices.size(); j++) {
                JSONArray coord = indices.getJSONArray(j);

                // Calculate values first
                int row = coord.getInt(0) + 1;
                int col = coord.getInt(1) + 1;
                int rId = i;

                Cell cell = new Cell(row, col, rId);

                region.cells.add(cell);
            }
            game.regions.add(region);
        }
    }

    /**
     * Converts the nested solution arrays into a human-readable ASCII grid.
     */
    private String buildSolutionGridString(JSONArray solArray, JSONArray domArray) {
        int maxR = 0, maxC = 0;

        // Find grid boundaries
        for (int i = 0; i < solArray.size(); i++) {
            JSONArray pair = solArray.getJSONArray(i);
            for (int j = 0; j < 2; j++) {
                maxR = Math.max(maxR, pair.getJSONArray(j).getInt(0));
                maxC = Math.max(maxC, pair.getJSONArray(j).getInt(1));
            }
        }

        int[][] grid = new int[maxR + 1][maxC + 1];
        for (int r = 0; r <= maxR; r++) {
            for (int c = 0; c <= maxC; c++) grid[r][c] = -1;
        }

        // Map values to grid positions
        for (int i = 0; i < solArray.size(); i++) {
            JSONArray locPair = solArray.getJSONArray(i);
            JSONArray valPair = domArray.getJSONArray(i);
            grid[locPair.getJSONArray(0).getInt(0)][locPair.getJSONArray(0).getInt(1)] = valPair.getInt(0);
            grid[locPair.getJSONArray(1).getInt(0)][locPair.getJSONArray(1).getInt(1)] = valPair.getInt(1);
        }

        StringBuilder sb = new StringBuilder();
        for (int[] row : grid) {
            for (int val : row) {
                sb.append(val == -1 ? " . " : " " + val + " ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Flexible parser for fields that might be a String, an Array of Strings,
     * or an Array of Objects (e.g., 'constructors').
     */
    private String[] parseStringOrObjectArray(JSONObject parent, String key) {
        if (!parent.hasKey(key)) return null;

        Object val = parent.get(key);

        if (val instanceof JSONArray) {
            JSONArray arr = (JSONArray) val; // Manual cast required
            String[] result = new String[arr.size()];

            for (int i = 0; i < arr.size(); i++) {
                Object item = arr.get(i);

                if (item instanceof JSONObject) {
                    JSONObject obj = (JSONObject) item; // Manual cast required
                    result[i] = obj.getString("name", obj.toString());
                } else {
                    result[i] = item.toString();
                }
            }
            return result;
        }

        return new String[]{val.toString()};
    }
}