package org.broad.igv.hic;

/**
 * @author jrobinso
 *         Date: 12/17/12
 *         Time: 9:16 AM
 */
public class HiCZoom {

    private HiC.Unit unit;
    private int binSize;

    public HiCZoom(HiC.Unit unit, int binSize) {
        this.unit = unit;
        this.binSize = binSize;
    }

    public HiC.Unit getUnit() {
        return unit;
    }

    public int getBinSize() {
        return binSize;
    }

    public String getKey() {
        return unit.toString() + "_" + binSize;
    }

    public String toString() {
        return getKey();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HiCZoom hiCZoom = (HiCZoom) o;

        if (binSize != hiCZoom.binSize) return false;
        if (unit != hiCZoom.unit) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = unit.hashCode();
        result = 31 * result + binSize;
        return result;
    }
}
