///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;

/**
 * Returns edges whose entries in the precision matrix exceed a certain threshold.
 *
 * @author Joseph Ramsey
 */
public class InverseCorrelation {

    private DataSet data;
    private double threshold;

    public InverseCorrelation(DataSet dataSet, double threshold) {
        this.data = dataSet;
        this.threshold = threshold;
    }

    public Graph search() {
        CovarianceMatrix cov = new CovarianceMatrix(data);

        TetradMatrix _data = cov.getMatrix();
        TetradMatrix inverse = _data.inverse();

        System.out.println(inverse);

        Graph graph = new EdgeListGraph(data.getVariables());

        for (int i = 0; i < inverse.rows(); i++) {
            for (int j = i + 1; j < inverse.columns(); j++) {
                double a = inverse.get(i, j);
                double b = inverse.get(i, i);
                double c = inverse.get(j, j);

                double r = -a / Math.sqrt(b * c);

                int sampleSize = data.getNumRows();
                int z = data.getNumColumns();

                double fisherZ = Math.sqrt(sampleSize - z - 3.0) *
                        0.5 * (Math.log(1.0 + r) - Math.log(1.0 - r));

                double p = getPValue(fisherZ);

                if (p < threshold) {
                    Node x = graph.getNodes().get(i);
                    Node y = graph.getNodes().get(j);
                    graph.addUndirectedEdge(x, y);
                }

//                if (abs(fisherZ) > threshold) {
//                    System.out.println(fisherZ + " &&& " + p);
//                    Node x = graph.getNodes().get(i);
//                    Node y = graph.getNodes().get(j);
//                    graph.addUndirectedEdge(x, y);
//                }

//                if (Math.abs(inverse.get(i, j)) > threshold) {
//                    Node x = graph.getNodes().get(i);
//                    Node y = graph.getNodes().get(j);
//                    graph.addUndirectedEdge(x, y);
//                }
            }
        }


        return graph;
    }

    public double getPValue(double z) {
        return 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, Math.abs(z)));
    }
}



