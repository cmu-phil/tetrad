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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the continuous BIC score for FGES.
 *
 * @author Joseph Ramsey
 */
public class BdeuScoreImages implements IBDeuScore {

    // The covariance matrix.
    private final List<BDeuScore> scores;

    // The variables of the covariance matrix.
    private final List<Node> variables;

    // The sample size of the covariance matrix.
    private int sampleSize;

    // True if verbose output should be sent to out.
    private boolean verbose;

    private double samplePrior = 1.0;

    private double structurePrior = 1.0;

    /**
     * Constructs the score using a covariance matrix.
     */
    public BdeuScoreImages(List<DataModel> dataModels) {
        if (dataModels == null) {
            throw new NullPointerException();
        }

        List<BDeuScore> scores = new ArrayList<>();

        for (DataModel model : dataModels) {
            if (model instanceof DataSet) {
                DataSet dataSet = (DataSet) model;

                if (!dataSet.isDiscrete()) {
                    throw new IllegalArgumentException("Datasets must be discrete.");
                }

                scores.add(new BDeuScore(dataSet));
            } else {
                throw new IllegalArgumentException("Only continuous data sets and covariance matrices may be used as input.");
            }
        }

        List<Node> variables = scores.get(0).getVariables();

        for (int i = 2; i < scores.size(); i++) {
            scores.get(i).setVariables(variables);
        }

        this.scores = scores;
        this.variables = variables;
    }


    public double localScoreDiff(int x, int y, int[] z) {
        double sum = 0.0;

        for (BDeuScore score : this.scores) {
            sum += score.localScoreDiff(x, y, z);
        }

        return sum / this.scores.size();
    }

    @Override
    public double localScoreDiff(int x, int y) {
        return localScoreDiff(x, y, new int[0]);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int[] parents) {
        double sum = 0.0;

        for (BDeuScore score : this.scores) {
            sum += score.localScore(i, parents);
        }

        return sum / this.scores.size();
    }

    public double localScore(int i, int[] parents, int index) {
        return localScoreOneDataSet(i, parents, index);
    }

    private double localScoreOneDataSet(int i, int[] parents, int index) {
        return this.scores.get(index).localScore(i, parents);
    }


    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */
    public double localScore(int i, int parent) {
        double sum = 0.0;

        for (BDeuScore score : this.scores) {
            sum += score.localScore(i, parent);
        }

        return sum / this.scores.size();
    }

    /**
     * Specialized scoring method for no parents. Used to speed up the effect edges search.
     */
    public double localScore(int i) {
        double sum = 0.0;

        for (BDeuScore score : this.scores) {
            sum += score.localScore(i);
        }

        return sum / this.scores.size();
    }

    public void setOut() {
        // The printstream output should be sent to.
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return false;
    }

    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    @Override
    public int getSampleSize() {
        return this.scores.get(0).getSampleSize();
    }

    public double getSamplePrior() {
        return this.samplePrior;
    }

    public void setSamplePrior(double samplePrior) {
        for (BDeuScore score : this.scores) {
            score.setSamplePrior(samplePrior);
        }
        this.samplePrior = samplePrior;
    }

    public double getStructurePrior() {
        return this.structurePrior;
    }

    public void setStructurePrior(double structurePrior) {
        for (BDeuScore score : this.scores) {
            score.setStructurePrior(structurePrior);
        }
        this.structurePrior = structurePrior;
    }

    @Override
    public Node getVariable(String targetName) {
        for (Node node : this.variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxDegree() {
        return 1000;
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    @Override
    public String toString() {
        return "BDeu Score Images";
    }

}



