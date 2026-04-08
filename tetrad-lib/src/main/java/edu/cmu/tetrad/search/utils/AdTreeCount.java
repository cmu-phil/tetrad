package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.collections4.map.HashedMap;

import java.util.*;

/**
 * A count-only variant of AdTree. Instead of storing the full list of row indices in each
 * subdivision (cell), this class stores only the count of rows. This makes the cache entries
 * O(1) in memory rather than O(n), making it practical for large datasets and bootstrap contexts
 * where the full AdTree cache would cause severe GC pressure.
 * <p>
 * The tradeoff is that getCell() is not supported -- only counts are available.
 *
 * @author josephramsey 2024-9-1 (refactored from AdTree)
 */
public class AdTreeCount {

    /**
     * Indices of variables.
     */
    private final Map<Node, Integer> nodesHash;

    /**
     * Discrete data only.
     */
    private final int[][] discreteData;

    /**
     * The rows of the dataset to use.
     */
    private final List<Integer> rows;

    /**
     * The maximum size of the cache.
     */
    private int maxCacheSize = 1000;

    /**
     * Cache storing counts (not row lists) keyed by variable prefix.
     * Each value is a map from cell index to count.
     */
    private final Map<List<Node>, Map<Integer, Integer>> subdivisionCache =
            new LinkedHashMap<>(maxCacheSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<List<Node>, Map<Integer, Integer>> eldest) {
                    return this.size() > maxCacheSize;
                }
            };

    /**
     * The maximum depth of the cache.
     */
    private int cacheDepthLimit = 10;

    /**
     * The number of categories for each variable in the current table.
     */
    private int[] dims;

    /**
     * The leaf counts: map from cell index to count.
     */
    private Map<Integer, Integer> leaves;

    /**
     * The variables in the current table (sorted order).
     */
    private List<DiscreteVariable> tableVariables;

    /**
     * Inverse mapping from sorted index to original index.
     */
    private HashedMap<Integer, Integer> inverseMap;

    /**
     * Constructs an AdTreeCount for the given dataset using all rows.
     *
     * @param dataSet A discrete dataset.
     */
    public AdTreeCount(DataSet dataSet) {
        this(dataSet, getAllRows(dataSet.getNumRows()));
    }

    /**
     * Constructs an AdTreeCount for the given dataset and row subset.
     *
     * @param dataSet A discrete dataset.
     * @param rows    The rows to use; if null, all rows are used.
     */
    public AdTreeCount(DataSet dataSet, List<Integer> rows) {
        validateDataSet(dataSet);
        this.rows = (rows == null) ? getAllRows(dataSet.getNumRows()) : validateRows(dataSet, rows);
        this.discreteData = initializeDiscreteData(dataSet);
        this.nodesHash = buildNodesHash(dataSet);
    }

    /**
     * Constructs an AdTreeCount using pre-initialized discrete data, avoiding re-scanning
     * the dataset. Use this in bootstrap contexts where only the rows change between samples.
     *
     * @param discreteData Pre-built column-major int[][] from the dataset.
     * @param nodesHash    Pre-built variable to column index map.
     * @param rows         The rows for this bootstrap sample.
     * @param numRows      The total number of rows in the dataset (for validation).
     */
    public AdTreeCount(int[][] discreteData, Map<Node, Integer> nodesHash, List<Integer> rows, int numRows) {
        this.discreteData = discreteData;
        this.nodesHash = nodesHash;
        this.rows = (rows == null) ? getAllRows(numRows) : rows;
    }

    private static List<Integer> getAllRows(int numRows) {
        List<Integer> rows = new ArrayList<>(numRows);
        for (int i = 0; i < numRows; i++) rows.add(i);
        return rows;
    }

    /**
     * Builds the count table for the given variables.
     *
     * @param variables The variables to build the table for.
     */
    public void buildTable(List<DiscreteVariable> variables) {
        ArrayList<DiscreteVariable> originalOrder = new ArrayList<>(variables);
        ArrayList<DiscreteVariable> sortedOrder = new ArrayList<>(variables);
        sortedOrder.sort(Comparator.comparingInt(nodesHash::get));

        this.inverseMap = new HashedMap<>();
        Map<DiscreteVariable, Integer> originalIndices = new HashMap<>();
        for (int i = 0; i < originalOrder.size(); i++) {
            originalIndices.put(originalOrder.get(i), i);
        }
        for (int i = 0; i < sortedOrder.size(); i++) {
            this.inverseMap.put(i, originalIndices.get(sortedOrder.get(i)));
        }

        validateVariables(sortedOrder);
        this.tableVariables = sortedOrder;
        this.dims = calculateDimensions(sortedOrder);

        // Start with a single root cell containing all row counts.
        // We use a map from cell index to count; the root is index 0 with count = rows.size().
        final Map<Integer, Integer>[] holder = new Map[]{new HashedMap<>()};
        holder[0].put(0, rows.size());

        // Also maintain the actual row groupings for subdivision — but only transiently,
        // never cached. We cache only the resulting counts.
        final Map<Integer, List<Integer>>[] rowHolder = new Map[]{new HashedMap<>()};
        rowHolder[0].put(0, new ArrayList<>(rows));

        List<Node> cacheKey = new ArrayList<>();

        for (DiscreteVariable v : sortedOrder) {
            cacheKey.add(v);
            List<Node> key = Collections.unmodifiableList(new ArrayList<>(cacheKey));

            if (key.size() <= cacheDepthLimit && subdivisionCache.containsKey(key)) {
                // Cache hit: restore counts directly, but we lose row lists so must
                // recompute row groupings from scratch for subsequent levels.
                holder[0] = subdivisionCache.get(key);
                // Recompute row groupings up to this depth for the next iteration.
                rowHolder[0] = recomputeRowGroups(sortedOrder.subList(0, key.size()));
            } else {
                // Cache miss: subdivide using current row groupings.
                Map<Integer, List<Integer>> newRowGroups = subdivideRows(rowHolder[0], v);
                rowHolder[0] = newRowGroups;

                // Derive counts from the new row groups.
                Map<Integer, Integer> newCounts = new HashedMap<>();
                for (Map.Entry<Integer, List<Integer>> entry : newRowGroups.entrySet()) {
                    newCounts.put(entry.getKey(), entry.getValue().size());
                }
                holder[0] = newCounts;

                if (key.size() <= cacheDepthLimit) {
                    subdivisionCache.put(key, new HashedMap<>(newCounts));
                }
            }
        }

        this.leaves = holder[0];
    }

    /**
     * Returns the count for the cell at the given coordinates.
     *
     * @param coords The coordinates of the cell.
     * @return The count.
     */
    public int getCount(int[] coords) {
        int cellIndex = getCellIndex(coords);
        return leaves.getOrDefault(cellIndex, 0);
    }

    /**
     * Returns the total number of cells in the table.
     *
     * @return The number of cells.
     */
    public int getNumCells() {
        int numCells = 1;
        for (int dim : dims) numCells *= dim;
        return numCells;
    }

    /**
     * Returns the cell index for the given coordinates.
     *
     * @param coords The coordinates.
     * @return The cell index.
     */
    public int getCellIndex(int... coords) {
        if (coords.length != tableVariables.size()) {
            throw new IllegalArgumentException("Wrong number of coordinates.");
        }
        return getCellIndexPrivate(coords, true);
    }

    /**
     * Sets the maximum cache size.
     *
     * @param maxCacheSize Must be >= 1.
     */
    public void setMaxCacheSize(int maxCacheSize) {
        if (maxCacheSize < 1) throw new IllegalArgumentException("Cache size must be at least 1.");
        this.maxCacheSize = maxCacheSize;
    }

    /**
     * Sets the cache depth limit. Set to 0 to disable caching entirely.
     *
     * @param cacheDepthLimit Must be >= 0.
     */
    public void setCacheDepthLimit(int cacheDepthLimit) {
        if (cacheDepthLimit < 0) throw new IllegalArgumentException("Cache depth limit must be at least 0.");
        this.cacheDepthLimit = cacheDepthLimit;
    }

    /**
     * Clears the cache. Useful between bootstrap runs if sharing an instance.
     */
    public void clearCache() {
        subdivisionCache.clear();
    }

    /**
     * Returns the pre-built discrete data array, for sharing across bootstrap instances.
     *
     * @return The discrete data array.
     */
    public int[][] getDiscreteData() {
        return discreteData;
    }

    /**
     * Returns the nodes hash, for sharing across bootstrap instances.
     *
     * @return The nodes hash.
     */
    public Map<Node, Integer> getNodesHash() {
        return nodesHash;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Recomputes row groupings from scratch for the given variable prefix.
     * Used after a cache hit restores counts but loses row lists.
     */
    private Map<Integer, List<Integer>> recomputeRowGroups(List<DiscreteVariable> prefix) {
        Map<Integer, List<Integer>> groups = new HashedMap<>();
        groups.put(0, new ArrayList<>(rows));

        for (DiscreteVariable v : prefix) {
            groups = subdivideRows(groups, v);
        }

        return groups;
    }

    private Map<Integer, List<Integer>> subdivideRows(Map<Integer, List<Integer>> groups, DiscreteVariable variable) {
        Map<Integer, List<Integer>> newGroups = new HashedMap<>();
        int varIndex = nodesHash.get(variable);

        for (Map.Entry<Integer, List<Integer>> entry : groups.entrySet()) {
            int parentIndex = entry.getKey();
            List<Integer> cell = entry.getValue();

            Map<Integer, List<Integer>> subcells = new HashedMap<>();
            for (int row : cell) {
                int category = discreteData[varIndex][row];
                subcells.computeIfAbsent(category, k -> new ArrayList<>()).add(row);
            }

            for (Map.Entry<Integer, List<Integer>> sub : subcells.entrySet()) {
                int childIndex = computeChildIndex(parentIndex, sub.getKey(), varIndex);
                newGroups.put(childIndex, sub.getValue());
            }
        }

        return newGroups;
    }

    private int computeChildIndex(int parentIndex, int category, int varIndex) {
        // We need the dimension of this variable to compute the child index correctly.
        // Find which position in tableVariables this varIndex corresponds to.
        int dim = 1;
        for (int i = 0; i < tableVariables.size(); i++) {
            if (nodesHash.get(tableVariables.get(i)) == varIndex) {
                dim = dims[i];
                break;
            }
        }
        return parentIndex * dim + category;
    }

    private int getCellIndexPrivate(int[] coords, boolean inverse) {
        if (coords == null) throw new IllegalArgumentException("Coordinates must not be null.");
        if (coords.length > tableVariables.size()) throw new IllegalArgumentException("Too many coordinates.");

        int cellIndex = 0;
        for (int i = 0; i < coords.length; i++) {
            int mappedIndex = inverse ? inverseMap.get(i) : i;
            if (coords[mappedIndex] < 0 || coords[mappedIndex] >= dims[i]) {
                throw new IllegalArgumentException("Coordinate " + i + " is out of bounds.");
            }
            cellIndex *= dims[i];
            cellIndex += coords[mappedIndex];
        }
        return cellIndex;
    }

    private void validateDataSet(DataSet dataSet) {
        if (dataSet == null) throw new IllegalArgumentException("Data set must not be null.");
    }

    private List<Integer> validateRows(DataSet dataSet, List<Integer> rows) {
        for (int row : rows) {
            if (row >= dataSet.getNumRows()) throw new IllegalArgumentException("Row index out of bounds: " + row);
        }
        return rows;
    }

    private int[][] initializeDiscreteData(DataSet dataSet) {
        if (dataSet instanceof BoxDataSet dataSet1) {
            if (dataSet1.getDataBox() instanceof VerticalIntDataBox dataBox) {
                return dataBox.getVariableVectors();
            } else {
                return new VerticalIntDataBox(dataSet1.getDataBox()).getVariableVectors();
            }
        }

        int[][] data = new int[dataSet.getNumColumns()][];
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            if (dataSet.getVariable(j) instanceof DiscreteVariable) {
                data[j] = extractColumn(dataSet, j);
            }
        }
        return data;
    }

    private int[] extractColumn(DataSet dataSet, int col) {
        int[] column = new int[dataSet.getNumRows()];
        for (int i = 0; i < dataSet.getNumRows(); i++) column[i] = dataSet.getInt(i, col);
        return column;
    }

    private Map<Node, Integer> buildNodesHash(DataSet dataSet) {
        Map<Node, Integer> map = new HashedMap<>();
        for (int j = 0; j < dataSet.getNumColumns(); j++) map.put(dataSet.getVariable(j), j);
        return map;
    }

    private void validateVariables(List<DiscreteVariable> variables) {
        if (variables == null || variables.isEmpty()) {
            throw new IllegalArgumentException("Variables list must not be null or empty.");
        }
        for (DiscreteVariable v : variables) {
            if (!nodesHash.containsKey(v)) throw new IllegalArgumentException("Variable not in dataset: " + v.getName());
        }
    }

    private int[] calculateDimensions(List<DiscreteVariable> variables) {
        int[] dimensions = new int[variables.size()];
        for (int i = 0; i < variables.size(); i++) dimensions[i] = variables.get(i).getNumCategories();
        return dimensions;
    }
}