package edu.cmu.tetradapp.app;

import com.formdev.flatlaf.FlatLightLaf;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.VerticalDoubleDataBox;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetradapp.Tetrad;
import edu.cmu.tetradapp.editor.EditorWindow;
import edu.cmu.tetradapp.model.BayesEstimatorWrapper;
import edu.cmu.tetradapp.model.BayesImWrapper;
import edu.cmu.tetradapp.model.BayesPmWrapper;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.DirichletEstimatorWrapper;
import edu.cmu.tetradapp.model.EdgewiseComparisonModel;
import edu.cmu.tetradapp.model.EmBayesEstimatorWrapper;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.model.GeneralizedSemEstimatorWrapper;
import edu.cmu.tetradapp.model.GeneralizedSemImWrapper;
import edu.cmu.tetradapp.model.GeneralizedSemPmWrapper;
import edu.cmu.tetradapp.model.GraphSelectionWrapper;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.HybridCgEstimatorWrapper;
import edu.cmu.tetradapp.model.HybridCgImWrapper;
import edu.cmu.tetradapp.model.HybridCgPmWrapper;
import edu.cmu.tetradapp.model.IdaModel;
import edu.cmu.tetradapp.model.KnowledgeBoxModel;
import edu.cmu.tetradapp.model.LatentClustersRunner;
import edu.cmu.tetradapp.model.LinearAdjustmentTotalEffectsModel;
import edu.cmu.tetradapp.model.MarkovCheckIndTestModel;
import edu.cmu.tetradapp.model.NoteModel;
import edu.cmu.tetradapp.model.RegressionRunner;
import edu.cmu.tetradapp.model.SemEstimatorWrapper;
import edu.cmu.tetradapp.model.SemImWrapper;
import edu.cmu.tetradapp.model.SemPmWrapper;
import edu.cmu.tetradapp.model.SemUpdaterWrapper;
import edu.cmu.tetradapp.model.SessionNodeWrapper;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.model.Simulation;
import edu.cmu.tetradapp.model.TextBayesUpdateModel;
import edu.cmu.tetradapp.model.datamanip.NtadExplorerWrapper;
import edu.cmu.tetradapp.session.Session;
import edu.cmu.tetradapp.session.SessionNode;
import edu.cmu.tetradapp.util.DesktopController;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Produces the screenshots used in the Tetrad manual (cmu-phil/tetrad-manual-rtd) by building sessions in code, opening
 * the real editors, and painting them to PNG files. Run it at each release so the manual's pictures cannot fall behind
 * the interface.
 * <p>
 * This is a developer tool, not a test: it lives in the test source tree so that it is never shipped, and its name does
 * not end in "Test" so that the build does not run it. Run its main method from the IDE. The first optional argument is
 * the output directory; the default is "tetrad-manual-screenshots" in the user's home directory, which keeps the output
 * out of this repository. The file names match the image names the manual already refers to, so the output can be
 * copied over docs/_static/images in the manual repo, or that directory can be given as the argument. A second optional
 * argument is a piece of text; only the pictures whose file names contain it are made.
 * <p>
 * The harness opens a real Tetrad window, because the editors need a realized window to lay themselves out, and closes
 * it when done. The simulated data are seeded, so two runs give the same pictures.
 * <p>
 * Each picture is a "scene": a small session built in code, and usually one box in it to open. The scenes are listed in
 * {@link #scenes()}, in the order the boxes appear in the palette. A scene that fails is reported and skipped; the rest
 * still run. To add a picture, add a scene.
 */
public final class ManualScreenshotHarness {

    /**
     * Size of the main window's content, in logical pixels. Tall enough to show the whole palette.
     */
    private static final int WIDTH = 1200;
    private static final int HEIGHT = 880;

    /**
     * Images are painted at twice the logical size so that they stay sharp on high-density displays.
     */
    private static final double SCALE = 2.0;

    /**
     * The least size an editor window is painted at, unless its scene asks for its natural size.
     */
    private static final int MIN_EDITOR_WIDTH = 900;
    private static final int MIN_EDITOR_HEIGHT = 640;

    private static final long SEED = 38271L;

    private static final String SESSION_NAME = "untitled1.tet";

    private static final String BOX = "tetrad-interface/box-by-box/";
    private static final String OVERVIEW = "tetrad-interface/overview/";

    private final File outDir;
    private final String filter;
    private JFrame frame;
    private TetradDesktop desktop;

    // Fixtures shared by the scenes, made once.
    private Graph dag;
    private DataSet continuousData;
    private DataSet discreteData;
    private DataSet mixedData;

    private ManualScreenshotHarness(File outDir, String filter) {
        this.outDir = outDir;
        this.filter = filter;
    }

    public static void main(String[] args) {
        File outDir = args.length > 0
                ? new File(args[0])
                : new File(System.getProperty("user.home"), "tetrad-manual-screenshots");
        String filter = args.length > 1 ? args[1] : "";

        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
        System.setProperty("apple.awt.application.name", "Tetrad");
        // The manual shows the light theme at the default font size, whatever this user's own settings are.
        FlatLightLaf.setup();
        Locale.setDefault(Locale.US);

        ManualScreenshotHarness harness = new ManualScreenshotHarness(outDir, filter);
        int status = 0;
        try {
            status = harness.run();
        } catch (Throwable t) {
            t.printStackTrace();
            status = 1;
        }
        System.exit(status);
    }

    // -----------------------------------------------------------------------------------------------------------
    // The scenes
    // -----------------------------------------------------------------------------------------------------------

    /**
     * Builds one session. Boxes are added in order, parents before children.
     */
    private interface Scene {
        void build(Stage stage) throws Exception;
    }

    /**
     * Something done to an open editor window before it is painted, such as pressing a button.
     */
    private interface WindowAction {
        void apply(EditorWindow window) throws Exception;
    }

    /**
     * The pictures, keyed by the file each is written to, in palette order.
     */
    private Map<String, Scene> scenes() {
        Map<String, Scene> scenes = new LinkedHashMap<>();

        // ---------------------------------------------------------------- The main window
        scenes.put(OVERVIEW + "main-window-overview.png", s -> {
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 110);
            s.search("Search1", 340, 110, true, data);
        });

        // ---------------------------------------------------------------- Data
        scenes.put(BOX + "data-box.png", s -> s.open(s.data("Data1", this.continuousData, 140, 110)));
        scenes.put(OVERVIEW + "data-table.png", s -> s.open(s.data("Data1", this.continuousData, 140, 110)));
        scenes.put(BOX + "ntad-explorer.png", s -> {
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 110);
            s.open(s.box("Data", "Data2", NtadExplorerWrapper.class, 340, 110, data));
        });

        // ---------------------------------------------------------------- Knowledge
        Scene knowledge = s -> {
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 110);
            SessionNodeWrapper box = s.box("Knowledge", "Knowledge1", KnowledgeBoxModel.class, 340, 110, data);
            KnowledgeBoxModel model = (KnowledgeBoxModel) box.getSessionNode().getModel();
            Knowledge k = new Knowledge(model.getKnowledge());
            List<String> names = this.continuousData.getVariableNames();
            for (int i = 0; i < names.size(); i++) {
                k.addToTier(i < 3 ? 0 : i < 6 ? 1 : 2, names.get(i));
            }
            model.setKnowledge(k);
            s.open(box);
        };
        scenes.put(BOX + "knowledge-box.png", knowledge);
        scenes.put(OVERVIEW + "graph-background-knowledge.png", knowledge);

        // ---------------------------------------------------------------- Search
        scenes.put(OVERVIEW + "algorithm-config-dialog.png", s -> {
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 110);
            s.open(s.search("Search1", 340, 110, false, data));
        });
        scenes.put(OVERVIEW + "algorithm-parameters.png", s -> {
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 110);
            s.open(s.search("Search1", 340, 110, false, data));
            s.then(window -> press(window, "Set Parameters"));
        });
        Scene searchResult = s -> {
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 110);
            s.open(s.search("Search1", 340, 110, true, data));
        };
        scenes.put(BOX + "search-box.png", searchResult);
        scenes.put(OVERVIEW + "results-graph.png", searchResult);
        scenes.put(OVERVIEW + "results-table.png", s -> {
            searchResult.build(s);
            s.then(window -> selectTab(window, "Edges"));
        });

        // ---------------------------------------------------------------- Latent Clusters
        scenes.put(BOX + "latent-clusters-box.png", s -> {
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 110);
            s.open(s.box("Latent_Clusters", "Latent Clusters1", LatentClustersRunner.class, 340, 110, data));
        });

        // ---------------------------------------------------------------- Graph
        Scene graph = s -> s.open(s.graph("Graph1", 140, 110));
        scenes.put(BOX + "graph-box.png", graph);
        scenes.put(OVERVIEW + "graph-editor.png", graph);
        scenes.put(BOX + "display-subgraphs.png", s -> {
            SessionNodeWrapper g = s.graph("Graph1", 140, 110);
            s.open(s.box("Graph", "Graph2", GraphSelectionWrapper.class, 340, 110, g));
        });

        // ---------------------------------------------------------------- Compare
        scenes.put(BOX + "compare-box.png", s -> {
            SessionNodeWrapper truth = s.graph("Graph1", 140, 110);
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 260);
            SessionNodeWrapper search = s.search("Search1", 340, 260, true, data);
            s.open(s.box("Compare", "Compare1", EdgewiseComparisonModel.class, 340, 110, truth, search));
        });
        scenes.put(BOX + "markov-checker.png", s -> {
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 110);
            SessionNodeWrapper search = s.search("Search1", 340, 110, true, data);
            s.open(s.box("Compare", "Compare1", MarkovCheckIndTestModel.class, 240, 260, data, search));
            s.atLeast(1025, 700);
        });

        // ---------------------------------------------------------------- Parametric Model
        scenes.put(BOX + "parametric-model-box.png", s -> s.open(s.pm(SemPmWrapper.class)));
        scenes.put(BOX + "bayes-pm.png", s -> s.open(s.pm(BayesPmWrapper.class)));
        scenes.put(BOX + "sem-pm.png", s -> s.open(s.pm(SemPmWrapper.class)));
        scenes.put(BOX + "hybrid-pm.png", s -> {
            SessionNodeWrapper g = s.graph("Graph1", 140, 110);
            SessionNodeWrapper data = s.data("Data1", this.mixedData, 140, 260);
            s.open(s.box("PM", "PM1", HybridCgPmWrapper.class, 340, 110, g, data));
        });
        scenes.put(BOX + "generalized-pm.png", s -> s.open(s.pm(GeneralizedSemPmWrapper.class)));

        // ---------------------------------------------------------------- Estimator
        Scene semEstimator = s -> {
            SessionNodeWrapper pm = s.pm(SemPmWrapper.class);
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 260);
            s.open(s.box("Estimator", "Estimator1", SemEstimatorWrapper.class, 340, 260, data, pm));
            s.atLeast(1025, 700);
        };
        scenes.put(BOX + "estimator-box.png", semEstimator);
        scenes.put(BOX + "sem-estimator.png", semEstimator);
        scenes.put(BOX + "ml-bayes-estimator.png", s -> {
            SessionNodeWrapper pm = s.pm(BayesPmWrapper.class);
            SessionNodeWrapper data = s.data("Data1", this.discreteData, 140, 260);
            s.open(s.box("Estimator", "Estimator1", BayesEstimatorWrapper.class, 340, 260, data, pm));
        });
        scenes.put(BOX + "dirichlet-estimator.png", s -> {
            SessionNodeWrapper pm = s.pm(BayesPmWrapper.class);
            SessionNodeWrapper data = s.data("Data1", this.discreteData, 140, 260);
            s.open(s.box("Estimator", "Estimator1", DirichletEstimatorWrapper.class, 340, 260, data, pm));
        });
        scenes.put(BOX + "em-bayes-estimator.png", s -> {
            SessionNodeWrapper pm = s.pm(BayesPmWrapper.class);
            SessionNodeWrapper data = s.data("Data1", this.discreteData, 140, 260);
            s.open(s.box("Estimator", "Estimator1", EmBayesEstimatorWrapper.class, 340, 260, data, pm));
        });
        scenes.put(BOX + "hybrid-cg-estimator.png", s -> {
            SessionNodeWrapper g = s.graph("Graph1", 140, 110);
            SessionNodeWrapper data = s.data("Data1", this.mixedData, 140, 260);
            SessionNodeWrapper pm = s.box("PM", "PM1", HybridCgPmWrapper.class, 340, 110, g, data);
            s.open(s.box("Estimator", "Estimator1", HybridCgEstimatorWrapper.class, 340, 260, data, pm));
        });
        scenes.put(BOX + "generalized-sem-estimator.png", s -> {
            SessionNodeWrapper pm = s.pm(GeneralizedSemPmWrapper.class);
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 260);
            s.open(s.box("Estimator", "Estimator1", GeneralizedSemEstimatorWrapper.class, 340, 260, pm, data));
        });

        // ---------------------------------------------------------------- Updater
        scenes.put(BOX + "updater-box.png", s -> {
            SessionNodeWrapper im = s.im(BayesPmWrapper.class, BayesImWrapper.class);
            s.open(s.box("Updater", "Updater1", TextBayesUpdateModel.class, 540, 260, im));
        });
        scenes.put(BOX + "sem-updater.png", s -> {
            SessionNodeWrapper im = s.im(SemPmWrapper.class, SemImWrapper.class);
            s.open(s.box("Updater", "Updater1", SemUpdaterWrapper.class, 540, 260, im));
        });

        // ---------------------------------------------------------------- Regression
        scenes.put(BOX + "regression-box.png", s -> {
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 110);
            s.open(s.box("Regression", "Regression1", RegressionRunner.class, 340, 110, data));
        });
        scenes.put(BOX + "adjustment-total-effects.png", s -> {
            SessionNodeWrapper g = s.graph("Graph1", 140, 110);
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 260);
            s.open(s.box("Regression", "Regression1", LinearAdjustmentTotalEffectsModel.class, 340, 260, data, g));
            s.then(window -> press(window, "Compute adjustment sets"), 3000);
        });
        scenes.put(BOX + "ida-check.png", s -> {
            SessionNodeWrapper data = s.data("Data1", this.continuousData, 140, 110);
            SessionNodeWrapper search = s.search("Search1", 340, 110, true, data);
            s.open(s.box("Regression", "Regression1", IdaModel.class, 240, 260, data, search));
            s.then(window -> press(window, "Run"), 3000);
        });

        // ---------------------------------------------------------------- Instantiated Model
        scenes.put(BOX + "instantiated-model-box.png", s -> s.open(s.im(SemPmWrapper.class, SemImWrapper.class)));
        scenes.put(BOX + "bayes-im.png", s -> s.open(s.im(BayesPmWrapper.class, BayesImWrapper.class)));
        scenes.put(BOX + "sem-im.png", s -> s.open(s.im(SemPmWrapper.class, SemImWrapper.class)));
        scenes.put(BOX + "hybrid-im.png", s -> {
            SessionNodeWrapper g = s.graph("Graph1", 140, 110);
            SessionNodeWrapper data = s.data("Data1", this.mixedData, 140, 260);
            SessionNodeWrapper pm = s.box("PM", "PM1", HybridCgPmWrapper.class, 340, 110, g, data);
            s.open(s.box("IM", "IM1", HybridCgImWrapper.class, 340, 260, pm));
        });
        scenes.put(BOX + "generalized-im.png",
                s -> s.open(s.im(GeneralizedSemPmWrapper.class, GeneralizedSemImWrapper.class)));

        // ---------------------------------------------------------------- Simulation
        Scene simulation = s -> s.open(s.box("Simulation", "Simulation1", Simulation.class, 140, 110));
        scenes.put(BOX + "simulation-box.png", simulation);
        scenes.put(OVERVIEW + "simulate-data-dialog.png", simulation);

        // ---------------------------------------------------------------- Note
        scenes.put(BOX + "note-box.png", s -> {
            s.open(s.box("Note", "Note1", NoteModel.class, 140, 110));
            s.naturalSize();
        });

        return scenes;
    }

    // -----------------------------------------------------------------------------------------------------------
    // Running the scenes
    // -----------------------------------------------------------------------------------------------------------

    private int run() throws Exception {
        if (!this.outDir.isDirectory() && !this.outDir.mkdirs()) {
            throw new IllegalStateException("Could not create " + this.outDir.getAbsolutePath());
        }

        makeFixtures();

        onEdt(() -> {
            openMainWindow();
            return null;
        });
        settle();

        List<String> made = new ArrayList<>();
        Map<String, String> failed = new LinkedHashMap<>();

        for (Map.Entry<String, Scene> entry : scenes().entrySet()) {
            String path = entry.getKey();
            if (!path.contains(this.filter)) {
                continue;
            }
            try {
                play(path, entry.getValue());
                made.add(path);
            } catch (Throwable t) {
                Throwable cause = t;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                failed.put(path, cause.getClass().getSimpleName() + ": " + cause.getMessage());
                t.printStackTrace();
                cleanUpAfterFailure();
            }
        }

        onEdt(() -> {
            this.frame.setVisible(false);
            this.frame.dispose();
            return null;
        });

        System.out.println();
        System.out.println("Made " + made.size() + " pictures in " + this.outDir.getAbsolutePath());
        if (!failed.isEmpty()) {
            System.out.println("FAILED: " + failed.size());
            failed.forEach((path, why) -> System.out.println("  " + path + "\n      " + why));
        }
        return failed.isEmpty() ? 0 : 1;
    }

    private void play(String path, Scene scene) throws Exception {
        // The same seed before every scene, so that a picture does not depend on which scenes ran before it.
        RandomUtil.getInstance().setSeed(SEED);

        Stage stage = new Stage();
        scene.build(stage);

        onEdt(() -> {
            show(stage.session);
            return null;
        });

        if (stage.target == null) {
            shoot(this.desktop, path);
            return;
        }

        EditorWindow window = openEditor(stage.target, stage.session);
        try {
            if (stage.roomy) {
                onEdt(() -> {
                    giveRoom(window, stage.minWidth, stage.minHeight);
                    return null;
                });
                settle();
            }
            if (stage.action != null) {
                onEdt(() -> {
                    stage.action.apply(window);
                    return null;
                });
                // An action may start work that finishes off the event thread.
                Thread.sleep(stage.pauseAfterAction);
            }
            shoot(window, path);
        } finally {
            closeEditor(window);
        }
    }

    /**
     * Many editors open at a size that suits a first look but clips their content, and a user would drag them larger.
     * This enlarges a window to at least the given size, within the desktop.
     */
    private void giveRoom(EditorWindow window, int minWidth, int minHeight) {
        final int leftMargin = 150;
        final int topMargin = 35;
        final int margin = 25;
        Dimension desk = this.desktop.getDesktopPane().getSize();
        int w = Math.min(Math.max(window.getWidth(), minWidth), desk.width - leftMargin - margin);
        int h = Math.min(Math.max(window.getHeight(), minHeight), desk.height - topMargin - margin);
        int x = Math.max(leftMargin, Math.min(window.getX(), desk.width - margin - w));
        int y = Math.max(topMargin, Math.min(window.getY(), desk.height - margin - h));
        window.setBounds(x, y, w, h);
        window.validate();
    }

    /**
     * A scene that failed may have left a dialog up or an editor open. Clears them so the next scene starts clean.
     */
    private void cleanUpAfterFailure() throws Exception {
        onEdt(() -> {
            for (Window window : Window.getWindows()) {
                if (window instanceof Dialog && window.isVisible()) {
                    System.out.println("  (closing a dialog titled \"" + ((Dialog) window).getTitle() + "\")");
                    window.dispose();
                }
            }
            for (JInternalFrame f : this.desktop.getDesktopPane().getAllFrames()) {
                if (f instanceof EditorWindow) {
                    f.setVisible(false);
                    this.desktop.getDesktopPane().remove(f);
                    f.dispose();
                }
            }
            return null;
        });
        settle();
    }

    // -----------------------------------------------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------------------------------------------

    private void makeFixtures() throws Exception {
        RandomUtil.getInstance().setSeed(SEED);
        this.dag = RandomGraph.randomGraph(8, 0, 9, 100, 100, 100, false, SEED);

        this.continuousData = new SemIm(new SemPm(this.dag)).simulateData(500, false);

        BayesPm bayesPm = new BayesPm(this.dag, 2, 3);
        this.discreteData = new MlBayesIm(bayesPm, MlBayesIm.InitializationMethod.RANDOM).simulateData(500, false);

        this.mixedData = mixedFrom(this.continuousData, 2);
    }

    /**
     * A copy of a continuous data set in which the first few variables have been cut into three categories at their
     * terciles.
     */
    private static DataSet mixedFrom(DataSet continuous, int numDiscrete) {
        int rows = continuous.getNumRows();
        int cols = continuous.getNumColumns();
        List<Node> variables = new ArrayList<>();
        for (int j = 0; j < cols; j++) {
            String name = continuous.getVariable(j).getName();
            variables.add(j < numDiscrete ? new DiscreteVariable(name, 3) : new ContinuousVariable(name));
        }
        DataSet mixed = new BoxDataSet(new VerticalDoubleDataBox(rows, cols), variables);
        for (int j = 0; j < cols; j++) {
            if (j < numDiscrete) {
                double[] sorted = new double[rows];
                for (int i = 0; i < rows; i++) {
                    sorted[i] = continuous.getDouble(i, j);
                }
                Arrays.sort(sorted);
                double low = sorted[rows / 3];
                double high = sorted[2 * rows / 3];
                for (int i = 0; i < rows; i++) {
                    double v = continuous.getDouble(i, j);
                    mixed.setInt(i, j, v < low ? 0 : v < high ? 1 : 2);
                }
            } else {
                for (int i = 0; i < rows; i++) {
                    mixed.setDouble(i, j, continuous.getDouble(i, j));
                }
            }
        }
        return mixed;
    }

    // -----------------------------------------------------------------------------------------------------------
    // Building a session
    // -----------------------------------------------------------------------------------------------------------

    /**
     * The session a scene builds, and what to do with it once it is on screen. A session is built in full before it
     * is shown, rather than box by box in a live workbench, because the palette reacts to boxes and arrows being
     * added and would be left on the Draw Edge tool.
     */
    private final class Stage {
        private final SessionWrapper session = new SessionWrapper(new Session(SESSION_NAME));
        private SessionNodeWrapper target;
        private WindowAction action;
        private long pauseAfterAction = 300;
        private boolean roomy = true;
        private int minWidth = MIN_EDITOR_WIDTH;
        private int minHeight = MIN_EDITOR_HEIGHT;

        /**
         * Adds a box holding a model of the given class, built from the given parent boxes as the application would
         * build it.
         *
         * @param boxType the box's type as it appears in tetradConfig.xml, for example "Data" or "Search".
         */
        SessionNodeWrapper box(String boxType, String name, Class<?> modelClass, int centerX, int centerY,
                               SessionNodeWrapper... parents) throws Exception {
            SessionNodeConfig config = TetradApplicationConfig.getInstance().getSessionNodeConfig(boxType);
            if (config == null) {
                throw new IllegalArgumentException("No box of type " + boxType);
            }
            SessionNode sessionNode = new SessionNode(boxType, name, config.getModels());
            SessionNodeWrapper wrapper = new SessionNodeWrapper(sessionNode);
            wrapper.setButtonType(boxType);
            wrapper.setCenter(centerX, centerY);
            this.session.addNode(wrapper);
            for (SessionNodeWrapper parent : parents) {
                this.session.addEdge(new Edge(parent, wrapper, Endpoint.TAIL, Endpoint.ARROW));
            }
            // In the application the workbench gives a box its parameter object when the box is placed.
            sessionNode.putParam(modelClass, new Parameters());
            sessionNode.createModel(modelClass, false);
            if (sessionNode.getModel() == null) {
                throw new IllegalStateException(name + " could not be given a " + modelClass.getSimpleName());
            }
            return wrapper;
        }

        SessionNodeWrapper data(String name, DataSet data, int centerX, int centerY) throws Exception {
            SessionNodeWrapper wrapper = box("Data", name, DataWrapper.class, centerX, centerY);
            ((DataWrapper) wrapper.getSessionNode().getModel()).setDataModel(data.copy());
            return wrapper;
        }

        SessionNodeWrapper graph(String name, int centerX, int centerY) throws Exception {
            SessionNodeWrapper wrapper = box("Graph", name, GraphWrapper.class, centerX, centerY);
            ((GraphWrapper) wrapper.getSessionNode().getModel())
                    .setGraph(new edu.cmu.tetrad.graph.EdgeListGraph(ManualScreenshotHarness.this.dag));
            return wrapper;
        }

        /**
         * A Search box set to FGES with the SEM BIC score.
         *
         * @param run whether to run the search, so that the box opens on its result rather than on the choice of
         *            algorithm.
         */
        SessionNodeWrapper search(String name, int centerX, int centerY, boolean run, SessionNodeWrapper... parents)
                throws Exception {
            SessionNodeWrapper wrapper = box("Search", name, GeneralAlgorithmRunner.class, centerX, centerY, parents);
            if (run) {
                GeneralAlgorithmRunner runner = (GeneralAlgorithmRunner) wrapper.getSessionNode().getModel();
                runner.setAlgorithm(new Fges(new SemBicScore()));
                runner.execute();
            }
            return wrapper;
        }

        /**
         * Graph -> Parametric Model, returning the Parametric Model box.
         */
        SessionNodeWrapper pm(Class<?> pmClass) throws Exception {
            SessionNodeWrapper g = graph("Graph1", 140, 110);
            return box("PM", "PM1", pmClass, 340, 110, g);
        }

        /**
         * Graph -> Parametric Model -> Instantiated Model, returning the Instantiated Model box.
         */
        SessionNodeWrapper im(Class<?> pmClass, Class<?> imClass) throws Exception {
            SessionNodeWrapper pm = pm(pmClass);
            return box("IM", "IM1", imClass, 540, 110, pm);
        }

        /**
         * Names the box whose editor is the picture. Without this the picture is of the whole main window.
         */
        void open(SessionNodeWrapper box) {
            this.target = box;
        }

        void then(WindowAction action) {
            this.action = action;
        }

        /**
         * As {@link #then(WindowAction)}, for an action that starts work whose result takes a moment to appear.
         */
        void then(WindowAction action, long pauseMillis) {
            this.action = action;
            this.pauseAfterAction = pauseMillis;
        }

        /**
         * Asks for a larger window than the usual minimum, for an editor with a wide toolbar or table. The desktop
         * caps it.
         */
        void atLeast(int width, int height) {
            this.minWidth = width;
            this.minHeight = height;
        }

        /**
         * Paints the editor at the size it opens at, rather than enlarging it. For small editors.
         */
        void naturalSize() {
            this.roomy = false;
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    // The main window
    // -----------------------------------------------------------------------------------------------------------

    /**
     * Builds the main window the way {@link edu.cmu.tetradapp.Tetrad} does, but at a fixed size and without the
     * listeners that record the window's size and position in the user's preferences.
     */
    private void openMainWindow() {
        this.desktop = new TetradDesktop();
        JOptionUtils.setCenteringComp(this.desktop);
        DesktopController.setReference(this.desktop);

        this.frame = new JFrame("Tetrad");
        this.frame.setContentPane(this.desktop);
        this.desktop.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.frame.pack();
        this.frame.setLocationRelativeTo(null);
        this.frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.frame.setVisible(true);

        // Editors run their work in a WatchedProcess, which looks here for the window to attach its dialog to.
        Tetrad.frame = this.frame;
    }

    /**
     * Shows a session in the main window, replacing whatever session was showing. The session always has the same
     * name, rather than the next free name in the user's session directory, so that the title does not differ from
     * run to run.
     */
    private void show(SessionWrapper session) {
        this.desktop.closeSessionByName(SESSION_NAME);
        SessionEditorWorkbench workbench = new SessionEditorWorkbench(session);
        this.desktop.addSessionEditor(new SessionEditor(SESSION_NAME, workbench));
    }

    // -----------------------------------------------------------------------------------------------------------
    // Opening and working editors
    // -----------------------------------------------------------------------------------------------------------

    private SessionEditorNode editorNodeFor(SessionNodeWrapper wrapper) {
        SessionEditorWorkbench workbench = this.desktop.getFrontmostSessionEditor().getSessionWorkbench();
        for (Component component : workbench.getComponents()) {
            if (component instanceof SessionEditorNode node
                && node.getSessionNode() == wrapper.getSessionNode()) {
                return node;
            }
        }
        throw new IllegalStateException("No box on the workbench for " + wrapper.getSessionName());
    }

    /**
     * Opens a box's editor by the same route as a double-click, and waits for the window to appear.
     */
    private EditorWindow openEditor(SessionNodeWrapper wrapper, SessionWrapper session) throws Exception {
        SessionEditorNode node = onEdt(() -> editorNodeFor(wrapper));
        onEdt(() -> {
            node.doDoubleClickAction(session);
            return null;
        });

        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            EditorWindow window = onEdt(() -> {
                for (JInternalFrame f : this.desktop.getDesktopPane().getAllFrames()) {
                    if (f instanceof EditorWindow w && w.isVisible()) {
                        return w;
                    }
                }
                return null;
            });
            if (window != null) {
                settle();
                return window;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("The editor for " + wrapper.getSessionName() + " did not open.");
    }

    private void closeEditor(EditorWindow window) throws Exception {
        onEdt(() -> {
            window.setVisible(false);
            this.desktop.getDesktopPane().remove(window);
            window.dispose();
            return null;
        });
        settle();
    }

    /**
     * Presses the first button in a window whose label contains the given text.
     */
    private static void press(Container window, String label) {
        AbstractButton button = find(window, AbstractButton.class,
                b -> b.getText() != null && b.getText().contains(label) && b.isShowing());
        if (button == null) {
            throw new IllegalStateException("No button labelled \"" + label + "\"");
        }
        button.doClick(0);
    }

    /**
     * Selects the tab with the given title, in whichever tabbed pane in the window has one.
     */
    private static void selectTab(Container window, String title) {
        JTabbedPane pane = find(window, JTabbedPane.class, p -> p.indexOfTab(title) >= 0);
        if (pane == null) {
            throw new IllegalStateException("No tab titled \"" + title + "\"");
        }
        pane.setSelectedIndex(pane.indexOfTab(title));
    }

    private static <T extends Component> T find(Container root, Class<T> type,
                                                java.util.function.Predicate<T> test) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && test.test(type.cast(child))) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T found = find(container, type, test);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------------------------------------------
    // Painting
    // -----------------------------------------------------------------------------------------------------------

    /**
     * Paints a component, as it is currently laid out, to a PNG under the output directory.
     */
    private void shoot(Component component, String relativePath) throws Exception {
        settle();
        BufferedImage image = onEdt(() -> {
            int w = component.getWidth();
            int h = component.getHeight();
            if (w <= 0 || h <= 0) {
                throw new IllegalStateException("Nothing to paint for " + relativePath);
            }
            BufferedImage img = new BufferedImage((int) Math.ceil(w * SCALE), (int) Math.ceil(h * SCALE),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.scale(SCALE, SCALE);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            component.paint(g);
            g.dispose();
            return img;
        });

        File file = new File(this.outDir, relativePath);
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create " + parent.getAbsolutePath());
        }
        ImageIO.write(image, "png", file);
        System.out.println("wrote " + relativePath + "  (" + image.getWidth() + " x " + image.getHeight() + ")");
    }

    // -----------------------------------------------------------------------------------------------------------
    // Threading
    // -----------------------------------------------------------------------------------------------------------

    /**
     * Runs a task on the event dispatch thread and returns its result, rethrowing whatever it throws.
     */
    private static <T> T onEdt(Callable<T> task) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    /**
     * Lets queued layout and repaint work finish before the next step looks at the screen.
     */
    private static void settle() throws Exception {
        for (int i = 0; i < 3; i++) {
            SwingUtilities.invokeAndWait(() -> {
            });
            Thread.sleep(150);
        }
    }
}
