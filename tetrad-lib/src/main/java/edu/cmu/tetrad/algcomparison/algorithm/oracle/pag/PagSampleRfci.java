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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

/**
 * PAG-Sampling-RFCI, the former name of the class now called PagSamplingRfci.
 * <p>
 * <b>Backward compatibility only.</b> This class is retained under its original name so that sessions saved by
 * Tetrad 7.6.10 and earlier, which refer to it by this class name, continue to load. Its registration annotations
 * are commented out, so it does not appear in the algorithm chooser; new work should use
 * {@link PagSamplingRfci}. Every method delegates there, so a restored session runs the same search.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "PAG-Sampling-RFCI",
//        command = "pag-sampling-rfci",
//        algoType = AlgType.allow_latent_common_causes
//)
public class PagSampleRfci extends AbstractBootstrapAlgorithm implements Algorithm, AcceptsKnowledge {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The knowledge.
     */
    private Knowledge knowledge;

    /**
     * No-arg constructor. Used for reflection; do not delete.
     */
    public PagSampleRfci() {
    }

    /**
     * Builds the delegate, configured from this object's state.
     *
     * @return the configured delegate
     */
    private PagSamplingRfci delegate() {
        PagSamplingRfci delegate = new PagSamplingRfci();
        if (this.knowledge != null) {
            delegate.setKnowledge(this.knowledge);
        }
        return delegate;
    }

    /**
     * Runs the search on the given data by delegating to the successor algorithm and returns the resulting graph.
     *
     * @param dataModel the data to search over.
     * @param parameters the parameters of the search.
     * @return the graph the successor returns.
     * @throws InterruptedException if the search is interrupted.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        return delegate().runSearch(dataModel, parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return delegate().getComparisonGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "PAG-Sampling-RFCI (retained for sessions saved by Tetrad 7.6.10 and earlier)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return delegate().getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        return delegate().getParameters();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }
}
