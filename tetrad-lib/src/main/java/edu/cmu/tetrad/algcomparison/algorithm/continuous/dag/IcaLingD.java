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
import java.util.ArrayList;
import java.util.List;

/**
 * ICA-LiNG-D, the former name of LiNG-D.
 * <p>
 * <b>Backward compatibility only.</b> This class is retained under its original name so that sessions saved by
 * Tetrad 7.6.10 and earlier, which refer to it by this class name, continue to load. Its registration annotations
 * are commented out, so it does not appear in the chooser; new work should use {@link LingD}.
 * Every method delegates to {@link LingD}, so a restored session runs the same search as the LiNG-D box.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "ICA-LiNG-D",
//        command = "ica-ling-d",
//        algoType = AlgType.forbid_latent_common_causes,
//        dataType = DataType.Continuous
//)
@Bootstrapping
public class IcaLingD extends AbstractBootstrapAlgorithm implements Algorithm, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The stable graphs found by the most recent search.
     */
    private List<Graph> stableGraphs = new ArrayList<>();

    /**
     * The unstable graphs found by the most recent search.
     */
    private List<Graph> unstableGraphs = new ArrayList<>();

    /**
     * The B-Hat matrices of the stable graphs.
     */
    private List<Matrix> stableBHats = new ArrayList<>();

    /**
     * The B-Hat matrices of the unstable graphs.
     */
    private List<Matrix> unstableBHats = new ArrayList<>();

    /**
     * No-arg constructor. Used for reflection; do not delete.
     */
    public IcaLingD() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph runSearch(DataModel dataSet, Parameters parameters) throws InterruptedException {
        LingD delegate = new LingD();
        Graph graph = delegate.runSearch(dataSet, parameters);
        this.stableGraphs = delegate.getStableGraphs();
        this.unstableGraphs = delegate.getUnstableGraphs();
        this.stableBHats = delegate.getStableBHats();
        this.unstableBHats = delegate.getUnstableBHats();
        return graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new LingD().getComparisonGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "ICA-LiNG-D (retained for sessions saved by Tetrad 7.6.10 and earlier; equivalent to LiNG-D)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return new LingD().getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        return new LingD().getParameters();
    }

    /**
     * Returns the stable graphs found by the most recent search.
     *
     * @return the stable graphs
     */
    public List<Graph> getStableGraphs() {
        return this.stableGraphs;
    }

    /**
     * Returns the unstable graphs found by the most recent search.
     *
     * @return the unstable graphs
     */
    public List<Graph> getUnstableGraphs() {
        return this.unstableGraphs;
    }

    /**
     * Returns the B-Hat matrices of the stable graphs.
     *
     * @return the stable B-Hat matrices
     */
    public List<Matrix> getStableBHats() {
        return this.stableBHats;
    }

    /**
     * Returns the B-Hat matrices of the unstable graphs.
     *
     * @return the unstable B-Hat matrices
     */
    public List<Matrix> getUnstableBHats() {
        return this.unstableBHats;
    }
}
