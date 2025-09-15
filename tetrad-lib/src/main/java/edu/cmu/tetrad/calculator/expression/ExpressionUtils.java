///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.calculator.expression;

import java.util.List;

/**
 * Sundry utility methods for expressions.
 */
class ExpressionUtils {

    /**
     * <p>renderExpression.</p>
     *
     * @param expression a {@link edu.cmu.tetrad.calculator.expression.Expression} object
     * @param position   a {@link edu.cmu.tetrad.calculator.expression.ExpressionDescriptor.Position} object
     * @param token      a {@link java.lang.String} object
     * @return a {@link java.lang.String} object
     */
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




