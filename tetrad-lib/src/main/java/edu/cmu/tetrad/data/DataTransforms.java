package edu.cmu.tetrad.data;


import cern.colt.list.DoubleArrayList;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.FastMath;

import java.rmi.MarshalledObject;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>DataTransforms class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DataTransforms {

    /**
     * Prevent instantiation.
     */
    private DataTransforms() {
    }

    /**
     * Log or unlog data
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @param a       a double
     * @param isUnlog a boolean
     * @param base    a int
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet logData(DataSet dataSet, double a, boolean isUnlog, int base) {
        Matrix data = dataSet.getDoubleData();
        Matrix X = data.like();

        for (int j = 0; j < data.getNumColumns(); j++) {
            double[] x1Orig = Arrays.copyOf(data.getColumn(j).toArray(), data.getNumRows());
            double[] x1 = Arrays.copyOf(data.getColumn(j).toArray(), data.getNumRows());

            if (dataSet.getVariable(j) instanceof DiscreteVariable) {
                X.assignColumn(j, new Vector(x1));
                continue;
            }

            for (int i = 0; i < x1.length; i++) {
                if (isUnlog) {
                    if (base == 0) {
                        x1[i] = FastMath.exp(x1Orig[i]) - a;
                    } else {
                        x1[i] = FastMath.pow(base, (x1Orig[i])) - a;
                    }
                } else {
                    double log = FastMath.log(a + x1Orig[i]);
                    if (base == 0) {
                        x1[i] = log;
                    } else {
                        x1[i] = log / FastMath.log(base);
                    }
                }
            }

            X.assignColumn(j, new Vector(x1));
        }

        return new BoxDataSet(new VerticalDoubleDataBox(X.transpose().toArray()), dataSet.getVariables());
    }

    /**
     * <p>standardizeData.</p>
     *
     * @param dataSets a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    public static List<DataSet> standardizeData(List<DataSet> dataSets) {
        List<DataSet> outList = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            Matrix data2 = standardizeData(dataSet.getDoubleData(), dataSet.getVariables());
            DataSet dataSet2 = new BoxDataSet(new VerticalDoubleDataBox(data2.transpose().toArray()), dataSet.getVariables());
            outList.add(dataSet2);
        }

        return outList;
    }

    /**
     * <p>standardizeData.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet standardizeData(DataSet dataSet) {
        List<DataSet> dataSets = Collections.singletonList(dataSet);
        List<DataSet> outList = standardizeData(dataSets);
        return outList.getFirst();
    }

    /**
     * <p>center.</p>
     *
     * @param dataList a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    public static List<DataSet> center(List<DataSet> dataList) {
        List<DataSet> dataSets = new ArrayList<>(dataList);
        List<DataSet> outList = new ArrayList<>();

        for (DataSet model : dataSets) {
            if (model == null) {
                throw new NullPointerException("Missing dataset.");
            }

            if (!(model.isContinuous())) {
                throw new IllegalArgumentException("Not a continuous data set: " + model.getName());
            }

            Matrix data2 = centerData(model.getDoubleData());
            List<Node> list = model.getVariables();
            List<Node> list2 = new ArrayList<>(list);

            DataSet dataSet2 = new BoxDataSet(new VerticalDoubleDataBox(data2.transpose().toArray()), list2);
            outList.add(dataSet2);
        }

        return outList;
    }

    /**
     * <p>discretize.</p>
     *
     * @param dataSet         a {@link edu.cmu.tetrad.data.DataSet} object
     * @param numCategories   a int
     * @param variablesCopied a boolean
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet discretize(DataSet dataSet, int numCategories, boolean variablesCopied) {
        Discretizer discretizer = new Discretizer(dataSet);
        discretizer.setVariablesCopied(variablesCopied);

        for (Node node : dataSet.getVariables()) {
//            if (dataSet.getVariable(node.getNode()) instanceof ContinuousVariable) {
            discretizer.equalIntervals(node, numCategories);
//            }
        }

        return discretizer.discretize();
    }

    /**
     * <p>convertNumericalDiscreteToContinuous.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     * @throws java.lang.NumberFormatException if any.
     */
    public static DataSet convertNumericalDiscreteToContinuous(
            DataSet dataSet) throws NumberFormatException {
        List<Node> variables = new ArrayList<>();

        for (Node variable : dataSet.getVariables()) {
            if (variable instanceof ContinuousVariable) {
                variables.add(variable);
            } else {
                variables.add(new ContinuousVariable(variable.getName()));
            }
        }

        DataSet continuousData = new BoxDataSet(new VerticalDoubleDataBox(dataSet.getNumRows(), variables.size()), variables);

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node variable = dataSet.getVariable(j);

            if (variable instanceof ContinuousVariable) {
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    continuousData.setDouble(i, j, dataSet.getDouble(i, j));
                }
            } else {
                DiscreteVariable discreteVariable = (DiscreteVariable) variable;

                boolean allNumerical = true;

                for (String cat : discreteVariable.getCategories()) {
                    try {
                        Double.parseDouble(cat);
                    } catch (NumberFormatException e) {
                        allNumerical = false;
                        break;
                    }
                }


                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    int index = dataSet.getInt(i, j);
                    String catName = discreteVariable.getCategory(index);
                    double value;

                    if (catName.equals("*")) {
                        value = Double.NaN;
                    } else {
                        if (allNumerical) {
                            value = Double.parseDouble(catName);
                        } else {
                            value = index;
                        }
                    }

                    continuousData.setDouble(i, j, value);
                }
            }
        }

        return continuousData;
    }

    /**
     * <p>concatenate.</p>
     *
     * @param dataSet1 a {@link edu.cmu.tetrad.data.DataSet} object
     * @param dataSet2 a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet concatenate(DataSet dataSet1, DataSet dataSet2) {
        List<Node> vars1 = dataSet1.getVariables();
        List<Node> vars2 = dataSet2.getVariables();
        Map<String, Integer> varMap2 = new HashMap<>();
        for (int i = 0; i < vars2.size(); i++) {
            varMap2.put(vars2.get(i).getName(), i);
        }
        int rows1 = dataSet1.getNumRows();
        int rows2 = dataSet2.getNumRows();
        int cols1 = dataSet1.getNumColumns();

        Matrix concatMatrix = new Matrix(rows1 + rows2, cols1);
        Matrix matrix1 = dataSet1.getDoubleData();
        Matrix matrix2 = dataSet2.getDoubleData();

        for (int i = 0; i < vars1.size(); i++) {
            int var2 = varMap2.get(vars1.get(i).getName());
            for (int j = 0; j < rows1; j++) {
                concatMatrix.set(j, i, matrix1.get(j, i));
            }
            for (int j = 0; j < rows2; j++) {
                concatMatrix.set(j + rows1, i, matrix2.get(j, var2));
            }
        }

        return new BoxDataSet(new VerticalDoubleDataBox(concatMatrix.transpose().toArray()), vars1);
    }

    /**
     * <p>concatenate.</p>
     *
     * @param dataSets a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet concatenate(DataSet... dataSets) {
        List<DataSet> _dataSets = new ArrayList<>();

        Collections.addAll(_dataSets, dataSets);

        return concatenate(_dataSets);
    }

    // Trying to optimize some.

    /**
     * <p>concatenate.</p>
     *
     * @param dataSets a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet concatenate(List<DataSet> dataSets) {
        int totalSampleSize = 0;

        for (DataSet dataSet : dataSets) {
            totalSampleSize += dataSet.getNumRows();
        }

        int numColumns = dataSets.get(0).getNumColumns();
        Matrix allData = new Matrix(totalSampleSize, numColumns);
        int q = 0;
        int r;

        for (DataSet dataSet : dataSets) {
            Matrix _data = dataSet.getDoubleData();
            r = _data.getNumRows();

            for (int i = 0; i < r; i++) {
                for (int j = 0; j < numColumns; j++) {
                    allData.set(q + i, j, _data.get(i, j));
                }
            }

            q += r;
        }

        return new BoxDataSet(new VerticalDoubleDataBox(allData.transpose().toArray()), dataSets.get(0).getVariables());
    }

    /**
     * <p>restrictToMeasured.</p>
     *
     * @param fullDataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet restrictToMeasured(DataSet fullDataSet) {
        List<Node> measuredVars = new ArrayList<>();
        List<Node> latentVars = new ArrayList<>();

        for (Node node : fullDataSet.getVariables()) {
            if (node.getNodeType() == NodeType.MEASURED || node.getNodeType() == NodeType.SELECTION) {
                measuredVars.add(node);
            } else {
                latentVars.add(node);
            }
        }

        return latentVars.isEmpty() ? fullDataSet : fullDataSet.subsetColumns(measuredVars);
    }

    /**
     * <p>getResamplingDataset.</p>
     *
     * @param data       a {@link edu.cmu.tetrad.data.DataSet} object
     * @param sampleSize a int
     * @return a sample without replacement with the given sample size from the given dataset.
     */
    public static DataSet getResamplingDataset(DataSet data, int sampleSize) {
        int actualSampleSize = data.getNumRows();
        int _size = sampleSize;
        if (actualSampleSize < _size) {
            _size = actualSampleSize;
        }

        List<Integer> availRows = new ArrayList<>();
        for (int i = 0; i < actualSampleSize; i++) {
            availRows.add(i);
        }

        RandomUtil.shuffle(availRows);

        List<Integer> addedRows = new ArrayList<>();
        int[] rows = new int[_size];
        for (int i = 0; i < _size; i++) {
            int row = -1;
            int index = -1;
            while (row == -1 || addedRows.contains(row)) {
                index = RandomUtil.getInstance().nextInt(availRows.size());
                row = availRows.get(index);
            }
            rows[i] = row;
            addedRows.add(row);
            availRows.remove(index);
        }

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        return new BoxDataSet(new VerticalDoubleDataBox(data.getDoubleData().getSelection(rows, cols).transpose().toArray()), data.getVariables());
    }

    /**
     * Get dataset sampled without replacement.
     *
     * @param data            original dataset
     * @param sampleSize      number of data (row)
     * @param randomGenerator random number generator
     * @return dataset
     */
    public static DataSet getResamplingDataset(DataSet data, int sampleSize, RandomGenerator randomGenerator) {
        int actualSampleSize = data.getNumRows();
        int _size = sampleSize;
        if (actualSampleSize < _size) {
            _size = actualSampleSize;
        }

        List<Integer> availRows = new ArrayList<>();
        for (int i = 0; i < actualSampleSize; i++) {
            availRows.add(i);
        }

        RandomUtil.shuffle(availRows);

        List<Integer> addedRows = new ArrayList<>();
        int[] rows = new int[_size];
        for (int i = 0; i < _size; i++) {
            int row = -1;
            int index = -1;
            while (row == -1 || addedRows.contains(row)) {
                index = randomGenerator.nextInt(availRows.size());
                row = availRows.get(index);
            }
            rows[i] = row;
            addedRows.add(row);
            availRows.remove(index);
        }

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = i;
        }

        return new BoxDataSet(new VerticalDoubleDataBox(data.getDoubleData().getSelection(rows, cols).transpose().toArray()), data.getVariables());
    }

    /**
     * <p>getBootstrapSample.</p>
     *
     * @param data       a {@link edu.cmu.tetrad.data.DataSet} object
     * @param sampleSize a int
     * @return a sample with replacement with the given sample size from the given dataset.
     */
    public static DataSet getBootstrapSample(DataSet data, int sampleSize) {
        int actualSampleSize = data.getNumRows();

        int[] rows = new int[sampleSize];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = RandomUtil.getInstance().nextInt(actualSampleSize);
        }

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(data.getDoubleData().getSelection(rows, cols).transpose().toArray()),
                data.getVariables());
        boxDataSet.setKnowledge(data.getKnowledge());
        return boxDataSet;
    }

    /**
     * Get dataset sampled with replacement.
     *
     * @param data            original dataset
     * @param sampleSize      number of data (row)
     * @param randomGenerator random number generator
     * @return dataset
     */
    public static DataSet getBootstrapSample(DataSet data, int sampleSize, RandomGenerator randomGenerator) {
        int actualSampleSize = data.getNumRows();
        int[] rows = new int[sampleSize];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = randomGenerator.nextInt(actualSampleSize);
        }

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = i;
        }

        BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(
                data.getDoubleData().getSelection(rows, cols).transpose().toArray()),
                data.getVariables());
        boxDataSet.setKnowledge(data.getKnowledge());

        return boxDataSet;
    }

    /**
     * <p>split.</p>
     *
     * @param data        a {@link edu.cmu.tetrad.data.DataSet} object
     * @param percentTest a double
     * @return a {@link java.util.List} object
     */
    public static List<DataSet> split(DataSet data, double percentTest) {
        if (percentTest <= 0 || percentTest >= 1) throw new IllegalArgumentException();

        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < data.getNumRows(); i++) rows.add(i);

        RandomUtil.shuffle(rows);

        int split = (int) (rows.size() * percentTest);

        List<Integer> rows1 = new ArrayList<>();
        List<Integer> rows2 = new ArrayList<>();

        for (int i = 0; i < split; i++) {
            rows1.add(rows.get(i));
        }

        for (int i = split; i < rows.size(); i++) {
            rows2.add(rows.get(i));
        }

        int[] _rows1 = new int[rows1.size()];
        int[] _rows2 = new int[rows2.size()];

        for (int i = 0; i < rows1.size(); i++) _rows1[i] = rows1.get(i);
        for (int i = 0; i < rows2.size(); i++) _rows2[i] = rows2.get(i);

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        BoxDataSet boxDataSet1 = new BoxDataSet(new VerticalDoubleDataBox(
                data.getDoubleData().getSelection(_rows1, cols).transpose().toArray()),
                data.getVariables());

        BoxDataSet boxDataSet2 = new BoxDataSet(new VerticalDoubleDataBox(
                data.getDoubleData().getSelection(_rows2, cols).transpose().toArray()),
                data.getVariables());

        List<DataSet> ret = new ArrayList<>();

        ret.add(boxDataSet1);
        ret.add(boxDataSet2);

        return ret;
    }

    /**
     * Subtracts the mean of each column from each datum that column.
     *
     * @param data a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet center(DataSet data) {
        DataSet _data = data.copy();

        for (int j = 0; j < _data.getNumColumns(); j++) {
            if (_data.getVariable(j) instanceof DiscreteVariable) {
                continue;
            }

            double sum = 0.0;
            int n = 0;

            for (int i = 0; i < _data.getNumRows(); i++) {
                double v = _data.getDouble(i, j);

                if (!Double.isNaN(v)) {
                    sum += v;
                    n++;
                }
            }

            double avg = sum / n;

            for (int i = 0; i < _data.getNumRows(); i++) {
                _data.setDouble(i, j, _data.getDouble(i, j) - avg);
            }
        }

        return _data;
    }

    /**
     * <p>shuffleColumns.</p>
     *
     * @param dataModel a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet shuffleColumns(DataSet dataModel) {
        String name = dataModel.getName();
        int numVariables = dataModel.getNumColumns();

        List<Integer> indicesList = new ArrayList<>();
        for (int i = 0; i < numVariables; i++) indicesList.add(i);
        RandomUtil.shuffle(indicesList);

        int[] indices = new int[numVariables];

        for (int i = 0; i < numVariables; i++) {
            indices[i] = indicesList.get(i);
        }

        DataSet dataSet = dataModel.subsetColumns(indices);
        dataSet.setName(name);
        return dataSet;
    }

    /**
     * <p>shuffleColumns2.</p>
     *
     * @param dataSets a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    public static List<DataSet> shuffleColumns2(List<DataSet> dataSets) {
        List<Node> vars = new ArrayList<>();

        List<Node> variables = dataSets.get(0).getVariables();
        RandomUtil.shuffle(variables);

        for (Node node : variables) {
            Node _node = dataSets.get(0).getVariable(node.getName());

            if (_node != null) {
                vars.add(_node);
            }
        }

        List<DataSet> ret = new ArrayList<>();

        for (DataSet m : dataSets) {
            DataSet data = m.subsetColumns(vars);
            data.setName(m.getName() + ".reordered");
            ret.add(data);
        }

        return ret;
    }

    /**
     * <p>covarianceNonparanormalDrton.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public static ICovarianceMatrix covarianceNonparanormalDrton(DataSet dataSet) {
        CovarianceMatrix covMatrix = new CovarianceMatrix(dataSet);
        Matrix data = dataSet.getDoubleData();
        int NTHREDS = Runtime.getRuntime().availableProcessors() * 10;
        final int EPOCH_COUNT = 100000;

        ExecutorService executor = Executors.newFixedThreadPool(NTHREDS);
        int runnableCount = 0;

        for (int _i = 0; _i < dataSet.getNumColumns(); _i++) {
            for (int _j = _i; _j < dataSet.getNumColumns(); _j++) {
                int i = _i;
                int j = _j;

                Runnable worker = () -> {
                    double tau = StatUtils.kendallsTau(data.getColumn(i).toArray(), data.getColumn(j).toArray());
                    covMatrix.setValue(i, j, tau);
                    covMatrix.setValue(j, i, tau);
                };

                executor.execute(worker);

                if (runnableCount < EPOCH_COUNT) {
                    runnableCount++;
//                    System.out.println(runnableCount);
                } else {
                    executor.shutdown();
                    try {
                        // Wait until all threads are finish
                        boolean b = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

                        if (b) {
                            System.out.println("Finished all threads");
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    executor = Executors.newFixedThreadPool(NTHREDS);
                    runnableCount = 0;
                }
            }
        }

        executor.shutdown();

        try {
            // Wait until all threads are finish
            boolean b = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            if (b) {
                System.out.println("Finished all threads");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return covMatrix;
    }

    /**
     * <p>getNonparanormalTransformed.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet getNonparanormalTransformed(DataSet dataSet) {
        try {
            Matrix data = dataSet.getDoubleData();
            Matrix X = data.like();
            double n = dataSet.getNumRows();
//            delta = 1.0 / (4.0 * FastMath.pow(n, 0.25) * FastMath.sqrt(FastMath.PI * FastMath.log(n)));

            NormalDistribution normalDistribution = new NormalDistribution();

            double std = Double.NaN;

            for (int j = 0; j < data.getNumColumns(); j++) {
                double[] x1Orig = Arrays.copyOf(data.getColumn(j).toArray(), data.getNumRows());
                double[] x1 = Arrays.copyOf(data.getColumn(j).toArray(), data.getNumRows());

                double a2Orig = new AndersonDarlingTest(x1).getASquaredStar();

                if (dataSet.getVariable(j) instanceof DiscreteVariable) {
                    X.assignColumn(j, new Vector(x1));
                    continue;
                }

                double std1 = StatUtils.sd(x1);
                double mu1 = StatUtils.mean(x1);
                double[] xTransformed = DataUtils.ranks(x1);

                for (int i = 0; i < xTransformed.length; i++) {
                    xTransformed[i] /= n;
                    xTransformed[i] = normalDistribution.inverseCumulativeProbability(xTransformed[i]);
                }

                if (Double.isNaN(std)) {
                    std = StatUtils.sd(x1Orig);
                }

                for (int i = 0; i < xTransformed.length; i++) {
                    xTransformed[i] *= std1;
                    xTransformed[i] += mu1;
                }

                double a2Transformed = new AndersonDarlingTest(xTransformed).getASquaredStar();

                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;

                for (double v : xTransformed) {
                    if (v > max && !Double.isInfinite(v)) {
                        max = v;
                    }

                    if (v < min && !Double.isInfinite(v)) {
                        min = v;
                    }
                }

                for (int i = 0; i < xTransformed.length; i++) {
                    if (xTransformed[i] == Double.POSITIVE_INFINITY) {
                        xTransformed[i] = max;
                    }

                    if (xTransformed[i] < Double.NEGATIVE_INFINITY) {
                        xTransformed[i] = min;
                    }
                }

                System.out.println(dataSet.getVariable(j) + ": A^2* = " + a2Orig + " transformed A^2* = " + a2Transformed);

//                if (a2Transformed < a2Orig) {
                X.assignColumn(j, new Vector(xTransformed));
//                } else {
//                    X.assignColumn(j, new Vector(x1Orig));
//                }
            }

            return new BoxDataSet(new VerticalDoubleDataBox(X.transpose().toArray()), dataSet.getVariables());
        } catch (OutOfRangeException e) {
            e.printStackTrace();
            return dataSet;
        }
    }

    /**
     * <p>removeConstantColumns.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet removeConstantColumns(DataSet dataSet) {
        int columns = dataSet.getNumColumns();
        int rows = dataSet.getNumRows();
        if (rows == 0) {
            return dataSet;
        }

        List<Integer> keepCols = new ArrayList<>();

        for (int j = 0; j < columns; j++) {
            Object previous = dataSet.getObject(0, j);
            boolean constant = true;
            for (int row = 1; row < rows; row++) {
                Object current = dataSet.getObject(row, j);
                if (!previous.equals(current)) {
                    constant = false;
                    break;
                }

                if (previous instanceof Double && current instanceof Double) {
                    double _previouw = (Double) previous;
                    double _current = (Double) current;

                    if (Double.isNaN(_previouw) && Double.isNaN(_current)) {
                        constant = false;
                        break;
                    }
                }
            }

            if (!constant) keepCols.add(j);
        }

        int[] newCols = new int[keepCols.size()];
        for (int j = 0; j < keepCols.size(); j++) newCols[j] = keepCols.get(j);

        return dataSet.subsetColumns(newCols);
    }

    /**
     * <p>getConstantColumns.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link java.util.List} object
     */
    public static List<Node> getConstantColumns(DataSet dataSet) {
        List<Node> constantColumns = new ArrayList<>();
        int rows = dataSet.getNumRows();

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Object first = dataSet.getObject(0, j);
            boolean constant = true;

            for (int row = 1; row < rows; row++) {
                Object current = dataSet.getObject(row, j);
                if (!first.equals(current)) {
                    constant = false;
                    break;
                }
            }

            if (constant) {
                constantColumns.add(dataSet.getVariable(j));
            }
        }

        return constantColumns;
    }

    /**
     * <p>removeRandomColumns.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @param aDouble a double
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet removeRandomColumns(DataSet dataSet, double aDouble) {
        int columns = dataSet.getNumColumns();
        int rows = dataSet.getNumRows();
        if (rows == 0) {
            return dataSet;
        }

        List<Integer> keepCols = new ArrayList<>();

        for (int j = 0; j < columns; j++) {
            if (RandomUtil.getInstance().nextDouble() > aDouble) {
                keepCols.add(j);
            }
        }

        int[] newCols = new int[keepCols.size()];
        for (int j = 0; j < keepCols.size(); j++) newCols[j] = keepCols.get(j);

        return dataSet.subsetColumns(newCols);
    }

    /**
     * <p>standardizeData.</p>
     *
     * @param data a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public static Matrix standardizeData(Matrix data) {
        Matrix data2 = data.copy();

        for (int j = 0; j < data2.getNumColumns(); j++) {
            double sum = 0.0;
             int count = 0;

            for (int i = 0; i < data2.getNumRows(); i++) {
                if (!Double.isNaN(data2.get(i, j))) {
                    sum += data2.get(i, j);
                    count++;
                }
            }

            double mean = sum / count;

            for (int i = 0; i < data.getNumRows(); i++) {
                if (!Double.isNaN(data2.get(i, j))) {
                    data2.set(i, j, data.get(i, j) - mean);
                }
            }

            double norm = 0.0;

            for (int i = 0; i < data.getNumRows(); i++) {
                double v = data2.get(i, j);

                if (!Double.isNaN(v)) {
                    norm += v * v;
                }
            }

            norm = FastMath.sqrt(norm / (data.getNumRows() - 1));

            for (int i = 0; i < data.getNumRows(); i++) {
                if (!Double.isNaN(data2.get(i, j))) {
                    data2.set(i, j, data2.get(i, j) / norm);
                }
            }
        }

        return data2;
    }

    public static Matrix standardizeData(Matrix data, List<Node> variables) {
        Matrix data2 = data.copy();

        for (int j = 0; j < data2.getNumColumns(); j++) {
            if (variables.get(j) instanceof DiscreteVariable) {
                continue;
            }

            double sum = 0.0;
            int count = 0;

            for (int i = 0; i < data2.getNumRows(); i++) {
                if (!Double.isNaN(data2.get(i, j))) {
                    sum += data2.get(i, j);
                    count++;
                }
            }

            double mean = sum / count;

            for (int i = 0; i < data.getNumRows(); i++) {
                if (!Double.isNaN(data2.get(i, j))) {
                    data2.set(i, j, data.get(i, j) - mean);
                }
            }

            double norm = 0.0;

            for (int i = 0; i < data.getNumRows(); i++) {
                double v = data2.get(i, j);

                if (!Double.isNaN(v)) {
                    norm += v * v;
                }
            }

            norm = FastMath.sqrt(norm / (data.getNumRows() - 1));

            for (int i = 0; i < data.getNumRows(); i++) {
                if (!Double.isNaN(data2.get(i, j))) {
                    data2.set(i, j, data2.get(i, j) / norm);
                }
            }
        }

        return data2;
    }

    /**
     * <p>standardizeData.</p>
     *
     * @param data an array of  objects
     * @return an array of  objects
     */
    public static double[] standardizeData(double[] data) {
        double[] data2 = new double[data.length];

        double sum = 0.0;

        for (double d : data) {
            sum += d;
        }

        double mean = sum / data.length;

        for (int i = 0; i < data.length; i++) {
            data2[i] = data[i] - mean;
        }

        double norm = 0.0;

        for (double v : data2) {
            norm += v * v;
        }

        norm = FastMath.sqrt(norm / (data2.length - 1));

        for (int i = 0; i < data2.length; i++) {
            data2[i] = data2[i] / norm;
        }

        return data2;
    }

    /**
     * <p>standardizeData.</p>
     *
     * @param data a {@link cern.colt.list.DoubleArrayList} object
     * @return a {@link cern.colt.list.DoubleArrayList} object
     */
    public static DoubleArrayList standardizeData(DoubleArrayList data) {
        DoubleArrayList data2 = new DoubleArrayList(data.size());

        double sum = 0.0;

        for (int i = 0; i < data.size(); i++) {
            sum += data.get(i);
        }

        double mean = sum / data.size();

        for (int i = 0; i < data.size(); i++) {
            data2.add(data.get(i) - mean);
        }

        double norm = 0.0;

        for (int i = 0; i < data2.size(); i++) {
            double v = data2.get(i);
            norm += v * v;
        }

        norm = FastMath.sqrt(norm / (data2.size() - 1));

        for (int i = 0; i < data2.size(); i++) {
            data2.set(i, data2.get(i) / norm);
        }

        return data2;
    }

    /**
     * <p>center.</p>
     *
     * @param d an array of  objects
     * @return an array of  objects
     */
    public static double[] center(double[] d) {
        double sum = 0.0;

        for (double v : d) {
            sum += v;
        }

        double mean = sum / d.length;
        double[] d2 = new double[d.length];

        for (int i = 0; i < d.length; i++) {
            d2[i] = d[i] - mean;
        }

        return d2;
    }

    /**
     * <p>centerData.</p>
     *
     * @param data a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public static Matrix centerData(Matrix data) {
        Matrix data2 = data.copy();

        for (int j = 0; j < data2.getNumColumns(); j++) {
            double sum = 0.0;

            for (int i = 0; i < data2.getNumRows(); i++) {
                sum += data2.get(i, j);
            }

            double mean = sum / data.getNumRows();

            for (int i = 0; i < data.getNumRows(); i++) {
                data2.set(i, j, data.get(i, j) - mean);
            }
        }

        return data2;
    }

    /**
     * <p>concatenate.</p>
     *
     * @param dataSets a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public static Matrix concatenate(Matrix... dataSets) {
        int totalSampleSize = 0;

        for (Matrix dataSet : dataSets) {
            totalSampleSize += dataSet.getNumRows();
        }

        int numColumns = dataSets[0].getNumColumns();
        Matrix allData = new Matrix(totalSampleSize, numColumns);
        int q = 0;
        int r;

        for (Matrix dataSet : dataSets) {
            r = dataSet.getNumRows();

            for (int i = 0; i < r; i++) {
                for (int j = 0; j < numColumns; j++) {
                    allData.set(q + i, j, dataSet.get(i, j));
                }
            }

            q += r;
        }

        return allData;
    }

    /**
     * <p>getBootstrapSample.</p>
     *
     * @param data       a {@link edu.cmu.tetrad.util.Matrix} object
     * @param sampleSize a int
     * @return a sample with replacement with the given sample size from the given dataset.
     */
    public static Matrix getBootstrapSample(Matrix data, int sampleSize) {
        int actualSampleSize = data.getNumRows();

        int[] rows = new int[sampleSize];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = RandomUtil.getInstance().nextInt(actualSampleSize);
        }

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        return data.getSelection(rows, cols);
    }

    /**
     * <p>copyColumn.</p>
     *
     * @param node   a {@link edu.cmu.tetrad.graph.Node} object
     * @param source a {@link edu.cmu.tetrad.data.DataSet} object
     * @param dest   a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static void copyColumn(Node node, DataSet source, DataSet dest) {
        int sourceColumn = source.getColumn(node);
        int destColumn = dest.getColumn(node);
        if (sourceColumn < 0) {
            throw new NullPointerException("The given node was not in the source dataset");
        }
        if (destColumn < 0) {
            throw new NullPointerException("The given node was not in the destination dataset");
        }
        int sourceRows = source.getNumRows();
        int destRows = dest.getNumRows();
        if (node instanceof ContinuousVariable) {
            for (int i = 0; i < destRows && i < sourceRows; i++) {
                dest.setDouble(i, destColumn, source.getDouble(i, sourceColumn));
            }
        } else if (node instanceof DiscreteVariable) {
            for (int i = 0; i < destRows && i < sourceRows; i++) {
                dest.setInt(i, destColumn, source.getInt(i, sourceColumn));
            }
        } else {
            throw new IllegalArgumentException("The given variable most be discrete or continuous");
        }
    }

    /**
     * Adds missing data values to cases in accordance with probabilities specified in a double array which has as many
     * elements as there are columns in the input dataset.  Hence, if the first element of the array of probabilities is
     * alpha, then the first column will contain a -99 (or other missing value code) in a given case with probability
     * alpha. This method will be useful in generating datasets which can be used to test algorithm that handle missing
     * data and/or latent variables. Author:  Frank Wimberly
     *
     * @param inData The data to which random missing data is to be added.
     * @param probs  The probability of adding missing data to each column.
     * @return The new data sets with missing data added.
     */
    public static DataSet addMissingData(
            DataSet inData, double[] probs) {
        DataSet outData;

        outData = inData.copy();

        if (probs.length != outData.getNumColumns()) {
            throw new IllegalArgumentException(
                    "Wrong number of elements in prob array");
        }

        for (double prob : probs) {
            if (prob < 0.0 || prob > 1.0) {
                throw new IllegalArgumentException("Probability out of range");
            }
        }

        for (int j = 0; j < outData.getNumColumns(); j++) {
            Node node = outData.getVariable(j);

            if (node instanceof ContinuousVariable) {
                for (int i = 0; i < outData.getNumRows(); i++) {
                    if (RandomUtil.getInstance().nextDouble() < probs[j]) {
                        outData.setDouble(i, j, Double.NaN);
                    }
                }
            } else if (node instanceof DiscreteVariable) {
                for (int i = 0; i < outData.getNumRows(); i++) {
                    if (RandomUtil.getInstance().nextDouble() < probs[j]) {
                        outData.setInt(i, j, -99);
                    }
                }
            }
        }

        return outData;
    }

    /**
     * <p>replaceMissingWithRandom.</p>
     *
     * @param inData a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet replaceMissingWithRandom(DataSet inData) {
        DataSet outData;

        try {
            outData = new MarshalledObject<>(inData).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (int j = 0; j < outData.getNumColumns(); j++) {
            Node variable = outData.getVariable(j);

            if (variable instanceof DiscreteVariable) {
                List<Integer> values = new ArrayList<>();

                for (int i = 0; i < outData.getNumRows(); i++) {
                    int value = outData.getInt(i, j);
                    if (value == -99) continue;
                    values.add(value);
                }

                Collections.sort(values);

                for (int i = 0; i < outData.getNumRows(); i++) {
                    if (outData.getInt(i, j) == -99) {
                        int value = RandomUtil.getInstance().nextInt(values.size());
                        outData.setInt(i, j, values.get(value));
                    }
                }
            } else {
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;

                for (int i = 0; i < outData.getNumRows(); i++) {
                    double value = outData.getDouble(i, j);
                    if (value < min) min = value;
                    if (value > max) max = value;
                }

                for (int i = 0; i < outData.getNumRows(); i++) {
                    double random = RandomUtil.getInstance().nextDouble();
                    outData.setDouble(i, j, min + random * (max - min));
                }
            }
        }

        return outData;
    }

    /**
     * Scales the continuous variables in the given DataSet to have values in the range [-1, 1].
     *
     * For each continuous column, the method computes the maximum of the absolute values of the minimum and maximum
     * of the column, and divides all values in that column by this maximum value. Discrete columns are not affected.
     *
     * @param dataSet The DataSet containing variables to be scaled.
     * @return A new DataSet with scaled continuous variables, while discrete variables remain unchanged.
     */
    public static DataSet scale(DataSet dataSet, double scale) {
        dataSet = dataSet.copy();

        // For each continuous column, find the min and max of the column, then max(abs(min, max)), then divide the column by that value.
        // Ignore the discrete columns.

        for (Node node : dataSet.getVariables()) {
            if (node instanceof ContinuousVariable) {
                int j = dataSet.getColumn(node);

                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    double value = dataSet.getDouble(i, j);
                    if (value < min) {
                        min = value;
                    }
                    if (value > max) {
                        max = value;
                    }
                }

//                double _max = Math.max(Math.abs(min), Math.abs(max)) * scale;

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    double value = dataSet.getDouble(i, j);
                    dataSet.setDouble(i, j, scaleToMinusOneToOne(value, min, max, scale));

//                    dataSet.setDouble(i, j, (value / _max) * scale);
                }
            }
        }

        return dataSet;
    }

    private static double scaleToMinusOneToOne(double value, double a, double b, double scale) {
        if (a == b) {
            throw new IllegalArgumentException("Lower and upper bounds must not be the same.");
        }
        return 2 * scale * (value - a) / (b - a) - scale;
    }

    public static DataSet scale(DataSet dataSet, double[] scales) {
        dataSet = dataSet.copy();

        // For each continuous column, find the min and max of the column, then max(abs(min, max)), then divide the column by that value.
        // Ignore the discrete columns.

        for (Node node : dataSet.getVariables()) {
            if (node instanceof ContinuousVariable) {
                int j = dataSet.getColumn(node);

                double scale = scales[j];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    double value = dataSet.getDouble(i, j);
                    dataSet.setDouble(i, j, value / scale);
                }
            }
        }

        return dataSet;
    }
}

