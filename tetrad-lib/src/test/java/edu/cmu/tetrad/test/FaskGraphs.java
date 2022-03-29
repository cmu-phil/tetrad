package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.multi.Fask;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads autistic/typical data files from a given directory and runs FASK on each one.
 *
 * @author jdramsey
 */
public class FaskGraphs {
    private final List<String> filenames = new ArrayList<>();
    private List<DataSet> datasets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private final List<Boolean> types = new ArrayList<>();

    /**
     * @param path       The path to the directory containing the files.
     * @param parameters Parameters for the FASK search.
     * @param contains   Some string(s) the data filenames must include. May be  blank.
     */
    public FaskGraphs(final String path, final Parameters parameters, final String... contains) {
        loadFiles(path, parameters, contains);
    }

    public List<String> getFilenames() {
        return this.filenames;
    }

    public List<Graph> getGraphs() {
        return this.graphs;
    }

    public void setGraphs(final List<Graph> graphs) {
        this.graphs = graphs;
    }

    public List<Boolean> getTypes() {
        return this.types;
    }

    public void reconcileNames(final FaskGraphs... files) {
        final Set<String> allNames = new HashSet<>();
        final List<Node> nodes = new ArrayList<>();

        for (final FaskGraphs file : files) {
            for (final Graph graph : file.getGraphs()) {
                for (final Node node : graph.getNodes()) {
                    if (!allNames.contains(node.getName())) {
                        nodes.add(node);
                        allNames.add(node.getName());
                    }
                }
            }
        }

        final List<Graph> graphs2 = new ArrayList<>();

        for (final Graph graph : this.graphs) {
            graphs2.add(GraphUtils.replaceNodes(graph, nodes));
        }

        this.graphs = graphs2;
    }

    public List<DataSet> getDatasets() {
        return this.datasets;
    }

    public void setDatasets(final List<DataSet> datasets) {
        this.datasets = datasets;
    }

    private void loadFiles(final String path, final Parameters parameters, final String... contains) {
        final File dir = new File(path);

        final File[] files = dir.listFiles();

        if (files == null) {
            throw new NullPointerException();
        }

        FILE:
        for (final File file : files) {
            final String name = file.getName();

            for (final String s : contains) {
                if (!name.contains(s)) continue FILE;
            }

            if (!name.contains("graph")) {
                try {
                    if (name.contains("autistic")) {
                        this.types.add(true);
                        final DataSet dataSet = DataUtils.loadContinuousData(new File(path, name), "//", '\"' ,
                                "*", true, Delimiter.TAB);
                        this.filenames.add(name);
                        this.datasets.add(dataSet);
                        final Fask fask = new Fask();
                        final Graph search = fask.search(dataSet, parameters);
                        this.graphs.add(search);
                    } else if (name.contains("typical")) {
                        this.types.add(false);
                        final DataSet dataSet = DataUtils.loadContinuousData(new File(path, name), "//", '\"' ,
                                "*", true, Delimiter.TAB);
                        this.filenames.add(name);
                        this.datasets.add(dataSet);
                        final Fask fask = new Fask();
                        final Graph search = fask.search(dataSet, parameters);
                        this.graphs.add(search);
                    }

                    System.out.println("Loaded " + name);
                } catch (final IOException e) {
                    System.out.println("File " + name + " could not be parsed.");
                }
            }
        }

        reconcileNames();
    }

}
