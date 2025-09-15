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

import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetrad.data.RegexTokenizer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests to make sure the DelimiterType enumeration hasn't been tampered with.
 *
 * @author josephramsey
 */
public final class TestDelimiterTokenizer {

    @Test
    public void testTokenizer() {
        final String line2 = "a,b,c,d";
        String[] tokens = {"a", "b", "c", "d"};
        DelimiterType delimiterType = DelimiterType.COMMA;
        RegexTokenizer tokenizer =
                new RegexTokenizer(line2, delimiterType.getPattern(), '"');

        int index = 0;
        while (tokenizer.hasMoreTokens()) {
            String s = tokenizer.nextToken();
            assertEquals(tokens[index++], s);
        }
    }
}






