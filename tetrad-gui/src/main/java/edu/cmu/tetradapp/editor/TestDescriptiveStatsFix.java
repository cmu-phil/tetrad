package edu.cmu.tetradapp.editor;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;


import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless regression test for the Descriptive Statistics fixes. Builds an
 * Auto-MPG-like dataset: continuous columns with NaN missing values, a
 * discrete integer-category column with -99 missing values, a discrete
 * string-category column (car names), and a constant column.
 */
public class TestDescriptiveStatsFix {

    public static void main(String[] args) throws Exception {
        int n = 398;

        // Variables.
        List<Node> vars = new ArrayList<>();
        ContinuousVariable mpg = new ContinuousVariable("mpg");
        ContinuousVariable horsepower = new ContinuousVariable("horsepower"); // has 6 NaNs
        ContinuousVariable constant = new ContinuousVariable("constant");     // constant column
        DiscreteVariable cylinders = new DiscreteVariable("cylinders",
                List.of("3", "4", "5", "6", "8"));                            // has -99s
        DiscreteVariable carName = new DiscreteVariable("car_name",
                List.of("chevrolet", "buick", "plymouth"));                   // non-numeric categories
        vars.add(mpg);
        vars.add(horsepower);
        vars.add(constant);
        vars.add(cylinders);
        vars.add(carName);

        DataSet dataSet = new BoxDataSet(new MixedDataBox(vars, n), vars);

        java.util.Random rng = new java.util.Random(42);

        for (int i = 0; i < n; i++) {
            dataSet.setDouble(i, 0, 10 + 30 * rng.nextDouble());
            dataSet.setDouble(i, 1, i < 6 ? Double.NaN : 40 + 190 * rng.nextDouble());
            dataSet.setDouble(i, 2, 1.0);
            dataSet.setInt(i, 3, i < 4 ? DiscreteVariable.MISSING_VALUE : rng.nextInt(5));
            dataSet.setInt(i, 4, rng.nextInt(3));
        }

        System.out.println("Mixed dataset: " + dataSet.getNumRows() + " x " + dataSet.getNumColumns()
                + ", isContinuous=" + dataSet.isContinuous());

        // 1. Model construction (this is what crashed with AIOOBE / NFE before).
        DescriptiveStatsModel model = new DescriptiveStatsModel(dataSet);
        System.out.println("Model constructed OK.");

        // 2. Render every cell the way JTable would (this is what crashed with
        //    IOOBE on ragged rows before).
        int rows = model.getRowCount();
        int cols = model.getColumnCount();
        System.out.println("Table: " + rows + " rows x " + cols + " cols");

        StringBuilder header = new StringBuilder();
        for (int c = 0; c < cols; c++) header.append(model.getColumnName(c)).append("\t");
        System.out.println(header);

        for (int r = 0; r < rows; r++) {
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                line.append(model.getValueAt(r, c)).append("\t");
            }
            System.out.println(line);
        }

        // 3. Sanity checks.
        check(cols == 20, "uniform column count (20) for mixed data, got " + cols);

        int missingCol = findColumn(model, "Missing");
        int meanCol = findColumn(model, "Mean");
        int minCol = findColumn(model, "Minimum");
        int maxCol = findColumn(model, "Maximum");
        int ks15Col = findColumn(model, "KS.15");
        int ks1Col = findColumn(model, "KS.1");
        int seCol = findColumn(model, "SE_Mean");
        int skewCol = findColumn(model, "Skewness");

        int hpRow = findRow(model, "horsepower");
        int cylRow = findRow(model, "cylinders");
        int nameRow = findRow(model, "car_name");
        int constRow = findRow(model, "constant");

        check("6".equals(String.valueOf(model.getValueAt(hpRow, missingCol))),
                "horsepower Missing == 6");
        check("4".equals(String.valueOf(model.getValueAt(cylRow, missingCol))),
                "cylinders Missing == 4");
        check(!"-".equals(String.valueOf(model.getValueAt(hpRow, meanCol))),
                "horsepower mean computed on observed values");
        check(!"-".equals(String.valueOf(model.getValueAt(cylRow, meanCol))),
                "cylinders mean computed despite -99s");
        check("-".equals(String.valueOf(model.getValueAt(nameRow, meanCol))),
                "car_name (non-numeric categories) shows '-' for mean");
        check("-".equals(String.valueOf(model.getValueAt(cylRow, minCol))),
                "discrete rows show '-' in continuous-only columns");
        check("-".equals(String.valueOf(model.getValueAt(constRow, ks15Col))),
                "constant column KS shows '-' rather than throwing");
        check(!String.valueOf(model.getValueAt(hpRow, seCol))
                        .equals(String.valueOf(model.getValueAt(hpRow, skewCol))),
                "SE_Mean is no longer a copy of Skewness");
        // Min/max on horsepower must be finite (NaNs previously polluted sort order).
        double min = parse(model.getValueAt(hpRow, minCol));
        double max = parse(model.getValueAt(hpRow, maxCol));
        check(min >= 40 && min < 60 && max > 210 && max <= 230,
                "horsepower min/max computed from observed values: " + min + ", " + max);
        check(ks15Col + 1 == ks1Col, "KS.15 and KS.1 are distinct adjacent columns");

        // 4. All-continuous variant with missing values (the exact crash scenario
        //    when Auto MPG is loaded as continuous).
        List<Node> cVars = new ArrayList<>(List.of(new ContinuousVariable("mpg"),
                new ContinuousVariable("horsepower")));
        DataSet cData = new BoxDataSet(new DoubleDataBox(n, 2), cVars);
        for (int i = 0; i < n; i++) {
            cData.setDouble(i, 0, 10 + 30 * rng.nextDouble());
            cData.setDouble(i, 1, i < 6 ? Double.NaN : 40 + 190 * rng.nextDouble());
        }
        DescriptiveStatsModel cModel = new DescriptiveStatsModel(cData);
        for (int r = 0; r < cModel.getRowCount(); r++)
            for (int c = 0; c < cModel.getColumnCount(); c++)
                cModel.getValueAt(r, c);
        System.out.println("All-continuous-with-missing model constructed and rendered OK.");

        // 5. Direct check of the KS statistic denominator: on a clean N(0,1)
        //    sample, D should be small; with missing values it should still be
        //    small (previously the fixed code path couldn't even run).
        Method ks = NormalityTests.class
                .getDeclaredMethod("kolmogorovSmirnov", DataSet.class, ContinuousVariable.class);
        ks.setAccessible(true);
        List<Node> gVars = new ArrayList<>(List.of(new ContinuousVariable("g")));
        DataSet gData = new BoxDataSet(new DoubleDataBox(1000, 1), gVars);
        for (int i = 0; i < 1000; i++)
            gData.setDouble(i, 0, i < 50 ? Double.NaN : rng.nextGaussian());
        double[] res = (double[]) ks.invoke(null, gData, gVars.get(0));
        check(res[0] > 0 && res[0] < 0.05, "KS D on N(0,1) with 50 NaNs is sane: " + res[0]);

        System.out.println("\nALL CHECKS PASSED");
    }

    private static double parse(Object o) {
        return Double.parseDouble(String.valueOf(o).replace(",", ""));
    }

    private static int findColumn(DescriptiveStatsModel m, String name) {
        for (int c = 0; c < m.getColumnCount(); c++)
            if (name.equals(m.getColumnName(c))) return c;
        throw new IllegalStateException("No column " + name);
    }

    private static int findRow(DescriptiveStatsModel m, String var) {
        for (int r = 0; r < m.getRowCount(); r++)
            if (var.equals(m.getValueAt(r, 0))) return r;
        throw new IllegalStateException("No row " + var);
    }

    private static void check(boolean b, String msg) {
        if (!b) throw new AssertionError("FAILED: " + msg);
        System.out.println("ok: " + msg);
    }
}
