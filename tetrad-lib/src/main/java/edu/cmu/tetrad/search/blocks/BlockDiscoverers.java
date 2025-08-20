package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.ntad_test.NtadTest;

public final class BlockDiscoverers {
    private BlockDiscoverers() {}

    public static BlockDiscoverer bpc(DataSet data, NtadTest ntad, double alpha) {
        return new BpcBlockDiscoverer(data, ntad, alpha);
    }

    public static BlockDiscoverer fofc(DataSet data, NtadTest test, double alpha) {
        return new FofcBlockDiscoverer(data, test, alpha);
    }

    public static BlockDiscoverer ftfc(DataSet data, NtadTest ntad, double alpha) {
        return new FtfcBlockDiscoverer(data, ntad, alpha);
    }

    public static BlockDiscoverer tsc(DataSet data, double alpha) {
        return new TscBlockDiscoverer(data, alpha);
    }
}