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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.calculator.expression.Context;
import edu.cmu.tetrad.calculator.expression.Expression;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.distribution.RealDistribution;

import java.util.ArrayList;
import java.util.List;

/**
 * @return a sample empirical distribution for a particular expression.
 */
public class EmpiricalDistributionForExpression {
    private GeneralizedSemPm semPm;
    private Node error;
    private Context context;

    public EmpiricalDistributionForExpression(GeneralizedSemPm semPm, Node error,
                                              Context context) {
        this.semPm = semPm;
        this.error = error;
        this.context = context;
    }

    public RealDistribution getDist() {
        List<Double> drawFromDistribution = new ArrayList<>();
        Expression expression = semPm.getNodeExpression(error);

        for (int k = 0; k < 5000; k++) {
            double evaluate = expression.evaluate(context);
            drawFromDistribution.add(evaluate);
        }

        return new EmpiricalCdf(drawFromDistribution);
    }
}

