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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;

/**
 * Returns edges whose entries in the precision matrix exceed a certain threshold.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class InverseCorrelation {

    private final DataSet data;
    private final double threshold;

    /**
     * <p>Constructor for InverseCorrelation.</p>
     *
     * @param dataSet   a {@link edu.cmu.tetrad.data.DataSet} object
     * @param threshold a double
     */
    public InverseCorrelation(DataSet dataSet, double threshold) {
        this.data = dataSet;
        this.threshold = threshold;
    }

    /**
     * <p>search.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph search() {
        CovarianceMatrix cov = new CovarianceMatrix(this.data);

        Matrix _data = cov.getMatrix();
        Matrix inverse = _data.inverse();

        System.out.println(inverse);

        Graph graph = new EdgeListGraph(this.data.getVariables());

        for (int i = 0; i < inverse.getNumRows(); i++) {
            for (int j = i + 1; j < inverse.getNumColumns(); j++) {
                double a = inverse.get(i, j);
                double b = inverse.get(i, i);
                double c = inverse.get(j, j);

                double r = -a / FastMath.sqrt(b * c);

                int sampleSize = this.data.getNumRows();
                int z = this.data.getNumColumns();

                double fisherZ = FastMath.sqrt(sampleSize - z - 3.0) *
                                 0.5 * (FastMath.log(1.0 + r) - FastMath.log(1.0 - r));

                double p = getPValue(fisherZ);

                if (p < this.threshold) {
                    Node x = graph.getNodes().get(i);
                    Node y = graph.getNodes().get(j);
                    graph.addUndirectedEdge(x, y);
                }
            }
        }


        return graph;
    }

    /**
     * <p>getPValue.</p>
     *
     * @param z a double
     * @return a double
     */
    public double getPValue(double z) {
        return 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, FastMath.abs(z)));
    }
}




