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

/**
 * Contains some common methods for Expression Descriptors (see).
 *
 * @author Tyler Gibson
 */
abstract class AbstractExpressionDescriptor implements ExpressionDescriptor {
    static final long serialVersionUID = 23L;

    /**
     * The human readable name for the descriptor.
     */
    private String name;

    /**
     * States what positions the expression can occur in.
     */
    private Position position;

    /**
     * The symbol used to represent the expression.
     */
    private String token;

    /**
     * The expression sig.
     */
    private ExpressionSignature signature;

    /**
     * True if the calculator should display this expression.
     */
    private boolean display;


    /**
     * Constructs an abstract expression descriptor.
     * @param name          - The name of the descriptor.
     * @param token         The token of the descriptor, also used for the signature.
     * @param position      The position that the expression can occur in.
     * @param unlimited     States whether an unlimited number of arguments is allowed.
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

    public String getName() {
        return this.name;
    }


    public String getToken() {
        return this.token;
    }


    public ExpressionSignature getSignature() {
        return this.signature;
    }


    public Position getPosition() {
        return this.position;
    }

    public boolean isDisplay() {
        return display;
    }

    //=============================== Inner Class ==============================================//


    /**
     * Basic implementation of expression signature.
     */
    public static class Signature implements ExpressionSignature {
        static final long serialVersionUID = 23L;

        private String signature;
        private String[] arguments;

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


        public String getSignature() {
            return this.signature;
        }

        public int getNumberOfArguments() {
            return this.arguments.length;
        }

        public String getArgument(int index) {
            return this.arguments[index];
        }

        /**
         * Generates a simple exemplar of this class to test serialization.
         */
        public static Signature serializableInstance() {
            return new Signature("+", true, false, "a");
        }
    }


}




