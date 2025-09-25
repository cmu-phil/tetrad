package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.Node;

/**
 * Represents a local modification (add/remove edge) in a MAG for GPS search.
 */
public final class Move {
    enum Type {
        ADD_DIR,   // add X → Y
        REM_DIR,   // remove X → Y
        ADD_BI,    // add X ↔ Y
        REM_BI,    // remove X ↔ Y
        ADD_UG,    // add X — Y
        REM_UG     // remove X — Y
    }

    final Type type;
    final Node x;
    final Node y;

    /** A short unique key, useful for tabu lists */
    final String key;

    Move(Type type, Node x, Node y) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.key = type + ":" + x.getName() + "-" + y.getName();
    }

    @Override
    public String toString() {
        return key;
    }
}