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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradMatrix;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Tries to find a good shifting of variables to minimize average BIC
 *
 * @author Joseph Ramsey
 */
public class ShiftSearch {

    private List<DataSet> dataSets;

    private int maxShift = 2;
    private IKnowledge knowledge = new Knowledge2();
    private int c = 4;
    private int maxNumShifts;
    private PrintStream out = System.out;
    private boolean scheduleStop = false;
    private boolean forwardSearch;

    public ShiftSearch(List<DataSet> dataSets) {
        this(dataSets, null);
    }

    public ShiftSearch(List<DataSet> dataSets, Graph measuredDag) {
        this.dataSets = dataSets;
    }

    public int[] search() {
        if (maxShift < 1) {
            throw new IllegalStateException("Max shift should be >= 1: " + maxShift);
        }

        int numVars = dataSets.get(0).getNumColumns();
        List<Node> nodes = dataSets.get(0).getVariables();
        int[] shifts;
        int[] bestshifts = new int[numVars];
        int maxNumRows = dataSets.get(0).getNumRows() - maxShift;

        double b = getAvgBic(dataSets);

        printShifts(bestshifts, b, nodes);

        DepthChoiceGenerator generator = new DepthChoiceGenerator(nodes.size(), getMaxNumShifts());
        int[] choice;

        CHOICE:
        while ((choice = generator.next()) != null) {
            shifts = new int[nodes.size()];

            double zSize = Math.pow(getMaxShift(), choice.length);
            int iIndex = dataSets.get(0).getVariables().indexOf(dataSets.get(0).getVariable("I"));

            Z:
            for (int z = 0; z < zSize; z++) {
                if (scheduleStop) break;

                int _z = z;

                for (int i = 0; i < choice.length; i++) {
                    if (choice[i] == iIndex) {
                        continue;
                    }

                    shifts[choice[i]] = (_z % (getMaxShift()) + 1);

                    if (!forwardSearch) {
                        shifts[choice[i]] = -shifts[choice[i]];
                    }

                    _z /= getMaxShift();
                }

                List<DataSet> _shiftedDataSets = getShiftedDataSets(shifts, maxNumRows);
                double _b = getAvgBic(_shiftedDataSets);

                if (_b < 0.999 * b) {
                    b = _b;

                    printShifts(shifts, b, nodes);

                    System.arraycopy(shifts, 0, bestshifts, 0, shifts.length);
                }
            }
        }

        println("\nShifts with the lowest BIC score: ");
        printShifts(bestshifts, b, nodes);

        return bestshifts;
    }

    private void printShifts(int[] shifts, double b, List<Node> nodes) {
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < shifts.length; i++) {
            buf.append(nodes.get(i) + "=" + shifts[i] + " ");
        }

        buf.append(b);
        println(buf.toString());
    }

    private void println(String s) {
        System.out.println(s);

        if (out != null) {
            out.println(s);
            out.flush();
        }
    }

    private List<DataSet> getShiftedDataSets(int[] shifts, int maxNumRows) {
        List<DataSet> shiftedDataSets2 = new ArrayList<DataSet>();

        for (DataSet dataSet : dataSets) {
            DataSet shiftedData = TimeSeriesUtils.createShiftedData(dataSet, shifts);
            shiftedDataSets2.add(shiftedData);
        }

        return ensureNumRows(shiftedDataSets2, maxNumRows);

//        return shiftedDataSets2;
    }

    private List<DataSet> truncateDataSets(List<DataSet> dataSets, int topMargin, int bottomMargin) {
        List<DataSet> truncatedData = new ArrayList<DataSet>();

        for (DataSet dataSet : dataSets) {
            TetradMatrix mat = dataSet.getDoubleData();
            TetradMatrix mat2 = mat.getPart(topMargin, mat.rows() - topMargin - bottomMargin - 1, 0, mat.columns() - 1);
            truncatedData.add(ColtDataSet.makeContinuousData(dataSet.getVariables(), mat2));
        }

        return truncatedData;
    }

    private List<DataSet> ensureNumRows(List<DataSet> dataSets, int numRows) {
        List<DataSet> truncatedData = new ArrayList<DataSet>();

        for (DataSet dataSet : dataSets) {
            TetradMatrix mat = dataSet.getDoubleData();
            TetradMatrix mat2 = mat.getPart(0, numRows - 1, 0, mat.columns() - 1);
            truncatedData.add(ColtDataSet.makeContinuousData(dataSet.getVariables(), mat2));
        }

        return truncatedData;
    }

    private double getAvgBic(List<DataSet> dataSets) {
        Images images = new Images(dataSets);
        images.setPenaltyDiscount(c);
        images.setKnowledge(knowledge);
        Graph pattern = images.search();

//        System.out.println(pattern);

        return -images.getModelScore() / dataSets.size();

//
//        Graph dag = SearchGraphUtils.dagFromPattern(pattern);
//
//        double score = 0.0;
//
//        for (DataSet dataSet : dataSets) {
//            Images images2 = new Images(Collections.singletonList(dataSet));
//
////            println(ges.getScore(dag));
////
//            double bic = -images2.getScore(dag);
////
////            SemPm semPm = new SemPm(dag);
////            SemEstimator est = new SemEstimator(dataSet, semPm);
////            est.estimate();
////            SemIm im = est.getEstimatedSem();
////            double bic = im.getFullBicScore();
//
////            println(im.getFullBicScore());
////            println();
//
//            score += bic;
//        }
//
//        return score /= dataSets.size();
    }

    public int getMaxShift() {
        return maxShift;
    }

    public void setMaxShift(int maxShift) {
        this.maxShift = maxShift;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public int getC() {
        return c;
    }

    public void setC(int c) {
        this.c = c;
    }

    public int getMaxNumShifts() {
        return maxNumShifts;
    }

    public void setMaxNumShifts(int maxNumShifts) {
        this.maxNumShifts = maxNumShifts;
    }

    public void setOut(OutputStream out) {
        this.out = new PrintStream(out);
    }

    public void stop() {
        this.scheduleStop = true;
    }

    public void setForwardSearch(boolean forwardSearch) {
        this.forwardSearch = forwardSearch;
    }
}


