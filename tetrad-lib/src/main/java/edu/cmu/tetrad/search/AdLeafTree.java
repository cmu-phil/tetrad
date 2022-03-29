package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.collections4.map.HashedMap;

import java.util.*;

/**
 * Constructs and AD leaf tree on the fly. Probably doesn't speed up the first
 * algorithm it's used for much, but it should speed up subsequent algorithm
 * on the same data.
 * </p>
 * Continuous variables in the data set are ignored.
 *
 * @author Joseph Ramsey
 */
public class AdLeafTree {

    // The data set the tree is for.
    private final DataSet dataSet;

    // Contains the root of the tree.
    private List<Vary> baseCase;

    // Indices of variables.
    private final Map<Node, Integer> nodesHash;

    // Discrete data only.
    private final int[][] discreteData;

    // Dimensions of the discrete variables (otherwise 0).
    private final int[] dims;

    public AdLeafTree(final DataSet dataSet) {
        this.dataSet = dataSet;

        this.discreteData = new int[dataSet.getNumColumns()][];
        this.dims = new int[dataSet.getNumColumns()];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            final Node v = dataSet.getVariable(j);

            if (v instanceof DiscreteVariable) {
                final int[] col = new int[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    col[i] = dataSet.getInt(i, j);
                }

                this.discreteData[j] = col;
                this.dims[j] = ((DiscreteVariable) v).getNumCategories();
            }
        }

        this.nodesHash = new HashMap<>();

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            final Node v = dataSet.getVariable(j);
            this.nodesHash.put(v, j);
        }

    }

    /**
     * Finds the set of indices into the leaves of the tree for the given variables.
     * Counts are the sizes of the index sets.
     *
     * @param A A list of discrete variables.
     * @return The list of index sets of the first variable varied by the second variable,
     * and so on, to the last variable.
     */
    public List<List<Integer>> getCellLeaves(final List<DiscreteVariable> A) {
        A.sort(Comparator.comparingInt(o -> this.nodesHash.get(o)));

        if (this.baseCase == null) {
            final Vary vary = new Vary();
            this.baseCase = new ArrayList<>();
            this.baseCase.add(vary);
        }

        List<Vary> varies = this.baseCase;

        for (final DiscreteVariable v : A) {
            varies = getVaries(varies, this.nodesHash.get(v));
        }

        final List<List<Integer>> rows = new ArrayList<>();

        for (final Vary vary : varies) {
            rows.addAll(vary.getRows());
        }

        return rows;
    }

    /**
     * Finds the set of indices into the leaves of the tree for the given variables.
     * Counts are the sizes of the index sets.
     *
     * @param A A list of discrete variables.
     * @return The list of index sets of the first variable varied by the second variable,
     * and so on, to the last variable.
     */
    public List<List<List<Integer>>> getCellLeaves(final List<DiscreteVariable> A, final DiscreteVariable B) {
        Collections.sort(A, new Comparator<DiscreteVariable>() {

            @Override
            public int compare(final DiscreteVariable o1, final DiscreteVariable o2) {
                return Integer.compare(AdLeafTree.this.nodesHash.get(o1), AdLeafTree.this.nodesHash.get(o2));
            }
        });

        if (this.baseCase == null) {
            final Vary vary = new Vary();
            this.baseCase = new ArrayList<>();
            this.baseCase.add(vary);
        }

        List<Vary> varies = this.baseCase;

        for (final DiscreteVariable v : A) {
            varies = getVaries(varies, this.nodesHash.get(v));
        }

        final List<List<List<Integer>>> rows = new ArrayList<>();

        for (final Vary vary : varies) {
            for (int i = 0; i < vary.getNumCategories(); i++) {
                final Vary subvary = vary.getSubvary(this.nodesHash.get(B), i);
                rows.add(subvary.getRows());
            }
        }

        return rows;
    }

    public void setColumn(final DiscreteVariable var, final int[] col) {
        this.discreteData[this.dataSet.getColumn(var)] = col;
    }

    private List<Vary> getVaries(final List<Vary> varies, final int v) {
        final List<Vary> _varies = new ArrayList<>();

        for (final Vary vary : varies) {
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
            final List<Integer> _rows = new ArrayList<>();
            for (int i = 0; i < AdLeafTree.this.dataSet.getNumRows(); i++) {
                _rows.add(i);
            }

            this.subVaries.add(new HashMap<Integer, Vary>());
            this.numCategories = 1;
            this.rows.add(_rows);
            this.subVaries = new ArrayList<>();
            this.subVaries.add(new HashMap<Integer, Vary>());
        }

        public Vary(final int col, final int numCategories, final List<Integer> supRows, final int[][] discreteData) {
            this.col = col;
            this.numCategories = numCategories;

            for (int i = 0; i < numCategories; i++) {
                this.rows.add(new ArrayList<Integer>());
            }

            for (int i = 0; i < numCategories; i++) {
                this.subVaries.add(new HashedMap<Integer, Vary>());
            }

            for (final int i : supRows) {
                final int index = discreteData[col][i];
                if (index != -99) {
                    this.rows.get(index).add(i);
                }
            }
        }

        public List<List<Integer>> getRows() {
            return this.rows;
        }

        public Vary getSubvary(final int w, final int cat) {
            Vary vary = this.subVaries.get(cat).get(w);

            if (vary == null) {
                vary = new Vary(w, AdLeafTree.this.dims[w], this.rows.get(cat), AdLeafTree.this.discreteData);
                this.subVaries.get(cat).put(w, vary);
            }

            return vary;
        }

        public int getNumCategories() {
            return this.numCategories;
        }
    }
}
