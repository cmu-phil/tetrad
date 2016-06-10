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
import edu.cmu.tetrad.algcomparison.mixed.pag.MixedWfgsFci;
import edu.cmu.tetrad.algcomparison.mixed.pag.MixedWgfci;
import edu.cmu.tetrad.algcomparison.mixed.pattern.*;
import edu.cmu.tetrad.algcomparison.simulation.MixedLeeHastieSimulation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;

/**
 * @author Joseph Ramsey
 */
public class RunMixedComparison {
    public static void main(String... args) {
        Map<String, Number> parameters = new LinkedHashMap<>();
        parameters.put("numCategories", 2);
        parameters.put("mgmParam1", 0.01);
        parameters.put("mgmParam2", 0.01);
        parameters.put("mgmParam3", 0.01);
        parameters.put("numLatents", 3);
        parameters.put("numRuns", 1);
        parameters.put("numSamples", 1000);
        parameters.put("numMeasures", 50);
        parameters.put("numEdges", 100);

        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("AP", "Adjacency Precision");
        stats.put("AR", "Adjacency Recall");
        stats.put("OP", "Orientation (Arrow) precision");
        stats.put("OR", "Orientation (Arrow) recall");
        stats.put("McAdj", "Matthew's correlation coeffficient for adjacencies");
        stats.put("McOr", "Matthew's correlation coefficient for arrow");
        stats.put("F1Adj", "F1 statistic for adjacencies");
        stats.put("F1Or", "F1 statistic for arrows");
        stats.put("SHD", "Structural hamming distance");
        stats.put("E", "Elapsed time in seconds");

        List<Algorithm> algorithms = new ArrayList<>();

        // Pattern
        algorithms.add(new MixedSemFgs());
        algorithms.add(new MixedBdeuFgs());
        algorithms.add(new MixedWfgs());
        algorithms.add(new MixedPc());
        algorithms.add(new MixedPcs());
        algorithms.add(new MixedCpc());
        algorithms.add(new MixedMGMFgs());
        algorithms.add(new MixedMGMPc());
        algorithms.add(new MixedMGMCpc());
        algorithms.add(new MixedWfgsFci());

        // PAG
        algorithms.add(new MixedWgfci());
        algorithms.add(new MixedFci());
        algorithms.add(new MixedGfci());

        Simulation simulation = new MixedLeeHastieSimulation();
//        Simulation simulation = new SemThenDiscretizeHalfSimulation();
//        Simulation simulation = new DiscreteBayesNetSimulation();

        String baseFileName = "Mixed";

        try {
            File dir = new File("comparison");
            dir.mkdirs();

            for (int index = 1; ; index++) {
                File comparison = new File("comparison", baseFileName + "." + index + ".txt");
                if (!comparison.exists()) {
                    PrintStream out = new PrintStream(new FileOutputStream(comparison));
                    new Comparison().testBestAlgorithms(parameters, stats, algorithms, simulation, out);
                    break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}




