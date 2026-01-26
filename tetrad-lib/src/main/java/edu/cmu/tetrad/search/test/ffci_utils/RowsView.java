package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import org.ejml.simple.SimpleMatrix;

import java.util.List;

/**
 * Immutable view of the active rows for a CI test.
 *
 * Encapsulates:
 *  - testwise deletion vs user-specified rows
 *  - row indexing
 *  - fast column extraction
 */
public final class RowsView {

    private final DataSet data;
    private final int[] rows;   // null => use all rows
    private final int nActive;

    public RowsView(DataSet data, List<Integer> rowsList) {
        this.data = data;

        if (rowsList == null) {
            this.rows = null;
            this.nActive = data.getNumRows();
        } else {
            this.rows = rowsList.stream().mapToInt(Integer::intValue).toArray();
            this.nActive = rows.length;
        }
    }

    /** Number of active rows */
    public int nActive() {
        return nActive;
    }

    /** Extract columns for the given nodes as an nActive × |nodes| matrix */
    public SimpleMatrix cols(DataSet data, List<Node> nodes) {
        int p = nodes.size();
        SimpleMatrix M = new SimpleMatrix(nActive, p);

        for (int j = 0; j < p; j++) {
            int col = data.getColumn(nodes.get(j));
            if (rows == null) {
                for (int i = 0; i < nActive; i++) {
                    M.set(i, j, data.getDouble(i, col));
                }
            } else {
                for (int i = 0; i < nActive; i++) {
                    M.set(i, j, data.getDouble(rows[i], col));
                }
            }
        }
        return M;
    }

    /** Stable hash for caching keyed on row selection */
    public int rowsHash() {
        if (rows == null) return 0;
        int h = 1;
        for (int r : rows) h = 31 * h + r;
        return h;
    }
}