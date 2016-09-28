package edu.cmu.tetrad.session;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jdramsey on 3/14/16.
 */
public interface SimulationParamsSource {
    Map<String, String> getParamSettings();
    void setAllParamSettings(Map<String, String> paramSettings);
    Map<String,String> getAllParamSettings();
}
