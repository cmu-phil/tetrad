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

import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndTestGSquare;
import edu.cmu.tetrad.search.IndTestTimeSeries;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the IndTestTimeSeries class.
 *
 * @author Joseph Ramsey
 */
public class TestIndTestGSquare extends TestCase {
    private final String[] discreteFiles = new String[]{
            "src/test/resources/embayes_l1x1x2x3MD.dat",
            "src/test/resources/determinationtest.dat"};

    private IndTestTimeSeries test;

    public TestIndTestGSquare(String name) {
        super(name);
    }

    public void testIsIndependent() {
        try {
            DataSet dataSet = getDataSet(1);

            IndTestGSquare test = new IndTestGSquare(dataSet, 0.05);
            List<Node> v = test.getVariables();

            Node x = v.get(0);
            Node y = v.get(1);
            ArrayList<Node> z = new ArrayList<Node>();
            z.add(v.get(2));
//            z.add(v.get(3));
            System.out.println(test.isIndependent(x, y, z));

            test.setDeterminationP(0.99);
            System.out.println("X determines Z " + test.determines(z, x));
            System.out.println("Y determines Z " + test.determines(z, y));
        }
        catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void testDetermination() {
        try {
            DataSet dataSet = getDataSet(1);

            IndTestGSquare test = new IndTestGSquare(dataSet, 0.05);

            Node x = dataSet.getVariable("X4");
            ArrayList<Node> z = new ArrayList<Node>();
//            z.add(dataSet.getVariable("X2"));

            test.setDeterminationP(0.99);
            System.out.println(x + " determined by " + z + ": " +
                    test.determines(z, x));
        }
        catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void rtestChiSquareCutoff() {
        test.setAlpha(0.05);
        assertEquals(3.84, test.chiSquareCutoff(), 0.01);
        test.setAlpha(0.01);
        assertEquals(6.63, test.chiSquareCutoff(), 0.01);
    }

    public DataSet getDataSet(int i) throws IOException {
        String filename = discreteFiles[i];
        System.out.println("Loading " + filename);

        DataReader reader = new DataReader();
        reader.setMissingValueMarker("-99");
        reader.setMaxIntegralDiscrete(5);
        return reader.parseTabular(new File(filename));
    }

    private static void printFile(Reader reader) {
        System.out.println();
        BufferedReader br = new BufferedReader(reader);

        try {
            String line;

            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static FileReader loadFile(String file) {
        try {
            return new FileReader(file);
        }
        catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error opening file " + file);
        }
    }

    public static Test suite() {
        return new TestSuite(TestIndTestGSquare.class);
    }

    public void setTest(IndTestTimeSeries test) {
        this.test = test;
    }
}





