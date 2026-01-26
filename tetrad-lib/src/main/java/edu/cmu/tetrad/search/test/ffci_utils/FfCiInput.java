package edu.cmu.tetrad.search.test.ffci_utils;

import edu.cmu.tetrad.graph.Node;

import java.util.List;

public record FfCiInput(Node x, Node y, List<Node> zSorted, int nActive) {}