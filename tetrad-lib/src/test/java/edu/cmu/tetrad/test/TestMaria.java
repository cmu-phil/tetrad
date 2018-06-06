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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.data.Dataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Tests CStaS.
 *
 * @author Joseph Ramsey
 */
public class TestMaria {


    public void test1() {

        try {
            File dir = new File("//Users/user/Downloads");

            Dataset dataset = new ContinuousTabularDataFileReader(new File(dir, "searchexpv4.csv"), Delimiter.COMMA).readInData();
//            Dataset dataset = new ContinuousTabularDataFileReader(new File(dir, "searchexpv1.csv"), Delimiter.COMMA).readInData();
            DataSet dataSet = (DataSet) DataConvertUtils.toDataModel(dataset);

//            dataSet = DataUtils.removeConstantColumns(dataSet);
//            dataSet = DataUtils.removeLowSdColumns(dataSet, 0.0001);

            IKnowledge knowledge = new Knowledge2();

            for (String name : dataSet.getVariableNames()) {
                if (!name.startsWith("mem")) {
                    knowledge.addToTier(1, name);
                }
            }

//            knowledge.addToTier(1, "edyrs");
            knowledge.addToTier(2, "mem_2");
            knowledge.addToTier(3, "mem_3");
            knowledge.addToTier(4, "mem_4");
            knowledge.addToTier(5, "mem_5");
            knowledge.addToTier(6, "mem_6");
            knowledge.addToTier(7, "mem_7");
            knowledge.addToTier(8, "mem_8");
            knowledge.addToTier(9, "mem_9");

            knowledge.addToTier(2, "fage");
            knowledge.addToTier(3, "gage");
            knowledge.addToTier(4, "hage");
            knowledge.addToTier(5, "jage");
            knowledge.addToTier(6, "kage");
            knowledge.addToTier(7, "lage");
            knowledge.addToTier(8, "mage");
            knowledge.addToTier(9, "nage");

            knowledge.addToTier(2, "falive");
            knowledge.addToTier(3, "galive");
            knowledge.addToTier(4, "halive");
            knowledge.addToTier(5, "jalive");
            knowledge.addToTier(6, "kalive");
            knowledge.addToTier(7, "lalive");
            knowledge.addToTier(8, "malive");
            knowledge.addToTier(9, "nalive");

//            knowledge.addToTier(4, "mem_4_ms");
//            knowledge.addToTier(5, "mem_5_ms");
//            knowledge.addToTier(6, "mem_6_ms");
//            knowledge.addToTier(7, "mem_7_ms");
//            knowledge.addToTier(8, "mem_8_ms");
//            knowledge.addToTier(9, "mem_9_ms");


//            Pc pc = new Pc(new IndTestFisherZ(dataSet, 0.001));
            final SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
            score.setPenaltyDiscount(3);
            Fask pc = new Fask(dataSet, score);
            pc.setDepth(4);
            pc.setDelta(.5);
            pc.setAlpha(1e-5);
            pc.setKnowledge(knowledge);
//            Fges pc = new Fges(score);
            Graph graph = pc.search();

//            edu.cmu.tetrad.search.GFci gfci = new edu.cmu.tetrad.search.GFci(new IndTestScore(score), score);
//            gfci.setKnowledge(knowledge);
//            Graph graph = gfci.search();

            System.out.println(graph);

            PrintStream out = new PrintStream(new FileOutputStream(new File(dir, "searchexpv1.graph.txt")));

            out.println(graph);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }


    }








    public static void main(String... args) {
        new TestMaria().test1();
    }
}



