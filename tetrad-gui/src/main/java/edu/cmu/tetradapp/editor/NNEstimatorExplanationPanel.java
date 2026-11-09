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
            <html><body style="font-family: sans-serif; font-size: 14pt; margin: 14px; line-height: 1.35;">

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
            implied by the DAG, not to win a prediction contest. The layer size, epochs, learning rate, and
            weight decay can be changed in the node's parameter dialog.</p>

            <p>Fitting is seeded. By default the seed is fixed, so pressing <b>Resimulate</b> again gives the
            same result, and two NN Estimator boxes on the same data and DAG give the same numbers, which is
            what you want when comparing DAGs. To see how much a number moves under a different fit, change
            the seed in the parameter dialog, or tick <b>Randomize the seed</b> there and resimulate a few
            times. If a number matters to you, do that at least once.</p>

            <h2>The status line at the bottom</h2>

            <p>After a fit, the footer reports the sample size of the resimulation, the whole-table MMD squared
            between observed and resimulated data on standardized variables, and the fraction of resimulated
            rows in which some mechanism was asked to extrapolate: a simulated parent value more than four
            training standard deviations from its training mean. When that fraction is more than a few
            percent, expect tails on the right side of the plot tab that the data never had; the network has
            no idea what to do out there. Until you run cross-validation the footer also shows how many nodes
            beat their marginal baseline on the training rows, which is optimistic; once cross-validation has
            run, the held-out summary takes its place. The MMD squared values in the footer and in the
            Cross-Validation tab are both computed on standardized data, so they can be compared.</p>

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
            are predicted from their parents. The k fold models are kept and shared with the Edge Strength
            tab, so the partial strengths there are measured on exactly these folds.</p>

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

            <p>One detail worth knowing: by default the folds are contiguous blocks of rows in file order, not
            random draws. If your rows are in time order this is a blocked cross-validation, which is what you
            want for serially dependent data. If your rows are sorted by some variable, each held-out block is
            a biased slice and the numbers will be pessimistic. In that case turn on <b>Shuffle rows before
            cutting folds</b> in the parameter dialog; the permutation uses the seed, so it is reproducible.</p>

            <h2>Tab: Edge Strength</h2>

            <p>This tab asks, for one edge X to Y at a time, how much the edge contributes. Two different
            questions are answered, and it matters which one you are reading.</p>

            <p><b>Intervention measures</b> (MMD squared, delta variance, KL) answer: how much does Y's fitted
            mechanism actually use X? Nothing is retrained for the measure itself. For each of a number of observed parent
            configurations, set by the <b>Parent configs</b> spinner, the tool draws Y many times from the
            mechanism with the configuration as observed, and many times again with X's input replaced by an
            independent draw from X's own distribution, everything else held fixed. The two sets of draws are
            compared and the comparison averaged over configurations. This is the arrow strength of Janzing and
            colleagues, as DoWhy implements it. <b>MMD squared</b> is a nonparametric distance between the two
            sets of draws, computed on Y standardized by its observed spread so values are comparable across
            children. For a continuous Y, <b>delta variance over variance of Y</b> is the extra spread that
            randomizing X induces in Y given its other parents, as a fraction of Y's total variance; for a
            linear mechanism it is the coefficient squared times the variance of X, divided by the variance of
            Y. For a discrete Y, <b>KL divergence</b> in bits measures how far the class probabilities move when
            X is randomized.</p>

            <p>Two more columns keep these numbers honest. <b>Plus or minus SD</b> is the spread of MMD squared
            across independent repeats of the whole computation, each with fresh configurations and draws;
            the number of repeats is a parameter. MMD squared is the noisier of the measures, typically a
            tenth or so of its value; the variance-based measure is far more stable, so use it for ranking
            when the two disagree. <b>Null MMD squared</b> is the refit-noise band: Y's mechanism is retrained
            with the <i>same</i> parents under a few new seeds, and the same conditional MMD squared is measured
            between the original and each refit. That is how much the fitted conditional moves from training
            randomness alone. An edge whose MMD squared does not clear the null mean plus two null standard
            deviations is shown in gray italics, and the status line counts how many edges clear it. The
            band is per child and shared by all edges into that child. It is a rough threshold, not a test;
            with only a few refits the standard deviation is itself uncertain.</p>

            <p><b>The partial measure</b> answers a different question: does X add anything to held-out
            prediction of Y once Y's other parents are known? It uses the same k folds as the
            Cross-Validation tab, set by the <b>CV k</b> spinner. On each fold, the fold's full model predicts
            the held-out rows, and so does a reduced model in which only Y's mechanism has been retrained,
            on that fold's training rows, without X. For a continuous Y the number reported is the
            difference in held-out R squared, full minus reduced, with R squared defined exactly as in the
            Cross-Validation table. For independent parents these differences add up to Y's R squared in that
            table. For a discrete Y it is the difference in held-out cross-entropy, in nats. Positive values
            are shown green and bold.</p>

            <p>The fold models are built once, the first time either this tab or the Cross-Validation tab
            needs them at a given k, and reused after that. Compute All on a large graph is therefore mostly
            the cost of one small retrain of the child per fold per edge.</p>

            <p>Why both? They disagree in exactly one common situation, and the disagreement is the point. If
            another parent W carries nearly the same information as X, the partial measure is near zero for
            both, because each is redundant given the other. The intervention measures stay large for whichever
            of the two the network actually leans on, because the mechanism is not refit and still uses that
            input. So a row with a large intervention strength and a near-zero partial means "this edge does
            real work in the fitted model, but you could drop it and another parent would take over." A row
            with both near zero is an edge the model neither uses nor needs. A row with both large is an
            edge that is doing work no other parent can do.</p>

            <p><b>Compute Parent Strengths</b> handles the selected child; <b>Compute All</b> handles every edge
            in the DAG, clearing previous results first. Results appear as each edge finishes, are kept across
            child selections, and are saved with the session. The table can be sorted by any column.</p>

            <h2>Reading MMD squared</h2>

            <p>MMD squared is a distance between two samples: zero means a kernel test could not tell them apart,
            and larger means more different. It is estimated here with 512 random Fourier features at a fixed
            bandwidth, so it is fast and slightly noisy. It has no natural units and no fixed threshold. Use it
            comparatively: this edge versus that edge on the same tab, this DAG versus that DAG with the same
            data and the same settings. On the Edge Strength tab it is computed on a standardized child, so
            edges into different children can be compared. Do not compare values across tabs or across
            datasets.</p>

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
              <li><b>Edge strength is not effect size.</b> It measures how much Y's fitted mechanism uses X, or
              how much X adds to prediction, within this model. A strong edge can still be confounded if the
              DAG is wrong. The intervention measures are also only as good as the fit: a network that
              over- or under-shoots a slope will over- or under-state that edge by the square of the error.</li>
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
              <li>Run Compute All on the Edge Strength tab. Sort by the partial column for edges you could
              drop; sort by MMD squared for edges the model leans on. Edges near zero on both are the
              candidates for removal; check whether removing them hurts cross-validation.</li>
              <li>Change the seed in the parameter dialog, resimulate, and confirm that whatever you concluded
              survives a different fit. The plus-or-minus and null columns tell you what to expect from
              Monte Carlo and training noise; a different seed tells you what the fit itself does.</li>
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
