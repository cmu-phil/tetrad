///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.score.SemBicScorer;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * Difference between the true and estiamted BIC scores.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BicDiffPerRecord implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * Whether to precompute covariances.
     */
    private boolean precomputeCovariances = true;

    /**
     * Constructs a new instance of the statistic.
     */
    public BicDiffPerRecord() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "BicDiffPerRecord";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Difference between the true and estimated BIC scores, " +
               "divided by the sample size";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the difference between the true and estimated BIC scores, divided by the sample size.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        double _true = SemBicScorer.scoreDag(GraphTransforms.dagFromCpdag(trueGraph, null), dataModel, precomputeCovariances);
        double est = SemBicScorer.scoreDag(GraphTransforms.dagFromCpdag(estGraph, null), dataModel, precomputeCovariances);
        if (abs(_true) < 0.0001) _true = 0.0;
        if (abs(est) < 0.0001) est = 0.0;
        return (_true - est) / ((DataSet) dataModel).getNumRows();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return tanh(value / 1e6);
    }

    /**
     * Returns true if the covariances are precomputed.
     *
     * @param precomputeCovariances True if the covariances are precomputed.
     */
    public void setPrecomputeCovariances(boolean precomputeCovariances) {
        this.precomputeCovariances = precomputeCovariances;
    }
}


