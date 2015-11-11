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

package edu.pitt.csb.mgm;

import cern.colt.matrix.DoubleMatrix2D;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.io.File;
import java.io.IOException;

/**
 * Created by ajsedgewick on 8/18/15.
 */
public class ExampleMixedSearch {
    public static void main(String[] args){
        try {
            String path = args[0];
            //String path = ExampleMixedSearch.class.getResource("test_data").getPath();
            //DoubleMatrix2D xIn = DoubleFactory2D.dense.make(MixedUtils.loadDataSelect(path, "med_test_C.txt"));
            //DoubleMatrix2D yIn = DoubleFactory2D.dense.make(MixedUtils.loadDataSelect(path, "med_test_D.txt"));
            Graph trueGraph = GraphUtils.loadGraphTxt(new File(path, "DAG_0_graph.txt"));
            DataSet data = MixedUtils.loadDataSet(path, "DAG_0_data.txt");

            //lambda is a sparsity parameter, higher values result in sparser graphs
            //separate lambda entries for continuous-continuous, continuous-discrete and discrete-discrete edges
            //respectively, generally lower lambdas for edges with discrete variables
            double[] lambda = {.2, .2, .2};
            double tolerance = 1e-7; //convergeance tolerance
            int iterLimit = 1000; //iteration limit

            MGM model = new MGM(data, lambda);
            model.learn(tolerance,iterLimit);
            Graph mgmGraph = model.graphFromMGM();

            //Graph class can't deal with edge weights, so we put them in a matrix
            DoubleMatrix2D adjMat = model.adjMatFromMGM();

            System.out.println("True Graph");
            System.out.println(trueGraph);

            System.out.println("MGM Graph");
            System.out.println(mgmGraph);

            System.out.println("AdjMat");
            System.out.println(adjMat.toString());

            System.out.println(MixedUtils.EdgeStatHeader);
            System.out.println(MixedUtils.stringFrom2dArray(MixedUtils.allEdgeStats(trueGraph, mgmGraph)));


        } catch (IOException e){
            e.printStackTrace();
        }
    }
}

