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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;

import java.util.Vector;

/**
 * Immutable object that wraps a dataset and gives a scatter plot.
 *
 * @author Michael Freenor
 */
public class ScatterPlotOld {


    private DataSet dataSet;

    private Vector indexSet;
    private Vector complementIndexSet;

    private boolean drawRegLine;

    private ContinuousVariable yVariable;
    private ContinuousVariable xVariable;

    private double[] yData;
    private double[] xData;

    private double xMin, yMin, xMax, yMax;

    /**
     * Constructs the scatter plot given the dataset to wrap and the node that should be viewed.
     *
     * @param dataSet
     */
    public ScatterPlotOld(DataSet dataSet, ContinuousVariable yVariable, ContinuousVariable xVariable) {

        setDrawRegLine(false);

        /*
            The next two if-else statements deal with what happens if the
            y and x variables are null.  If they are null, the code simply
            selects the first continuous variable from the data set and
            uses that instead.
         */

        if(xVariable != null)
            this.xVariable = xVariable;
        else
        {
            for(int i = 0; i < dataSet.getNumColumns(); i++)
            {
                if (dataSet.getVariable(i) instanceof ContinuousVariable)
                {
                    this.xVariable = (ContinuousVariable) dataSet.getVariable(i);
                    break;
                }
            }
        }
        if(yVariable != null)
            this.setyVariable(yVariable);
        else
        {
            for(int i = 0; i < dataSet.getNumColumns(); i++)
            {
                if (dataSet.getVariable(i) instanceof ContinuousVariable)
                {
                    this.setyVariable((ContinuousVariable) dataSet.getVariable(i));
                    break;
                }
            }
        }

        int yIndex = dataSet.getColumn(yVariable);
        int xIndex = dataSet.getColumn(xVariable);

        if (yIndex == -1) yIndex = 0;
        if (xIndex == -1) xIndex = 0;

        setxData(new double[dataSet.getNumRows()]);
        setyData(new double[dataSet.getNumRows()]);

//        xMin = xMax = dataSet.getDouble(0, xIndex);
//        yMin = yMax = dataSet.getDouble(0, yIndex);

        xMin = Double.MAX_VALUE;
        xMax = Double.MIN_VALUE;
        yMin = Double.MAX_VALUE;
        yMax = Double.MIN_VALUE;

        for(int i = 0; i < dataSet.getNumRows(); i++)
        {
            getyData()[i] = dataSet.getDouble(i, yIndex);
            getxData()[i] = dataSet.getDouble(i, xIndex);

            if(getyData()[i] < yMin) yMin = getyData()[i];
            if(getyData()[i] > yMax) yMax = getyData()[i];
            if(getxData()[i] < xMin) xMin = getxData()[i];
            if(getxData()[i] > xMax) xMax = getxData()[i];
        }
        
        this.setDataSet(dataSet);
        setIndexSet(new Vector());
        setComplementIndexSet(new Vector());
        for(int i = 0; i < dataSet.getNumRows(); i++)
            getIndexSet().add(i);
    }

    //==================================== Public Methods ====================================//

     /**
     * @return the max sample value between the y and x variables.
     *
     * @return - max Value amongst the y and x variables.
     */
    public double getMaxSample() {
        if(xMax > yMax) return xMax;
        else return yMax;
    }


    /**
     * @return the min sample value.
     *
     * @return - min value in sample.
     */
    public double getMinSample() {
        if(xMin < yMin) return xMin;
        else return yMin;
    }

    public ContinuousVariable getXVariable()
    {
        return xVariable;
    }

    public ContinuousVariable getYVariable()
    {
        return getyVariable();
    }

    public double[] getYData()
    {
        return getyData();
    }

    public double[] getXData()
    {
        return getxData();
    }

    /**
     * The complete data set
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    public double[] getyData() {
        return yData;
    }

    public void setyData(double[] yData) {
        this.yData = yData;
    }

    public double[] getxData() {
        return xData;
    }

    public void setxData(double[] xData) {
        this.xData = xData;
    }

    public void setDataSet(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    /**
     * The indexSet is the set of indices that will be rendered when the
     * ScatterPlot is drawn.  This is used in the case of adding conditional
     * variables, where we only want to view the y/x variables plotted against
     * each other when some other set of variables fall within particular ranges.
     *
     * When there are no conditional variables in use, the indexSet contains
     * numbers (0, ..., (n - 1)) (all of the indices).
     */
    public Vector getIndexSet() {
        return indexSet;
    }

    public void setIndexSet(Vector indexSet) {
        this.indexSet = indexSet;
    }

    public Vector getComplementIndexSet() {
        return complementIndexSet;
    }

    public void setComplementIndexSet(Vector complementIndexSet) {
        this.complementIndexSet = complementIndexSet;
    }

    public boolean isDrawRegLine() {
        return drawRegLine;
    }

    public void setDrawRegLine(boolean drawRegLine) {
        this.drawRegLine = drawRegLine;
    }

    public ContinuousVariable getyVariable() {
        return yVariable;
    }

    public void setyVariable(ContinuousVariable yVariable) {
        this.yVariable = yVariable;
    }
}




