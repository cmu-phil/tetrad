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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.ImagesScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.SublistGenerator;
import org.apache.commons.math3.util.FastMath;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Tries to find a good shifting of variables to minimize average BIC for
 * time-series data. The idea is that the data one is presented with may have the variables temporally shifted with
 * respect to one another. ShiftSearch attempts to find a shifting of the variables that reduces this temporal
 * shifting.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ShiftSearch {
    private final List<DataModel> dataSets;
    private int maxShift = 2;
    private Knowledge knowledge = new Knowledge();
    private int c = 4;
    private int maxNumShifts;
    private PrintStream out = System.out;
    private boolean scheduleStop;
    private boolean forwardSearch;
    private boolean precomputeCovariances = false;

    /**
     * <p>Constructor for ShiftSearch.</p>
     *
     * @param dataSets a {@link java.util.List} object
     */
    public ShiftSearch(List<DataModel> dataSets) {
        this.dataSets = dataSets;
    }

    /**
     * <p>search.</p>
     *
     * @return an array of {@link int} objects
     */
    public int[] search() {
        if (this.maxShift < 1) {
            throw new IllegalStateException("Max shift should be >= 1: " + this.maxShift);
        }

        int numVars = ((DataSet) this.dataSets.get(0)).getNumColumns();
        List<Node> nodes = this.dataSets.get(0).getVariables();
        int[] shifts;
        int[] bestshifts = new int[numVars];
        int maxNumRows = ((DataSet) this.dataSets.get(0)).getNumRows() - this.maxShift;

        double b = getAvgBic(this.dataSets);

        printShifts(bestshifts, b, nodes);

        SublistGenerator generator = new SublistGenerator(nodes.size(), getMaxNumShifts());
        int[] choice;

        while ((choice = generator.next()) != null) {
            shifts = new int[nodes.size()];

            double zSize = FastMath.pow(getMaxShift(), choice.length);
            int iIndex = this.dataSets.get(0).getVariables().indexOf(this.dataSets.get(0).getVariable("I"));

            for (int z = 0; z < zSize; z++) {
                if (this.scheduleStop) break;

                int _z = z;

                for (int j : choice) {
                    if (j == iIndex) {
                        continue;
                    }

                    shifts[j] = (_z % (getMaxShift()) + 1);

                    if (!this.forwardSearch) {
                        shifts[j] = -shifts[j];
                    }

                    _z /= getMaxShift();

                }

                List<DataModel> _shiftedDataSets = getShiftedDataSets(shifts, maxNumRows);
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

    /**
     * <p>Getter for the field <code>maxShift</code>.</p>
     *
     * @return a int
     */
    public int getMaxShift() {
        return this.maxShift;
    }

    /**
     * <p>Setter for the field <code>maxShift</code>.</p>
     *
     * @param maxShift a int
     */
    public void setMaxShift(int maxShift) {
        this.maxShift = maxShift;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * <p>Setter for the field <code>knowledge</code>.</p>
     *
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * <p>Getter for the field <code>c</code>.</p>
     *
     * @return a int
     */
    public int getC() {
        return this.c;
    }

    /**
     * <p>Setter for the field <code>c</code>.</p>
     *
     * @param c a int
     */
    public void setC(int c) {
        this.c = c;
    }

    /**
     * <p>Getter for the field <code>maxNumShifts</code>.</p>
     *
     * @return a int
     */
    public int getMaxNumShifts() {
        return this.maxNumShifts;
    }

    /**
     * <p>Setter for the field <code>maxNumShifts</code>.</p>
     *
     * @param maxNumShifts a int
     */
    public void setMaxNumShifts(int maxNumShifts) {
        this.maxNumShifts = maxNumShifts;
    }

    /**
     * <p>Setter for the field <code>out</code>.</p>
     *
     * @param out a {@link java.io.OutputStream} object
     */
    public void setOut(OutputStream out) {
        this.out = new PrintStream(out);
    }

    /**
     * <p>stop.</p>
     */
    public void stop() {
        this.scheduleStop = true;
    }

    /**
     * <p>Setter for the field <code>forwardSearch</code>.</p>
     *
     * @param forwardSearch a boolean
     */
    public void setForwardSearch(boolean forwardSearch) {
        this.forwardSearch = forwardSearch;
    }

    private void printShifts(int[] shifts, double b, List<Node> nodes) {
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < shifts.length; i++) {
            buf.append(nodes.get(i)).append("=").append(shifts[i]).append(" ");
        }

        buf.append(b);
        println(buf.toString());
    }

    private void println(String s) {
        System.out.println(s);

        if (this.out != null) {
            this.out.println(s);
            this.out.flush();
        }
    }

    private List<DataModel> getShiftedDataSets(int[] shifts, int maxNumRows) {
        List<DataModel> shiftedDataSets2 = new ArrayList<>();

        for (DataModel dataSet : this.dataSets) {
            DataSet shiftedData = TsUtils.createShiftedData((DataSet) dataSet, shifts);
            shiftedDataSets2.add(shiftedData);
        }

        return ensureNumRows(shiftedDataSets2, maxNumRows);
    }

    private List<DataModel> ensureNumRows(List<DataModel> dataSets, int numRows) {
        List<DataModel> truncatedData = new ArrayList<>();

        for (DataModel _dataSet : dataSets) {
            DataSet dataSet = (DataSet) _dataSet;
            Matrix mat = dataSet.getDoubleData();
            Matrix mat2 = mat.getPart(0, numRows - 1, 0, mat.getNumColumns() - 1);
            truncatedData.add(new BoxDataSet(new DoubleDataBox(mat2.toArray()), dataSet.getVariables()));
        }

        return truncatedData;
    }

    private double getAvgBic(List<DataModel> dataSets) {
        List<Score> scores = new ArrayList<>();
        for (DataModel dataSet : dataSets) {
            SemBicScore _score = new SemBicScore((DataSet) dataSet, precomputeCovariances);
            scores.add(_score);
        }

        ImagesScore imagesScore = new ImagesScore(scores);
        Fges images = new Fges(imagesScore);
        images.setKnowledge(this.knowledge);
        images.search();
        return -images.getModelScore() / dataSets.size();
    }

    /**
     * <p>Setter for the field <code>precomputeCovariances</code>.</p>
     *
     * @param precomputeCovariances a boolean
     */
    public void setPrecomputeCovariances(boolean precomputeCovariances) {
        this.precomputeCovariances = precomputeCovariances;
    }
}


