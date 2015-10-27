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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetrad.util.TetradLogger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public final class TestDataReader extends TestCase {

    /**
     * Change the name of this constructor to match the name of the test class.
     */
    public TestDataReader(String name) {
        super(name);
    }

    public void test1() {
        TetradLogger.getInstance().addOutputStream(System.out);

        File file = new File("src/test/resources/cheese.txt");
        char[] chars = fileToCharArray(file);

        DataReader reader = new DataReader();
        reader.setDelimiter(DelimiterType.WHITESPACE);
        reader.setIdsSupplied(true);
        reader.setIdLabel("Case");

        DataModel data = reader.parseTabular(chars);

        TetradLogger.getInstance().removeOutputStream(System.out);

        System.out.println(data);
    }

    // Without the ar names.
    public void test1b() {
        TetradLogger.getInstance().addOutputStream(System.out);

        File file = new File("src/test/resources/cheese2.txt");
        char[] chars = fileToCharArray(file);

        DataReader reader = new DataReader();
        reader.setDelimiter(DelimiterType.WHITESPACE);
        reader.setVariablesSupplied(false);
        reader.setIdsSupplied(true);
        reader.setIdLabel(null);

        DataModel data = reader.parseTabular(chars);

        TetradLogger.getInstance().removeOutputStream(System.out);

        System.out.println(data);
    }

//    public void test2() {
//        TetradLogger.getInstance().addOutputStream(System.out);
//
//        File file = new File("test_data/g1set.txt");
//        char[] chars = fileToCharArray(file);
//
//        DataReader reader = new DataReader();
//        reader.setDelimiter(DelimiterType.WHITESPACE);
//        reader.setVariablesSupplied(true);
//
//        DataModel data = reader.parseTabular(chars);
//        TetradLogger.getInstance().removeOutputStream(System.out);
//
//        System.out.println(data);
//    }

    // big
//    public void test3() {
//        TetradLogger.getInstance().addOutputStream(System.out);
//
//        File file = new File("test_data/determinationtest.dat");
////        char[] chars = fileToCharArray(file);
//
//        DataReader reader = new DataReader();
//        reader.setDelimiter(DelimiterType.TAB);
//        reader.setMissingValueMarker("*");
//
//        DataModel data = null;
//        try {
//            data = reader.parseTabular(file);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        TetradLogger.getInstance().removeOutputStream(System.out);
//        System.out.println(data);
//    }

//    public void test4() {
//        TetradLogger.getInstance().addOutputStream(System.out);
//        File file = new File("test_data/soybean.data");
////        char[] chars = fileToCharArray(file);
//
//        DataReader reader = new DataReader();
//        reader.setCommentMarker("//");
//        reader.setDelimiter(DelimiterType.COMMA);
//        reader.setIdsSupplied(true);
//        reader.setIdLabel(null);
//        reader.setMissingValueMarker("*");
//        reader.setMaxIntegralDiscrete(10);
//
//        DataModel data = null;
//        try {
//            data = reader.parseTabular(file);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        TetradLogger.getInstance().removeOutputStream(System.out);
//
//        System.out.println(data);
//    }

//    public void test5() {
//        TetradLogger.getInstance().addOutputStream(System.out);
//        File file = new File("src/test/resources/pub.txt");
//        char[] chars = fileToCharArray(file);
//
//        DataReader reader = new DataReader();
//        reader.setCommentMarker("//");
//        reader.setDelimiter(DelimiterType.WHITESPACE);
//        reader.setIdsSupplied(true);
//        reader.setIdLabel(null);
//        reader.setMissingValueMarker("*");
//        reader.setMaxIntegralDiscrete(10);
//
//        DataModel data = reader.parseCovariance(chars);
//
//        TetradLogger.getInstance().removeOutputStream(System.out);
//
//        System.out.println(data);
//    }

//    public void rtest6() {
//        TetradLogger.getInstance().addOutputStream(System.out);
//
//        File file = new File("test_data/dataparser/runSimulation.dat");
//        char[] chars = fileToCharArray(file);
//
//        DataReader reader = new DataReader();
//        reader.setDelimiter(DelimiterType.WHITESPACE);
//
//        DataModel data = reader.parseTabular(chars);
//
//        System.out.println(data);
//
//        TetradLogger.getInstance().removeOutputStream(System.out);
//
//    }

//    public void test7() {
//        TetradLogger.getInstance().addOutputStream(System.out);
//
//        File file = new File("test_data/dataparser/test2.dat");
//        char[] chars = fileToCharArray(file);
//
//        DataReader reader = new DataReader();
//        reader.setDelimiter(DelimiterType.COMMA);
//        reader.setCommentMarker("@");
//        reader.setIdsSupplied(true);
//        reader.setIdLabel("ID");
//        reader.setQuoteChar('\'');
//
//        DataModel data = reader.parseTabular(chars);
//
//        System.out.println(data);
//
//        TetradLogger.getInstance().removeOutputStream(System.out);
//
//    }

//    public void test8() {
//        TetradLogger.getInstance().addOutputStream(System.out);
//
//        File file = new File("test_data/dataparser/test3.dat");
//        char[] chars = fileToCharArray(file);
//
//        DataReader reader = new DataReader();
//        reader.setDelimiter(DelimiterType.COMMA);
//        reader.setCommentMarker("@");
//        reader.setIdsSupplied(true);
//        reader.setIdLabel(null);
//        reader.setMissingValueMarker("Missing");
//
//        DataModel data = reader.parseTabular(chars);
//
//        System.out.println(data);
//
//        TetradLogger.getInstance().removeOutputStream(System.out);
//
//    }

//    public void test9() {
//        TetradLogger.getInstance().addOutputStream(System.out);
//
//        File file = new File("test_data/dataparser/test4.dat");
//        char[] chars = fileToCharArray(file);
//
//        DataReader reader = new DataReader();
//        reader.setDelimiter(DelimiterType.WHITESPACE);
//
//        DataModel data = reader.parseTabular(chars);
//
//        System.out.println(data);
//
//        TetradLogger.getInstance().removeOutputStream(System.out);
//
//    }

//    public void test11() {
//        try {
//            DataReader reader = new DataReader();
//            reader.parseTabular(new File("test_data/roi_file_1_5.txt"));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    public void test10() {
        TetradLogger.getInstance().addOutputStream(System.out);
        File file = new File("src/test/resources/bollen.txt");
        char[] chars = fileToCharArray(file);

        DataReader reader = new DataReader();
//        DataModel data = parser.parseCovariance(chars);
        DataModel data = reader.parseTabular(chars);

        TetradLogger.getInstance().removeOutputStream(System.out);

        System.out.println(data);
    }

//    public void rtest11() {
//        TetradLogger.getInstance().addOutputStream(System.out);
//        File file = new File("test_data/covartest.dat");
//        char[] chars = fileToCharArray(file);
//
//        DataReader reader = new DataReader();
//        reader.setCommentMarker("//");
//        reader.setDelimiter(DelimiterType.WHITESPACE);
//        reader.setIdsSupplied(true);
//        reader.setIdLabel(null);
//        reader.setMissingValueMarker("*");
//        reader.setMaxIntegralDiscrete(10);
//
//        DataModel data = reader.parseCovariance(chars);
//
//        TetradLogger.getInstance().removeOutputStream(System.out);
//
//        System.out.println(data);
//    }

//    public void rtest12() {
//        try {
//            DataParser parser = new DataParser();
//            parser.setIdsSupplied(true);
//            parser.setIdLabel("ID");
//
//            RectangularDataSet data1 = parser.parseTabular(new File("/home/jdramsey/Desktop/temp/dataset_012.csv"));
//            RectangularDataSet data2 = parser.parseTabular(new File("/home/jdramsey/Desktop/temp/dataset_ABC.csv"));
//            RectangularDataSet data3 = parser.parseTabular(new File("/home/jdramsey/Desktop/temp/dataset_m10p1.csv"));
//
//
////            System.out.println(data3);
//
//            if (data1.getNumColumns() != data2.getNumColumns()) {
//                throw new IllegalArgumentException();
//            }
//
//            if (data1.getNumRows() != data2.getNumRows()) {
//                throw new IllegalArgumentException();
//            }
//
//
//            for (int i = 0; i < data1.getNumRows(); i++) {
//                for (int j = 0; j < data1.getNumColumns(); j++) {
////                    DiscreteVariable var1 = (DiscreteVariable) data1.getVariable(j);
////                    DiscreteVariable ar2 = (DiscreteVariable) data2.getVariable(j);
//
//                    int i1 = data1.getInt(i, j);
//                    int i2 = data3.getInt(i, j);
//
//                    if (i1 != i2) {
//                        throw new IllegalArgumentException();
//                    }
//                }
//            }
//        }
//        catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

//    public void testData12() {
//        try {
//            File file = new File("/home/jdramsey/Desktop/data3.txt");
//
//            DataReader reader = new DataReader();
//
//            DataSet data = reader.parseTabular(file);
//
//            System.out.println(data);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    private char[] fileToCharArray(File file) {
        try {
            FileReader reader = new FileReader(file);
            CharArrayWriter writer = new CharArrayWriter();
            int c;

            while ((c = reader.read()) != -1) {
                writer.write(c);
            }

            return writer.toCharArray();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestDataReader.class);
    }
}



