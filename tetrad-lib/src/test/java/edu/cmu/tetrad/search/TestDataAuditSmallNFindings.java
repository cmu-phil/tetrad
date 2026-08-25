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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.data.audit.AuditFinding;
import edu.cmu.tetrad.data.audit.DataAudit;
import edu.cmu.tetrad.data.audit.FindingCode;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the small-n / repeated-measures audit findings added for datasets like the cystic-fibrosis example:
 * DUPLICATE_COLUMNS (localizable exact affine copies, including sign-reversed indicator pairs),
 * COMPLETE_CASES_FORCE_SINGULARITY (complete-case count alone forces singularity of any complete-case covariance),
 * GROUP_CONSTANT_VARIABLE (subject-level variables in a grouped file, effective n = number of groups), and the
 * nUsed count now carried by HIGH_CORRELATION.
 *
 * @author josephramsey
 */
public class TestDataAuditSmallNFindings {

    private static boolean has(List<AuditFinding> findings, FindingCode code, String... vars) {
        K:
        for (AuditFinding f : findings) {
            if (f.getCode() != code) continue;
            for (String v : vars) {
                if (!f.getVariables().contains(v)) continue K;
            }
            return true;
        }
        return false;
    }

    /**
     * A grouped mixed dataset: 4 groups x 10 rows; a group id; two group-constant binaries that are exact
     * complements; a row-level binary duplicated in a second column; and continuous variables with a block of
     * missingness leaving fewer complete rows than continuous variables.
     */
    private static DataSet grouped() {
        int n = 40;
        List<Node> vars = new ArrayList<>();
        vars.add(new DiscreteVariable("group", 4));       // 0
        vars.add(new DiscreteVariable("subjA", 2));       // 1: group-constant
        vars.add(new DiscreteVariable("subjB", 2));       // 2: complement of subjA
        vars.add(new DiscreteVariable("flag", 2));        // 3: row-level
        vars.add(new DiscreteVariable("flagCopy", 2));    // 4: duplicate of flag
        for (int j = 0; j < 6; j++) vars.add(new ContinuousVariable("C" + j)); // 5..10

        DataSet d = new BoxDataSet(new MixedDataBox(vars, n), vars);
        Random rng = new Random(7311L);

        for (int i = 0; i < n; i++) {
            int g = i / 10;
            d.setInt(i, 0, g);
            int a = g % 2;
            d.setInt(i, 1, a);
            d.setInt(i, 2, 1 - a);
            int f = rng.nextInt(2);
            d.setInt(i, 3, f);
            d.setInt(i, 4, f);

            for (int j = 0; j < 6; j++) {
                d.setDouble(i, 5 + j, rng.nextGaussian());
            }
        }

        // Missing block: leave only 4 complete rows (< 6 continuous variables), by blanking C0 on most rows.
        for (int i = 0; i < n; i++) {
            if (i % 10 != 0) d.setDouble(i, 5, Double.NaN);
        }

        return d;
    }

    @Test
    public void testFindingsFireAndLocalize() {
        DataSet d = grouped();
        DataAudit.Config config = new DataAudit.Config().withSerialGroupVariable("group");
        DataAudit audit = new DataAudit(d, config);
        List<AuditFinding> f = audit.getFindings();

        // Duplicates localized: complements and copies, including the discrete pairs.
        assertTrue(has(f, FindingCode.DUPLICATE_COLUMNS, "subjA", "subjB"));
        assertTrue(has(f, FindingCode.DUPLICATE_COLUMNS, "flag", "flagCopy"));

        // Complete-case count (4) minus one is below the number of continuous variables (6).
        assertTrue(has(f, FindingCode.COMPLETE_CASES_FORCE_SINGULARITY));

        // Group-constant variables named; row-level ones not.
        assertTrue(has(f, FindingCode.GROUP_CONSTANT_VARIABLE, "subjA"));
        assertTrue(has(f, FindingCode.GROUP_CONSTANT_VARIABLE, "subjB"));
        assertFalse(has(f, FindingCode.GROUP_CONSTANT_VARIABLE, "flag"));
    }

    @Test
    public void testNoSpuriousFindingsOnCleanData() {
        int n = 60;
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 4; j++) vars.add(new ContinuousVariable("X" + j));

        DataSet d = new BoxDataSet(new MixedDataBox(vars, n), vars);
        Random rng = new Random(101L);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < 4; j++) d.setDouble(i, j, rng.nextGaussian());
        }

        List<AuditFinding> f = new DataAudit(d).getFindings();
        assertFalse(has(f, FindingCode.DUPLICATE_COLUMNS));
        assertFalse(has(f, FindingCode.COMPLETE_CASES_FORCE_SINGULARITY));
        assertFalse(has(f, FindingCode.GROUP_CONSTANT_VARIABLE));
        assertFalse(has(f, FindingCode.PAIRWISE_CORRELATION_NOT_PSD));
    }
}
