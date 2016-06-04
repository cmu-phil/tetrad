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

package edu.cmu.tetrad.algcomparison.explorations;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.algcomparison.mixed.pag.MixedFci;
import edu.cmu.tetrad.algcomparison.mixed.pag.MixedGfci;
import edu.cmu.tetrad.algcomparison.mixed.pag.MixedWgfci;
import edu.cmu.tetrad.algcomparison.mixed.pattern.*;
import edu.cmu.tetrad.algcomparison.simulation.SemThenDiscretizeHalfSimulation;

import java.util.*;

/**
 * @author Joseph Ramsey
 */
public class ExploreMixedComparison {
    public static void main(String... args) {
        Map<String, Number> parameters = new LinkedHashMap<>();
        parameters.put("numMeasures", 10);
        parameters.put("numLatents", 3);
        parameters.put("maxDegree", 10);
        parameters.put("maxIndegree", 10);
        parameters.put("maxOutdegree", 10);
        parameters.put("connected", 0);
        parameters.put("numEdges", 10);
        parameters.put("sampleSize", 1000);
        parameters.put("minCategoriesForSearch", 2);
        parameters.put("maxCategoriesForSearch", 4);
        parameters.put("numRuns", 5);
        parameters.put("alpha", 0.001);
        parameters.put("penaltyDiscount", 4);
        parameters.put("mgmParam1", 0.1);
        parameters.put("mgmParam2", 0.1);
        parameters.put("mgmParam3", 0.1);
        parameters.put("ofInterestCutoff", 0.05);

        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("AP", "Adjacency Precision");
        stats.put("AR", "Adjacency Recall");
        stats.put("OP", "Orientation (Arrow) precision");
        stats.put("OR", "Orientation (Arrow) recall");
        stats.put("McAdj", "Matthew's correlation coeffficient for adjacencies");
        stats.put("McOr", "Matthew's correlation coefficient for arrow");
        stats.put("F1Adj", "F1 statistic for adjacencies");
        stats.put("F1Or", "F1 statistic for arrows");
        stats.put("E", "Elapsed time in seconds");

        List<Algorithm> algorithms = new ArrayList<>();

        // Fast

        algorithms.add(new MixedSemFgs());
        algorithms.add(new MixedBdeuFgs());
        algorithms.add(new MixedWfgs());
        algorithms.add(new MixedWgfci());

        // Slow
        algorithms.add(new MixedPc());
        algorithms.add(new MixedPcs());
        algorithms.add(new MixedCpc());
        algorithms.add(new MixedFci());
        algorithms.add(new MixedGfci());

        // These can't be run on non-mixed data.
        algorithms.add(new MixedMGMFgs());
        algorithms.add(new MixedMGMPc());


//        Simulation simulation = new LeeHastieSimulation();
        Simulation simulation = new SemThenDiscretizeHalfSimulation();

        new Comparison().testBestAlgorithms(parameters, stats, algorithms, simulation);
    }

}




