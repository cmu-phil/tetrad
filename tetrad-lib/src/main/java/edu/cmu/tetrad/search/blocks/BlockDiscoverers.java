package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.ntad_test.NtadTest;

public final class BlockDiscoverers {
    private BlockDiscoverers() {}

    public static BlockDiscoverer bpc(DataSet data, NtadTest ntad, double alpha, SingleClusterPolicy policy) {
        return new BpcBlockDiscoverer(data, ntad, alpha, policy);
    }

    public static BlockDiscoverer fofc(DataSet data, NtadTest test, double alpha, SingleClusterPolicy policy) {
        return new FofcBlockDiscoverer(data, test, alpha, policy);
    }

    public static BlockDiscoverer ftfc(DataSet data, NtadTest ntad, double alpha, SingleClusterPolicy policy) {
        return new FtfcBlockDiscoverer(data, ntad, alpha, policy);
    }

    public static BlockDiscoverer tscTest(DataSet data, double alpha, SingleClusterPolicy policy) {
        return new TscTestBlockDiscoverer(data, alpha, policy);
    }

    public static BlockDiscoverer tscScore(DataSet data, double alpha,
                                           double ebicGamma, double ridge, double penaltyDiscount, SingleClusterPolicy policy) {
        return new TscScoreBlockDiscoverer(data, alpha, ebicGamma, ridge, penaltyDiscount, policy);
    }
}