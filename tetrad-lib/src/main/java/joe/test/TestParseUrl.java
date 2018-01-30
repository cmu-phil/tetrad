package joe.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.TimeSeriesUtils;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.pitt.dbmi.data.Dataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.DataReader;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import org.junit.Test;

import java.io.*;
import java.net.URL;
import java.util.*;

public class TestParseUrl {

//    String[] coins = {
//            "bitcoin",
//            "ethereum",
//            "ripple",
//            "bitcoin-cash",
//            "cardano",
//            "stellar",
//            "litecoin",
//            "nem",
//            "neo",
//            "eos",
//            "iota",
//            "dash",
//            "monero",
//            "bitconnect",
//            "electroneum"
//    };

    String[] coins = {
            "bitcoin",
            "augur",
            "bancor",
            "basic-attention-token",
            "bitcoin-cash",
            "bitcoin-gold",
            "civic",
            "dash",
            "decred",
            "edgeless",
            "eos",
            "ethereum",
            "funfair",
            "golem-network-tokens",
            "rlc",
            "litecoin",
            "guppy",
            "omisego",
            "salt",
            "status",

            "ripple",
            "bitcoin-cash",
            "cardano",
            "stellar",
            "nem",
            "neo",
            "iota",
            "monero",
            "bitconnect",
            "electroneum"
    };

    @Test
    public void test1() {
        InputStream is = null;
        BufferedReader br;
        String line;

        try {

            for (String coin : coins) {
                final String spec = "https://coinmarketcap.com/currencies/" + coin + "/historical-data?start=20170128&end=20180129";
                System.out.println(spec);

                URL url = new URL(spec);
                is = url.openStream();  // throws an IOException
                br = new BufferedReader(new InputStreamReader(is));

                List<double[]> recordList = new ArrayList<>();

                while ((line = br.readLine()) != null) {
                    if (line.contains("<tr class=\"text-right\">")) {
                        for (int i = 0; i < 1; i++) {
                            br.readLine();
                        }

                        double[] record = new double[6];

                        boolean fullRecord = true;

                        for (int i = 0; i < 6; i++) {
                            line = br.readLine();

                            line = line.split("\"")[1];

                            if (line.equalsIgnoreCase("-")) {
                                fullRecord = false;
                                continue;
                            }

                            double d = Double.parseDouble(line);
                            record[i] = d;
                        }

                        if (fullRecord) {
                            recordList.add(record);
                        }
                    }
                }

                List<Node> variables = new ArrayList<>();

                variables.add(new ContinuousVariable("Open"));
                variables.add(new ContinuousVariable("High"));
                variables.add(new ContinuousVariable("Low"));
                variables.add(new ContinuousVariable("Close"));
                variables.add(new ContinuousVariable("Volume"));
                variables.add(new ContinuousVariable("MarketCap"));

                DoubleDataBox box = new DoubleDataBox(recordList.size(), 6);
                DataSet dataSet = new BoxDataSet(box, variables);

                for (int i = 0; i < recordList.size(); i++) {
                    for (int j = 0; j < 6; j++) {
                        dataSet.setDouble(recordList.size() - i - 1, j, recordList.get(i)[j]);
                    }
                }

                DataWriter.writeRectangularData(dataSet, new PrintWriter(new File(
                        "/Users/user/Downloads/coins/" + coin)), '\t');
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                if (is != null) is.close();
            } catch (IOException ioe) {
                // nothing to see here
            }
        }
    }

    @Test
    public void test2() {
        int targetIndex = 0;
        final int numLags = 4;
        final int sampleSize = 365;
        final int daysBack = 10;
        final int skipDays = 0;

        double[][][] advice = new double[daysBack][coins.length][];
        double[][] totalAdvice = new double[coins.length][2];

        for (double[] a : totalAdvice) Arrays.fill(a, 1.0);

        DataSet[] dataSets = new DataSet[coins.length];

        for (int c = 0; c < coins.length; c++) {
            try {
                DataReader reader = new ContinuousTabularDataFileReader(new File("/Users/user/Downloads/coins/" + coins[c]), Delimiter.TAB);
                DataSet dataSet = readData(reader);
                dataSets[c] = dataSet;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (int d = daysBack - 1; d >= 0; d--) {
            for (int c = 0; c < coins.length; c++) {
                final int r = dataSets[c].getNumRows() - d;
                try {
                    double[] a = adviceForInitialRecords(r, sampleSize, targetIndex, numLags, dataSets[c], skipDays);
                    advice[d][c] = a;

                    if (!Double.isNaN(a[0])) {
                        totalAdvice[c][0] *= a[0];
                    }

                    if (!Double.isNaN(a[1])) {
                        totalAdvice[c][1] *= a[1];
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        LinkedList<Integer> hold = new LinkedList<>();
        hold.add(indexOf(coins, "bitcoin"));
        hold.add(indexOf(coins, "ethereum"));
        hold.add(indexOf(coins, "edgeless"));
        hold.add(indexOf(coins, "basic-attention-token"));

        for (int _d = 0; _d < daysBack; _d++) {
            final int d = _d;
            System.out.println("\nDays back = " + _d);

            LinkedList<Integer> _c = new LinkedList<>();
            for (int c = 0; c < coins.length; c++) {

                final double[] a = advice[_d][c];

                if (a == null) continue;

                if (a[0] != 1.0) {
                    _c.add(c);
                }
            }

            _c.sort((c1, c2) -> Double.compare(advice[d][c2][0], advice[d][c1][0]));

            for (int c : _c) {
                if (advice[_d][c][0] < 2) {
                    System.out.println("Aggregate advice " + coins[c] + " Predicted = " + advice[_d][c][0] + " Actual = " + advice[_d][c][1]);
                }
            }

            if (advice[_d][hold.getLast()][0] < .95 && hold.size() > 2) {
                System.out.println("\nSELL " + coins[hold.getLast()]);
                hold.remove(hold.getLast());
            }

            if (!hold.contains(_c.getFirst()) && advice[_d][_c.getFirst()][0] < 2) {
                System.out.println("\nBUY " + coins[_c.getFirst()]);
                hold.addFirst(_c.getFirst());
            }

            System.out.println("\nHolding:\n");

            for (int c : hold) {
                System.out.println("Holding: " + coins[c] + " Predicted = " + advice[_d][c][0] + " Actual = " + advice[_d][c][1]);
            }
        }

        System.out.println();

        LinkedList<Integer> _c = new LinkedList<>();
        for (int c = 0; c < coins.length; c++) {
            final double[] doubles = totalAdvice[c];

            if (doubles == null) continue;

            if (doubles[0] != 1.0) {
                _c.add(c);
            }
        }

        _c.sort((c1, c2) -> Double.compare(totalAdvice[c2][0], totalAdvice[c1][0]));

        for (int c : _c) {
            System.out.println();
            System.out.println("Total advice " + coins[c] + ": Expected = " + totalAdvice[c][0] + " Actual = " + totalAdvice[c][1]);

            for (int d = 0; d < daysBack; d++) {
                System.out.println("\tDays back " + d + " " + coins[c] + ": Expected = " + advice[d][c][0] + " Actual = " + advice[d][c][1]);
            }
        }
    }

    private Integer indexOf(String[] coins, String coin) {
        for (int c = 0; c < coins.length; c++) {
            if (coin.equalsIgnoreCase(coins[c])) return c;
        }

        return -1;
    }

    private DataSet readData(DataReader reader) throws IOException {
        Dataset dataset = reader.readInData();
        return getDataSet(dataset);
    }

    private DataSet getDataSet(Dataset dataset) {
        return (DataSet) DataConvertUtils.toDataModel(dataset);
    }

    private double[] adviceForInitialRecords(int r, int sampleSize, int targetIndex,
                                             int numLags, DataSet dataSet,
                                             int skipDays) {

        List<Integer> _rows = new ArrayList<>();

        for (int i = Math.max(0, r - sampleSize); i < r; i++) {
            _rows.add(i);
        }

        int[] rows = new int[_rows.size()];
        for (int i = 0; i < _rows.size(); i++) rows[i] = _rows.get(i);

        DataSet subset = dataSet.subsetRows(rows);
        DataSet lagged = TimeSeriesUtils.createLagData(subset, numLags + skipDays);

        List<Node> regressors = new ArrayList<>(lagged.getVariables());
        Node target = regressors.get(targetIndex); // current.

        for (Node node : new ArrayList<>(regressors)) {
            final int lag = TimeSeriesUtils.getLag(node.getName());
            if (lag == 0) regressors.remove(node);
            if (lag <= skipDays) regressors.remove(node);
        }

        RegressionDataset regressionDataset = new RegressionDataset(lagged);

        RegressionResult result = regressionDataset.regress(target, regressors);

        double[] x = new double[lagged.getNumColumns() - 6 - 6 * skipDays];

        int i = 0;

        for (int w = 1 + skipDays; w <= numLags + skipDays; w++) {
            for (int s = 0; s < 6; s++) {
                x[i++] = subset.getDouble(subset.getNumRows() - w - skipDays, s);
            }
        }

        double predictedValue = result.getPredictedValue(x);

        final double previousValue = dataSet.getDouble(r - 1 - skipDays, targetIndex);
        final double actualValue = r > dataSet.getNumRows() - 1 ? Double.NaN : dataSet.getDouble(r, targetIndex);

        double change = (predictedValue / previousValue);
        double actualChange = (actualValue / previousValue);

        return new double[]{change, actualChange};
    }
}
