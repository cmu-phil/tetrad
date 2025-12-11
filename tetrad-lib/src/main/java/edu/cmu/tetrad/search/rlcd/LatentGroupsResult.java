package edu.cmu.tetrad.search.rlcd;

final class LatentGroupsResult {
    private final int[][] adjacency;

    LatentGroupsResult(int[][] adjacency) {
        this.adjacency = adjacency;
    }

    public int[][] getAdjacency() {
        return adjacency;
    }
}