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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.audit.AuditFinding;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * A table of {@link AuditFinding}s for the Data Audit dialog: severity, code, the variables involved, and the
 * finding's message, in the order the audit's checks ran (so findings of the same kind are grouped). Findings
 * describe properties of the data; per the audit's contract they carry no recommendations.
 *
 * @author josephramsey
 * @see edu.cmu.tetrad.data.audit.DataAudit
 */
class DataAuditFindingsModel extends AbstractTableModel {
    private static final long serialVersionUID = 23L;

    /**
     * The column headers, in order.
     */
    private static final String[] COLUMNS = {"Severity", "Code", "Variables", "Message"};

    /**
     * The findings, in check order.
     */
    private final List<AuditFinding> findings;

    /**
     * Constructs the model over the given findings.
     *
     * @param findings the findings, in check order.
     */
    public DataAuditFindingsModel(List<AuditFinding> findings) {
        this.findings = findings;
    }

    /**
     * {@inheritDoc}
     */
    public String getColumnName(int col) {
        return COLUMNS[col];
    }

    /**
     * {@inheritDoc}
     */
    public int getRowCount() {
        return this.findings.size();
    }

    /**
     * {@inheritDoc}
     */
    public int getColumnCount() {
        return COLUMNS.length;
    }

    /**
     * {@inheritDoc}
     */
    public Object getValueAt(int row, int col) {
        AuditFinding finding = this.findings.get(row);

        switch (col) {
            case 0:
                return finding.getSeverity().toString();
            case 1:
                return finding.getCode().toString();
            case 2:
                return String.join(", ", finding.getVariables());
            case 3:
                return finding.getMessage();
            default:
                throw new IllegalArgumentException("Unexpected column: " + col);
        }
    }

    /**
     * Returns the finding at the given row, for renderers.
     *
     * @param row the row.
     * @return the finding.
     */
    public AuditFinding getFinding(int row) {
        return this.findings.get(row);
    }
}
