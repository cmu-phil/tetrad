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

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.data.CorrelationMatrixOnTheFly;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
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
            DataSet dataSet = (DataSet) DataConvertUtils.toDataModel(dataset);

//            dataSet = DataUtils.removeConstantColumns(dataSet);
            dataSet = DataUtils.removeLowSdColumns(dataSet, 0.1);

//            Pc pc = new Pc(new IndTestFisherZ(dataSet, 0.001));
            final SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
            score.setPenaltyDiscount(2);
            Fask pc = new Fask(dataSet, score);
            pc.setDepth(3);
            pc.setDelta(.5);
            pc.setAlpha(1e-5);
//            Fges pc = new Fges(score);
            Graph graph = pc.search();
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



