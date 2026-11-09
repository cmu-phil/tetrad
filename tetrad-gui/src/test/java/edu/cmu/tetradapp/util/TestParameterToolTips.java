/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Pins the tooltip content for parameter rows (added 2026-8-24). The last test fails on the unpatched code, where the
 * input control carried no tooltip and the label carried the unwrapped long description.
 */
public class TestParameterToolTips {

    @Test
    public void doubleWithUnboundedAboveShowsDefaultAndLowerBound() {
        ParamDescription d = new ParamDescription("penaltyDiscount", "Penalty discount",
                "The parameter c in a modified BIC score.", 2.0, 0.0, Double.MAX_VALUE);
        assertEquals("Default: 2; range: at least 0", ParameterToolTips.defaultAndRange(d));
    }

    @Test
    public void doubleWithInfinityBoundsShowsNoRange() {
        ParamDescription d = new ParamDescription("x", "X", "x.", 0.5, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY);
        assertEquals("Default: 0.5", ParameterToolTips.defaultAndRange(d));
    }

    @Test
    public void doubleWithMinValueLowerBoundIsUnboundedBelow() {
        ParamDescription d = new ParamDescription("x", "X", "x.", 0.5, Double.MIN_VALUE, 1.0);
        assertEquals("Default: 0.5; range: at most 1", ParameterToolTips.defaultAndRange(d));
    }

    @Test
    public void intWithBothBoundsShowsClosedRange() {
        ParamDescription d = new ParamDescription("depth", "Depth", "d.", -1, -1, Integer.MAX_VALUE);
        assertEquals("Default: -1; range: at least -1", ParameterToolTips.defaultAndRange(d));
        ParamDescription e = new ParamDescription("k", "K", "k.", 3, 1, 10);
        assertEquals("Default: 3; range: [1, 10]", ParameterToolTips.defaultAndRange(e));
        ParamDescription f = new ParamDescription("k", "K", "k.", 3, Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertEquals("Default: 3", ParameterToolTips.defaultAndRange(f));
    }

    @Test
    public void booleanShowsDefaultOnly() {
        ParamDescription d = new ParamDescription("verbose", "Verbose", "v.", Boolean.TRUE);
        assertEquals("Default: true", ParameterToolTips.defaultAndRange(d));
    }

    @Test
    public void enumeratedStringShowsChoices() {
        ParamDescription d = new ParamDescription("pooledTestMethod", "Pooled test method", "p.", "fisher");
        d.setAllowedValues(List.of("fisher", "tippett"));
        assertEquals("Default: fisher; choices: fisher, tippett", ParameterToolTips.defaultAndRange(d));
    }

    @Test
    public void tooltipIsWrappedHtmlWithNameAndEscapedText() {
        ParamDescription d = new ParamDescription("alpha", "Cutoff for p values (alpha)",
                "Reject independence   when p < alpha; larger\n values give denser graphs & more edges.",
                0.01, 0.0, 1.0);
        String tip = ParameterToolTips.forParameter(d);
        assertTrue(tip.startsWith("<html>"));
        assertTrue(tip.endsWith("</html>"));
        assertTrue("wrapping div", tip.contains("width:"));
        assertTrue("parameter id", tip.contains("<code>alpha</code>"));
        assertTrue("short description as heading", tip.contains("<b>Cutoff for p values (alpha)</b>"));
        assertTrue("angle bracket escaped", tip.contains("p &lt; alpha"));
        assertTrue("ampersand escaped", tip.contains("graphs &amp; more"));
        assertFalse("raw angle bracket leaked", tip.contains("p < alpha"));
        assertTrue("internal whitespace collapsed", tip.contains("independence when"));
        assertTrue("facts line", tip.contains("Default: 0.01; range: [0, 1]"));
    }

    @Test
    public void everyManualParameterProducesATooltipWithoutThrowing() {
        ParamDescriptions descs = ParamDescriptions.getInstance();
        int n = 0;
        for (String name : descs.getNames()) {
            String tip = ParameterToolTips.forParameter(descs.get(name));
            assertTrue(name, tip.contains("<code>" + name + "</code>"));
            n++;
        }
        assertTrue("manual had no parameters?", n > 100);
    }

    /**
     * Regression: the input control of a parameter row must carry the tooltip, and it must be the HTML form. Fails on
     * the unpatched code (no tooltip on the field; raw text on the label).
     */
    @Test
    public void parameterRowFieldCarriesHtmlTooltip() {
        Parameters parameters = new Parameters();
        Map<String, Box> rows = ParameterComponents.createParameterComponents(
                Set.of(Params.PENALTY_DISCOUNT), parameters);
        Box row = rows.get(Params.PENALTY_DISCOUNT);
        assertNotNull(row);

        JLabel label = null;
        JComponent field = null;
        for (Component c : row.getComponents()) {
            if (c instanceof JLabel l) label = l;
            else if (c instanceof JTextField t) field = t;
        }
        assertNotNull("label", label);
        assertNotNull("field", field);

        assertNotNull("field has no tooltip", field.getToolTipText());
        assertTrue(field.getToolTipText().startsWith("<html>"));
        assertTrue(field.getToolTipText().contains("<code>" + Params.PENALTY_DISCOUNT + "</code>"));
        assertEquals(label.getToolTipText(), field.getToolTipText());
    }
}
