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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.calculator.parser.ExpressionLexer;
import edu.cmu.tetrad.calculator.parser.Token;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 *
 * @author Tyler Gibson
 */
public final class TestLexer extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestLexer(String name) {
        super(name);
    }

    


    public void testLexer(){
        String s = "(1 + 2.5)";
        ExpressionLexer lexer = new ExpressionLexer(s);

        Token token = lexer.nextToken();
        System.out.println("Token string: " + lexer.getTokenString());
        assertTrue(token == Token.LPAREN);

        token = lexer.nextToken();
        System.out.println("Token string: " + lexer.getTokenString());
        assertTrue(token == Token.NUMBER);

        token = lexer.nextToken();
        System.out.println("Token string: " + lexer.getTokenString());
        assertTrue(token == Token.OPERATOR);

        token = lexer.nextToken();
        System.out.println("Token string: " + lexer.getTokenString());
        assertTrue(token == Token.NUMBER);

        token = lexer.nextToken();
        System.out.println("Token string: " + lexer.getTokenString());
        assertTrue(token == Token.RPAREN);

        assertTrue(lexer.nextToken() == Token.EOF);
    }



    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestLexer.class);
    }
}




