/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Params;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * May 10, 2019 2:31:48 PM
 *
 * @author Zhou Yuan <zhy19@pitt.edu>
 */
public class TestParamDescriptions {
    @Test
    public void testUndocumentedParams() {
        Set<String> allParams = Params.getParameters();
        
        List<String> undocumentedParams = new ArrayList<>();
        
        for (String param : allParams) {
            if (ParamDescriptions.getInstance().get(param) == null) {
                System.out.println("Undocumented parameter found in HTML manual: " + param);
                undocumentedParams.add(param);
            }
        }

        assertEquals(0, undocumentedParams.size());
    }
    
    @Test
    public void testUnsupportedParamsValueType() {
        List<String> paramsWithUnsupportedValueType = ParamDescriptions.getInstance().getParamsWithUnsupportedValueType();
        
        paramsWithUnsupportedValueType.forEach(e->{
            System.out.println("Unsupported parameter value type found in HTML manual for: " + e);
        });
        
        // Require all params in edu.cmu.tetrad.util.Params have value type specified in HTML manual
        // Except the system paramters, like printStream
        assertEquals(0, paramsWithUnsupportedValueType.size());
    }
}
