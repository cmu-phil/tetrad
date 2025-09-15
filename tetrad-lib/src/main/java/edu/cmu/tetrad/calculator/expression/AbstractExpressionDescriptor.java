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

import java.io.Serial;

/**
 * Contains some common methods for Expression Descriptors (see).
 *
 * @author Tyler Gibson
 */
abstract class AbstractExpressionDescriptor implements ExpressionDescriptor {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The human-readable name for the descriptor.
     */
    private final String name;

    /**
     * States what positions the expression can occur in.
     */
    private final Position position;

    /**
     * The symbol used to represent the expression.
     */
    private final String token;

    /**
     * The expression sig.
     */
    private final ExpressionSignature signature;

    /**
     * True if the calculator should display this expression.
     */
    private final boolean display;


    /**
     * Constructs an abstract expression descriptor.
     *
     * @param name      - The name of the descriptor.
     * @param token     The token of the descriptor, also used for the signature.
     * @param position  The position that the expression can occur in.
     * @param unlimited States whether an unlimited number of arguments is allowed.
     */
    public AbstractExpressionDescriptor(String name, String token, Position position, boolean unlimited) {
        if (name == null) {
            throw new NullPointerException("name was null.");
        }
        if (token == null) {
            throw new NullPointerException("token was null.");
        }
        if (position == null) {
            throw new NullPointerException("position was null.");
        }

        this.signature = new Signature(token, unlimited, false, token, "expr");
        this.name = name;
        this.token = token;
        this.position = position;
        this.display = true;
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
     * <p>Getter for the field <code>token</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getToken() {
        return this.token;
    }


    /**
     * <p>Getter for the field <code>signature</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.calculator.expression.ExpressionSignature} object
     */
    public ExpressionSignature getSignature() {
        return this.signature;
    }


    /**
     * <p>Getter for the field <code>position</code>.</p>
     *
     * @return a Position object
     */
    public Position getPosition() {
        return this.position;
    }

    /**
     * <p>isDisplay.</p>
     *
     * @return a boolean
     */
    public boolean isDisplay() {
        return this.display;
    }

    //=============================== Inner Class ==============================================//


    /**
     * Basic implementation of expression signature.
     */
    public static class Signature implements ExpressionSignature {
        @Serial
        private static final long serialVersionUID = 23L;

        /**
         * The function name.
         */
        private final String[] arguments;

        /**
         * The signature string.
         */
        private String signature;

        public Signature(String function, boolean unlimited, boolean commulative, String... arguments) {
            if (function == null) {
                throw new NullPointerException("function was null.");
            }
            this.arguments = arguments;
            this.signature = function;
            // create signature string.
            if (!commulative) {
                this.signature += "(";
                for (int i = 0; i < arguments.length; i++) {
                    if (i != 0) {
                        this.signature += ", ";
                    }
                    this.signature += arguments[i];
                }
                if (unlimited) {
                    if (arguments.length != 0) {
                        this.signature += ", ";
                    }
                    this.signature += "...";
                }
                this.signature += ")";
            }
        }

        /**
         * Generates a simple exemplar of this class to test serialization.
         */
        public static Signature serializableInstance() {
            return new Signature("+", true, false, "a");
        }

        public String getSignature() {
            return this.signature;
        }

        public int getNumberOfArguments() {
            return this.arguments.length;
        }

        public String getArgument(int index) {
            return this.arguments[index];
        }
    }


}





