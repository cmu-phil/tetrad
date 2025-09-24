// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/CorePagBuilder.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.*;

final class CorePagBuilder {
    Graph build(MecTriples mec) {
        Graph pag = new EdgeListGraph(mec.skeleton().getNodes());
        for (Edge e : mec.skeleton().getEdges()) pag.addEdge(Edges.undirectedEdge(e.getNode1(), e.getNode2()));

        for (Triple t : mec.colliders()) {
            boolean fullHead = mec.order().getOrDefault(t, 1) == 0; // k==0 → fully directed, else o->
            orientInto(pag, t.getX(), t.getY(), fullHead);
            orientInto(pag, t.getZ(), t.getY(), fullHead);
        }
        return pag;
    }

    private void orientInto(Graph pag, Node from, Node to, boolean fullHead) {
        Edge e = pag.getEdge(from, to);
        if (e == null) {
            if (fullHead) pag.addEdge(Edges.directedEdge(from, to));
            else pag.addEdge(new Edge(from, to, Endpoint.CIRCLE, Endpoint.ARROW));
            return;
        }
        Endpoint a = e.getEndpoint(from), b = e.getEndpoint(to);
        if (fullHead) {
            if (b != Endpoint.ARROW || a == Endpoint.CIRCLE) {
                pag.removeEdge(e);
                pag.addEdge(Edges.directedEdge(from, to));
            }
        } else {
            if (b != Endpoint.ARROW || a == Endpoint.ARROW) {
                pag.removeEdge(e);
                pag.addEdge(new Edge(from, to, Endpoint.CIRCLE, Endpoint.ARROW));
            }
        }
    }
}