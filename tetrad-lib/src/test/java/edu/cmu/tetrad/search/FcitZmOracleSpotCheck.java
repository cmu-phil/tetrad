package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.*;

/**
 * SMOKE CHECK ONLY -- not the exhaustive test. Samples random latent-variable models at small
 * observed sizes, drives Fcit and FcitZm (both commit routes) from the m-separation oracle on
 * the true MAG, and counts how often the terminal PAG is exactly the true PAG. Use PKE14 for
 * the real exhaustive enumeration.
 * <p>
 * args: [0] = number of models (default 150), [1] = observed vars (default 5),
 * [2] = latents (default 2).
 */
public final class FcitZmOracleSpotCheck {
    private FcitZmOracleSpotCheck() {
    }

    public static void main(String[] args) throws Exception {
        int numModels = args.length > 0 ? Integer.parseInt(args[0]) : 150;
        int obs = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        int latents = args.length > 2 ? Integer.parseInt(args[2]) : 2;

        int fcitExact = 0, zmPagExact = 0, zmMagExact = 0, usable = 0;

        for (int m = 0; m < numModels; m++) {
            RandomUtil.getInstance().setSeed(10_000L + m);
            // randomGraph's first argument is the number of MEASURED variables.
            Graph dag = RandomGraph.randomGraph(obs, latents,
                    2 * (obs + latents), 6, 4, 4, false);

            Graph trueMag = GraphTransforms.dagToMag(dag);
            Graph truePag;
            try {
                truePag = GraphTransforms.dagToPag(dag, false);
            } catch (Exception e) {
                continue;
            }

            List<Node> observed = new ArrayList<>();
            for (Node n : truePag.getNodes()) {
                if (n.getNodeType() == NodeType.MEASURED) observed.add(n);
            }
            if (observed.size() != obs) continue;
            usable++;

            if (exact(runFcit(trueMag), truePag, observed)) fcitExact++;
            if (exact(runZm(trueMag, FcitZm.COMMIT_ROUTE.PAG), truePag, observed)) zmPagExact++;
            if (exact(runZm(trueMag, FcitZm.COMMIT_ROUTE.MAG), truePag, observed)) zmMagExact++;
        }

        System.out.println("models usable        : " + usable);
        System.out.println("Fcit exact           : " + fcitExact);
        System.out.println("FcitZm (PAG route)   : " + zmPagExact);
        System.out.println("FcitZm (MAG route)   : " + zmMagExact);
    }

    private static boolean exact(Graph terminal, Graph truePag, List<Node> observed) {
        if (terminal == null) return false;
        Graph t = GraphUtils.replaceNodes(terminal, observed);
        Graph g = GraphUtils.replaceNodes(new EdgeListGraph(truePag), observed);
        if (t.getNumEdges() != g.getNumEdges()) return false;
        for (Edge e : g.getEdges()) {
            Edge e2 = t.getEdge(t.getNode(e.getNode1().getName()), t.getNode(e.getNode2().getName()));
            if (e2 == null || !e.equals(e2)) return false;
        }
        return true;
    }

    private static Graph runFcit(Graph trueMag) {
        try {
            Fcit f = new Fcit(new MsepTest(new EdgeListGraph(trueMag)),
                    new GraphScore(new EdgeListGraph(trueMag)));
            f.setKnowledge(new Knowledge());
            f.setCompleteRuleSetUsed(true);
            f.setVerbose(false);
            return f.search();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Graph runZm(Graph trueMag, FcitZm.COMMIT_ROUTE route) {
        try {
            FcitZm f = new FcitZm(new MsepTest(new EdgeListGraph(trueMag)),
                    new GraphScore(new EdgeListGraph(trueMag)));
            f.setKnowledge(new Knowledge());
            f.setCompleteRuleSetUsed(true);
            f.setCommitRoute(route);
            f.setVerbose(false);
            return f.search();
        } catch (Throwable t) {
            return null;
        }
    }
}
