///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.ParamType;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemLavaanParser;
import edu.cmu.tetrad.sem.SemLavaanRenderer;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.SemXmlParser;
import edu.cmu.tetrad.sem.SemXmlRenderer;
import edu.cmu.tetrad.util.NumberFormatUtil;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests that a SEM IM saved as XML or as lavaan syntax loads back as the same model.
 *
 * @author Claude
 */
public final class TestSemImSaveLoad {

    /**
     * A model with a latent L, correlated errors for X and W, nonzero means, and, if cyclic, the cycle X, Y, Z.
     */
    private static SemIm sampleSemIm(boolean cyclic) {
        Graph graph = new EdgeListGraph();
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node z = new GraphNode("Z");
        Node l = new GraphNode("L");
        Node w = new GraphNode("W");
        l.setNodeType(NodeType.LATENT);

        int i = 0;

        for (Node node : List.of(x, y, z, l, w)) {
            node.setCenter(40 + 70 * i, 50 + 30 * i++);
            graph.addNode(node);
        }

        graph.addDirectedEdge(x, y);
        graph.addDirectedEdge(y, z);
        graph.addDirectedEdge(l, z);
        graph.addDirectedEdge(l, w);
        graph.addBidirectedEdge(x, w);

        if (cyclic) {
            graph.addDirectedEdge(z, x);
        }

        SemIm semIm = new SemIm(new SemPm(graph));
        semIm.setMean(semIm.getVariableNode("X"), 1.5);
        semIm.setMean(semIm.getVariableNode("Y"), -2.0);
        semIm.setMean(semIm.getVariableNode("Z"), 3.0);
        semIm.setMean(semIm.getVariableNode("W"), 0.7);
        return semIm;
    }

    /**
     * Asserts that the two models have the same graph and the same parameter values, to within the tolerance.
     */
    private static void assertSameModel(SemIm expected, SemIm actual, boolean means, double tolerance) {
        assertEquals(expected.getVariableNodes().size(), actual.getVariableNodes().size());
        assertEquals(expected.getSemPm().getParameters().size(), actual.getSemPm().getParameters().size());

        for (Parameter parameter : expected.getSemPm().getParameters()) {
            if (parameter.getType() == ParamType.MEAN) {
                continue;
            }

            Node a = actual.getVariableNode(parameter.getNodeA().getName());
            Node b = actual.getVariableNode(parameter.getNodeB().getName());
            assertNotNull(a);
            assertNotNull(b);

            Parameter _parameter = parameter.getType() == ParamType.COEF
                    ? actual.getSemPm().getCoefficientParameter(a, b)
                    : parameter.getType() == ParamType.VAR ? actual.getSemPm().getVarianceParameter(a)
                    : covarianceParameter(actual, a, b);
            assertNotNull(parameter.toString(), _parameter);
            assertEquals(parameter.getType(), _parameter.getType());
            assertEquals(parameter.toString(), expected.getParamValue(parameter), actual.getParamValue(_parameter),
                    tolerance);
        }

        if (means) {
            for (Node node : expected.getVariableNodes()) {
                assertEquals(expected.getIntercept(node), actual.getIntercept(actual.getVariableNode(node.getName())),
                        tolerance);
            }
        }
    }

    /**
     * Returns the error covariance parameter for the two nodes, in whichever order the model has them.
     */
    private static Parameter covarianceParameter(SemIm semIm, Node a, Node b) {
        for (Parameter parameter : semIm.getSemPm().getParameters()) {
            if (parameter.getType() == ParamType.COVAR
                && ((parameter.getNodeA() == a && parameter.getNodeB() == b)
                    || (parameter.getNodeA() == b && parameter.getNodeB() == a))) {
                return parameter;
            }
        }

        return null;
    }

    @Test
    public void testXmlRoundTrip() {
        SemIm semIm = sampleSemIm(false);
        SemIm loaded = SemXmlParser.getSemIm(SemXmlRenderer.getElement(semIm));

        assertSameModel(semIm, loaded, true, 1e-12);

        for (Node node : semIm.getVariableNodes()) {
            Node _node = loaded.getVariableNode(node.getName());
            assertEquals(node.getNodeType(), _node.getNodeType());
            assertEquals(node.getCenterX(), _node.getCenterX());
            assertEquals(node.getCenterY(), _node.getCenterY());
            assertEquals(semIm.getMean(node), loaded.getMean(_node), 1e-12);
        }

        // Saving should not turn on the error terms of the model being saved.
        assertFalse(semIm.getSemPm().getGraph().isShowErrorTerms());
        assertFalse(loaded.getSemPm().getGraph().isShowErrorTerms());
    }

    @Test
    public void testXmlRoundTripCyclic() {
        SemIm semIm = sampleSemIm(true);
        SemIm loaded = SemXmlParser.getSemIm(SemXmlRenderer.getElement(semIm));

        assertTrue(loaded.isCyclic());

        // Intercepts are not defined for a cyclic model, so the means are not saved.
        assertSameModel(semIm, loaded, false, 1e-12);
    }

    @Test
    public void testXmlRoundTripFixedParameter() {
        SemPm semPm = new SemPm(sampleSemIm(false).getSemPm().getGraph());
        Parameter fixed = semPm.getCoefficientParameter(semPm.getGraph().getNode("X"), semPm.getGraph().getNode("Y"));
        fixed.setFixed(true);
        SemIm semIm = new SemIm(semPm);
        semIm.setFixedParamValue(fixed, 0.25);

        SemIm loaded = SemXmlParser.getSemIm(SemXmlRenderer.getElement(semIm));
        Parameter _fixed = loaded.getSemPm().getCoefficientParameter(loaded.getVariableNode("X"),
                loaded.getVariableNode("Y"));

        assertTrue(_fixed.isFixed());
        assertTrue(loaded.getFixedParameters().contains(_fixed));
        assertEquals(0.25, loaded.getParamValue(_fixed), 1e-12);
        assertEquals(semIm.getNumFreeParams(), loaded.getNumFreeParams());
    }

    @Test
    public void testLavaanRoundTrip() {
        SemIm semIm = sampleSemIm(false);

        // The lavaan syntax is written in the number format of the application, so it is only that precise.
        int digits = NumberFormatUtil.getInstance().getNumberFormat().getMaximumFractionDigits();
        double tolerance = Math.pow(10, -digits);

        for (boolean fixParameters : new boolean[]{false, true}) {
            String lavaan = SemLavaanRenderer.semImToLavaan(semIm, true, true, true, fixParameters);
            SemLavaanParser.Result result = SemLavaanParser.parse(lavaan);

            assertTrue(result.warnings().toString(), result.warnings().isEmpty());
            assertSameModel(semIm, result.semIm(), true, tolerance);
        }
    }

    @Test
    public void testLavaanHandWritten() {
        String lavaan = """
                # A model written by hand, with a latent, a label, and a formula split over two lines.
                f =~ 1*y1 + 0.8*y2 + start(1.2)*y3   # loadings
                y4 ~ b1*f + 0.3*x1 +
                     -0.25*x2 ; x1 ~~ 0.4*x2
                y4 ~ start(2.5e+0)*f
                f ~~ 1*f; y1 ~~ 1.1*y1; y2 ~~ 1*y2; y3 ~~ 1*y3; y4 ~~ 2*y4; x1 ~~ 1*x1; x2 ~~ 1*x2
                x1 ~ 2*1
                y4 ~ 1*1
                indirect := b1*b1
                b1 == 0.5
                """;

        SemLavaanParser.Result result = SemLavaanParser.parse(lavaan);
        SemIm semIm = result.semIm();
        Node f = semIm.getVariableNode("f");
        Node y4 = semIm.getVariableNode("y4");
        Node x1 = semIm.getVariableNode("x1");
        Node x2 = semIm.getVariableNode("x2");

        assertEquals(NodeType.LATENT, f.getNodeType());
        assertEquals(NodeType.MEASURED, y4.getNodeType());
        assertEquals(7, semIm.getVariableNodes().size());
        assertEquals(7, semIm.getSemPm().getGraph().getNumEdges());

        assertEquals(1.0, semIm.getEdgeCoef(f, semIm.getVariableNode("y1")), 0.0);
        assertEquals(0.8, semIm.getEdgeCoef(f, semIm.getVariableNode("y2")), 0.0);
        assertEquals(1.2, semIm.getEdgeCoef(f, semIm.getVariableNode("y3")), 0.0);
        assertEquals(2.5, semIm.getEdgeCoef(f, y4), 0.0);
        assertEquals(0.3, semIm.getEdgeCoef(x1, y4), 0.0);
        assertEquals(-0.25, semIm.getEdgeCoef(x2, y4), 0.0);
        assertEquals(0.4, semIm.getParamValue(covarianceParameter(semIm, x1, x2)), 0.0);
        assertEquals(1.1, semIm.getErrVar(semIm.getVariableNode("y1")), 0.0);
        assertEquals(2.0, semIm.getErrVar(y4), 0.0);

        // The mean of y4 is its intercept plus what its parents contribute, 1 + 0.3 * 2.
        assertEquals(2.0, semIm.getMean(x1), 1e-12);
        assertEquals(1.6, semIm.getMean(y4), 1e-12);
        assertEquals(1.0, semIm.getIntercept(y4), 1e-12);

        // The defined parameter and the constraint are skipped, each with a warning, and nothing else.
        assertEquals(result.warnings().toString(), 2, result.warnings().size());
    }

    @Test
    public void testLavaanWithoutValues() {
        SemLavaanParser.Result result = SemLavaanParser.parse("y ~ x + z\nx ~~ z");
        SemIm semIm = result.semIm();
        Node x = semIm.getVariableNode("x");
        Node y = semIm.getVariableNode("y");
        Node z = semIm.getVariableNode("z");

        // Nothing is random: the parameters with no value get fixed defaults, and a warning says so.
        assertEquals(0.0, semIm.getEdgeCoef(x, y), 0.0);
        assertEquals(0.0, semIm.getEdgeCoef(z, y), 0.0);
        assertEquals(0.0, semIm.getParamValue(covarianceParameter(semIm, x, z)), 0.0);
        assertEquals(1.0, semIm.getErrVar(y), 0.0);
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().getFirst().startsWith("No value was given for 6 of the parameters"));
    }

    /**
     * A SEM IM with no variables is what an IM box with no parents holds until a file is loaded into it. It is
     * logged when it is made, so it has to print.
     */
    @Test
    public void testEmptySemImPrints() {
        SemIm semIm = new SemIm(new SemPm(new EdgeListGraph()));

        assertTrue(semIm.getVariableNodes().isEmpty());
        assertNotNull(semIm.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLavaanEmpty() {
        SemLavaanParser.parse("# Nothing but a comment.\n");
    }
}
