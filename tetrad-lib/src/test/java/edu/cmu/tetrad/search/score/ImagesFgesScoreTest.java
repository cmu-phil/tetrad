package edu.cmu.tetrad.search.score;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;

public class ImagesFgesScoreTest {

    @Test
    public void testLocalScoreDiffBug() throws InterruptedException {
        Score mockScore = new MockScore(10.0);
        List<Score> scores = new ArrayList<>();
        scores.add(mockScore);
        
        ImagesScore imagesScore = new ImagesScore(scores);
        
        double diff = imagesScore.localScoreDiff(0, 1, new int[0]);
        
        // With the bug, diff will be NaN because count will be 0 (10.0 is not NaN)
        // or it will be 0/0 = NaN.
        // Actually if 10.0 is passed, it fails if (!Double.isNaN(_score)) check.
        // Wait, the code was:
        // if (Double.isNaN(_score)) {
        //     sum += _score;
        //     count++;
        // }
        // So if _score is 10.0, it is NOT NaN, so it is skipped.
        // sum = 0.0, count = 0.
        // returns 0.0 / 0 = NaN.
        
        assertFalse("Score should not be NaN for valid component scores", Double.isNaN(diff));
        assertEquals(10.0, diff, 1e-6);
    }

    private static class MockScore implements Score {
        private final double val;
        public MockScore(double val) { this.val = val; }
        @Override public double localScoreDiff(int x, int y, int[] z) { return val; }
        @Override public double localScore(int i, int[] parents) { return val; }
        @Override public double localScore(int i, int parent) { return val; }
        @Override public double localScore(int i) { return val; }
        @Override public boolean isEffectEdge(double bump) { return false; }
        @Override public List<edu.cmu.tetrad.graph.Node> getVariables() { return new ArrayList<>(); }
        @Override public int getSampleSize() { return 100; }
        @Override public int getMaxDegree() { return 100; }
        @Override public boolean determines(List<edu.cmu.tetrad.graph.Node> z, edu.cmu.tetrad.graph.Node y) { return false; }
    }
}
