package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.TsUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests that variable names which collide with Tetrad's internal naming conventions are
 * handled sanely: the lag-name parsing utilities are forgiving and consistent with
 * NaturalSort's definition of a valid lagged name (exactly one colon with a nonnegative
 * integer suffix), createLagData refuses colon-containing names loudly instead of
 * mis-tiering them or silently skipping the lagging, and Knowledge wildcard specs treat
 * regex metacharacters in names literally.
 *
 * <p>Each test here fails on the unpatched build and passes on the patched one.
 *
 * @author josephramsey
 */
public class TestVariableNameRobustness {

    /**
     * A colon followed by a non-integer is not a lag suffix; getLag should treat the name
     * as unlagged rather than throwing NumberFormatException. Valid lag suffixes still
     * parse, and a doubled suffix like "A:1:2" is not a valid lagged name.
     */
    @Test
    public void testGetLagForgiving() {
        assertEquals(0, TsUtils.getLag("price:usd"));
        assertEquals(3, TsUtils.getLag("X:3"));
        assertEquals(0, TsUtils.getLag("X"));
        assertEquals(0, TsUtils.getLag("A:1:2"));
    }

    /**
     * getNameNoLag should strip the suffix only from valid lagged names. Formerly it cut
     * at the first colon unconditionally, so "price:usd" came back as "price".
     */
    @Test
    public void testGetNameNoLagOnlyStripsValidSuffixes() {
        assertEquals("price:usd", TsUtils.getNameNoLag("price:usd"));
        assertEquals("X", TsUtils.getNameNoLag("X:3"));
        assertEquals("A:1:2", TsUtils.getNameNoLag("A:1:2"));
        assertEquals("X", TsUtils.getNameNoLag("X"));
    }

    /**
     * getIndex should never throw. Formerly an entirely numeric name threw
     * IllegalArgumentException("Not integer suffix.") -- exactly backwards, since names
     * with no integer suffix at all returned 0.
     */
    @Test
    public void testGetIndexNeverThrows() {
        assertEquals(2024, TsUtils.getIndex("2024"));
        assertEquals(0, TsUtils.getIndex("X_lag"));
        assertEquals(12, TsUtils.getIndex("X12"));
        assertEquals(2, TsUtils.getIndex("X_lag2"));
    }

    /**
     * createLagData on data with a colon-containing variable name should throw a clear
     * IllegalArgumentException. Formerly it caught the resulting NumberFormatException
     * internally and silently returned the original, unlagged dataset.
     */
    @Test
    public void testCreateLagDataRejectsColonNames() {
        List<Node> variables = new ArrayList<>();
        variables.add(new ContinuousVariable("price:usd"));
        variables.add(new ContinuousVariable("X"));

        DataSet data = new BoxDataSet(new DoubleDataBox(10, 2), variables);

        for (int i = 0; i < 10; i++) {
            data.setDouble(i, 0, i);
            data.setDouble(i, 1, 10 - i);
        }

        try {
            DataSet lagged = TsUtils.createLagData(data, 1);
            fail("Expected IllegalArgumentException for colon-containing variable name, but "
                 + "createLagData returned a dataset with " + lagged.getNumColumns()
                 + " columns (the unpatched code silently skips the lagging).");
        } catch (IllegalArgumentException e) {
            assertTrue("Exception message should name the offending variable: " + e.getMessage(),
                    e.getMessage().contains("price:usd"));
        }
    }

    /**
     * In a Knowledge wildcard spec, a dot should match a literal dot, not any character.
     * Formerly the spec "X.*" was compiled as the raw regex "X..*", which also matched
     * names like "Xa1".
     */
    @Test
    public void testKnowledgeWildcardDotIsLiteral() {
        Knowledge knowledge = new Knowledge(Arrays.asList("X.1", "X.2", "Xa1", "Y"));

        knowledge.setForbidden("X.*", "Y");

        assertTrue(knowledge.isForbidden("X.1", "Y"));
        assertTrue(knowledge.isForbidden("X.2", "Y"));
        assertFalse("Wildcard spec \"X.*\" should not match \"Xa1\"; the dot must be literal.",
                knowledge.isForbidden("Xa1", "Y"));
    }

    /**
     * A wildcard spec containing regex metacharacters like '(' should match literally
     * rather than throwing PatternSyntaxException.
     */
    @Test
    public void testKnowledgeWildcardMetacharactersDoNotThrow() {
        Knowledge knowledge = new Knowledge(Arrays.asList("V(1)", "V(2)", "W"));

        knowledge.setForbidden("V(*", "W");

        assertTrue(knowledge.isForbidden("V(1)", "W"));
        assertTrue(knowledge.isForbidden("V(2)", "W"));
        assertFalse(knowledge.isForbidden("W", "V(1)"));
    }
}
