package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Pc;
import org.apache.commons.math3.util.FastMath;

import java.util.List;

public class SvarSetEndpointStrategy implements SetEndpointStrategy {

    private final IndependenceTest independenceTest;
    private final Knowledge knowledge;

    public SvarSetEndpointStrategy(IndependenceTest independenceTest, Knowledge knowledge) {
        if (independenceTest == null) {
            throw new IllegalArgumentException("Independence test is null.");
        }

        if (knowledge == null) {
            throw new IllegalArgumentException("Knowledge is null.");
        }

        this.independenceTest = independenceTest;
        this.knowledge = knowledge;
    }

    @Override
    public void setEndpoint(Graph graph, Node a, Node b, Endpoint endpoint) {
        graph.setEndpoint(a, b, endpoint);
        orientSimilarPairs(graph, knowledge, a, b, endpoint, independenceTest);
    }

    private void orientSimilarPairs(Graph graph, Knowledge knowledge, Node x, Node y, Endpoint mark, IndependenceTest independenceTest) {
        if (x.getName().equals("time") || y.getName().equals("time")) {
            return;
        }
        System.out.println("Entering orient similar pairs method for x and y: " + x + ", " + y);
        int ntiers = knowledge.getNumTiers();
        int indx_tier = knowledge.isInWhichTier(x);
        int indy_tier = knowledge.isInWhichTier(y);
        int tier_diff = FastMath.max(indx_tier, indy_tier) - FastMath.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        List<String> tier_x = knowledge.getTier(indx_tier);
//        Collections.sort(tier_x);
        List<String> tier_y = knowledge.getTier(indy_tier);
//        Collections.sort(tier_y);

        int i;
        for (i = 0; i < tier_x.size(); ++i) {
            if (getNameNoLag(x.getName()).equals(getNameNoLag(tier_x.get(i)))) {
                indx_comp = i;
                break;
            }
        }

        for (i = 0; i < tier_y.size(); ++i) {
            if (getNameNoLag(y.getName()).equals(getNameNoLag(tier_y.get(i)))) {
                indy_comp = i;
                break;
            }
        }

        if (indx_comp == -1) System.out.println("WARNING: indx_comp = -1!!!! ");
        if (indy_comp == -1) System.out.println("WARNING: indy_comp = -1!!!! ");

        for (i = 0; i < ntiers - tier_diff; ++i) {
            if (knowledge.getTier(i).size() == 1) continue;
            String A;
            Node x1;
            String B;
            Node y1;
            if (indx_tier >= indy_tier) {
                List<String> tmp_tier1 = knowledge.getTier(i + tier_diff);
//                Collections.sort(tmp_tier1);
                List<String> tmp_tier2 = knowledge.getTier(i);
//                Collections.sort(tmp_tier2);
                A = tmp_tier1.get(indx_comp);
                B = tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = independenceTest.getVariable(A);
                y1 = independenceTest.getVariable(B);

                if (graph.isAdjacentTo(x1, y1) && graph.getEndpoint(x1, y1) == Endpoint.CIRCLE) {
                    System.out.print("Orient edge " + graph.getEdge(x1, y1).toString());
                    graph.setEndpoint(x1, y1, mark);
                    System.out.println(" by structure knowledge as: " + graph.getEdge(x1, y1).toString());
                }
            }
        }

    }


    /**
     * <p>getNameNoLag.</p>
     *
     * @param obj a {@link java.lang.Object} object
     * @return a {@link java.lang.String} object
     */
    public String getNameNoLag(Object obj) {
        return TsUtils.getNameNoLag(obj);
    }
}
