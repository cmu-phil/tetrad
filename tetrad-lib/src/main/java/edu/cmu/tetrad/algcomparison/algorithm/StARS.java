///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.sweep.ParameterSweep;
import edu.cmu.tetrad.algcomparison.sweep.SweepReport;
import edu.cmu.tetrad.algcomparison.sweep.SweepResult;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * StARS (Stability Approach to Regularization Selection): selects a value for one numeric parameter of a wrapped
 * algorithm by sweeping the parameter over [low, high] in steps of 0.5, measuring the StARS adjacency instability of
 * the algorithm's output over a fixed set of resamples at each value, choosing the value with the largest
 * instability strictly below the cutoff (Params "StARS.cutoff"), and then running the wrapped algorithm on the full
 * data at the chosen value. The resamples are drawn once and shared across values, so instability comparisons are
 * paired.
 * <p>
 * The sweep and instability machinery is delegated to {@link ParameterSweep}; this class is a thin selector over
 * the resulting {@link SweepReport}, whose full evidence (per-value instabilities, point graphs, edge-probability
 * graphs) can be obtained programmatically by running the harness directly.
 * <p>
 * Parameters read from the Parameters object: "percentSubsampleSize" (fraction of the sample per resample),
 * "numSubsamples", "StARS.cutoff" (the instability cutoff beta), "logScale" (if true, evaluated values are
 * 10^lambda for lambda in [low, high]), and optionally Params.SEED for reproducible resampling.
 * <p>
 * Changes from the pre-2026 implementation, preserved semantics aside: (1) with "logScale" set, the parameter is
 * now evaluated at the transformed value 10^lambda during the sweep, where previously the sweep evaluated the raw
 * lambda but the final search used 10^lambda; (2) if no value's instability falls below the cutoff, the most stable
 * value is used (with a log message), where previously the parameter was set to NaN; (3) resampling is
 * seed-controlled via Params.SEED; (4) a concurrency hazard in the resample collection was removed.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class StARS implements Algorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The low value for the parameter.
     */
    private final double low;

    /**
     * The high value for the parameter.
     */
    private final double high;

    /**
     * The parameter to vary.
     */
    private final String parameter;

    /**
     * The algorithm to tune.
     */
    private final Algorithm algorithm;

    /**
     * <p>Constructor for StARS.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     * @param parameter a {@link java.lang.String} object
     * @param low       a double
     * @param high      a double
     */
    public StARS(Algorithm algorithm, String parameter, double low, double high) {
        if (low >= high) {
            throw new IllegalArgumentException("Must have low < high");
        }
        this.algorithm = algorithm;
        this.low = low;
        this.high = high;
        this.parameter = parameter;
    }

    /**
     * Applies the log-scale transform to a swept value if "logScale" is set.
     *
     * @param value      the raw swept value.
     * @param parameters the parameters.
     * @return the value the parameter is actually set to.
     */
    private static double getValue(double value, Parameters parameters) {
        if (parameters.getBoolean("logScale", false)) {
            return TMath.round(TMath.pow(10.0, value) * 1000000000.0) / 1000000000.0;
        } else {
            return TMath.round(value * 1000000000.0) / 1000000000.0;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws InterruptedException if any
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) throws InterruptedException {
        DataSet _dataSet = (DataSet) dataSet;

        double percentageB = parameters.getDouble("percentSubsampleSize");
        double beta = parameters.getDouble("StARS.cutoff");
        int numSubsamples = parameters.getInt("numSubsamples");
        long seed = parameters.getLong(Params.SEED, -1L);

        List<Double> values = new ArrayList<>();

        for (double lambda = this.low; lambda <= this.high; lambda += 0.5) {
            values.add(StARS.getValue(lambda, parameters));
        }

        ParameterSweep sweep = new ParameterSweep(this.algorithm, parameters);
        sweep.setNumResamples(numSubsamples);
        sweep.setPercentResampleSize(percentageB);
        sweep.setWithReplacement(true);
        sweep.setSeed(seed);
        sweep.setVerbose(parameters.getBoolean(Params.VERBOSE, false));

        SweepReport report = sweep.sweep(_dataSet, this.parameter, values);

        for (SweepResult result : report.getResults()) {
            TetradLogger.getInstance().log("StARS: " + this.parameter + " = "
                    + result.getSetting().get(this.parameter) + ", D = " + result.getAdjacencyInstability());
        }

        SweepResult selected = report.selectByInstability(beta);

        if (selected == null) {
            selected = report.selectMostStable();
            TetradLogger.getInstance().log("StARS: no value had instability below the cutoff " + beta
                    + "; using the most stable value instead.");
        }

        if (selected == null) {
            throw new IllegalStateException("StARS: no instability could be computed; check numSubsamples.");
        }

        double chosen = ((Number) selected.getSetting().get(this.parameter)).doubleValue();

        TetradLogger.getInstance().log("StARS: FINAL " + this.parameter + " = " + chosen
                + ", D = " + selected.getAdjacencyInstability());

        Parameters _parameters = new Parameters(parameters);
        _parameters.set(this.parameter, chosen);

        return this.algorithm.search(dataSet, _parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return this.algorithm.getComparisonGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "StARS for " + this.algorithm.getDescription() + " parameter = " + this.parameter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.algorithm.getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = this.algorithm.getParameters();
        parameters.add("depth");
        parameters.add("verbose");
        parameters.add("StARS.percentageB");
        parameters.add("StARS.tolerance");
        parameters.add("StARS.cutoff");
        parameters.add("numSubsamples");

        return parameters;
    }
}
