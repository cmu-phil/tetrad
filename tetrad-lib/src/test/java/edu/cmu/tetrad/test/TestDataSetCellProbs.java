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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.bayes.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author josephramsey
 */
public final class TestDataSetCellProbs {

    @Test
    public void testCreateUsingBayesIm() {
        Graph graph = GraphUtils.convert("X1-->X2,X1-->X3,X2-->X4,X3-->X4");
        Dag dag = new Dag(graph);
        BayesPm bayesPm = new BayesPm(dag);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.InitializationMethod.RANDOM);

        BayesImProbs bayesImProbs = new BayesImProbs(bayesIm);

        DataSet dataSet = bayesIm.simulateData(1000, false);

        CellTableProbs dataSetProbs = new CellTableProbs(dataSet);

        int[] cell = new int[4];

        for (int i = 0; i < 200; i++) {
            for (int j = 0; j < 4; j++) {
                cell[j] = RandomUtil.getInstance().nextInt(bayesIm.getNumColumns(j));
            }

            double count1 = bayesImProbs.getCellProb(cell);
            double count2 = dataSetProbs.getCellProb(cell);

            assertEquals(count1, count2, .05);
        }
    }
}





