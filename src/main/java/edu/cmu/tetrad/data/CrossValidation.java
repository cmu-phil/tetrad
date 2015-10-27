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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
* User: jdramsey
* Date: Feb 22, 2010
* Time: 3:44:11 PM
* To change this template use File | Settings | File Templates.
*/
public class CrossValidation {

    /**
     * The reference dataset passed into the class through the constructor.
     */
    private DataSet dataSet;

    /**
     * Stores training data for getModel k. Only call after processing nextIteration
     */
    private DataSet tDataSet;

    /**
     * Stores validation data for getModel k. Only call after processing nextIteration
     */
    private DataSet vDataSet;

    /**
     * Number of k-fold cross-validation chunks.
     */
    private int k;

    /**
     * Current k.
     */
    private int curk;

    /**
     * Shuffled list of random indices used to construct training and
     * validation datasets.
     */
    private List<Integer> chunks = new ArrayList<Integer>();

    /**
     * Class constructor.
     *
     * @param ds	The dataset on which cross-validation processes.
     * @param k		The number of cross-validation chunks/slices.
     */
    public CrossValidation(DataSet ds, int k){

        // initialize variables
        this.dataSet = ds;
        this.k = k;
        this.curk = 1;

        // randomly partition data
        int rows = dataSet.getNumRows();

        for (int i = 0; i<(rows); i+=k){
            for (int j = 0; j < k; j++){
                if (i+j < rows){
                    chunks.add(j+1);
                }
            }
        }

        Collections.shuffle(chunks);

    }

    /**
     * Tells if cross-validation can iterate to the next k.
     *
     * @return 		Returns a boolean value indicating if more
     * 				chunks can be processed.
     */
    public boolean hasNext(){

        return (curk < k);

    }

    /**
     * Constructs training and validation datasets for getModel k.
     */
    public void nextIteration (){

        List<Node> vars = dataSet.getVariables();
        TetradMatrix data = dataSet.getDoubleData();

        int rows = dataSet.getNumRows();
        int cols = dataSet.getNumColumns();

        int targetRows = rows/k;
        if (curk <= rows%k) targetRows ++;

        double[][] vd = new double[targetRows][cols];
        double[][] td = new double[rows-targetRows][cols];

        int v = 0;
        for (int i = 0; i<rows; i++){
            if (chunks.get(i) == curk){
                for (int j = 0; j<cols; j++) vd[v][j] = data.get(i,j);
                v++;
            } else {
                for (int j = 0; j<cols; j++) td[i-v][j] = data.get(i,j);
            }
        }

        TetradMatrix vdata = new TetradMatrix(vd);
        TetradMatrix tdata = new TetradMatrix(td);

        tDataSet = ColtDataSet.makeContinuousData(vars, tdata);
        vDataSet = ColtDataSet.makeContinuousData(vars, vdata);

        curk++;

    }

    /**
     * Get training DataSet.
     *
     * @return		Returns the dataset containing the
     * 				getModel k's training data
     */
    public DataSet getTrainingData(){
        return tDataSet;
    }

    /**
     * Get validation DataSet.
     *
     * @return		Returns the dataset containing the
     * 				getModel k's validation data
     */
    public DataSet getValidationData(){
        return vDataSet;
    }

}



