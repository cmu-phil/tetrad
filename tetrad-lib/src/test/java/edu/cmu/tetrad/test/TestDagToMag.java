package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.DagToMag;
import edu.cmu.tetrad.search.DagToPag2;
import org.junit.Test;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the functions of EndpointMatrixGraph and EdgeListGraph through the
 * Graph interface.
 *
 * @author Joseph Ramsey
 */
public final class TestDagToMag {

    /**
     * Test whether criterion of MAG are satisfied
     * 1. is a DAG (trivially)
     * 2. no inducing path between non-adjacent nodes
     */
    @Test
    public void testDagToMag0() {
        checkConvertInducingPath("Latent(L)-->X1,Latent(L)-->X2,X1-->X3,X2-->X3,X3-->X4,X2-->X5");
    }

    @Test
    public void testDagToMag11() {
        String inputDag = "Latent(L)-->X1,Latent(L)-->X2,X1-->X3,X2-->X3,X3-->X4,X2-->X5";
        checkConvertAdjacency(inputDag, false);

        String outputMag = "X1-->X3,X2-->X3,X3-->X4,X2-->X5";
        checkDagToMag(inputDag, outputMag, false);
    }

    @Test
    public void testDagToMag12() {
        String inputDag = "Latent(L)<-X1,Latent(L)-->X2,X1-->X3,X2-->X3,X3-->X4,X2-->X5";
        checkConvertAdjacency(inputDag, false);

        String outputMag = "X1-->X3,X2-->X3,X3-->X4,X2-->X5";
        checkDagToMag(inputDag, outputMag, false);
    }

//    @Test
//    public void testDagToMag13() {
//        String inputDag = "Latent(L)<--X1,Latent(L)-->X2,L-->X3,X2-->X3,X3-->X4";
//        checkConvertAdjacency(inputDag, false);
//
//        String outputMag = "X1-->X2,X2-->X3,X3-->X4";
//        checkDagToMag(inputDag, outputMag, true);
//    }

//    @Test
//    public void testDagToMag14() {
//        String inputDag = "Latent(L)<--X1,Latent(L)<--X2,X1-->X3,X3-->X4,X2-->X3";
//        checkConvertAdjacency(inputDag, false);
//
//        String outputMag = "X1<->X2,X1-->X3,X2-->X3,X3-->X4";
//        checkDagToMag(inputDag, outputMag, true);
//    }

    /**
     * Input a DAG, convert to a MAG, check if inducing path between non-adjacent nodes exist
     * @param inputDag
     */
    private void checkConvertInducingPath(String inputDag) {

        // Set up graph and node objects
        Graph dag = GraphConverter.convert(inputDag);

        // Set up DagToMag
        Graph mag = new DagToMag(dag).convert();

        // List allNodes (all of which are measured, since it's mag)
        List<Node> allNodes = mag.getNodes();

        // find non-adjacent nodes
        for (Node node : allNodes) {
            List<Node> adjNodes = mag.getAdjacentNodes(node);
            List<Node> nonAdjNodes = new ArrayList<Node>();

            for (Node i : adjNodes) {
                if (false == adjNodes.contains(i)) {
                    nonAdjNodes.add(i);
                }
            }

            if (nonAdjNodes.size() < 1) continue;

            for (Node i : nonAdjNodes) {
                if (mag.existsInducingPath(node, i)) {
                    throw new RuntimeException("Inducing path found in converted MAG");
                }
            }
        }
    }

    /**
     * Input a DAG, check adjacency w.r.t. output of DagToPag2
     */
    private void checkConvertAdjacency(String inputDag, boolean verbose) {

        // Set up graph and node objects.
        Graph dag = GraphConverter.convert(inputDag);

        // Set up DagToPag2 (just for the purpose of checking adjacency).
        Graph pag = new DagToPag2(dag).convert();

        // Set up DagToMag.
        Graph resultMag = new DagToMag(dag).convert();

        // Check adjacency.
        pag.reorientAllWith(Endpoint.TAIL);
        resultMag.reorientAllWith(Endpoint.TAIL);

        resultMag = GraphUtils.replaceNodes(resultMag, pag.getNodes());

        if (verbose) {
            System.out.println("Start checkConvertAdjacency ... ");
            System.out.println("inputDag\n" + dag);
            System.out.println("DagToPag2\n" + pag);
            System.out.println("DagToMag\n" + resultMag);
        }

        // Do test.
        if (!(resultMag.equals(pag))) {
            fail();
        }
    }


    /**
     * Input a DAG, check converted MAG
     */
    private void checkDagToMag(String inputDag, String outputMag, boolean verbose) {

        // Set up graph and node objects.
        Graph dag = GraphConverter.convert(inputDag);

        // Set up DagToMag
        Graph resultMag = new DagToMag(dag).convert();

        // Build comparison MAG.
        Graph trueMag = GraphConverter.convert(outputMag);

        resultMag = GraphUtils.replaceNodes(resultMag, trueMag.getNodes());

        if (verbose) {
            System.out.println("Start checkDagToMag ... ");
            System.out.println("inputDag\n" + dag);
            System.out.println("DagToMag\n" + resultMag);
            System.out.println("trueMag\n" + trueMag);
        }

        // Do test
        if (!(resultMag.equals(trueMag))) {
            fail();
        }
    }
}