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

package edu.cmu.tetrad.calculator.expression;

import java.util.List;

/**
 * Sundry utility methods for expressions.
 */
public class ExpressionUtils {

    public static String renderExpression(Expression expression, ExpressionDescriptor.Position position, String token) {

        List<Expression> expressions = expression.getExpressions();

        if (position == ExpressionDescriptor.Position.INFIX
                || (position == ExpressionDescriptor.Position.BOTH && expressions.size() == 2)) {
            Expression expression1 = expressions.get(0);
            Expression expression2 = expressions.get(1);

            ExpressionDescriptor.Position position1 = expression1.getPosition();
            ExpressionDescriptor.Position position2 = expression2.getPosition();

            StringBuilder buf = new StringBuilder();

            if (position1 == ExpressionDescriptor.Position.INFIX && !expression1.getToken().equals(token)) {
                buf.append("(");
                buf.append(expression1);
                buf.append(")");
            } else {
                buf.append(expression1);
            }

            buf.append(token);

            if (position2 == ExpressionDescriptor.Position.INFIX && !expression2.getToken().equals(token)) {
                buf.append("(");
                buf.append(expression2);
                buf.append(")");
            } else {
                buf.append(expression2);
            }

            return buf.toString();


//            return "(" + expression1 + middleToken +
//                    expression2 + ")";
        } else {
            StringBuilder buf = new StringBuilder();
            buf.append(token);
            buf.append("(");

            for (int i = 0; i < expressions.size(); i++) {
                buf.append(expressions.get(i));

                if (i < expressions.size() - 1) {
                    buf.append(", ");
                }
            }

            buf.append(")");
            return buf.toString();
        }
    }

}



