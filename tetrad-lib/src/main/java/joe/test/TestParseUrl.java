package joe.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.pitt.dbmi.data.Dataset;
import org.junit.Test;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class TestParseUrl {



    @Test
    public void test1() {
//        URL url;
        InputStream is = null;
        BufferedReader br;
        String line;

        try {
            String[] coins = {
                    "bitcoin",
                    "ethereum",
                    "ripple",
                    "bitcoin-cash",
                    "cardano",
                    "litecoin",
                    "monero"
            };

            for (String coin : coins) {
                URL url = new URL("https://coinmarketcap.com/currencies/" + coin + "/historical-data/?start=20170821&end=20180121");
//                url = new URL(_url);
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

                            if (line.contains("-")){
                                fullRecord = false;
                                continue;
                            }

                            line = line.split("<")[1];
                            line = line.split(">")[1];
                            line = line.replaceAll(",", "");
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
                        "/Users/user/Downloads/" + coin)), '\t');
            }
        } catch (MalformedURLException mue) {
            mue.printStackTrace();
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
}
