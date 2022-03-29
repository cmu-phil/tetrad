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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.Matrix;

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

    private final List<DataModel> dataSets;

    private int maxShift = 2;
    private IKnowledge knowledge = new Knowledge2();
    private int c = 4;
    private int maxNumShifts;
    private PrintStream out = System.out;
    private boolean scheduleStop = false;
    private boolean forwardSearch;

    public ShiftSearch(final List<DataModel> dataSets) {
        this(dataSets, null);
    }

    public ShiftSearch(final List<DataModel> dataSets, final Graph measuredDag) {
        this.dataSets = dataSets;
    }

    public int[] search() {
        if (this.maxShift < 1) {
            throw new IllegalStateException("Max shift should be >= 1: " + this.maxShift);
        }

        final int numVars = ((DataSet) this.dataSets.get(0)).getNumColumns();
        final List<Node> nodes = this.dataSets.get(0).getVariables();
        int[] shifts;
        final int[] bestshifts = new int[numVars];
        final int maxNumRows = ((DataSet) this.dataSets.get(0)).getNumRows() - this.maxShift;

        double b = getAvgBic(this.dataSets);

        printShifts(bestshifts, b, nodes);

        final DepthChoiceGenerator generator = new DepthChoiceGenerator(nodes.size(), getMaxNumShifts());
        int[] choice;

        CHOICE:
        while ((choice = generator.next()) != null) {
            shifts = new int[nodes.size()];

            final double zSize = Math.pow(getMaxShift(), choice.length);
            final int iIndex = this.dataSets.get(0).getVariables().indexOf(((DataSet) this.dataSets.get(0)).getVariable("I"));

            Z:
            for (int z = 0; z < zSize; z++) {
                if (this.scheduleStop) break;

                int _z = z;

                for (int i = 0; i < choice.length; i++) {
                    if (choice[i] == iIndex) {
                        continue;
                    }

                    shifts[choice[i]] = (_z % (getMaxShift()) + 1);

                    if (!this.forwardSearch) {
                        shifts[choice[i]] = -shifts[choice[i]];
                    }

                    _z /= getMaxShift();

                }

                final List<DataModel> _shiftedDataSets = getShiftedDataSets(shifts, maxNumRows);
                final double _b = getAvgBic(_shiftedDataSets);

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

    private void printShifts(final int[] shifts, final double b, final List<Node> nodes) {
        final StringBuilder buf = new StringBuilder();

        for (int i = 0; i < shifts.length; i++) {
            buf.append(nodes.get(i) + "=" + shifts[i] + " ");
        }

        buf.append(b);
        println(buf.toString());
    }

    private void println(final String s) {
        System.out.println(s);

        if (this.out != null) {
            this.out.println(s);
            this.out.flush();
        }
    }

    private List<DataModel> getShiftedDataSets(final int[] shifts, final int maxNumRows) {
        final List<DataModel> shiftedDataSets2 = new ArrayList<>();

        for (final DataModel dataSet : this.dataSets) {
            final DataSet shiftedData = TimeSeriesUtils.createShiftedData((DataSet) dataSet, shifts);
            shiftedDataSets2.add(shiftedData);
        }

        return ensureNumRows(shiftedDataSets2, maxNumRows);

//        return shiftedDataSets2;
    }

    private List<DataSet> truncateDataSets(final List<DataSet> dataSets, final int topMargin, final int bottomMargin) {
        final List<DataSet> truncatedData = new ArrayList<>();

        for (final DataSet dataSet : dataSets) {
            final Matrix mat = dataSet.getDoubleData();
            final Matrix mat2 = mat.getPart(topMargin, mat.rows() - topMargin - bottomMargin - 1, 0, mat.columns() - 1);
            truncatedData.add(new BoxDataSet(new DoubleDataBox(mat2.toArray()), dataSet.getVariables()));
        }

        return truncatedData;
    }

    private List<DataModel> ensureNumRows(final List<DataModel> dataSets, final int numRows) {
        final List<DataModel> truncatedData = new ArrayList<>();

        for (final DataModel _dataSet : dataSets) {
            final DataSet dataSet = (DataSet) _dataSet;
            final Matrix mat = dataSet.getDoubleData();
            final Matrix mat2 = mat.getPart(0, numRows - 1, 0, mat.columns() - 1);
            truncatedData.add(new BoxDataSet(new DoubleDataBox(mat2.toArray()), dataSet.getVariables()));
        }

        return truncatedData;
    }

    private double getAvgBic(final List<DataModel> dataSets) {
        final SemBicScoreImages fgesScore = new SemBicScoreImages(dataSets);
        fgesScore.setPenaltyDiscount(this.c);
        final Fges images = new Fges(fgesScore);
        images.setKnowledge(this.knowledge);
        images.search();
        return -images.getModelScore() / dataSets.size();
    }

    public int getMaxShift() {
        return this.maxShift;
    }

    public void setMaxShift(final int maxShift) {
        this.maxShift = maxShift;
    }

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public int getC() {
        return this.c;
    }

    public void setC(final int c) {
        this.c = c;
    }

    public int getMaxNumShifts() {
        return this.maxNumShifts;
    }

    public void setMaxNumShifts(final int maxNumShifts) {
        this.maxNumShifts = maxNumShifts;
    }

    public void setOut(final OutputStream out) {
        this.out = new PrintStream(out);
    }

    public void stop() {
        this.scheduleStop = true;
    }

    public void setForwardSearch(final boolean forwardSearch) {
        this.forwardSearch = forwardSearch;
    }
}


