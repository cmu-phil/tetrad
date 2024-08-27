package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.collections4.map.HashedMap;

import java.util.*;

/**
 * Constructs and AD leaf tree on the fly. Probably doesn't speed up the first algorithm it's used for much, but it
 * should speed up subsequent algorithms on the same data.
 * <p>
 * Continuous variables in the data set are ignored.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class AdTree {
    /**
     * The data set the tree is for.
     */
    private final DataSet dataSet;
    /**
     * Indices of variables.
     */
    private final Map<Node, Integer> nodesHash;
    /**
     * Discrete data only.
     */
    private final int[][] discreteData;
    /**
     * Dimensions of the discrete variables (otherwise 0).
     */
    private final int[] dims;
    /**
     * The dimensions of the test variables, in order.
     */
    private int[] _dims;
    /**
     * Contains the root of the tree.
     */
    private List<Vary> baseCase;
    /**
     * The cell leaves. This is a list of lists of integers, where each list of integers is a list of indices into the
     * rows of the data set. The list at index i is the list of indices into the rows of the data set that are in cell
     * i of the table; the coordinates of this cell are calculated using the getCellIndex method.
     */
    private List<List<Integer>> cellLeaves;

    /**
     * Constructs an AD Leaf Tree for the given dataset.
     *
     * @param dataSet A discrete dataset.
     */
    public AdTree(DataSet dataSet) {
        this.dataSet = dataSet;

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

        this.nodesHash = new HashMap<>();

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
    public int getCellIndex(int...coords) {
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
     * Returns the cell in the table for the given coordinates. This is a list of indices into the rows of the data
     * set.
     *
     * @param coords the coordinates of the cell.
     * @return the cell in the table.
     */
    public List<Integer> getCell(int[] coords) {
        int cellIndex = getCellIndex(coords);
        return getCell(cellIndex);
    }

    /**
     * Returns the cell in the table for the given index. This is a list of indices into the rows of the data set.
     *
     * @param cellIndex the index of the cell.
     * @return the cell in the table.
     * @see #getCellIndex(int[])
     */
    public List<Integer> getCell(int cellIndex) {
        return cellLeaves.get(cellIndex);
    }

    /**
     * Calculates the cells for each combination of the given variables. The sizes of these cells are counts for
     * a multidimensional contingency table for the given variables.
     *
     * @param A A list of discrete variables. variable.
     */
    public void calculateTable(List<DiscreteVariable> A) {
//        A.sort(Comparator.comparingInt(this.nodesHash::get));

        this._dims = new int[A.size()];
        for (int i = 0; i < A.size(); i++) {
            this._dims[i] = A.get(i).getNumCategories();
        }

        if (this.baseCase == null) {
            Vary vary = new Vary();
            this.baseCase = new ArrayList<>();
            this.baseCase.add(vary);
        }

        List<Vary> varies = this.baseCase;

        for (DiscreteVariable v : A) {
            varies = getVaries(varies, this.nodesHash.get(v));
        }

        List<List<Integer>> rows = new ArrayList<>();

        for (Vary vary : varies) {
            rows.addAll(vary.getRows());
        }

        this.cellLeaves = rows;
    }

    private List<Vary> getVaries(List<Vary> varies, int v) {
        List<Vary> _varies = new ArrayList<>();

        for (Vary vary : varies) {
            for (int i = 0; i < vary.getNumCategories(); i++) {
                _varies.add(vary.getSubvary(v, i));
            }
        }

        return _varies;
    }

    private class Vary {
        int col;
        int numCategories;
        List<List<Integer>> rows = new ArrayList<>();
        List<Map<Integer, Vary>> subVaries = new ArrayList<>();

        // Base case.
        public Vary() {
            List<Integer> _rows = new ArrayList<>();
            for (int i = 0; i < AdTree.this.dataSet.getNumRows(); i++) {
                _rows.add(i);
            }

            this.subVaries.add(new HashMap<>());
            this.numCategories = 1;
            this.rows.add(_rows);
            this.subVaries = new ArrayList<>();
            this.subVaries.add(new HashMap<>());
        }

        public Vary(int col, int numCategories, List<Integer> supRows, int[][] discreteData) {
            this.col = col;
            this.numCategories = numCategories;

            for (int i = 0; i < numCategories; i++) {
                this.rows.add(new ArrayList<>());
            }

            for (int i = 0; i < numCategories; i++) {
                this.subVaries.add(new HashedMap<>());
            }

            for (int i : supRows) {
                int index = discreteData[col][i];
                if (index != -99) {
                    this.rows.get(index).add(i);
                }
            }
        }

        public List<List<Integer>> getRows() {
            return this.rows;
        }

        public Vary getSubvary(int w, int cat) {
            Vary vary = this.subVaries.get(cat).get(w);

            if (vary == null) {
                vary = new Vary(w, AdTree.this.dims[w], this.rows.get(cat), AdTree.this.discreteData);
                this.subVaries.get(cat).put(w, vary);
            }

            return vary;
        }

        public int getNumCategories() {
            return this.numCategories;
        }
    }
}
