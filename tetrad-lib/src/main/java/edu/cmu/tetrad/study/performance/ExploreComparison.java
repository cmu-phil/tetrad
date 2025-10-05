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

package edu.cmu.tetrad.study.performance;

import edu.cmu.tetrad.sem.ScoreType;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs algorithm on data set (simulation is OK), printing out error statistics.
 *
 * @author josephramsey 2016.03.24
 * @version $Id: $Id
 */
public class ExploreComparison {

    /**
     * Private constructor to prevent instantiation.
     */
    private ExploreComparison() {
    }

    /**
     * <p>main.</p>
     *
     * @param args a {@link java.lang.String} object
     */
    public static void main(String... args) {
        new ExploreComparison().runFromSimulation();
    }

    private void runFromSimulation() {
        ComparisonParameters params = new ComparisonParameters();
        params.setDataType(ComparisonParameters.DataType.Continuous);
        params.setAlgorithm(ComparisonParameters.Algorithm.FGES);
//        params.setIndependenceTest(ComparisonParameters.IndependenceTestType.FisherZ);
        params.setScore(ScoreType.SemBic);
//        params.setOneEdgeFaithfulnessAssumed(false);
        params.setNumVars(100);
        params.setNumEdges(100);
        params.setPenaltyDiscount(4);

        List<ComparisonResult> results = new ArrayList<>();

        for (int sampleSize = 1000; sampleSize <= 1000; sampleSize += 100) {
            params.setSampleSize(sampleSize);
            try {
                results.add(Comparison.compare(params));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        ArrayList<Comparison.TableColumn> tableColumns = new ArrayList<>();
        tableColumns.add(Comparison.TableColumn.AdjPrec);
        tableColumns.add(Comparison.TableColumn.AdjRec);
        tableColumns.add(Comparison.TableColumn.AhdPrec);
        tableColumns.add(Comparison.TableColumn.AhdRec);
        tableColumns.add(Comparison.TableColumn.SHD);
        tableColumns.add(Comparison.TableColumn.Elapsed);

        System.out.println(Comparison.summarize(results, tableColumns));
    }
}

