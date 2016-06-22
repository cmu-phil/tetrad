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

package edu.cmu.tetrad.algcomparison.comparisons;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.algcomparison.mixed.pag.*;
import edu.cmu.tetrad.algcomparison.mixed.pattern.*;
import edu.cmu.tetrad.algcomparison.simulation.MixedLeeHastieSimulation;
import edu.cmu.tetrad.algcomparison.simulation.MixedSemThenDiscretizeHalfSimulation;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Joseph Ramsey
 */
public class RunMixedComparison {
    public static void main(String... args) {
        Map<String, Number> parameters = new LinkedHashMap<>();
        parameters.put("numCategories", 4);
        parameters.put("mgmParam1", 0.1);
        parameters.put("mgmParam2", 0.1);
        parameters.put("mgmParam3", 0.1);
        parameters.put("numLatents", 0);
        parameters.put("numRuns", 1);
        parameters.put("sampleSize", 5000);
        parameters.put("numMeasures", 30);
        parameters.put("numEdges", 30);
        parameters.put("penaltyDiscount", 4);
        parameters.put("fgsDepth", -1);
        parameters.put("percentDiscreteForMixedSimulation", 50);

        List<String> stats = new ArrayList<>();
        stats.add("AP");
        stats.add("AR");
        stats.add("OP");
        stats.add("OR");
        stats.add("McAdj");
        stats.add("McOr");
        stats.add("F1Adj");
        stats.add("F1Or");
        stats.add("SHD");
        stats.add("E");
        stats.add("W");

        Map<String, Double> statWeights = new LinkedHashMap<>();
        statWeights.put("AP", 2.0);
        statWeights.put("AR", 1.0);
        statWeights.put("OP", 2.0);
        statWeights.put("OR", 1.0);
//        statWeights.put("McAdj", 1.0);
//        statWeights.put("McOr", 0.5);
//        statWeights.put("F1Adj", 1.0);
//        statWeights.put("F1Or", 0.5);
//        statWeights.put("SHD", 0.5);
//        statWeights.put("E", .2);

        List<Algorithm> algorithms = new ArrayList<>();

        // Pattern
        algorithms.add(new MixedFgsSem());
        algorithms.add(new MixedFgsBdeu());
//        algorithms.add(new MixedFgsMS());
        algorithms.add(new MixedFgsCG());
//        algorithms.add(new MixedFgsMgm());
//        algorithms.add(new MixedWfgs());
        algorithms.add(new MixedPc());
//        algorithms.add(new MixedPcWfgs());
//        algorithms.add(new MixedPcWGfci());
//        algorithms.add(new MixedPcs());
//        algorithms.add(new MixedPcsMgm());
//        algorithms.add(new MixedPcsWfgs());
//        algorithms.add(new MixedCpc());
//        algorithms.add(new MixedCpcMgm());
//        algorithms.add(new MixedCpcWfgs());
//        algorithms.add(new MixedCpcWGfci());
//        algorithms.add(new MixedWgfci());

//        PAG
//        algorithms.add(new MixedFciWfgs());
//        algorithms.add(new MixedFci());
//        algorithms.add(new MixedGfciMixedScore());
//        algorithms.add(new MixedGfciCG());

        Simulation simulation = new MixedLeeHastieSimulation();
//        Simulation simulation = new MixedSemThenDiscretizeHalfSimulation();
//        Simulation simulation = new DiscreteBayesNetSimulation();

        String baseFileName = "Mixed";

        try {
            File dir = new File("comparison");
            dir.mkdirs();
            File comparison = new File("comparison", baseFileName + ".txt");
            PrintStream out = new PrintStream(new FileOutputStream(comparison));
            new Comparison().testBestAlgorithms(parameters, statWeights, algorithms, stats, simulation, out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}




