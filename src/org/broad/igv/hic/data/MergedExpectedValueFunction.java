package org.broad.igv.hic.data;

import org.broad.igv.hic.NormalizationType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jrobinso
 *         Date: 12/26/12
 *         Time: 9:30 PM
 */
public class MergedExpectedValueFunction implements ExpectedValueFunction {

    List<ExpectedValueFunction> densityFunctions;
    double[] expectedValues = null;

    public MergedExpectedValueFunction(ExpectedValueFunction densityFunction) {
        this.densityFunctions = new ArrayList<ExpectedValueFunction>();
        densityFunctions.add(densityFunction);
    }

    public void addDensityFunction(ExpectedValueFunction densityFunction) {
        // TODO -- verify same unit, binsize, type, denisty array size
        densityFunctions.add(densityFunction);
    }

    @Override
    public double getExpectedValue(int chrIdx, int distance) {
        double sum = 0;
        for(ExpectedValueFunction df : densityFunctions) {
            sum += df.getExpectedValue(chrIdx, distance);
        }
        return sum;
    }

    @Override
    public double[] getExpectedValues() {
        if (expectedValues != null) return expectedValues;
        int length = 0;
        for (ExpectedValueFunction df : densityFunctions) {
            if (((ExpectedValueFunctionImpl)df).getExpectedValues().length > length)
                length = ((ExpectedValueFunctionImpl)df).getExpectedValues().length;
        }
        expectedValues = new double[length];
        for (ExpectedValueFunction df : densityFunctions) {
            double[] current = ((ExpectedValueFunctionImpl)df).getExpectedValues();
            for (int i=0; i<current.length; i++) {
                expectedValues[i] += current[i];
            }
        }
        return expectedValues;
    }

    @Override
    public int getLength() {
        return densityFunctions.get(0).getLength();
    }

    @Override
    public NormalizationType getType() {
        return densityFunctions.get(0).getType();
    }

    @Override
    public String getUnit() {
        return densityFunctions.get(0).getUnit();
    }

    @Override
    public int getBinSize() {
        return densityFunctions.get(0).getBinSize();
    }
}
