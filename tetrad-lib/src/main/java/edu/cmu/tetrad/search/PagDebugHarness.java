package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PagDebugHarness {
    private final AtomicBoolean firstIllegalDumped = new AtomicBoolean(false);
    private final Set<Node> selection; // pass your selection set or Collections.emptySet()
    private final String tag; // identify the grid point (params hash, seed, etc.)

    public PagDebugHarness(Set<Node> selection, String tag) {
        this.selection = selection;
        this.tag = tag;
    }

    public Graph checkPoint(Graph g, String stage) {
        // Use the exact same legality method as your “manual” path
        PagLegalityCheck.LegalPagRet ret = PagLegalityCheck.isLegalPag(g, selection);

        if (!ret.isLegalPag() && firstIllegalDumped.compareAndSet(false, true)) {
            dump(g, stage, ret.getReason());
        }
        return g;
    }

    private void dump(Graph g, String stage, String reason) {
        try {
            String ts = LocalDateTime.now().toString().replace(':', '-');
            File dir = new File("lv_lite_illegal_dumps");
            if (!dir.exists()) dir.mkdirs();

            File meta = new File(dir, tag + "__" + stage + "__" + ts + "__meta.txt");
            try (FileWriter fw = new FileWriter(meta, StandardCharsets.UTF_8)) {
                fw.write("Stage: " + stage + "\n");
                fw.write("Reason: " + reason + "\n");
                fw.write("Tag: " + tag + "\n");
            }

            File graph = new File(dir, tag + "__" + stage + "__" + ts + "__graph.txt");
            try (FileWriter fw = new FileWriter(graph, StandardCharsets.UTF_8)) {
                fw.write(g.toString()); // or your preferred Graph save format
            }
        } catch (Exception e) {
            // swallow; this is debug-only
        }
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 10; i++) {
            Graph g = RandomGraph.randomGraph(20, 6, 40, 100, 100, 100, false);
            SemPm pm = new SemPm(g);
            SemIm im = new SemIm(pm);
            DataSet dataSet = im.simulateData(1000, false);

            Score score = new SemBicScore(new CovarianceMatrix(dataSet), 2);
            LvDumb lvDumb = new LvDumb(score);
            lvDumb.setVerbose(false);
            Graph pag = lvDumb.search();

            PagLegalityCheck.LegalPagRet ret = PagLegalityCheck.isLegalPag(pag, Collections.emptySet());
            System.out.println(ret.isLegalPag() + " :: " + ret.getReason());
        }
    }
}