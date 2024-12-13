package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.algcomparison.CompareTwoGraphs;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.TabularComparison;
import edu.cmu.tetradapp.util.WatchedProcess;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.Serial;

import static edu.cmu.tetrad.graph.GraphUtils.getComparisonGraph;

/**
 * The StatsListEditor class is a JPanel that displays statistics for a tabular comparison.
 */
public class StatsListEditor extends JPanel {

    @Serial
    private static final long serialVersionUID = 8455624852328328919L;

    /**
     * The comparison being edited.
     */
    private final TabularComparison comparison;

    /**
     * The parameters for the comparison.
     */
    private final Parameters params;

    /**
     * The data model for the comparison.
     */
    private final DataModel dataModel;

    /**
     * The target graph for the comparison.
     */
    private final Graph targetGraph;

    /**
     * The reference graph for the comparison.
     */
    private Graph referenceGraph;

    /**
     * The text area for the table.
     */
    private JTextArea area;

    /**
     * <p>Constructor for StatsListEditor.</p>
     *
     * @param comparison a {@link edu.cmu.tetradapp.model.TabularComparison} object
     */
    public StatsListEditor(TabularComparison comparison) {
        this.comparison = comparison;
        this.params = comparison.getParams();
        this.targetGraph = comparison.getTargetGraph();
        this.referenceGraph = comparison.getReferenceGraph();
        this.dataModel = comparison.getDataModel();
        setup();
    }

    private void setup() {
        JMenuBar menubar = menubar();
        show(menubar);
    }

    private void show(JMenuBar menubar) {
        setLayout(new BorderLayout());
        add(menubar, BorderLayout.NORTH);
        add(getTableDisplay(), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private JComponent getTableDisplay() {
        this.area = new JTextArea();
        this.area.setText(tableTextWithHeader());
        this.area.moveCaretPosition(0);
        this.area.setSelectionStart(0);
        this.area.setSelectionEnd(0);

        this.area.setBorder(new EmptyBorder(5, 5, 5, 5));

        this.area.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
//        this.area.setPreferredSize(new Dimension(1200, 1800));

        JScrollPane pane = new JScrollPane(this.area);
        pane.setPreferredSize(new Dimension(700, 700));

        Box b = Box.createVerticalBox();
        b.add(pane);

        return b;
    }

    @NotNull
    private String tableTextWithHeader() {
        String table = CompareTwoGraphs.getStatsListTable(this.referenceGraph, this.targetGraph, this.dataModel, this.comparison.getElapsedTime());
        return "True graph from " + this.comparison.getReferenceName() + "\nTarget graph from " + this.comparison.getTargetName()
               + "\n\n" + table;
    }

    @NotNull
    private JMenuBar menubar() {
        JMenuBar menubar = new JMenuBar();
        JMenu menu = new JMenu("Compare To...");
        JMenuItem graph = new JCheckBoxMenuItem("DAG");
        graph.setBackground(Color.WHITE);
        JMenuItem cpdag = new JCheckBoxMenuItem("CPDAG");
        cpdag.setBackground(Color.YELLOW);
        JMenuItem pag = new JCheckBoxMenuItem("PAG");
        pag.setBackground(Color.GREEN.brighter().brighter());

        ButtonGroup group = new ButtonGroup();
        group.add(graph);
        group.add(cpdag);
        group.add(pag);

        menu.add(graph);
        menu.add(cpdag);
        menu.add(pag);

        menubar.add(menu);

        this.referenceGraph = getComparisonGraph(this.comparison.getReferenceGraph(), this.params);

        switch (this.params.getString("graphComparisonType")) {
            case "CPDAG":
                menu.setText("Compare to CPDAG...");
                cpdag.setSelected(true);
                break;
            case "PAG":
                menu.setText("Compare to PAG...");
                pag.setSelected(true);
                break;
            default:
                menu.setText("Compare to DAG...");
                graph.setSelected(true);
                break;
        }

        graph.addActionListener(e -> {
            this.params.set("graphComparisonType", "DAG");
            menu.setText("Compare to DAG...");
            menu.setBackground(Color.WHITE);
//            this.referenceGraph = getComparisonGraph(this.comparison.getReferenceGraph(), this.params);

            new WatchedProcess() {
                @Override
                public void watch() {
                    SwingUtilities.invokeLater(() -> {
                        referenceGraph = getComparisonGraph(comparison.getReferenceGraph(), params);
                        area.setText(tableTextWithHeader());
                        area.moveCaretPosition(0);
                        area.setSelectionStart(0);
                        area.setSelectionEnd(0);
                        area.repaint();
                    });
                }
            };

//            this.area.setText(tableTextWithHeader());
//            this.area.moveCaretPosition(0);
//            this.area.setSelectionStart(0);
//            this.area.setSelectionEnd(0);
//
//            this.area.repaint();

        });

        cpdag.addActionListener(e -> {
            this.params.set("graphComparisonType", "CPDAG");
            menu.setText("Compare to CPDAG...");
            menu.setBackground(Color.YELLOW);
//            this.referenceGraph = getComparisonGraph(this.comparison.getReferenceGraph(), this.params);

            new WatchedProcess() {
                @Override
                public void watch() throws InterruptedException {
                    SwingUtilities.invokeLater(() -> {
                        referenceGraph = getComparisonGraph(comparison.getReferenceGraph(), params);
                        area.setText(tableTextWithHeader());
                        area.moveCaretPosition(0);
                        area.setSelectionStart(0);
                        area.setSelectionEnd(0);
                        area.repaint();
                    });
                }
            };

//            this.area.setText(tableTextWithHeader());
//            this.area.moveCaretPosition(0);
//            this.area.setSelectionStart(0);
//            this.area.setSelectionEnd(0);
//
//            this.area.repaint();

        });

        pag.addActionListener(e -> {
            this.params.set("graphComparisonType", "PAG");
            menu.setText("Compare to PAG...");
            menu.setBackground(Color.GREEN.brighter().brighter());

            new WatchedProcess() {
                @Override
                public void watch() {
                    SwingUtilities.invokeLater(() -> {
                        referenceGraph = getComparisonGraph(comparison.getReferenceGraph(), params);
                        area.setText(tableTextWithHeader());
                        area.moveCaretPosition(0);
                        area.setSelectionStart(0);
                        area.setSelectionEnd(0);
                        area.repaint();
                    });
                }
            };

//            this.area.setText(tableTextWithHeader());
//            this.area.moveCaretPosition(0);
//            this.area.setSelectionStart(0);
//            this.area.setSelectionEnd(0);
//            this.area.repaint();
        });

        return menubar;
    }
}
