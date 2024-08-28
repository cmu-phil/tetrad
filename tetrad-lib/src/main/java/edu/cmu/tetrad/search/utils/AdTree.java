package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.collections4.map.HashedMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static edu.cmu.tetrad.search.utils.GraphSearchUtils.getAllRows;

/**
 * Constructs and AD tree for a given data set. The AD tree is used to calculate the cells of a multidimensional
 * contingency table for a given set of variables. The AD tree is constructed by specifying the variables to be used in
 * the table and then calling the calculateTable method. Each of these cells is a list of indices into the rows of the
 * data set; the sizes of these cells give the counts for the multidimensional contingency table over the given
 * variables. The number of cells in the table is given by the getNumCells method. The cells of the table are accessed
 * using the getCell methods.
 * <p>
 * Continuous variables in the data set are ignored.
 *
 * @author josephramsey
 * @version $Id: $Id
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
     * Dimensions of the discrete variables in the data. These are the variables passed into the constructor.
     */
    private final int[] dims;
    /**
     * The rows of the dataset to use; the default is to use all the rows. This is useful for subsampling.
     */
    private final List<Integer> rows;
    /**
     * The dimensions of the test variables, in order. These are the variables given to the calculateTable method.
     *
     * @see #calculateTable(List)
     */
    private int[] _dims;
    /**
     * Contains the root of the tree.
     */
    private List<Subdivision> allData;
    /**
     * The cell leaves.
     * <p>
     * This is a list of cells, where each cell is a list of indices into the rows of the data set.
     * <p>
     * The list at index i is the list of indices into the rows of the data set that are in cell i of the table; the
     * coordinates of this cell are calculated using the getCellIndex method.
     */
    private Map<Integer, List<Integer>> cellLeaves;

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
        if (dataSet == null) {
            throw new IllegalArgumentException("Data set must not be null.");
        }

        if (rows == null) {
            rows = getAllRows(dataSet.getNumRows());
        }

        // Make sure all rows are less than the number of rows in the dataset.
        for (int row : rows) {
            if (row >= dataSet.getNumRows()) {
                throw new IllegalArgumentException("Row index out of bounds: " + row);
            }
        }

        this.rows = rows;
        this.discreteData = new int[dataSet.getNumColumns()][];
        this.dims = new int[dataSet.getNumColumns()];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node v = dataSet.getVariable(j);

            if (v instanceof DiscreteVariable) {
                int[] col = new int[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    col[i] = dataSet.getInt(i, j);
                }

                this.discreteData[j] = col;
                this.dims[j] = ((DiscreteVariable) v).getNumCategories();
            }
        }

        this.nodesHash = new HashedMap<>();

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            this.nodesHash.put(dataSet.getVariable(j), j);
        }
    }

    /**
     * Return the number of cells in the table.
     *
     * @return the number of cells in the table.
     */
    public int getNumCells() {
        int numCells = 1;

        for (int dim : this._dims) {
            numCells *= dim;
        }

        return numCells;
    }

    /**
     * Returns the index of the cell in the table for the given coordinates.
     *
     * @param coords the coordinates of the cell.
     * @return the index of the cell in the table.
     */
    public int getCellIndex(int... coords) {
        if (_dims.length != coords.length) {
            throw new IllegalArgumentException("Wrong number of coordinates.");
        }

        for (int i = 0; i < coords.length; i++) {
            if (coords[i] < 0 || coords[i] >= _dims[i]) {
                throw new IllegalArgumentException("Coordinate " + i + " is out of bounds.");
            }
        }

        int cellIndex = 0;

        for (int i = 0; i < coords.length; i++) {
            cellIndex *= _dims[i];
            cellIndex += coords[i];
        }

        return cellIndex;
    }

    /**
     * Returns the cell in the table for the given index, or null if no cell has been recorded there (which case we may
     * assume the cell is empty).
     * <p>
     * This is a list of indices into the rows of the data set.
     *
     * @param cellIndex the index of the cell.
     * @return the cell in the table, or null if no cell has been recorded there.
     * @see #getCellIndex(int[])
     */
    public List<Integer> getCell(int cellIndex) {
        return cellLeaves.get(cellIndex);
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
        List<Integer> cell = cellLeaves.get(cellIndex);
        return cell == null ? 0 : cell.size();
    }

    /**
     * Calculates the cells for each combination of the given variables. The sizes of these cells are counts for a
     * multidimensional contingency table for the given variables.
     *
     * @param A A list of discrete variables. variable.
     */
    public void calculateTable(List<DiscreteVariable> A) {
        this._dims = new int[A.size()];

        for (int i = 0; i < A.size(); i++) {
            this._dims[i] = A.get(i).getNumCategories();
        }

        // All subdivisions of the data are subdivisions of the entire dataset (or list of all rows to be used).
        // If this hasn't been calculated yet, we calculate it now.
        if (this.allData == null) {
            Subdivision subdivision = new Subdivision(rows);
            this.allData = new ArrayList<>();
            this.allData.add(subdivision);
        }

        // Now we subdivide the data by each variable in A, in order.
        List<Subdivision> subdivisions = this.allData;

        for (DiscreteVariable v : A) {
            subdivisions = getSubdivision(subdivisions, this.nodesHash.get(v));
        }

        // We now need to reassemble the map from indices to cells. The subdivisions are in the right order, since
        // we constructed them in order. We just need to reassemble the cells in the right order for each category
        // of the last variable. We do not need to store cells if they are null or empty.
        Map<Integer, List<Integer>> cells = new HashedMap<>();

        int index = 0;

        for (Subdivision subdivision : subdivisions) {
            for (int i = 0; i < subdivision.getNumCategories(); i++) {
                List<Integer> cell = subdivision.getCells().get(i);

                if (cell != null && !cell.isEmpty()) {
                    cells.put(index, cell);
                }

                index++;
            }
        }

        this.cellLeaves = cells;
    }

    /**
     * Retrieves a list of subdivisions based on the provided list of subdivisions and a variable index.
     *
     * @param varies A list of subdivisions to be used for generating new subdivisions.
     * @param v      The variable index.
     * @return A list of subdivisions generated based on the provided subdivisions and variable index.
     */
    private List<Subdivision> getSubdivision(List<Subdivision> varies, int v) {
        List<Subdivision> subdivisions = new ArrayList<>();

        for (Subdivision subdivision : varies) {
            for (int i = 0; i < subdivision.getNumCategories(); i++) {
                subdivisions.add(subdivision.getNextSubdivision(v, i));
            }
        }

        return subdivisions;
    }

    /**
     * Represents a subdivision of a dataset in an AD Tree. This subdivides all the rows in the dataset into cells or
     * subcells of cells based on the categories of a single variables. The rows in the dataset are stored in a list of
     * cells, where each cell is a list of indices into the rows of the data set. The sizes of these lists give the
     * counts for the multidimensional contingency table over the given variables.
     */
    private class Subdivision {
        /**
         * The number of categories for the variables that we're subdividing by.
         */
        private final int numCategories;
        /**
         * The subdivided cells, in order.
         */
        private final Map<Integer, List<Integer>> cells = new HashedMap<>();
        /**
         * The subdivisions of the data by the categories of the variable we're subdividing by.
         */
        private List<Map<Integer, Subdivision>> subdivisions = new ArrayList<>();

        /**
         * This constructor is used to get the base case--i.e., the subdivision that consists of the entire dataset (or
         * at least all the rows under consideration in a subsample or bootstrap sample) as one big cell.
         */
        public Subdivision(List<Integer> rows) {
            this.subdivisions.add(new HashedMap<>());
            this.numCategories = 1;
            this.cells.put(0, rows);
            this.subdivisions = new ArrayList<>();
            this.subdivisions.add(new HashedMap<>());
        }

        /**
         * Represents a subdivision of a dataset in an AD Tree. This subdivides all the rows in the dataset into cells
         * or subcells of cells based on the categories of a single variable.
         *
         * @param var              The variable to subdivide by.
         * @param numCategories    The number of categories in the variable.
         * @param previousCellRows The rows in the previous cell that we're subdividing.
         * @param discreteData     The discrete data.
         */
        public Subdivision(int var, int numCategories, List<Integer> previousCellRows, int[][] discreteData) {
            this.numCategories = numCategories;

            for (int index = 0; index < numCategories; index++) {
                this.cells.put(index, new ArrayList<>());
            }

            for (int index = 0; index < numCategories; index++) {
                this.subdivisions.add(new HashedMap<>());
            }

            if (previousCellRows != null && !previousCellRows.isEmpty()) {
                for (int i : previousCellRows) {
                    int index = discreteData[var][i];

                    if (index != -99) {
                        this.cells.get(index).add(i);
                    }
                }

                for (int i = 0; i < numCategories; i++) {
                    if (this.cells.get(i).isEmpty()) {
                        this.cells.remove(i);
                    }
                }
            }
        }

        /**
         * Returns the cells of the Subdivision.
         *
         * @return a map from indices to cells.
         */
        public Map<Integer, List<Integer>> getCells() {
            return this.cells;
        }

        /**
         * Returns the next subdivision for a given variable and category in the AD Tree.
         *
         * @param w   The variable index.
         * @param cat The category index.
         * @return The next subdivision for the given variable and category.
         */
        public Subdivision getNextSubdivision(int w, int cat) {
            Subdivision subdivision = this.subdivisions.get(cat).get(w);

            if (subdivision == null) {
                subdivision = new Subdivision(w, AdTree.this.dims[w], this.cells.get(cat), AdTree.this.discreteData);
                this.subdivisions.get(cat).put(w, subdivision);
            }

            return subdivision;
        }

        /**
         * Returns the number of categories in the Subdivision.
         *
         * @return The number of categories in the Subdivision.
         */
        public int getNumCategories() {
            return this.numCategories;
        }
    }
}
