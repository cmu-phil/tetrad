///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphPersistence;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.IndTestMultinomialLogisticRegression;
import edu.cmu.tetrad.search.PcStable;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.io.File;
import java.lang.management.ManagementFactory;

/**
 * Created by ajsedgewick on 9/10/15.
 */
public class ExploreIndepTests {
    public static void main(String[] args) {
        try {
            String path = ExampleMixedSearch.class.getResource("test_data").getPath();
            Graph trueGraph = SearchGraphUtils.cpdagFromDag(GraphPersistence.loadGraphTxt(new File(path, "DAG_0_graph.txt")));
            DataSet ds = MixedUtils.loadDataSet(path, "DAG_0_data.txt");

            IndTestMultinomialLogisticRegression indMix = new IndTestMultinomialLogisticRegression(ds, .05);
            IndTestMultinomialLogisticRegressionWald indWalLin = new IndTestMultinomialLogisticRegressionWald(ds, .05, true);
            IndTestMultinomialLogisticRegressionWald indWalLog = new IndTestMultinomialLogisticRegressionWald(ds, .05, false);

            PcStable s1 = new PcStable(indMix);
            PcStable s2 = new PcStable(indWalLin);
            PcStable s3 = new PcStable(indWalLog);

            long time = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
            Graph g1 = SearchGraphUtils.cpdagFromDag(s1.search());
            System.out.println("Mix Time " + ((ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - time) / 1000.0));

            time = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
            Graph g2 = SearchGraphUtils.cpdagFromDag(s2.search());
            System.out.println("Wald lin Time " + ((ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - time) / 1000.0));

            time = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
            Graph g3 = SearchGraphUtils.cpdagFromDag(s3.search());
            System.out.println("Wald log Time " + ((ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - time) / 1000.0));

            System.out.println(MixedUtils.EdgeStatHeader);
            System.out.println(MixedUtils.stringFrom2dArray(MixedUtils.allEdgeStats(trueGraph, g1)));
            System.out.println(MixedUtils.stringFrom2dArray(MixedUtils.allEdgeStats(trueGraph, g2)));
            System.out.println(MixedUtils.stringFrom2dArray(MixedUtils.allEdgeStats(trueGraph, g3)));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}

