package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.collections4.iterators.CollatingIterator;
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
    private DataSet dataSet;

    // Contains the root of the tree.
    private List<Vary> baseCase;

    // Indices of variables.
    private Map<Node, Integer> nodesHash;

    // Discrete data only.
    private int[][] discreteData;

    // Dimensions of the discrete variables (otherwise 0).
    private int[] dims;

    public AdLeafTree(DataSet dataSet) {
        this.dataSet = dataSet;

        discreteData = new int[dataSet.getNumColumns()][];
        dims = new int[dataSet.getNumColumns()];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node v = dataSet.getVariable(j);

            if (v instanceof DiscreteVariable) {
                int[] col = new int[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    col[i] = dataSet.getInt(i, j);
                }

                discreteData[j] = col;
                dims[j] = ((DiscreteVariable) v).getNumCategories();
            }
        }

        nodesHash = new HashMap<>();

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node v = dataSet.getVariable(j);
            nodesHash.put(v, j);
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
    public List<List<Integer>> getCellLeaves(List<DiscreteVariable> A) {
        Collections.sort(A, new Comparator<DiscreteVariable>() {

            @Override
            public int compare(DiscreteVariable o1, DiscreteVariable o2) {
                return Integer.compare(nodesHash.get(o1), nodesHash.get(o2));
            }
        });

        if (baseCase == null) {
            Vary vary = new Vary();
            this.baseCase = new ArrayList<>();
            baseCase.add(vary);
        }

        List<Vary> varies = baseCase;

        for (DiscreteVariable v : A) {
            varies = getVaries(varies, nodesHash.get(v));
        }

        List<List<Integer>> rows = new ArrayList<>();

        for (Vary vary : varies) {
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
    public List<List<List<Integer>>> getCellLeaves(List<DiscreteVariable> A, DiscreteVariable B) {
        Collections.sort(A, new Comparator<DiscreteVariable>() {

            @Override
            public int compare(DiscreteVariable o1, DiscreteVariable o2) {
                return Integer.compare(nodesHash.get(o1), nodesHash.get(o2));
            }
        });

        if (baseCase == null) {
            Vary vary = new Vary();
            this.baseCase = new ArrayList<>();
            baseCase.add(vary);
        }

        List<Vary> varies = baseCase;

        for (DiscreteVariable v : A) {
            varies = getVaries(varies, nodesHash.get(v));
        }

        List<List<List<Integer>>> rows = new ArrayList<>();

        for (Vary vary : varies) {
            for (int i = 0; i < vary.getNumCategories(); i++) {
                Vary subvary = vary.getSubvary(nodesHash.get(B), i);
                rows.add(subvary.getRows());
            }
        }

        return rows;
    }

    public void setColumn(DiscreteVariable var, int[] col) {
        discreteData[dataSet.getColumn(var)] = col;
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
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                _rows.add(i);
            }

            subVaries.add(new HashMap<Integer, Vary>());
            numCategories = 1;
            rows.add(_rows);
            subVaries = new ArrayList<>();
            subVaries.add(new HashMap<Integer, Vary>());
        }

        public Vary(int col, int numCategories, List<Integer> supRows, int[][] discreteData) {
            this.col = col;
            this.numCategories = numCategories;

            for (int i = 0; i < numCategories; i++) {
                rows.add(new ArrayList<Integer>());
            }

            for (int i = 0; i < numCategories; i++) {
                subVaries.add(new HashedMap<Integer, Vary>());
            }

            for (int i : supRows) {
                int index = discreteData[col][i];
                if (index != -99) {
                    rows.get(index).add(i);
                }
            }
        }

        public List<List<Integer>> getRows() {
            return rows;
        }

        public Vary getSubvary(int w, int cat) {
            Vary vary = subVaries.get(cat).get(w);

            if (vary == null) {
                vary = new Vary(w, dims[w], rows.get(cat), discreteData);
                subVaries.get(cat).put(w, vary);
            }

            return vary;
        }

        public int getNumCategories() {
            return numCategories;
        }
    }
}
