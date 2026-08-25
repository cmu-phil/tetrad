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
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.audit.AuditFinding;
import edu.cmu.tetrad.data.audit.DataAudit;
import edu.cmu.tetrad.data.audit.FindingCode;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the audit's notes channel ({@link DataAudit#notes()}): a NON_GAUSSIAN finding attaches the nonlinearity /
 * additivity cross-reference note, a clean Gaussian dataset attaches none, and the note text stays out of the
 * findings themselves and out of the "findings" array of the JSON, so that the findings-only contract of the audit
 * remains literally true.
 *
 * @author josephramsey
 */
public class TestDataAuditNotes {

    private static DataSet dataSet(int n, boolean secondColumnExponential, long seed) {
        Random random = new Random(seed);

        List<Node> variables = new ArrayList<>();
        variables.add(new ContinuousVariable("x1"));
        variables.add(new ContinuousVariable("x2"));

        DataSet data = new BoxDataSet(new DoubleDataBox(n, 2), variables);

        for (int i = 0; i < n; i++) {
            data.setDouble(i, 0, random.nextGaussian());
            data.setDouble(i, 1, secondColumnExponential
                    ? -Math.log(random.nextDouble())
                    : random.nextGaussian());
        }

        return data;
    }

    @Test
    public void testNonGaussianFindingAttachesNote() {
        DataAudit audit = new DataAudit(dataSet(500, true, 38293L));

        assertTrue(audit.hasFinding(FindingCode.NON_GAUSSIAN));
        assertEquals(1, audit.notes().size());
        assertEquals(DataAudit.NON_GAUSSIAN_NOTE, audit.notes().get(0));
        assertTrue(audit.report().contains("Notes"));
        assertTrue(audit.report().contains("Nonlinearity Checks"));
    }

    @Test
    public void testNoNonGaussianFindingAttachesNoNote() {
        DataAudit audit = new DataAudit(dataSet(500, false, 74011L));

        assertFalse(audit.hasFinding(FindingCode.NON_GAUSSIAN));
        assertTrue(audit.notes().isEmpty());
        assertFalse(audit.report().contains("Nonlinearity Checks"));
    }

    /**
     * The note is a cross-reference, not a finding: no finding message may carry it, and in the JSON it must appear
     * under "notes" rather than inside the "findings" array, so that a consumer reading findings alone sees findings
     * only.
     */
    @Test
    public void testNoteIsNotCarriedByAnyFinding() {
        DataAudit audit = new DataAudit(dataSet(500, true, 38293L));

        for (AuditFinding finding : audit.getFindings()) {
            assertFalse(finding.getMessage().contains("Nonlinearity Checks"));
        }

        String json = audit.toJson();
        int findingsStart = json.indexOf("\"findings\":[");
        int notesStart = json.indexOf("\"notes\":[");

        assertTrue(findingsStart >= 0 && notesStart > findingsStart);
        assertFalse(json.substring(findingsStart, notesStart).contains("Nonlinearity Checks"));
        assertTrue(json.substring(notesStart).contains("Nonlinearity Checks"));
    }
}
