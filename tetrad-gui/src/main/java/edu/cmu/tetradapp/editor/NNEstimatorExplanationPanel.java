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
 * The Explanation tab of the NN Estimator: a plain-language account of what the estimator fits, what each of
 * the other three tabs computes, how to read the numbers, and where the tool stops being informative. It is
 * static text and is not recomputed for the selected data.
 *
 * <p>The text lives in the interface rather than only in the manual because the estimator's numbers (MMD
 * squared, out-of-sample R squared, edge strengths) are easy to over-read. A number with no account of where it
 * came from invites exactly the confident wrong reading the tool is meant to prevent.</p>
 *
 * @author josephramsey
 * @see NNEstimatorComparePanel
 * @see edu.cmu.tetrad.sem.NNEstimator
 */
final class NNEstimatorExplanationPanel {

    /**
     * The explanation, in the HTML 3.2 subset Swing renders. Kept to headings, paragraphs, lists and a table so
     * that it lays out the same on every platform.
     */
    private static final String HTML = """
            <html><body style="font-family: sans-serif; font-size: 11pt; margin: 12px;">

            <h2>What this tool is</h2>

            <p>The NN Estimator takes two inputs: a dataset and a DAG over the same variables. For each variable
            in the DAG it trains a small neural network to model that variable given its parents. Root variables,
            which have no parents, are not modeled at all; they are resampled from their observed values. Once
            every variable has a mechanism, the tool can run the DAG forward in causal order and generate as
            many synthetic rows as you like. Those rows are the <b>resimulated</b> data.</p>

            <p>Nothing here assumes a parametric family. There is no linearity assumption, no Gaussian
            assumption, and no conditional-probability table. Continuous variables get a general-noise
            mechanism: the network takes the parents together with a noise value as inputs, and the noise is
            drawn by bootstrap from the residuals seen in training, so it can enter non-additively. Discrete
            variables get a softmax classifier over their categories. Continuous parents enter as standardized
            values; discrete parents enter as one-hot indicators. Mixed continuous and discrete data is fine.</p>

            <p>The networks are deliberately small: one hidden layer of 48 units, trained for 200 passes over the
            data with mild weight decay. They are meant to be faithful enough to reproduce the joint distribution
            implied by the DAG, not to win a prediction contest. Each time you press <b>Resimulate</b> the
            estimator is refit from a fresh random seed, so two resimulations of the same data and DAG will not
            be identical. If a number matters to you, run it more than once and see whether it moves.</p>

            <h2>The status line at the bottom</h2>

            <p>After a fit, the footer reports the sample size of the resimulation, the whole-table MMD squared
            between observed and resimulated data, the mean per-node improvement over a baseline, and the
            fraction of nodes that improved at all. Two cautions. First, the per-node improvement is measured on
            the training rows, not on held-out rows, so it is optimistic; the Cross-Validation tab gives the
            honest version. Second, this MMD squared is computed on standardized data, while the MMD squared
            values on the other two tabs are computed on the raw scale, so do not compare a footer value with a
            tab value.</p>

            <h2>Tab: Observed vs. Resimulated</h2>

            <p>This is the tab to look at first. Left is your data; right is a resimulation from the fitted
            model. Pick one or more variables for rows and columns to get a matrix of histograms and scatter
            plots, drawn identically on both sides. If the model has captured the joint distribution, the two
            sides should look alike: same marginal shapes, same pairwise scatter shapes, same discrete cell
            frequencies. The <b>Settings</b> menu lets you add trend lines, change bin counts, jitter discrete
            points for display, and condition on ranges of other variables.</p>

            <p>What to look for: a pair that is clearly curved on the left and straight on the right means the
            mechanism under-fit that relationship. A pair that shows structure on the left and a cloud on the
            right means the DAG does not connect those variables closely enough for the model to reproduce their
            dependence. A right-hand marginal that is wider than the left, or that reaches values the data never
            takes, usually means the simulation is extrapolating: a simulated parent value fell outside the
            range the child's network was trained on.</p>

            <p>The <b>Sample size</b> spinner sets how many rows the right side has. Larger is smoother but
            slower to draw. Pressing <b>Resimulate</b> refits everything and replaces the right side.</p>

            <h2>Tab: Cross-Validation</h2>

            <p>This tab answers a different question: not "does the model reproduce the data it was trained
            on," but "does each variable's mechanism predict rows it has never seen." The rows are split into
            k blocks, each block is held out in turn, a fresh model is trained on the rest, and the held-out rows
            are predicted from their parents.</p>

            <p>For a <b>continuous</b> variable the table reports out-of-sample R squared: one minus the
            held-out mean squared error divided by the variable's marginal variance. Zero means knowing the
            parents helped no more than predicting the mean; negative means it helped less; values approaching
            one mean the parents nearly determine the variable. For a <b>discrete</b> variable it reports the
            improvement in held-out cross-entropy, in nats, over predicting the marginal class frequencies. Again
            zero means the parents added nothing. Root variables are omitted because there is nothing to
            condition on.</p>

            <p>The whole-graph line above the table is an average over folds of MMD squared between each
            held-out block and a resimulation of the same size from that fold's model. It measures how well the
            entire factorization generalizes, not just each node.</p>

            <p>How to read a bad row. A node with negative out-of-sample R squared has a parent set that hurts
            more than it helps. That can mean the parents are wrong for that node, that there are too few rows
            to learn the mechanism, or that the true relationship is one this small network cannot represent.
            It does not by itself say which.</p>

            <p>One detail worth knowing: the folds are contiguous blocks of rows in file order, not random
            draws. If your rows are in time order this is a blocked cross-validation, which is what you want
            for serially dependent data. If your rows are sorted by some variable, each held-out block is a
            biased slice and the numbers will be pessimistic. Shuffle the rows first in that case.</p>

            <h2>Tab: Edge Strength</h2>

            <p>This tab asks, for one edge X to Y at a time, how much the edge contributes. The method is
            the same for every measure: keep every mechanism in the graph fixed except Y's, retrain Y's
            mechanism with X removed from its parents, and compare. Only the one structural equation changes,
            so nothing cascades through the rest of the graph.</p>

            <p>Two kinds of measure are reported for each edge.</p>

            <p><b>Marginal measures</b> compare the distribution of Y under the full model against the
            distribution of Y under the reduced model, each estimated by simulating the number of rows in the
            <b>Simulated n</b> spinner. There are three of them. <b>MMD squared</b> is a nonparametric distance
            between the two distributions of Y; it picks up any change in shape. For a continuous Y, <b>delta
            variance</b> is the variance of Y with the edge removed minus its variance with the edge present;
            a large positive value means the parent was absorbing a lot of Y's variation. For a discrete Y,
            <b>KL divergence</b> in bits measures how far the class frequencies move when the edge is cut.
            These are analogous to what DoWhy reports under the name arrow strength.</p>

            <p><b>The partial measure</b> controls for Y's other parents first. For continuous Y: predict Y from
            its other parents with the reduced mechanism, take the residual, and ask how much of that residual
            X can explain, measured as out-of-sample R squared from a small cross-validated regression of the
            residual on X using the <b>CV k</b> spinner. This is the nonparametric cousin of partial R squared.
            For discrete Y it is the cross-validated improvement in cross-entropy of the full model over the
            reduced model. Positive values are shown green and bold: X carries information about Y beyond what
            the other parents carry.</p>

            <p>Why both? The marginal measures can be large for an edge whose parent is nearly a copy of another
            parent, because removing either one changes Y's distribution. The partial measure will be near zero
            for both such edges, because each is redundant given the other. When the two kinds of measure
            disagree, that is the usual reason.</p>

            <p><b>Compute Parent Strengths</b> handles the selected child; <b>Compute All</b> handles every edge
            in the DAG, clearing previous results first. Results appear as each edge finishes, are kept across
            child selections, and are saved with the session. The table can be sorted by any column.</p>

            <h2>Reading MMD squared</h2>

            <p>MMD squared is a distance between two samples: zero means a kernel test could not tell them apart,
            and larger means more different. It is estimated here with 512 random Fourier features at a fixed
            bandwidth, so it is fast and slightly noisy. It has no natural units and no fixed threshold. Use it
            comparatively: this edge versus that edge on the same tab, this DAG versus that DAG with the same
            data and the same simulated sample size. Do not compare values across tabs, across datasets, or
            across sample sizes.</p>

            <h2>What this tool does not tell you</h2>

            <ul>
              <li><b>It does not test causal direction.</b> A DAG with an edge reversed will often resimulate
              the data just as well, because a flexible mechanism can represent the conditional in either
              direction. A good fit here means the DAG is <i>adequate</i> as a factorization of the joint
              distribution. It does not mean the arrows are right. For that, use the Markov Check and your
              own knowledge of the domain.</li>
              <li><b>A dense DAG will always fit at least as well as a sparse one</b> on the training data,
              because adding parents can only add inputs to a network. Prefer the Cross-Validation numbers,
              and among DAGs that generalize equally well, prefer the one with fewer edges.</li>
              <li><b>Edge strength is not effect size.</b> It measures how much Y's distribution or
              predictability depends on X within this fitted model. A strong edge can still be confounded if the
              DAG is wrong.</li>
              <li><b>Small networks under-fit sharp nonlinearities</b> such as thresholds or high-frequency
              oscillation. If a scatter plot on the left has structure the right cannot match, that is the
              model's limit, not evidence about the DAG.</li>
              <li><b>The simulation can extrapolate.</b> Simulated parents can land outside the training range,
              and the child's network has no idea what to do there. Watch the resimulated marginals for tails
              the data never had.</li>
            </ul>

            <h2>A suggested workflow</h2>

            <ol>
              <li>Look at Observed vs. Resimulated for the variables you care most about. If the right side
              is obviously wrong, nothing downstream is worth reading.</li>
              <li>Run Cross-Validation. Note any node with out-of-sample R squared near zero or negative.</li>
              <li>Run Compute All on the Edge Strength tab. Sort by the partial column. Edges with partial
              values near zero are candidates for removal; check whether removing them hurts cross-validation.</li>
              <li>Resimulate once or twice more and confirm that whatever you concluded survives a change of
              seed.</li>
            </ol>

            </body></html>
            """;

    private NNEstimatorExplanationPanel() {
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
