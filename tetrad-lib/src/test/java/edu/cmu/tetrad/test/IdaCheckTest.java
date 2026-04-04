package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.DataBox;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.OrderedPair;
import edu.cmu.tetrad.search.IdaCheck;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class IdaCheckTest {

    @Test
    public void testEmptyTotalEffects() {
        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        List<Node> nodes = new ArrayList<>();
        nodes.add(x);
        nodes.add(y);

        Graph graph = new EdgeListGraph(nodes);
        graph.addDirectedEdge(x, y);

        DataBox dataBox = new DoubleDataBox(10, 2);
        DataSet dataSet = new BoxDataSet(dataBox, nodes);
        
        // Setup a SemIm with X -> Y having a coefficient, say 0.5
        SemPm semPm = new SemPm(graph);
        SemIm semIm = new SemIm(semPm);
        semIm.setParamValue(x, y, 0.5);

        IdaCheck idaCheck = new IdaCheck(graph, dataSet, semIm);
        
        OrderedPair<Node> pair = new OrderedPair<>(x, y);
        
        // Currently, if totalEffects is empty, what happens?
        // Let's see if we can get an empty totalEffects.
        // If x is a parent of y in a DAG, IDA should find something.
        // What if they are disconnected?
        
        Graph graph2 = new EdgeListGraph(nodes);
        IdaCheck idaCheck2 = new IdaCheck(graph2, dataSet, semIm);
        
        // X and Y are disconnected. IDA might return something (0.0) or empty.
        // Let's print or assert.
        double min = idaCheck2.getMinTotalEffect(x, y);
        double max = idaCheck2.getMaxTotalEffect(x, y);
        
        System.out.println("[DEBUG_LOG] Min: " + min);
        System.out.println("[DEBUG_LOG] Max: " + max);
        
        double sqDist = idaCheck2.getSquaredDistance(pair);
        System.out.println("[DEBUG_LOG] Squared Distance: " + sqDist);

        double sqMinDist = idaCheck2.getSquaredMinTrueDistance(pair);
        System.out.println("[DEBUG_LOG] Squared Min True Distance: " + sqMinDist);

        double trueEffect = idaCheck2.getTrueTotalEffect(pair);
        System.out.println("[DEBUG_LOG] True Total Effect: " + trueEffect);
        
        // With REGULAR IDA, disconnected nodes result in total effect [0.0]
        assertEquals(0.0, min, 0.0);
        // Ida.distance(effects=[0.0], trueEffect=0.5) returns abs(0.0 - 0.5) = 0.5.
        // IdaCheck.getSquaredDistance returns 0.5 * 0.5 = 0.25.
        assertEquals(0.25, sqDist, 0.0);
        assertEquals(0.25, sqMinDist, 0.0);

        // Test OPTIMAL IDA where it might return empty list
        // Note: For disconnected nodes, OPTIMAL IDA actually returns [0.0] as well, 
        // because it orientations about X don't find any amenable paths to Y.
        // Let's check what it actually returns.
        idaCheck2.setIdaType(edu.cmu.tetrad.search.Ida.IDA_TYPE.OPTIMAL);
        double minOpt = idaCheck2.getMinTotalEffect(x, y);
        System.out.println("[DEBUG_LOG] Min Opt: " + minOpt);
        assertEquals(0.0, minOpt, 0.0);
        
        double sqDistOpt = idaCheck2.getSquaredDistance(pair);
        assertEquals(0.25, sqDistOpt, 0.0);
    }
}
