package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.DagMetric;
import edu.cmu.tetrad.sem.DagMetricResult;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DagModelScoreModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public final class DagModelScoreEditor extends JPanel {

    private final List<DagMetric> metrics = new ArrayList<>();
    private final List<DagMetricResult> rows = new ArrayList<>();
    private final ResultTableModel model = new ResultTableModel();

    private DataSet data;
    private Graph dag;

    public DagModelScoreEditor(DagModelScoreModel model) {
        this();

        if (!model.getInputGraph().paths().isLegalDag()) {
            throw new IllegalArgumentException("Input graph is not a legal DAG");
        }

        setContext(model.getInputData(), model.getGraph());
    }

    public DagModelScoreEditor() {
        super(new BorderLayout(8, 8));

        addMetrics();

        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);

        JButton recompute = new JButton("Recompute");
        recompute.addActionListener(e -> recompute());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(recompute);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    private void addMetrics() {
        if (data.isContinuous()) {
            metrics.add(semBic());
            metrics.add(ffml());
            metrics.add(legendreBic());
            metrics.add(minimaxTrffBic());
        } else if (data.isMixed()) {
            metrics.add(ffml());
            metrics.add(legendreBic());
            metrics.add(minimaxTrffBic());
        }
    }

    private static @NotNull DagMetric minimaxTrffBic() {
        return (data, dag) -> {
            edu.cmu.tetrad.algcomparison.score.MinimaxTRffBicScore algScore = new edu.cmu.tetrad.algcomparison.score.MinimaxTRffBicScore();
            edu.cmu.tetrad.search.score.MinimaxTRffBicScore score = (edu.cmu.tetrad.search.score.MinimaxTRffBicScore) algScore.getScore(data, new Parameters());

            double _score = 0.0;

            dag = GraphUtils.replaceNodes(dag, data.getVariables());

            List<Node> nodes = data.getVariables();

            for (Node node : nodes) {
                List<Node> parents = dag.getParents(node);
                int i = nodes.indexOf(node);
                int[] parentsIndices = parents.stream().mapToInt(nodes::indexOf).toArray();
                _score += score.localScore(i, parentsIndices);
            }

            return new DagMetricResult("Minimax t-RFF BIC", _score, "General Mixed BIC Score");
        };
    }

    private static @NotNull DagMetric legendreBic() {
        return (data, dag) -> {
            edu.cmu.tetrad.algcomparison.score.MinimaxLegendreScore algScore = new edu.cmu.tetrad.algcomparison.score.MinimaxLegendreScore();
            edu.cmu.tetrad.search.score.MinimaxLegendreScore score = (edu.cmu.tetrad.search.score.MinimaxLegendreScore) algScore.getScore(data, new Parameters());

            double _score = 0.0;

            dag = GraphUtils.replaceNodes(dag, data.getVariables());

            List<Node> nodes = data.getVariables();

            for (Node node : nodes) {
                List<Node> parents = dag.getParents(node);
                int i = nodes.indexOf(node);
                int[] parentsIndices = parents.stream().mapToInt(nodes::indexOf).toArray();
                _score += score.localScore(i, parentsIndices);
            }

            return new DagMetricResult("Legendre BIC", _score, "General Mixed BIC Score");
        };
    }

    private static @NotNull DagMetric ffml() {
        return (data, dag) -> {
            edu.cmu.tetrad.algcomparison.score.FfMl algScore = new edu.cmu.tetrad.algcomparison.score.FfMl();
            edu.cmu.tetrad.search.score.FfMl score = (edu.cmu.tetrad.search.score.FfMl) algScore.getScore(data, new Parameters());

            double _score = 0.0;

            dag = GraphUtils.replaceNodes(dag, data.getVariables());

            List<Node> nodes = data.getVariables();

            for (Node node : nodes) {
                List<Node> parents = dag.getParents(node);
                int i = nodes.indexOf(node);
                int[] parentsIndices = parents.stream().mapToInt(nodes::indexOf).toArray();
                _score += score.localScore(i, parentsIndices);
            }

            return new DagMetricResult("FFML", _score, "General Mixed Likelihood Score");
        };
    }

    private static @NotNull DagMetric semBic() {
        return (data, dag) -> {
            edu.cmu.tetrad.algcomparison.score.SemBicScore algScore = new edu.cmu.tetrad.algcomparison.score.SemBicScore();
            edu.cmu.tetrad.search.score.SemBicScore score = (edu.cmu.tetrad.search.score.SemBicScore) algScore.getScore(data, new Parameters());

            double _score = 0.0;

            dag = GraphUtils.replaceNodes(dag, data.getVariables());

            List<Node> nodes = data.getVariables();

            for (Node node : nodes) {
                List<Node> parents = dag.getParents(node);
                int i = nodes.indexOf(node);
                int[] parentsIndices = parents.stream().mapToInt(nodes::indexOf).toArray();
                _score += score.localScore(i, parentsIndices);
            }

            return new DagMetricResult("SEM BIC", _score, "Linear Gaussian BIC");
        };
    }

    /**
     * Add metrics in the order you want them displayed.
     */
    public DagModelScoreEditor addMetric(DagMetric metric) {
        if (metric != null) metrics.add(metric);
        return this;
    }

    public void setContext(DataSet data, Graph dag) {
        this.data = data;
        this.dag = dag;
        recompute();
    }

    public void recompute() {
        rows.clear();
        if (data == null || dag == null) {
            model.fireTableDataChanged();
            return;
        }

        for (DagMetric m : metrics) {
            rows.add(m.compute(data, dag));
        }
        model.fireTableDataChanged();
    }

    private final class ResultTableModel extends AbstractTableModel {
        private final String[] cols = {"Metric", "Value", "Note"};

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int c) {
            return cols[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            DagMetricResult x = rows.get(r);
            NumberFormat nf = NumberFormat.getNumberInstance();
            return switch (c) {
                case 0 -> x.name();
                case 1 -> Double.isFinite(x.value()) ? nf.format(x.value()) : Double.NaN;
                case 2 -> x.note();
                default -> "";
            };
        }
    }
}