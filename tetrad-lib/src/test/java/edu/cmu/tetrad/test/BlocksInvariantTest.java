package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.blocks.*;
import edu.cmu.tetrad.search.ntad_test.Cca;
import edu.cmu.tetrad.search.ntad_test.NtadTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class BlocksInvariantTest {
    @Test
    public void testBpcAdapterSpec() {
        DataSet ds = getData();
//        NtadTest ntad = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false, -1);
        BlockSpec spec = BlockDiscoverers.bpc(ds,0.05, -1, SingleClusterPolicy.EXCLUDE,
                false).discover();
        assertSame(ds, spec.dataSet());
        assertEquals(spec.blocks().size(), spec.blockVariables().size());
        BlocksUtil.validateBlocks(spec.blocks(), spec.dataSet());
        // Singleton blocks should map to observed nodes, multi>1 to latents:
        for (int i = 0; i < spec.blocks().size(); i++) {
            List<Integer> b = spec.blocks().get(i);
            boolean isSingleton = (b.size() == 1);
            // You can assert on NodeType if you want:
            // NodeType t = spec.blockVariables().get(i).getNodeType();
            // assertEquals(isSingleton ? NodeType.MEASURED : NodeType.LATENT, t);
        }
    }

    private DataSet getData() {
        Graph graph = RandomGraph.randomGraph(20, 3, 20, 100, 100, 100, false);
        SemPm sem = new SemPm(graph);
        SemIm sim = new SemIm(sem);
        return sim.simulateData(1000, false);
    }

    @Test
    public void testFofcAdapterSpec() {
        DataSet ds = getData();
        NtadTest ntad = new Cca(ds.getDoubleData().getSimpleMatrix(), false, -1);
        BlockSpec spec = BlockDiscoverers.fofc(ds, 0.05, -1, SingleClusterPolicy.EXCLUDE,
                false).discover();
        assertSame(ds, spec.dataSet());
        assertEquals(spec.blocks().size(), spec.blockVariables().size());
        BlocksUtil.validateBlocks(spec.blocks(), spec.dataSet());
    }

    @Test
    public void testFtfcAdapterSpec() {
        DataSet ds = getData();
        NtadTest ntad = new Cca(ds.getDoubleData().getSimpleMatrix(), false, -1);
        BlockSpec spec = BlockDiscoverers.ftfc(ds, 0.05, -1, SingleClusterPolicy.EXCLUDE,
                false).discover();
        assertSame(ds, spec.dataSet());
        assertEquals(spec.blocks().size(), spec.blockVariables().size());
        BlocksUtil.validateBlocks(spec.blocks(), spec.dataSet());
    }

    @Test
    public void testTscAdapterSpec() {
        DataSet ds = getData();
        BlockSpec spec = BlockDiscoverers.tsc(ds, 0.05, -1, 1e-8, 2, SingleClusterPolicy.EXCLUDE,
                0, false).discover();
        assertSame(ds, spec.dataSet());
        assertEquals(spec.blocks().size(), spec.blockVariables().size());
        BlocksUtil.validateBlocks(spec.blocks(), spec.dataSet());
    }
}