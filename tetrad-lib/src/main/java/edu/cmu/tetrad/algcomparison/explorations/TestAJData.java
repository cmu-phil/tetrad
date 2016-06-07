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

import edu.cmu.tetrad.algcomparison.mixed.pattern.MixedSemFgs;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * @author Joseph Ramsey
 */
public class TestAJData {
    public void testAjData() {
        double penalty = 4;

        try {

            for (int i = 0; i < 50; i++) {
                File dataPath = new File("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/2016.05.25/" +
                        "Simulated_data_for_Madelyn/simulation/data/DAG_" + i + "_data.txt");
                DataReader reader = new DataReader();
                DataSet Dk = reader.parseTabular(dataPath);

                File graphPath = new File("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/2016.05.25/" +
                        "Simulated_data_for_Madelyn/simulation/networks/DAG_" + i + "_graph.txt");

                Graph dag = GraphUtils.loadGraphTxt(graphPath);

                long start = System.currentTimeMillis();

//            Graph pattern = searchSemFgs(Dk);
//            Graph pattern = searchBdeuFgs(Dk, k);
//                Graph pattern = searchMixedFgs1(Dk, penalty);


                Map<String, Number> parameters = new LinkedHashMap<>();
                parameters.put("alpha", 0.001);
                parameters.put("penaltyDiscount", 4);
                parameters.put("mgmParam1", 0.1);
                parameters.put("mgmParam2", 0.1);
                parameters.put("mgmParam3", 0.1);
                parameters.put("OfInterestCutoff", 0.05);

                Graph pattern = new MixedSemFgs().search(Dk, parameters);

                long stop = System.currentTimeMillis();

                long elapsed = stop - start;
                long elapsedSeconds = elapsed / 1000;

                Graph truePattern = SearchGraphUtils.patternForDag(dag);

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison3(pattern, truePattern, System.out);
                NumberFormat nf = new DecimalFormat("0.00");

                System.out.println(i +
                        "\t" + nf.format(comparison.getAdjPrec()) +
                        "\t" + nf.format(comparison.getAdjRec()) +
                        "\t" + nf.format(comparison.getAhdPrec()) +
                        "\t" + nf.format(comparison.getAhdRec()) +
                        "\t" + elapsedSeconds);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}




