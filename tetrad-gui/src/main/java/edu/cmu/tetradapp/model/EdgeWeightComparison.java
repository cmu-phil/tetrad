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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.text.NumberFormat;

/**
 * <p>EdgeWeightComparison class.</p>
 *
 * @author Michael Freenor
 * @version $Id: $Id
 */
public class EdgeWeightComparison implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The reference SEM IM.
     */
    private final SemIm reference;

    /**
     * The target SEM IM.
     */
    private final SemIm target;

    /**
     * The name of the model.
     */
    private String name;

    /**
     * <p>Constructor for EdgeWeightComparison.</p>
     *
     * @param reference  a {@link edu.cmu.tetradapp.model.SemEstimatorWrapper} object
     * @param target     a {@link edu.cmu.tetradapp.model.SemEstimatorWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public EdgeWeightComparison(SemEstimatorWrapper reference, SemEstimatorWrapper target, Parameters parameters) {
        this.reference = reference.getEstimatedSemIm();
        this.target = target.getEstimatedSemIm();
    }

    /**
     * <p>Constructor for EdgeWeightComparison.</p>
     *
     * @param reference  a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     * @param target     a {@link edu.cmu.tetradapp.model.SemEstimatorWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public EdgeWeightComparison(SemImWrapper reference, SemEstimatorWrapper target, Parameters parameters) {
        this.reference = reference.getSemIm();
        this.target = target.getEstimatedSemIm();
    }

    /**
     * <p>Constructor for EdgeWeightComparison.</p>
     *
     * @param reference  a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     * @param target     a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public EdgeWeightComparison(SemImWrapper reference, SemImWrapper target, Parameters parameters) {
        this.reference = reference.getSemIm();
        this.target = target.getSemIm();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new DataWrapper(new Parameters());
    }

    /**
     * <p>getDisplayString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getDisplayString() {
        String displayString = "";

        SemIm ref = this.reference;
        Matrix referenceMatrix = ref.getEdgeCoef();
        Matrix targetMatrix = this.target.getEdgeCoef();

        if (targetMatrix.getNumColumns() != referenceMatrix.getNumColumns() || targetMatrix.getNumRows() != referenceMatrix.getNumRows())
            return "The SEM IM's you selected don't have the same number of variables!  No comparison is possible here.";

        double score = 0;
        for (int i = 0; i < ref.getEdgeCoef().getNumRows(); i++) {
            for (int j = 0; j < ref.getEdgeCoef().getNumColumns(); j++) {
                score += (targetMatrix.get(i, j) - referenceMatrix.get(i, j))
                         * (targetMatrix.get(i, j) - referenceMatrix.get(i, j));
            }
        }

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        displayString += "Sum of squared differences of corresponding\nedge weights:\n\n" + nf.format(score);
        return displayString;
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.name = name;
    }
}



