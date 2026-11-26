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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;
import org.junit.Test;

import javax.swing.JLabel;

import java.awt.Component;
import java.awt.Color;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * The Scale Free tab of the random graph editor keeps its settings in the parameters the random graph is made from.
 * It used to keep them in the machine's preferences, where the graph maker did not look, so that nothing set in the
 * tab had any effect.
 *
 * @author Claude (Anthropic)
 */
public class TestScaleFreeTab {

    private static <T> List<T> fields(Container container, Class<T> kind, List<T> into) {
        for (Component component : container.getComponents()) {
            if (kind.isInstance(component)) {
                into.add(kind.cast(component));
            } else if (component instanceof Container inner) {
                fields(inner, kind, into);
            }
        }

        return into;
    }

    @Test
    public void testTheTabEditsTheParameters() {
        Parameters parameters = new Parameters();
        RandomDagScaleFreeEditor tab = new RandomDagScaleFreeEditor(parameters);

        // In the order they are laid out: measured nodes, latents; then alpha, beta, gamma, delta in, delta out.
        List<IntTextField> counts = fields(tab, IntTextField.class, new ArrayList<>());
        List<DoubleTextField> values = fields(tab, DoubleTextField.class, new ArrayList<>());
        assertEquals(2, counts.size());
        assertEquals(5, values.size());

        // It starts from the registered defaults.
        assertEquals(0.05, values.get(0).getValue(), 0.0);
        assertEquals(0.9, values.get(1).getValue(), 0.0);
        assertEquals(0.05, values.get(2).getValue(), 1e-12);
        assertEquals(3.0, values.get(3).getValue(), 0.0);
        assertEquals(3.0, values.get(4).getValue(), 0.0);

        // What is set in it is where the graph maker reads it.
        values.get(0).setValue(0.3);
        values.get(1).setValue(0.5);
        values.get(3).setValue(0.25);
        values.get(4).setValue(1.5);
        counts.get(0).setValue(12);
        counts.get(1).setValue(2);

        assertEquals(0.3, parameters.getDouble("scaleFreeAlpha"), 0.0);
        assertEquals(0.5, parameters.getDouble("scaleFreeBeta"), 0.0);
        assertEquals(0.25, parameters.getDouble("scaleFreeDeltaIn"), 0.0);
        assertEquals(1.5, parameters.getDouble("scaleFreeDeltaOut"), 0.0);
        assertEquals(12, parameters.getInt("newGraphNumMeasuredNodes"));
        assertEquals(2, parameters.getInt("newGraphNumLatents"));

        // Gamma is what is left of 1.
        assertEquals(0.2, values.get(2).getValue(), 1e-12);
        assertEquals(14, tab.getNumNodes());
    }

    /**
     * The tab shares its two node counts with the DAG tab, and shows what the other has set when it is brought up
     * to date.
     */
    @Test
    public void testNodeCountsFollowTheParameters() {
        Parameters parameters = new Parameters();
        RandomDagScaleFreeEditor tab = new RandomDagScaleFreeEditor(parameters);
        List<IntTextField> counts = fields(tab, IntTextField.class, new ArrayList<>());

        parameters.set("newGraphNumMeasuredNodes", 20);
        parameters.set("newGraphNumLatents", 3);
        tab.refreshNodeCounts();

        assertEquals(20, counts.get(0).getValue());
        assertEquals(3, counts.get(1).getValue());
    }

    /**
     * Parameters stored before the deltas were registered as real numbers hold whole numbers; the tab reads them.
     */
    @Test
    public void testWholeNumberDeltasFromAnOlderSession() {
        Parameters parameters = new Parameters();
        parameters.set("scaleFreeDeltaIn", 3);
        parameters.set("scaleFreeDeltaOut", 4);

        List<DoubleTextField> values = fields(new RandomDagScaleFreeEditor(parameters), DoubleTextField.class,
                new ArrayList<>());
        assertEquals(3.0, values.get(3).getValue(), 0.0);
        assertEquals(4.0, values.get(4).getValue(), 0.0);
    }

    /**
     * A delta may be set to 0 in the tab and may not be set below it: a negative value is not taken, and the field
     * and the parameters keep what they had.
     */
    @Test
    public void testADeltaMayBeZeroButNotLess() {
        Parameters parameters = new Parameters();
        List<DoubleTextField> values = fields(new RandomDagScaleFreeEditor(parameters), DoubleTextField.class,
                new ArrayList<>());

        values.get(3).setValue(0.0);
        values.get(4).setValue(0.0);
        assertEquals(0.0, parameters.getDouble("scaleFreeDeltaIn"), 0.0);
        assertEquals(0.0, parameters.getDouble("scaleFreeDeltaOut"), 0.0);

        values.get(3).setValue(0.5);
        values.get(4).setValue(0.75);
        values.get(3).setValue(-1.0);
        values.get(4).setValue(-0.001);
        assertEquals(0.5, values.get(3).getValue(), 0.0);
        assertEquals(0.75, values.get(4).getValue(), 0.0);
        assertEquals(0.5, parameters.getDouble("scaleFreeDeltaIn"), 0.0);
        assertEquals(0.75, parameters.getDouble("scaleFreeDeltaOut"), 0.0);
    }

    /**
     * Alpha and beta must each be above 0 and below 1, which is what the graph maker requires. A value outside that
     * is not taken, and the field and the parameters keep what they had.
     */
    @Test
    public void testAlphaAndBetaAreKeptBetweenZeroAndOne() {
        Parameters parameters = new Parameters();
        List<DoubleTextField> values = fields(new RandomDagScaleFreeEditor(parameters), DoubleTextField.class,
                new ArrayList<>());

        values.get(1).setValue(0.5);
        values.get(0).setValue(0.3);

        for (double refused : new double[]{0.0, -0.2, 1.0, 1.5, Double.NaN}) {
            values.get(0).setValue(refused);
            values.get(1).setValue(refused);
            assertEquals(0.3, values.get(0).getValue(), 0.0);
            assertEquals(0.5, values.get(1).getValue(), 0.0);
            assertEquals(0.3, parameters.getDouble("scaleFreeAlpha"), 0.0);
            assertEquals(0.5, parameters.getDouble("scaleFreeBeta"), 0.0);
        }
    }

    /**
     * Alpha and beta together must leave something for gamma. That is not refused field by field, since the way
     * from one good pair to another can pass through a pair that is not; the line under Gamma says what is wrong
     * while it is, and goes back to the plain note when it is put right.
     */
    @Test
    public void testTheNoteWarnsWhileAlphaAndBetaLeaveNothingForGamma() {
        Parameters parameters = new Parameters();
        RandomDagScaleFreeEditor tab = new RandomDagScaleFreeEditor(parameters);
        List<DoubleTextField> values = fields(tab, DoubleTextField.class, new ArrayList<>());

        assertEquals(List.of("Note: Gamma = 1 - Alpha - Beta; Alpha, Beta, Gamma > 0"), notes(tab));

        // From the defaults of 0.05 and 0.9, raising alpha first passes through a pair that sums to more than 1.
        values.get(0).setValue(0.3);
        assertEquals(0.3, parameters.getDouble("scaleFreeAlpha"), 0.0);
        assertEquals(List.of("Alpha + Beta must be less than 1, so that Gamma > 0"), notes(tab));
        assertEquals(Color.RED, noteLabels(tab).getFirst().getForeground());

        values.get(1).setValue(0.5);
        assertEquals(List.of("Note: Gamma = 1 - Alpha - Beta; Alpha, Beta, Gamma > 0"), notes(tab));
        assertEquals(0.2, values.get(2).getValue(), 1e-12);
    }

    private static List<JLabel> noteLabels(Container tab) {
        List<JLabel> labels = new ArrayList<>();

        for (JLabel label : fields(tab, JLabel.class, new ArrayList<>())) {
            if (label.getText() != null && label.getText().contains("Gamma") && label.getText().length() > 10) {
                labels.add(label);
            }
        }

        return labels;
    }

    private static List<String> notes(Container tab) {
        List<String> texts = new ArrayList<>();
        for (JLabel label : noteLabels(tab)) texts.add(label.getText());
        return texts;
    }
}
