package edu.cmu.tetrad.latest;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.InputStream;
import java.util.Properties;

/**
 * Author : Jeremy Espino MD
 * Created  9/20/16 11:06 AM
 */
public class LatestClient {

    private static final Logger LOGGER = Logger.getLogger(LatestClient.class);

    private static LatestClient theInstance = null;
    private String latestResult;

    private LatestClient() {
    }

    public static LatestClient getInstance() {
        if (theInstance == null) {
            theInstance = new LatestClient();
        }
        return theInstance;
    }

    public String getLatestResult() {
        return latestResult;

    }

    public boolean checkLatest(String softwareName, String version) {

           LOGGER.debug("running version: " + version);

           String BASE_URL;
           final Properties applicationProperties =  new Properties();
           try (final InputStream stream =
                      this.getClass().getResourceAsStream("/tetrad-lib.properties")) {
               applicationProperties.load(stream);
               BASE_URL = applicationProperties.getProperty("latest.version.url");
           } catch (Exception e) {
               LOGGER.error("Could not read tetrad-lib.properties file", e);
               latestResult = String.format("Running version %s but unable to contact version server.", version);
               // return without checking for latest version
               return false;
           }

           try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
               HttpGet request = new HttpGet(BASE_URL + softwareName);
               request.addHeader("content-type", "application/json");
               HttpResponse result = httpClient.execute(request);
               String json = EntityUtils.toString(result.getEntity(), "UTF-8");
               LOGGER.debug("response from latest version server: " + json);


               com.google.gson.Gson gson = new com.google.gson.Gson();
               SoftwareVersion softwareVersion = gson.fromJson(json, SoftwareVersion.class);

               if (softwareVersion.getSoftwareVersion().equalsIgnoreCase(version)) {
                   latestResult = String.format("Running version %s which is the latest version.", version);
                   return true;
               } else {
                   latestResult = String.format("Running version %s but the latest version is %s available.", version, softwareVersion.getSoftwareVersion());
                   return false;
               }


           } catch (Exception ex) {

               LOGGER.error("Could not contact server for latest version", ex);
               latestResult = String.format("Running version %s but unable to contact version server.", version);
               return false;

           }

       }
}
