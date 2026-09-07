///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.DiscreteBicScore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Regression test: DiscreteBicScore must not overrun its contingency table when a declared category of the child
 * does not occur in the data. Before the fix, the child value was indexed with its raw code while the table was
 * sized by the number of attested (observed) categories, throwing ArrayIndexOutOfBoundsException whenever a
 * variable's highest declared category was absent. The score on such data must equal the score on the same data
 * recoded to only its observed categories.
 */
public class TestDiscreteBicScoreUnobservedCategory {

    @Test
    public void testChildWithUnobservedMiddleAndTopCategory() {
        int n = 60;
        int[] x = new int[n];
        int[] y = new int[n]; // declared 0..3, only 0 and 3 occur
        for (int i = 0; i < n; i++) {
            x[i] = i % 2;
            y[i] = (i % 3 == 0) == (x[i] == 1) ? 3 : 0;
        }

        DataSet declared = dataSet(new int[][]{x, y}, new int[]{2, 4});
        int[] yRecoded = new int[n];
        for (int i = 0; i < n; i++) yRecoded[i] = y[i] == 3 ? 1 : 0;
        DataSet recoded = dataSet(new int[][]{x, yRecoded}, new int[]{2, 2});

        DiscreteBicScore s1 = new DiscreteBicScore(declared);
        DiscreteBicScore s2 = new DiscreteBicScore(recoded);

        // Fails with ArrayIndexOutOfBoundsException before the fix.
        assertEquals(s2.localScore(1, new int[]{0}), s1.localScore(1, new int[]{0}), 1e-10);
        assertEquals(s2.localScore(1, new int[0]), s1.localScore(1, new int[0]), 1e-10);
        assertEquals(s2.localScoreDiff(0, 1, new int[0]), s1.localScoreDiff(0, 1, new int[0]), 1e-10);
    }

    private static DataSet dataSet(int[][] columns, int[] numCategories) {
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < columns.length; j++) {
            List<String> cats = new ArrayList<>();
            for (int c = 0; c < numCategories[j]; c++) cats.add(Integer.toString(c));
            vars.add(new DiscreteVariable("V" + j, cats));
        }
        return new BoxDataSet(new VerticalIntDataBox(columns), vars);
    }
}
