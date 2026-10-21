/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.test.IndTestEquivalence;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the equivalence (TOST) test on the Fisher Z partial correlation.
 *
 * <p>Reports independence only when it can show the partial correlation is negligible, instead of
 * when it fails to reject zero. An adjacency search deletes an edge on a report of independence,
 * so with an ordinary test it deletes on the absence of evidence; the removal region |z| &lt; c*se
 * shrinks with the sample size, which means weak-but-real edges are deleted most eagerly at
 * exactly the sample sizes where deleting them is least justified. Choosing this test moves the
 * burden of proof onto removal.
 *
 * <p>Two caveats before selecting it. The margin must exceed roughly 1.645/sqrt(n) at alpha = 0.05
 * or nothing is removable and the search returns a complete graph, which is why
 * {@link Params#EQUIVALENCE_MARGIN} defaults to 0, meaning "scale the margin to the sample size".
 * And the test's meaning of independence is weaker than an ordinary test's meaning of dependence,
 * so an algorithm that asks "are these two dependent?" inside an orientation rule may be degraded
 * by it. For CCD, prefer {@link Params#USE_EQUIVALENCE_REMOVAL} on the algorithm, which applies
 * the equivalence rule to the adjacency phase alone and leaves orientation on the selected test.
 *
 * @author josephramsey
 * @see IndTestEquivalence
 * @see edu.cmu.tetrad.search.Ccd#setAdjacencyTest(IndependenceTest)
 */
@TestOfIndependence(
        name = "Equivalence (TOST)",
        command = "equivalence-test",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class EquivalenceTest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the test.
     */
    public EquivalenceTest() {
    }

    /**
     * Builds the equivalence test for the given data and parameters. When
     * {@link Params#EQUIVALENCE_MARGIN} is 0 the margin is scaled to the sample size, as
     * {@link Params#EQUIVALENCE_MARGIN_MULTIPLIER} times the smallest margin that sample size
     * could certify.
     *
     * @param dataModel  the data set or covariance matrix to test independence against.
     * @param parameters the parameters of the test.
     * @return the equivalence test.
     * @throws IllegalArgumentException if the data model is neither a data set nor a covariance
     *                                  matrix, or the sample size is too small to be usable.
     */
    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        double alpha = parameters.getDouble(Params.ALPHA);
        double margin = parameters.getDouble(Params.EQUIVALENCE_MARGIN);
        double multiplier = parameters.getDouble(Params.EQUIVALENCE_MARGIN_MULTIPLIER);

        IndTestFisherZ fisherZ;
        int sampleSize;

        if (dataModel instanceof ICovarianceMatrix covarianceMatrix) {
            fisherZ = new IndTestFisherZ(covarianceMatrix, alpha);
            sampleSize = covarianceMatrix.getSampleSize();
        } else if (dataModel instanceof DataSet dataSet) {
            fisherZ = new IndTestFisherZ(dataSet, alpha);
            sampleSize = dataSet.getNumRows();
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        return build(fisherZ, sampleSize, margin, multiplier, alpha);
    }

    /**
     * Builds the equivalence test from an already-constructed Fisher Z test, resolving the margin
     * the same way {@link #getTest(DataModel, Parameters)} does. Exposed so that an algorithm
     * applying the equivalence rule to one phase only can reuse the resolution logic.
     *
     * @param fisherZ    the underlying Fisher Z test.
     * @param sampleSize the sample size the margin is scaled to when margin is 0.
     * @param margin     the explicit margin, or 0 to scale it to the sample size.
     * @param multiplier the multiplier used when margin is 0.
     * @param alpha      the one-sided level of each of the two tests.
     * @return the equivalence test.
     * @throws IllegalArgumentException if the sample size is too small for the automatic margin.
     */
    public static IndTestEquivalence build(IndTestFisherZ fisherZ, int sampleSize, double margin,
                                           double multiplier, double alpha) {
        if (margin > 0) {
            return new IndTestEquivalence(fisherZ, margin, alpha);
        }

        if (sampleSize <= 4) {
            throw new IllegalArgumentException("The equivalence test needs more than 4 cases to "
                    + "scale its margin; got " + sampleSize + ". Set " + Params.EQUIVALENCE_MARGIN
                    + " explicitly to bypass the scaling.");
        }

        return IndTestEquivalence.autoMargin(fisherZ, multiplier, alpha, sampleSize);
    }

    /**
     * Returns a short description of this test.
     *
     * @return the description.
     */
    @Override
    public String getDescription() {
        return "Equivalence (TOST) test on the Fisher Z partial correlation";
    }

    /**
     * Returns the data type this test requires.
     *
     * @return continuous data.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns the parameters this test uses.
     *
     * @return the parameter names.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.EQUIVALENCE_MARGIN);
        parameters.add(Params.EQUIVALENCE_MARGIN_MULTIPLIER);
        return parameters;
    }
}
