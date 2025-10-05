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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.calculator.parser.ExpressionLexer;
import edu.cmu.tetrad.calculator.parser.Token;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * @author Tyler Gibson
 */
public final class TestLexer {

    @Test
    public void testLexer() {
        final String s = "(1 + 2.5)";
        ExpressionLexer lexer = new ExpressionLexer(s);

        Token token = lexer.nextToken();
        assertSame(token, Token.LPAREN);
        assertEquals("(", lexer.getTokenString());

        token = lexer.nextToken();
        assertSame(token, Token.NUMBER);
        assertEquals("1", lexer.getTokenString());

        token = lexer.nextToken();
        assertSame(token, Token.OPERATOR);
        assertEquals("+", lexer.getTokenString());

        token = lexer.nextToken();
        assertSame(token, Token.NUMBER);
        assertEquals("2.5", lexer.getTokenString());

        token = lexer.nextToken();
        assertSame(token, Token.RPAREN);
        assertEquals(")", lexer.getTokenString());

        assertSame(lexer.nextToken(), Token.EOF);
    }
}





