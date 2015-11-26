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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Stores the parameters needed for multiple linear regression.
 *
 * @author Frank Wimberly, Joseph Ramsey
 */
public final class LogisticRegressionParams implements StandardRegressionParams {
    static final long serialVersionUID = 23L;

    /**
     * @serial Range [0, 1].
     */
    private double alpha = 0.05;

    /**
     * @serial Can be null.
     */
    private String targetName;

    /**
     * @serial Can be null
     */
    private String[] regressorNames = new String[0];

    /**
     * @serial Can be null.
     */
    private List varNames;

    /**
     * @serial Can be null.
     */
    private Graph sourceGraph;

    //=============================CONSTRUCTORS===========================//

    /**
     * Constructs a new Regression paraneter object. Must be a blank
     * constructor.
     */
    public LogisticRegressionParams() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static LogisticRegressionParams serializableInstance() {
        LogisticRegressionParams regressionParams =
                new LogisticRegressionParams();
        regressionParams.setTargetName("X");
        return regressionParams;
    }

    //=============================PUBLIC METHODS========================//

    public List getVarNames() {
        return this.varNames;
    }

    public void setVarNames(List varNames) {
        this.varNames = varNames;
    }

    public Graph getSourceGraph() {
        return this.sourceGraph;
    }

    public void setSourceGraph(Graph graph) {
        this.sourceGraph = graph;
    }

    public void setIndTestType(IndTestType testType) {
        // Ignore.
    }

    public IndTestType getIndTestType() {
        return null;
    }

    @Override
    public void setIndependenceFacts(IndependenceFacts facts) {
        throw new UnsupportedOperationException();
    }

    public void setIndTestParams2(IndTestParams indTestParams) {
        //Ignore.
    }

    public IndTestParams getIndTestParams() {
        return null;
    }

    public IKnowledge getKnowledge() {
        return null;
    }

    public void setKnowledge(IKnowledge knowledge) {
        // Ignore.
    }

    /**
     * Sets the target variable for the regression.
     */
    public void setTargetName(String targetName) {
        if (targetName == null) {
            throw new NullPointerException();
        }
        this.targetName = targetName;
    }

    /*
     * @return the target variable for the PCX search.
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * Sets the significance level for the search.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha out of range: " + alpha);
        }

        this.alpha = alpha;
    }

    /**
     * @return the significance level for the search.
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the array of regressor indices
     */
    public void setRegressorNames(String[] names) {
        this.regressorNames = names;
    }

    /**
     * @return the array of regressor indices
     */
    public String[] getRegressorNames() {
        return regressorNames;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalStateException("Alpha out of range: " + alpha);
        }
    }
}





