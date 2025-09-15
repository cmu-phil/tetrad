/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.calculator.expression.Context;
import edu.cmu.tetrad.calculator.expression.Expression;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.distribution.RealDistribution;

import java.util.ArrayList;
import java.util.List;

/**
 * Returns a sample empirical distribution for a particular expression.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class EmpiricalDistributionForExpression {
    private final GeneralizedSemPm semPm;
    private final Node error;
    private final Context context;

    /**
     * <p>Constructor for EmpiricalDistributionForExpression.</p>
     *
     * @param semPm   a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
     * @param error   a {@link edu.cmu.tetrad.graph.Node} object
     * @param context a {@link edu.cmu.tetrad.calculator.expression.Context} object
     */
    public EmpiricalDistributionForExpression(GeneralizedSemPm semPm, Node error,
                                              Context context) {
        this.semPm = semPm;
        this.error = error;
        this.context = context;
    }

    /**
     * Generates an empirical cumulative distribution function (CDF) based on the evaluation
     * of an expression over a repetitive sampling process.
     *
     * The method retrieves an expression associated with a specified error node, evaluates
     * the expression in a specified context over 5000 iterations, and collects the results.
     * These collected values are then used to construct and return an empirical CDF.
     *
     * @return a {@link RealDistribution} object representing
     *         the empirical cumulative distribution function (CDF) generated based on the
     *         evaluated sample values.
     */
    public RealDistribution getDist() {
        List<Double> drawFromDistribution = new ArrayList<>();
        Expression expression = this.semPm.getNodeExpression(this.error);

        for (int k = 0; k < 5000; k++) {
            double evaluate = expression.evaluate(this.context);
            drawFromDistribution.add(evaluate);
        }

        return new EmpiricalCdf(drawFromDistribution);
    }
}

