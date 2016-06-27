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
import edu.cmu.tetrad.algcomparison.continuous.pag.*;
import edu.cmu.tetrad.algcomparison.continuous.pattern.*;
import edu.cmu.tetrad.algcomparison.discrete.pag.*;
import edu.cmu.tetrad.algcomparison.discrete.pattern.*;
import edu.cmu.tetrad.algcomparison.mixed.pag.*;
import edu.cmu.tetrad.algcomparison.mixed.pattern.*;
import edu.cmu.tetrad.algcomparison.simulation.MixedLeeHastieSimulation;

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
public class RunComparison {
    public static void main(String... args) {
        Algorithm.DataType dataType = Algorithm.DataType.Mixed;

        Map<String, Number> parameters = new LinkedHashMap<>();

        parameters.put("scaleFreeAlpha", .1);
        parameters.put("scaleFreeBeta", .8);
        parameters.put("scaleFreeDeltaIn", 3.0);
        parameters.put("scaleFreeDeltaOut", 3.0);
        parameters.put("samplePrior", 1);
        parameters.put("structurePrior", 1);
        parameters.put("numCategories", 4);
        parameters.put("mgmParam1", 0.1);
        parameters.put("mgmParam2", 0.1);
        parameters.put("mgmParam3", 0.1);
        parameters.put("numLatents", 0);
        parameters.put("numRuns", 1);
        parameters.put("sampleSize", 1000);
        parameters.put("numMeasures", 10);
        parameters.put("numEdges", 20);
        parameters.put("penaltyDiscount", 4);
        parameters.put("fgsDepth", -1);

        if (dataType == Algorithm.DataType.Continuous) {
            parameters.put("percentDiscreteForMixedSimulation", 0);
        } else if (dataType == Algorithm.DataType.Discrete) {
            parameters.put("percentDiscreteForMixedSimulation", 100);
        } else if (dataType == Algorithm.DataType.Mixed) {
            parameters.put("percentDiscreteForMixedSimulation", 50);
        }

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
        statWeights.put("AR", 2.0);
        statWeights.put("OP", 1.0);
        statWeights.put("OR", 1.0);
//        statWeights.put("McAdj", 1.0);
//        statWeights.put("McOr", 0.5);
//        statWeights.put("F1Adj", 1.0);
//        statWeights.put("F1Or", 0.5);
//        statWeights.put("SHD", 0.5);
//        statWeights.put("E", .2);

//        List<Algorithm> algorithms = getFullAlgorithmsList();
        List<Algorithm> algorithms = getSpecialSet();


        Simulation simulation = new MixedLeeHastieSimulation();
//        Simulation simulation = new MixedSemThenDiscretizeHalfSimulation();
//        Simulation simulation = new DiscreteBayesNetSimulation();

        try {
            File dir = new File("comparison");
            dir.mkdirs();
            File comparison = new File("comparison", dataType + ".txt");
            PrintStream out = new PrintStream(new FileOutputStream(comparison));
            new Comparison().testBestAlgorithms(parameters, statWeights, algorithms, stats, simulation,
                    out, dataType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Algorithm> getSpecialSet() {
        List<Algorithm> algorithms = new ArrayList<>();

//        algorithms.add(new MixedFgsSem());
//        algorithms.add(new MixedFgsBdeu());
//
//        algorithms.add(new MixedWfgs());

        algorithms.add(new MixedFgsCG());

//        algorithms.add(new MixedPcCg());
//        algorithms.add(new MixedPcsCg());
//        algorithms.add(new MixedCpcCg());

//        algorithms.add(new MixedCpcLrt());

//        algorithms.add(new MixedWgfci());
//
//        algorithms.add(new MixedGfciCG());

        return algorithms;
    }

    private static List<Algorithm> getFullAlgorithmsList() {
        List<Algorithm> algorithms = new ArrayList<>();

//        // Pattern
        algorithms.add(new ContinuousPcFz());
        algorithms.add(new ContinuousCpcFz());
        algorithms.add(new ContinuousPcsFz());

        algorithms.add(new ContinuousPcFgs());
        algorithms.add(new ContinuousPcsFgs());
        algorithms.add(new ContinuousCpcFgs());

        algorithms.add(new ContinuousPcSemBic());
        algorithms.add(new ContinuousCpcSemBic());
        algorithms.add(new ContinuousPcsSemBic());

        algorithms.add(new ContinuousFgs());
        algorithms.add(new ContinuousFgs2());

        algorithms.add(new ContinuousGpc());

        algorithms.add(new DiscretePcChiSquare());
        algorithms.add(new DiscretePcsChiSquare());
        algorithms.add(new DiscreteCpcChiSquare());

        algorithms.add(new DiscretePcGSquare());
        algorithms.add(new DiscretePcsGSquare());
        algorithms.add(new DiscreteCpcGSquare());

        algorithms.add(new DiscreteFgsBdeu());
        algorithms.add(new DiscreteFgsBic());

        //21
        algorithms.add(new MixedFgsSem());
        algorithms.add(new MixedFgsBdeu());

        algorithms.add(new MixedWfgs());
        algorithms.add(new MixedWgfci());
//
        algorithms.add(new MixedPcLrtWfgs());
        algorithms.add(new MixedPcsLrtWfgs());
        algorithms.add(new MixedCpcWfgs());
//
        algorithms.add(new MixedPcLrtWGfci());
        algorithms.add(new MixedPcsLrtWfgs());
        algorithms.add(new MixedCpcLrtWGfci());

        algorithms.add(new MixedFgsMS());

        algorithms.add(new MixedFgsCG());

        algorithms.add(new MixedPcCg());
        algorithms.add(new MixedPcsCg());
        algorithms.add(new MixedCpcCg());

        algorithms.add(new MixedFgsMgm());

        algorithms.add(new MixedPcMgm());
        algorithms.add(new MixedPcsMgm());
        algorithms.add(new MixedCpcMgm());
//
        algorithms.add(new MixedPcMlrw());
        algorithms.add(new MixedCpcMlrw());
        algorithms.add(new MixedPcsMlrw());
//
        algorithms.add(new MixedPcLrt());
        algorithms.add(new MixedPcsLrt());
        algorithms.add(new MixedCpcLrt());
//
//        PAG
        algorithms.add(new ContinuousFciFz());
        algorithms.add(new ContinuousFciMaxFz());
        algorithms.add(new ContinuousRfciFz());

        algorithms.add(new ContinuousFciSemBic());
        algorithms.add(new ContinuousRfciSemBic());

        algorithms.add(new ContinuousGfci());

        algorithms.add(new DiscreteFciCs());
        algorithms.add(new DiscreteFciGs());

        algorithms.add(new DiscreteFciCs());
        algorithms.add(new DiscreteRfciGs());

        algorithms.add(new DiscreteGfci());

//        algorithms.add(new MixedFciLrtWfgs());
//        algorithms.add(new MixedFciLrt());
//        algorithms.add(new MixedGfciMixedScore());
//        algorithms.add(new MixedGfciCG());
        algorithms.add(new MixedFciCG());
//        algorithms.add(new MixedFciMlrw());

//        Cyclic PAG
//        algorithms.add(new ContinuousCcd());
        return algorithms;
    }

}




