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

package edu.cmu.tetrad.algcomparison.algorithm.continuous.dag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

/**
 * ICA-LiNGAM, the former name of LiNGAM.
 * <p>
 * <b>Backward compatibility only.</b> This class is retained under its original name so that sessions saved by
 * Tetrad 7.6.10 and earlier, which refer to it by this class name, continue to load. Its registration annotations
 * are commented out, so it does not appear in the chooser; new work should use {@link Lingam}.
 * Every method delegates to {@link Lingam}, so a restored session runs the same search as the LiNGAM box.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "ICA-LiNGAM",
//        command = "ica-lingam",
//        algoType = AlgType.forbid_latent_common_causes,
//        dataType = DataType.Continuous
//)
@Bootstrapping
public class IcaLingam extends AbstractBootstrapAlgorithm implements Algorithm, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The B-Hat matrix found by the most recent search.
     */
    private Matrix bHat = new Matrix(0, 0);

    /**
     * No-arg constructor. Used for reflection; do not delete.
     */
    public IcaLingam() {
    }

    /**
     * Runs the search on the given data by delegating to the successor algorithm and returns the resulting graph.
     *
     * @param dataSet  the data to search over.
     * @param parameters the parameters of the search.
     * @return the graph the successor returns.
     * @throws InterruptedException if the search is interrupted.
     */
    @Override
    public Graph runSearch(DataModel dataSet, Parameters parameters) throws InterruptedException {
        Lingam delegate = new Lingam();
        Graph graph = delegate.runSearch(dataSet, parameters);
        this.bHat = delegate.getBHat();
        return graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new Lingam().getComparisonGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "ICA-LiNGAM (retained for sessions saved by Tetrad 7.6.10 and earlier; equivalent to LiNGAM)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return new Lingam().getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        return new Lingam().getParameters();
    }

    /**
     * Returns the B-Hat matrix found by the most recent search.
     *
     * @return the B-Hat matrix
     */
    public Matrix getBHat() {
        return this.bHat;
    }
}
