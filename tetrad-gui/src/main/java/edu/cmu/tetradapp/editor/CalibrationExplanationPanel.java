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
 * The Explanation tab of {@link CalibrationCalculatorAction}: a plain-language account of what the Alpha and
 * Penalty Discount tabs compute, how the two relate, and where each stops being trustworthy. It is static text;
 * the numbers it quotes as examples are illustrative and are not recomputed for the selected data.
 *
 * <p>The text is deliberately in the interface rather than only in the manual, because the point of the
 * calculator is to make the user look at the choice. A number with no account of where it came from would defeat
 * that.</p>
 *
 * @author josephramsey
 * @see CalibrationCalculatorAction
 * @see AlphaCalculatorPanel
 * @see PenaltyDiscountCalculatorPanel
 */
final class CalibrationExplanationPanel {

    /**
     * The explanation, in the HTML 3.2 subset Swing renders. Kept to headings, paragraphs, lists and a table so
     * that it lays out the same on every platform.
     */
    private static final String HTML = """
            <html><body style="font-family: sans-serif; font-size: 11pt; margin: 12px;">

            <h2>What these calculators do</h2>

            <p>Both tabs answer the same question from the two sides of the same ledger: <i>how strict should the
            search be, so that it produces about as many spurious edges as I am willing to accept?</i> The Alpha
            tab answers it for constraint-based searches (PC, FCI and their relatives), whose strictness is a
            significance level. The Penalty Discount tab answers it for score-based searches (FGES, BOSS, GRaSP and
            their relatives), whose strictness is the multiplier on the BIC penalty. Neither value is applied
            anywhere automatically. You read it, decide whether it makes sense for your problem, and copy it into
            the search's <b>alpha</b> or <b>penaltyDiscount</b> parameter yourself.</p>

            <h2>The common idea: a budget on spurious edges</h2>

            <p>You supply two things you have to guess at. An <b>expected degree</b>: roughly how many neighbors
            an average variable has in the true graph. And a <b>target FDR</b>: what fraction of spurious edges,
            relative to true ones, you can live with. From p and the degree the calculator knows about how many
            true edges there are (p &times; degree / 2), and, more importantly, how many pairs of variables are
            <i>not</i> truly connected. Every one of those null pairs is a chance for a spurious edge. If each one
            slips through with some probability, the expected number of spurious edges is (number of null pairs)
            &times; (that probability). Set that equal to the budget, FDR &times; (expected true edges), and solve
            for the setting that delivers it.</p>

            <p>The guesses matter less than they look. The expected degree enters only logarithmically into the
            penalty discount and only linearly into alpha, so being wrong by a factor of two moves the answer
            modestly. What matters more is looking at the number at all.</p>

            <h2>Alpha: a division</h2>

            <p>In PC and its relatives an adjacency between two variables survives only if <i>every</i>
            conditioning set the search tries fails to separate them. If the pair is truly non-adjacent, some set
            the search tries does separate them, and the test wrongly rejects that independence with probability
            alpha. So a null pair slips through with probability at most alpha, and</p>

            <p style="margin-left: 30px;"><b>alpha &asymp; budget / (number of null pairs) &asymp;
            FDR &times; degree / (p &minus; 1)</b></p>

            <p>Three things follow. This holds for <i>any</i> test that runs at its nominal level, not just Fisher
            Z. It does not depend on N at all; N enters only on the power side. And it is an upper bound, because a
            null pair is usually tested against several separating sets and needs only one of them to accept, so
            the realized count runs below the budget, which is the safe direction to be wrong in.</p>

            <h2>Penalty discount: an inversion</h2>

            <p>A score adds a parent when the likelihood gain exceeds the penalty, <i>c &times; df &times; ln N</i>,
            where df is the number of parameters the parent costs. Under the null the gain is approximately
            chi-square on df degrees of freedom, so a null pair slips through with probability
            P(&chi;&sup2;(df) &gt; c df ln N). That probability depends on c, on N and on df, and there is no
            closed form for the c that brings the sum over pairs down to the budget, so the calculator finds it by
            bisection. At p = 100, N = 1000, degree 5 and FDR 0.01 it returns about 1.75 for SEM BIC; the
            conventional c = 2 is the same calculation with an absolute budget of one spurious edge.</p>

            <h2>Why the degrees of freedom matter, and why this belongs on the data</h2>

            <p>Both calculations cost a pair of variables by the product of the two variables' <b>parameter block
            sizes</b>: one for SEM BIC and Fisher Z, categories minus one for a discrete variable under the
            conditional Gaussian, Degenerate Gaussian and discrete scores and tests, and the basis-expansion size
            for a continuous variable under the Basis Function score and test. That is why the calculators live in
            the Data Editor rather than being a formula in p and N: with the data in hand the block sizes, and
            hence the degrees-of-freedom histogram over pairs, are known exactly, including the mixed-type
            classes.</p>

            <p>The df changes the answer a great deal, and in a direction people often get backwards. A
            chi-square with more degrees of freedom is <i>relatively</i> narrower (its spread is
            &radic;(2/df) of its mean), and the penalty threshold sits at a fixed multiple, c ln N, of the mean.
            So at fixed c the tail collapses as df grows: at c = 2 and N = 1000 a df = 1 pair is accepted under
            the null with probability about 2 &times; 10<sup>&minus;4</sup>, a df = 9 pair (Basis Function, two
            continuous variables at truncation 3) with probability about 2 &times; 10<sup>&minus;22</sup>. The
            calibrated c for a high-df score is therefore far <i>smaller</i> than for SEM BIC on the same data,
            and on mixed data a single c tests the continuous-continuous pairs at a level many orders of magnitude
            stricter than the binary-binary pairs. The calibration gets the total count right; the per-df table
            in the Result tab is where you see that it does not equalize the classes.</p>

            <p>The same fact shows up on the test side as a power problem. A linear effect of partial correlation
            r delivers its evidence, &minus;(N &minus; |S| &minus; 3) ln(1 &minus; r&sup2;), into <i>one</i>
            component of the block, while the null is charged for all df of them. So at the same alpha, N and r,
            a higher-df pair has less power and needs a larger effect to be seen. This is the Basis Function
            score's conservatism on weak linear edges, viewed from the test side.</p>

            <h2>The second criterion: the smallest effect worth an edge</h2>

            <p>The false-edge budget controls how many <i>null</i> pairs become edges. It says nothing about how
            small a real dependence you want to bother with, and at large N it will admit any real dependence
            however tiny: at N = 20,000 and c = 1 a score accepts partial correlations of about 0.02, and on real
            data that is nearly everything. The optional <b>minimum partial correlation</b> sets an effect floor
            instead.</p>

            <p>For a penalty discount the two criteria push the same way. A larger c both suppresses false
            positives and ignores small effects, so the calculator takes the <b>larger</b> of the two values: the
            FDR criterion binds at small N, the effect criterion at large N. Be aware that the effect criterion
            grows like N / ln N and can become very large at big N; that is correct, but it is a sharp rule.</p>

            <p>For alpha the two criteria <b>conflict</b>. A smaller alpha buys fewer spurious edges <i>and</i>
            less power at the same time, so no single value is conservative on both counts. The Alpha tab reports
            both levels and says whether the budget's level is already large enough to detect the effect you asked
            for at the power you asked for, in every df class. When it is not, the shortfall is in the sample size,
            not in alpha: raise N, relax the budget, or accept a larger effect floor.</p>

            <h2>The bridge between the two tabs</h2>

            <p>A penalty discount c at sample size N is the same decision rule, per pair, as a chi-square test at
            level alpha = P(&chi;&sup2;(1) &gt; c ln N), and the calculators report each other's equivalent
            setting. This is how a score-based and a constraint-based search on the same data can be put at
            comparable strictness, and it is exact in both directions.</p>

            <h2>Multiplicity: the cost of depth</h2>

            <p>The reason alpha is an upper bound (a null pair needs only one separating set to accept) is also
            the reason constraint-based searches lose true edges: a <i>true</i> edge is removed if <i>any</i> of
            the conditioning sets tried fails to reject. The number of sets tried per pair grows quickly with the
            depth and the degree (about 26 at degree 5 and depth 3; 256 at degree 8 and unlimited depth), and it
            multiplies the per-test miss rate. Multiplicity helps precision and hurts recall in the same measure.
            The false-edge criterion gives no warning about this, because it is only about null pairs; the
            multiplicity note in the Alpha tab is there to make the other side visible.</p>

            <h2>Where the numbers stop being trustworthy</h2>

            <ul>
            <li>Everything here is a first-moment, asymptotic calculation under a Gaussian likelihood. Treat the
            output as the right order of magnitude and a defensible starting point, not as a guarantee. The
            honest check is to measure the null rejection rate on permuted data.</li>
            <li>The power figures on the Alpha tab are for tests whose null is chi-square on the degrees of
            freedom shown: Fisher Z, CG-LRT, DG-LRT and BF-LRT. They do not apply to the kernel and
            random-feature tests (KCI, GCM, RFF). The level itself still does.</li>
            <li>For the Basis Function score on the default min-max embedding, the null is <i>not</i> chi-square;
            its tail is heavier, and the exact calibration sets c too low, in past measurements by enough to
            produce about a hundred times the budgeted false edges. Either use the rank-transformed embedding,
            whose null is chi-square, or check <b>Permutation-fitted nulls</b> so the calculator estimates the
            null from your data. The fitted null still under-covers the far tail somewhat.</li>
            <li>The "smallest r seen" figures assume a linear effect carried by one block component. A genuinely
            nonlinear dependence spreads its evidence across components and is easier to detect than they say;
            they are the floor for the linear case, which is the case that gets missed.</li>
            <li>The true-edge loss bound in the multiplicity note treats the tests as independent. They share
            data and are strongly dependent, so it overstates the loss; the expected number of failing tests is
            the more informative figure.</li>
            <li>The sample size the score or test actually uses may not be the row count: complete-case
            deletion, testwise deletion and effective-sample-size corrections all change it. The N field is
            editable for that reason.</li>
            </ul>

            <h2>Reading a result</h2>

            <p>Look at the recommended value, then look at the per-df table and the sweep. The table tells you
            which variable-type pairs are being tested strictly and which loosely at that single setting. The
            sweep tells you what the conventional settings you might have used instead (alpha 0.01, c = 2) would
            have bought on this data, and where the false-edge and effect-size criteria cross. If the recommended
            value is far from what you expected, that is information about your data or your assumptions, and it
            is worth understanding before running the search.</p>

            </body></html>
            """;

    private CalibrationExplanationPanel() {
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
