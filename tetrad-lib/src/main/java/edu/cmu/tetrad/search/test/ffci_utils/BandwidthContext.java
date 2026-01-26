package edu.cmu.tetrad.search.test.ffci_utils;

import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Context information for bandwidth selection.
 * Used only for cache keys and logging — not for computation.
 */
public record BandwidthContext(String tag, List<Node> varsForKey) {

    /**
     * Stable identifier for caching bandwidths.
     *
     * Properties:
     *  - order-independent in varsForKey
     *  - depends only on variable names + tag
     *  - deterministic across JVM runs
     */
    public String id() {
        StringBuilder sb = new StringBuilder(128);

        sb.append(tag == null ? "null" : tag);
        sb.append("|vars=");

        if (varsForKey != null && !varsForKey.isEmpty()) {
            List<String> names = new ArrayList<>(varsForKey.size());
            for (Node v : varsForKey) {
                names.add(v.getName());
            }
            Collections.sort(names);
            for (String s : names) {
                sb.append(s).append(',');
            }
        }

        return sb.toString();
    }
}