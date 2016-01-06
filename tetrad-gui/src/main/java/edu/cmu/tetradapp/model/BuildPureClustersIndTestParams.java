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
import edu.cmu.tetrad.search.BpcAlgorithmType;
import edu.cmu.tetrad.search.TestType;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * @author Ricardo Silva
 */
public class BuildPureClustersIndTestParams implements MimIndTestParams {
    static final long serialVersionUID = 23L;

    /**
     * @serial Range [0, 1].
     */
    private double alpha = 0.0001;

    /**
     * @serial Can be null.
     */
    private List varNames;

    /**
     * @serial
     * @deprecated
     */
    private int tetradTestType;

    /**
     * @serial
     */
    private TestType _tetradTestType;

    private BpcAlgorithmType algorithmType = BpcAlgorithmType.BUILD_PURE_CLUSTERS;

    /**
     * @serial
     * @deprecated
     */
    private int purifyTestType;

    /**
     * Serial.
     */
    private TestType _purifyTestType;

    //=============================CONSTRUCTORS==========================//

    public BuildPureClustersIndTestParams(double alpha, TestType tetradTestType, TestType purifyType) {
        setAlpha(alpha);
        setTetradTestType(tetradTestType);
        setPurifyTestType(purifyType);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static BuildPureClustersIndTestParams serializableInstance() {
        return new BuildPureClustersIndTestParams(0.05, TestType.TETRAD_WISHART, TestType.GAUSSIAN_PVALUE);
    }

    //============================PUBLIC METHODS=========================//

    public TestType getTetradTestType() {
        return _tetradTestType;
    }

    public void setTetradTestType(TestType type) {
        _tetradTestType = type;
    }

    public TestType getPurifyTestType() {
        return _purifyTestType;
    }

    public void setPurifyTestType(TestType type) {
        _purifyTestType = type;
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

    public List getVarNames() {
        return this.varNames;
    }

    public List<String> getLatentVarNames() {
        throw new UnsupportedOperationException();
    }

    public void setLatentVarNames(List<String> latentVarNames) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Graph getSourceGraph() {
        return null;
    }

    @Override
    public IKnowledge getKnowledge() {
        return null;
    }

    @Override
    public Clusters getClusters() {
        return null;
    }

    public void setVarNames(List<String> varNames) {
        this.varNames = varNames;
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

        if (alpha < 0.0 && alpha > 1.0) {
            throw new IllegalArgumentException(
                    "Alpha should be in [0, 1]: " + alpha);
        }
    }

    public BpcAlgorithmType getAlgorithmType() {
        return algorithmType;
    }

    public void setAlgorithmType(BpcAlgorithmType algorithmType) {
        this.algorithmType = algorithmType;
    }
}





