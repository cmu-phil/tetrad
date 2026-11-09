package edu.cmu.tetrad.test;

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
 * Tests the RESERVED_VARIABLE_NAME audit check: names that Tetrad's own machinery gives special
 * meaning are reported, aggregated into one finding per collision category, with lag-suffix
 * names reported at INFO (since lagged data carries them on purpose) and the rest at WARNING.
 * The check is findings-only: it reports the property and takes no position on what to do.
 *
 * <p>These tests do not compile against the unpatched jar, since the finding code is new there.
 *
 * @author josephramsey
 */
public class TestDataAuditVariableNames {

    private static DataSet dataSet(List<String> names) {
        List<Node> variables = new ArrayList<>();

        for (String name : names) {
            variables.add(new ContinuousVariable(name));
        }

        DataSet data = new BoxDataSet(new DoubleDataBox(50, names.size()), variables);
        Random random = new Random(38293L);

        for (int i = 0; i < 50; i++) {
            for (int j = 0; j < names.size(); j++) {
                data.setDouble(i, j, random.nextGaussian());
            }
        }

        return data;
    }

    /**
     * A dataset with unremarkable names produces no RESERVED_VARIABLE_NAME finding.
     */
    @Test
    public void testCleanNamesProduceNoFinding() {
        DataAudit audit = new DataAudit(dataSet(List.of("X1", "X2", "income", "mean_age")));
        assertFalse(audit.hasFinding(FindingCode.RESERVED_VARIABLE_NAME));
    }

    /**
     * Valid lag-suffix names are aggregated into a single INFO finding listing all of them, so a
     * fully lagged dataset produces one finding, not one per column.
     */
    @Test
    public void testLagSuffixNamesAggregateToOneInfoFinding() {
        DataAudit audit = new DataAudit(dataSet(List.of("X", "Y", "X:1", "Y:1", "X:2", "Y:2")));

        List<AuditFinding> findings = audit.getFindings(FindingCode.RESERVED_VARIABLE_NAME);

        assertEquals(1, findings.size());
        assertEquals(AuditFinding.Severity.INFO, findings.get(0).getSeverity());
        assertEquals(List.of("X:1", "Y:1", "X:2", "Y:2"), findings.get(0).getVariables());
    }

    /**
     * A colon name whose suffix does not parse as a lag gets a WARNING finding, separate from the
     * INFO finding for valid lag suffixes in the same dataset.
     */
    @Test
    public void testColonNonLagNameGetsWarning() {
        DataAudit audit = new DataAudit(dataSet(List.of("X", "X:1", "price:usd")));

        List<AuditFinding> findings = audit.getFindings(FindingCode.RESERVED_VARIABLE_NAME);

        assertEquals(2, findings.size());

        AuditFinding warning = findings.stream()
                .filter(f -> f.getSeverity() == AuditFinding.Severity.WARNING)
                .findFirst().orElseThrow();

        assertEquals(List.of("price:usd"), warning.getVariables());
        assertTrue(warning.getMessage().contains("price:usd"));
    }

    /**
     * A name beginning with "E_" gets a WARNING, and when the remainder after "E_" is itself the
     * name of another variable in the dataset, the message says so.
     */
    @Test
    public void testErrorPrefixNamesReported() {
        DataAudit audit = new DataAudit(dataSet(List.of("X", "E_X", "E_field")));

        List<AuditFinding> findings = audit.getFindings(FindingCode.RESERVED_VARIABLE_NAME);

        assertEquals(1, findings.size());

        AuditFinding finding = findings.get(0);

        assertEquals(AuditFinding.Severity.WARNING, finding.getSeverity());
        assertEquals(List.of("E_X", "E_field"), finding.getVariables());
        assertTrue("Message should note the outright collision for E_X: " + finding.getMessage(),
                finding.getMessage().contains("E_X") && finding.getMessage().contains("another variable"));
    }

    /**
     * Names containing '*' or ',' are reported in one WARNING finding.
     */
    @Test
    public void testKnowledgeSpecCharactersReported() {
        DataAudit audit = new DataAudit(dataSet(List.of("X", "A*B", "left,right")));

        List<AuditFinding> findings = audit.getFindings(FindingCode.RESERVED_VARIABLE_NAME);

        assertEquals(1, findings.size());
        assertEquals(AuditFinding.Severity.WARNING, findings.get(0).getSeverity());
        assertEquals(List.of("A*B", "left,right"), findings.get(0).getVariables());
    }
}
