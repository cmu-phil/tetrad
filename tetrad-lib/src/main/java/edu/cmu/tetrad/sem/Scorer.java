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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;

import java.util.List;

/**
 * Interface for a class that represents a scoring of a SEM model.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface Scorer {
    /**
     * <p>score.</p>
     *
     * @param dag a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a double
     */
    double score(Graph dag);

    /**
     * <p>getCovMatrix.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    ICovarianceMatrix getCovMatrix();

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    String toString();

    /**
     * <p>getFml.</p>
     *
     * @return a double
     */
    double getFml();

    /**
     * <p>getBicScore.</p>
     *
     * @return a double
     */
    double getBicScore();

    /**
     * <p>getChiSquare.</p>
     *
     * @return a double
     */
    double getChiSquare();

    /**
     * <p>getPValue.</p>
     *
     * @return a double
     */
    double getPValue();

    /**
     * <p>getDataSet.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    DataSet getDataSet();

    /**
     * <p>getNumFreeParams.</p>
     *
     * @return a int
     */
    int getNumFreeParams();

    /**
     * <p>getDof.</p>
     *
     * @return a int
     */
    int getDof();

    /**
     * <p>getSampleSize.</p>
     *
     * @return a int
     */
    int getSampleSize();

    /**
     * <p>getMeasuredNodes.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<Node> getMeasuredNodes();

    /**
     * <p>getSampleCovar.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    Matrix getSampleCovar();

    /**
     * <p>getEdgeCoef.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    Matrix getEdgeCoef();

    /**
     * <p>getErrorCovar.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    Matrix getErrorCovar();

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<Node> getVariables();

    /**
     * <p>getEstSem.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    SemIm getEstSem();
}



