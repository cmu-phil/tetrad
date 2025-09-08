package edu.cmu.tetrad.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/** Fit X ~ f(Pa) and return residuals per row. */
public interface ResidualRegressor {
    /** Fit a local mechanism for target from parents in data. */
    void fit(DataSet data, Node target, List<Node> parents);

    /** Predict fitted values for all rows. parentsRows: each row's parent values (already in data). */
    double[] predict(DataSet data, Node target, List<Node> parents);

    /** Convenience: residuals = y - yhat. */
    default double[] residuals(DataSet data, Node target, List<Node> parents) {
        double[] yhat = predict(data, target, parents);
        double[] y = data.getDoubleData().getColumn(data.getColumnIndex(target)).toArray();
        double[] r = new double[y.length];
        for (int i = 0; i < y.length; i++) r[i] = y[i] - yhat[i];
        return r;
    }
}