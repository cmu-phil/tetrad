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

import javax.swing.*;
import java.awt.*;

/**
 * The Explanation tab of the Interventional Histogram: what the tool estimates, how the resampling
 * scheme works, exactly when it is a valid identification of P(Y | do(X)), and when it is not. Static
 * text, not recomputed for the selected data.
 *
 * <p>The tool is a prototype, and the point of putting the validity boundary in the interface rather
 * than only in the manual is that "do" in a label invites a causal reading whether or not the
 * conditions for one hold.</p>
 *
 * @author josephramsey
 * @see InterventionalHistogramEditor
 * @see edu.cmu.tetradapp.model.InterventionalHistogramModel
 */
final class InterventionalHistogramExplanationPanel {

    /**
     * The explanation, in the HTML 3.2 subset Swing renders. Kept to headings, paragraphs and lists so
     * that it lays out the same on every platform.
     */
    private static final String HTML = """
            <html><body style="font-family: sans-serif; font-size: 14pt; margin: 14px; line-height: 1.35;">

            <h2>What this tool shows</h2>

            <p>This tool takes a dataset and a graph over the same variables, and displays a histogram of an
            outcome variable Y under an intervention do(X<sub>1</sub> = x<sub>1</sub>, ...,
            X<sub>m</sub> = x<sub>m</sub>). Each intervened variable must be <b>discrete in the data</b>; its
            value is chosen from that variable's category names. Y itself may be discrete or continuous.
            Mixed datasets are fine, with one caveat about continuous parents described below.</p>

            <p>The histogram is built from a pseudo-sample of Y drawn by resampling rows of the actual data.
            No model is fitted; everything is empirical. The bottom strip estimates
            P(lo &le; Y &le; hi) as the fraction of the drawn sample falling in that range. For a discrete Y,
            lo and hi refer to category indices, so for example lo = hi = 2 gives the probability of the
            third category.</p>

            <h2>How the sample is drawn</h2>

            <p>Write X for the intervened variables and Z for the union of their parents in the graph
            (excluding Y and the X's themselves). The sampler approximates the back-door adjustment</p>

            <p style="margin-left: 24px;">P(Y | do(X = x)) &nbsp;=&nbsp; &Sigma;<sub>z</sub>
            P(Y | X = x, Z = z) &middot; P(Z = z)</p>

            <p>by, for each draw: (a) picking a random data row to supply values z for Z, which samples Z
            from its marginal distribution, then (b) finding a row that matches both X = x and Z = z
            exactly, and reading off its Y value, which samples Y from its conditional distribution given
            X and Z. If no intervention is specified, Y is simply bootstrapped from all rows, giving the
            observational marginal.</p>

            <h2>When this is valid, and when it is not</h2>

            <p>For a <b>single</b> intervened X, adjustment on the parents of X is the classical back-door
            identity, so the display is a consistent estimate of P(Y | do(X = x)) provided all of the
            following hold:</p>

            <ul>
              <li>The graph is a <b>DAG</b> and is causally correct. For a CPDAG, MAG, or PAG, "parents" are
                  not causally well defined, and the adjustment set used here has no guarantee.</li>
              <li><b>Causal sufficiency</b>: every parent of X is measured. Unmeasured confounding of X and Y
                  is invisible to this tool and biases the result toward the observational conditional.</li>
              <li>All parents of X are <b>discrete</b>. Continuous parents cannot be matched exactly and are
                  <b>dropped from the adjustment set</b>; the status line reports any that were dropped.
                  Dropping a genuine confounder moves the answer toward the observational
                  P(Y | X = x).</li>
              <li>Y is not itself a parent of X. In that case the correct interventional answer is just the
                  marginal of Y, but this sampler still conditions on X, which is wrong for that case.</li>
            </ul>

            <p>For <b>multiple simultaneous</b> interventions, pooling the parents of all intervened
            variables into one adjustment set is a heuristic, not an identification result; the correct
            expression is the truncated factorization, which this sampler does not compute. Treat
            multi-variable do() displays as exploratory.</p>

            <h2>The fallback, and why the status line matters</h2>

            <p>Step (b) above is rejection sampling: the sampler hunts for a row matching X and Z jointly.
            In sparse strata no such row may be found within the attempt limit, and the draw <b>falls back
            to matching X only</b> -- an observational draw for that step. The status line reports the
            fraction of draws that fell back. A large fallback fraction means the display is closer to the
            observational conditional P(Y | X = x) than to the interventional distribution, exactly in the
            strata where confounding adjustment was needed most. Small cell counts, many intervened
            variables, or high-cardinality parents all push this fraction up.</p>

            <h2>Reading the histogram</h2>

            <p>A discrete Y is shown with one labeled bar per category. A continuous Y uses the number of
            bins set above; the bins control is disabled for a discrete Y. "Remove zeros" only affects the
            display, not the sample or the probability strip. Rerunning with the same inputs reuses a fixed
            random seed, so the display is reproducible; it is not an independent replication.</p>

            <p>This tool is a <b>prototype</b>. It does not yet use a general adjustment-set search, weighted
            (IPW) resampling, or model-based inference, any of which would relax the exact-matching and
            discreteness restrictions. Its numbers are Monte Carlo estimates from resampled real rows and
            inherit both the sampling noise of the draw and the finite-cell noise of the data.</p>

            </body></html>
            """;

    private InterventionalHistogramExplanationPanel() {
    }

    /**
     * Builds the Explanation tab.
     *
     * @return A scrollable, read-only rendering of the explanation.
     */
    static JComponent create() {
        JEditorPane pane = new JEditorPane("text/html", HTML);
        pane.setEditable(false);
        pane.setCaretPosition(0);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setPreferredSize(new Dimension(780, 400));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }
}
