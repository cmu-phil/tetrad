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

import edu.cmu.tetrad.data.Clusters;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.TestType;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * @author Ricardo Silva
 */
public class PurifyIndTestParams implements MimIndTestParams {
    static final long serialVersionUID = 23L;

    /**
     * @serial Range [0, 1].
     */
    private double alpha = 0.0001;

    /**
     * @serial Range greater than or equal to 1.
     */
    private int numClusters;

    /**
     * @serial No range restriction.
     */
    private TestType _tetradTestType;

    /**
     * @serial
     * @deprecated
     */
    private int tetradTestType;

    /**
     * Serial.
     */
    private TestType _purifyTestType;

    /**
     * @serial Can be null.
     */
    private MimParams originalParams;

    /**
     * @serial Can be null.
     */
    private List<String> varNames;
    private Graph sourceGraph;
    private IKnowledge getKnowledge;

    //=============================CONSTRUCTORS==========================//

    public PurifyIndTestParams(double alpha, int numClusters,
                               TestType tetradTestType, TestType purifyTestType, MimParams originalParams) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha out of range: " + alpha);
        }

        if (numClusters < 1) {
            throw new IllegalArgumentException(
                    "Number of clusters should be >= 1: " + numClusters);
        }

        this.alpha = alpha;
        this.numClusters = numClusters;
        this._tetradTestType = tetradTestType;
        this._purifyTestType = purifyTestType;
        this.originalParams = originalParams;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PurifyIndTestParams serializableInstance() {
        return new PurifyIndTestParams(0.0001, 2, TestType.TETRAD_WISHART,
                TestType.GAUSSIAN_FACTOR, BuildPureClustersParams.serializableInstance());
    }

    //=============================PUBLIC METHODS=======================//

    public TestType getTetradTestType() {
        return _tetradTestType;
    }

    public void setTetradTestType(TestType type) {
        _tetradTestType = type;
    }

    public Clusters getClusters() {
        return originalParams.getClusters();
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha out of range: " + alpha);
        }
        this.alpha = alpha;
    }

    public List<String> getVarNames() {
        return this.varNames;
    }

    public void setVarNames(List<String> varNames) {
        this.varNames = varNames;
    }

    public List<String> getLatentVarNames() {
        throw new UnsupportedOperationException();
    }

    public void setLatentVarNames(List<String> latentVarNames) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Graph getSourceGraph() {
        return this.sourceGraph;
    }

    @Override
    public IKnowledge getKnowledge() {
        return this.getKnowledge;
    }

    //=============================PRIVATE METHODS=======================//

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

        if (numClusters < 1) {
            throw new IllegalStateException(
                    "NumClusters out of range: " + numClusters);
        }
    }

    public TestType getPurifyTestType() {
        return _purifyTestType;
    }

    public void setPurifyTestType(TestType _purifyTestType) {
        this._purifyTestType = _purifyTestType;
    }
}





