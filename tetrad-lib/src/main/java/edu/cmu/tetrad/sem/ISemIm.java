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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.Simulator;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;

import java.util.List;

/**
 * An interface for SemIM's; see implementations.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface ISemIm extends Simulator {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * <p>getSemPm.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemPm} object
     */
    SemPm getSemPm();

    /**
     * <p>getFreeParamValues.</p>
     *
     * @return an array of  objects
     */
    double[] getFreeParamValues();

    /**
     * <p>setFreeParamValues.</p>
     *
     * @param params an array of  objects
     */
    void setFreeParamValues(double[] params);

    /**
     * <p>getParamValue.</p>
     *
     * @param parameter a {@link edu.cmu.tetrad.sem.Parameter} object
     * @return a double
     */
    double getParamValue(Parameter parameter);

    /**
     * <p>setParamValue.</p>
     *
     * @param parameter a {@link edu.cmu.tetrad.sem.Parameter} object
     * @param value     a double
     */
    void setParamValue(Parameter parameter, double value);

    /**
     * <p>setFixedParamValue.</p>
     *
     * @param parameter a {@link edu.cmu.tetrad.sem.Parameter} object
     * @param value     a double
     */
    void setFixedParamValue(Parameter parameter, double value);

    /**
     * <p>getParamValue.</p>
     *
     * @param nodeA a {@link edu.cmu.tetrad.graph.Node} object
     * @param nodeB a {@link edu.cmu.tetrad.graph.Node} object
     * @return a double
     */
    double getParamValue(Node nodeA, Node nodeB);

    /**
     * <p>setParamValue.</p>
     *
     * @param nodeA a {@link edu.cmu.tetrad.graph.Node} object
     * @param nodeB a {@link edu.cmu.tetrad.graph.Node} object
     * @param value a double
     */
    void setParamValue(Node nodeA, Node nodeB, double value);

    /**
     * <p>getFreeParameters.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<Parameter> getFreeParameters();

    /**
     * <p>getNumFreeParams.</p>
     *
     * @return a int
     */
    int getNumFreeParams();

    /**
     * <p>getFixedParameters.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<Parameter> getFixedParameters();

    /**
     * <p>getSampleSize.</p>
     *
     * @return a int
     */
    int getSampleSize();

    /**
     * <p>getScore.</p>
     *
     * @return a double
     */
    double getScore();

    /**
     * <p>isParameterBoundsEnforced.</p>
     *
     * @return a boolean
     */
    boolean isParameterBoundsEnforced();

    /**
     * <p>setParameterBoundsEnforced.</p>
     *
     * @param b a boolean
     */
    void setParameterBoundsEnforced(boolean b);

    /**
     * <p>listUnmeasuredLatents.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<Node> listUnmeasuredLatents();

    /**
     * <p>isCyclic.</p>
     *
     * @return a boolean
     */
    boolean isCyclic();

    /**
     * <p>isEstimated.</p>
     *
     * @return a boolean
     */
    boolean isEstimated();

    /**
     * <p>getVariableNodes.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<Node> getVariableNodes();

    /**
     * <p>getMean.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a double
     */
    double getMean(Node node);

    /**
     * <p>getMeanStdDev.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a double
     */
    double getMeanStdDev(Node node);

    /**
     * <p>getIntercept.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a double
     */
    double getIntercept(Node node);

    /**
     * <p>setErrVar.</p>
     *
     * @param nodeA a {@link edu.cmu.tetrad.graph.Node} object
     * @param value a double
     */
    void setErrVar(Node nodeA, double value);

    /**
     * <p>setEdgeCoef.</p>
     *
     * @param x     a {@link edu.cmu.tetrad.graph.Node} object
     * @param y     a {@link edu.cmu.tetrad.graph.Node} object
     * @param value a double
     */
    void setEdgeCoef(Node x, Node y, double value);

    /**
     * <p>setIntercept.</p>
     *
     * @param y         a {@link edu.cmu.tetrad.graph.Node} object
     * @param intercept a double
     */
    void setIntercept(Node y, double intercept);

    /**
     * <p>setMean.</p>
     *
     * @param node  a {@link edu.cmu.tetrad.graph.Node} object
     * @param value a double
     */
    void setMean(Node node, double value);

    /**
     * <p>getStandardError.</p>
     *
     * @param parameter                  a {@link edu.cmu.tetrad.sem.Parameter} object
     * @param maxFreeParamsForStatistics a int
     * @return a double
     */
    double getStandardError(Parameter parameter, int maxFreeParamsForStatistics);

    /**
     * <p>getTValue.</p>
     *
     * @param parameter                  a {@link edu.cmu.tetrad.sem.Parameter} object
     * @param maxFreeParamsForStatistics a int
     * @return a double
     */
    double getTValue(Parameter parameter, int maxFreeParamsForStatistics);

    /**
     * <p>getPValue.</p>
     *
     * @param parameter                  a {@link edu.cmu.tetrad.sem.Parameter} object
     * @param maxFreeParamsForStatistics a int
     * @return a double
     */
    double getPValue(Parameter parameter, int maxFreeParamsForStatistics);

    /**
     * <p>getPValue.</p>
     *
     * @return a double
     */
    double getPValue();

    /**
     * <p>getVariance.</p>
     *
     * @param nodeA     a {@link edu.cmu.tetrad.graph.Node} object
     * @param implCovar a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a double
     */
    double getVariance(Node nodeA, Matrix implCovar);

    /**
     * <p>getStdDev.</p>
     *
     * @param node      a {@link edu.cmu.tetrad.graph.Node} object
     * @param implCovar a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a double
     */
    double getStdDev(Node node, Matrix implCovar);

    /**
     * <p>getMeasuredNodes.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<Node> getMeasuredNodes();

    /**
     * <p>getImplCovarMeas.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    Matrix getImplCovarMeas();

    /**
     * <p>getImplCovar.</p>
     *
     * @param recalculate a boolean
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    Matrix getImplCovar(boolean recalculate);

    /**
     * <p>getBicScore.</p>
     *
     * @return a double
     */
    double getBicScore();

    /**
     * <p>getRmsea.</p>
     *
     * @return a double
     */
    double getRmsea();

    /**
     * <p>getCfi.</p>
     *
     * @return a double
     */
    double getCfi();

    /**
     * <p>getChiSquare.</p>
     *
     * @return a double
     */
    double getChiSquare();

    /**
     * <p>isSimulatedPositiveDataOnly.</p>
     *
     * @return a boolean
     */
    boolean isSimulatedPositiveDataOnly();

}



