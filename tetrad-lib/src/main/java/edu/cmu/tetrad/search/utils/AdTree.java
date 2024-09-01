package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.collections4.map.HashedMap;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static edu.cmu.tetrad.search.utils.GraphSearchUtils.getAllRows;

/**
 * An AD tree is a data structure used to store the data for a given dataset in a way that makes it easy to calculate
 * cell counts for a multidimensional contingency table for a given set of variables.
 * <p>
 * It is a tree of cells, where each cell is a list of indices into the rows of the data set.
 * <p>
 * Each child cell is a subset of the parent cell for a particular value of a new variables in the list.
 * <p>
 * The list of variables is given in the method <code>buildTable</code>. The tree starts with a node representing the
 * list of all rows in the data (or all rows given in the constructor), and then subdivides the data by each variable in
 * turn. The leaves of the tree constitute the final subdivision of the data into cells, and the sizes of these cells
 * are the counts for the multidimensional contingency table for the given list of variables.
 * <p>
 * Continuous variables are ignored for this data structure.
 * <p>
 * This is an adaptation of the AD tree as described in: Anderson, B., &amp; Moore, A. W. (1998). AD-trees for fast
 * counting and rule learning. In KDD98 Conference.
 *
 * @author josephramsey 2024-8-28
 */
public class AdTree {
    /**
     * Indices of variables.
     */
    private final Map<Node, Integer> nodesHash;
    /**
     * Discrete data only.
     */
    private final int[][] discreteData;
    /**
     * The rows of the dataset to use; the default is to use all the rows. This is useful for subsampling.
     */
    private final List<Integer> rows;
    /**
     * The number of categories for each of the variables in the table.
     */
    private int[] dims;
    /**
     * The cell leaves.
     * <p>
     * This is a list of cells, where each cell is a list of indices into the rows of the data set.
     * <p>
     * The list at index i is the list of indices into the rows of the data set that are in cell i of the table; the
     * coordinates of this cell are calculated using the getCellIndex method.
     */
    private Map<Integer, Subdivision> leaves;
    /**
     * The variables in the table.
     */
    private List<DiscreteVariable> tableVariables;

    /**
     * Constructs an AD Leaf Tree for the given dataset, without subsampling.
     *
     * @param dataSet A discrete dataset.
     */
    public AdTree(DataSet dataSet) {
        this(dataSet, getAllRows(dataSet.getNumRows()));
    }

    /**
     * Constructs an AD Leaf Tree for the given dataset.
     *
     * @param dataSet A discrete dataset.
     * @param rows    The rows of the dataset to use; the default is to use all the rows.
     *                <p>
     *                This is useful for subsampling.
     */
    public AdTree(DataSet dataSet, List<Integer> rows) {
        validateDataSet(dataSet);
        this.rows = (rows == null) ? getAllRows(dataSet.getNumRows()) : validateRows(dataSet, rows);
        this.discreteData = initializeDiscreteData(dataSet);
        this.nodesHash = buildNodesHash(dataSet);
    }

    /**
     * Builds the contingency table based on the given list of discrete variables.
     *
     * @param variables A list of discrete variables.
     */
    public void buildTable(List<DiscreteVariable> variables) {
        validateVariables(variables);
        this.tableVariables = variables;
        this.dims = calculateDimensions(variables);
        this.leaves = buildSubdivisions(variables);
    }

    private void validateDataSet(DataSet dataSet) {
        if (dataSet == null) {
            throw new IllegalArgumentException("Data set must not be null.");
        }
    }

    private List<Integer> validateRows(DataSet dataSet, List<Integer> rows) {
        for (int row : rows) {
            if (row >= dataSet.getNumRows()) {
                throw new IllegalArgumentException("Row index out of bounds: " + row);
            }
        }
        return rows;
    }

    private int[][] initializeDiscreteData(DataSet dataSet) {
        int[][] discreteData = new int[dataSet.getNumColumns()][];
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node v = dataSet.getVariable(j);
            if (v instanceof DiscreteVariable) {
                discreteData[j] = extractColumn(dataSet, j);
            }
        }
        return discreteData;
    }

    private int[] extractColumn(DataSet dataSet, int columnIndex) {
        int[] column = new int[dataSet.getNumRows()];
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            column[i] = dataSet.getInt(i, columnIndex);
        }
        return column;
    }

    private Map<Node, Integer> buildNodesHash(DataSet dataSet) {
        Map<Node, Integer> nodesHash = new HashedMap<>();
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            nodesHash.put(dataSet.getVariable(j), j);
        }
        return nodesHash;
    }

    private void validateVariables(List<DiscreteVariable> variables) {
        if (variables == null || variables.isEmpty()) {
            throw new IllegalArgumentException("Variables list must not be null or empty.");
        }
        for (DiscreteVariable v : variables) {
            if (!nodesHash.containsKey(v)) {
                throw new IllegalArgumentException("Variable not in dataset: " + v.getName());
            }
        }
    }

    private int[] calculateDimensions(List<DiscreteVariable> variables) {
        int[] dimensions = new int[variables.size()];
        for (int i = 0; i < variables.size(); i++) {
            dimensions[i] = variables.get(i).getNumCategories();
        }
        return dimensions;
    }

    private Map<Integer, Subdivision> buildSubdivisions(List<DiscreteVariable> variables) {
        Map<Integer, Subdivision> subdivisions = new HashedMap<>();
        subdivisions.put(0, new Subdivision(null, -1, rows));
        for (DiscreteVariable v : variables) {
            subdivisions = subdivide(subdivisions, v);
        }
        return subdivisions;
    }

    private Map<Integer, Subdivision> subdivide(Map<Integer, Subdivision> subdivisions, DiscreteVariable variable) {
        Map<Integer, Subdivision> newSubdivisions = new HashedMap<>();
        int varIndex = nodesHash.get(variable);
        for (Map.Entry<Integer, Subdivision> entry : subdivisions.entrySet()) {
            List<Integer> cell = entry.getValue().cell();
            Map<Integer, List<Integer>> subcells = groupByCategory(cell, varIndex);
            newSubdivisions.putAll(createSubdivisions(entry.getValue(), subcells));
        }
        return newSubdivisions;
    }

    private Map<Integer, List<Integer>> groupByCategory(List<Integer> cell, int varIndex) {
        Map<Integer, List<Integer>> subcells = new HashedMap<>();
        for (int i : cell) {
            int category = discreteData[varIndex][i];
            subcells.computeIfAbsent(category, k -> new ArrayList<>()).add(i);
        }
        return subcells;
    }

    private Map<Integer, Subdivision> createSubdivisions(Subdivision parent, Map<Integer, List<Integer>> subcells) {
        Map<Integer, Subdivision> newSubdivisions = new HashedMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : subcells.entrySet()) {
            Subdivision newSubdivision = new Subdivision(parent, entry.getKey(), entry.getValue());
            newSubdivisions.put(getIndex(newSubdivision), newSubdivision);
        }
        return newSubdivisions;
    }

    /**
     * Return the number of cells in the table.
     *
     * @return the number of cells in the table.
     */
    public int getNumCells() {
        int numCells = 1;

        for (int dim : dims) {
            numCells *= dim;
        }

        return numCells;
    }

    /**
     * Returns the index of the cell in the table for the given coordinates. It is assumed that the given coordinates
     * are within the bounds of the table--that is, that the first coordinate is between 0 and the dimension of the
     * first of the first variable, the second coordinate is between 0 and the dimension of the second variable, and so
     * on. The coordinates are 0-based. It is also assumed that the number of coordinates is equal to the number of
     * variables in the table.
     *
     * @param coords the coordinates of the cell.
     * @return the index of the cell in the table.
     * @throws IllegalArgumentException if the coordinates are null, if there are too many coordinates, if a coordinate
     *                                  is out of bounds, or if the number of coordinates is not equal to the number of
     *                                  variables in the table.
     */
    public int getCellIndex(int... coords) {
        if (coords.length != tableVariables.size()) {
            throw new IllegalArgumentException("Wrong number of coordinates.");
        }

        return getCellIndexPrivate(coords);
    }

    /**
     * Returns the cell in the table for the given index.
     * <p>
     * This is a list of indices into the rows of the data set.
     *
     * @param cellIndex the index of the cell.
     * @return the cell in the table.
     * @see #getCellIndex(int[])
     */
    public List<Integer> getCell(int cellIndex) {
        Subdivision subdivision = leaves.get(cellIndex);
        return subdivision == null ? new ArrayList<>() : subdivision.cell();
    }

    /**
     * Returns the count of the cell in the table for the given coordinates.
     *
     * @param coords the coordinates of the cell.
     * @return the cell in the table.
     */
    public int getCount(int[] coords) {
        int cellIndex = getCellIndex(coords);
        return getCount(cellIndex);
    }

    /**
     * Returns the count of the cell in the table for the given index.
     *
     * @param cellIndex the index of the cell.
     * @return the cell in the table.
     */
    public int getCount(int cellIndex) {
        Subdivision subdivision = leaves.get(cellIndex);
        return subdivision == null ? 0 : subdivision.cell().size();
    }

    /**
     * Returns the index of the cell in the table for the given coordinates. It is assumed that the given coordinates
     * are within the bounds of the table--that is, that the first coordinate is between 0 and the number of categories
     * of the first variable, the second coordinate is between 0 and the number of categories of the second variable,
     * and so on.
     * <p>
     * There cannot be more coordinates than there are variables in the table.
     *
     * @param coords the coordinates of the cell.
     * @return the index of the cell in the table.
     */
    private int getCellIndexPrivate(int[] coords) {
        if (coords == null) {
            throw new IllegalArgumentException("Coordinates must not be null.");
        }

        if (coords.length > tableVariables.size()) {
            throw new IllegalArgumentException("Too many coordinates.");
        }

        for (int i = 0; i < coords.length; i++) {
            if (coords[i] < 0 || coords[i] >= dims[i]) {
                throw new IllegalArgumentException("Coordinate " + i + " is out of bounds.");
            }
        }

        int cellIndex = 0;

        for (int i = 0; i < coords.length; i++) {
            cellIndex *= dims[i];
            cellIndex += coords[i];
        }

        return cellIndex;
    }


    /**
     * Returns the index of the cell in the table for the given subdivision. We get this by following the previous
     * subdivisions back to the root and recording the categories and dimensions of each variable.
     *
     * @param subdivision the subdivision.
     * @return the index of the cell in the table.
     */
    private int getIndex(Subdivision subdivision) {
        LinkedList<Integer> indices = new LinkedList<>();

        while (subdivision.previousSubdivision() != null) {
            indices.addFirst(subdivision.category());
            subdivision = subdivision.previousSubdivision();
        }

        return getCellIndexPrivate(indices.stream().mapToInt(i -> i).toArray());
    }

    /**
     * Represents a subdivision of an AD Leaf Tree.
     * <p>
     * A subdivision is a combination of previous subdivisions, a category, and a list of cells. It is used to calculate
     * the cells for each combination of variables in the tree's table.
     *
     * @param previousSubdivision The previous subdivision in the tree.
     * @param category            The category of the current subdivision.
     * @param cell                The list of cells in the subdivision.
     */
    private record Subdivision(AdTree.Subdivision previousSubdivision, int category, List<Integer> cell) {
    }
}
