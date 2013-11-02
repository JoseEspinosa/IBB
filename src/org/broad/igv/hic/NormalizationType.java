package org.broad.igv.hic;

/**
* @author jrobinso
*         Date: 8/31/13
*         Time: 9:47 PM
*/
public enum NormalizationType {
    NONE("None"),
    VC("Coverage"),
    VC_SQRT("Coverage (Sqrt)"),
    KR("Balanced"),
    GW_KR("Genome-wide balanced"),
    INTER_KR("Inter balanced"),
    GW_VC("Genome-wide coverage"),
    INTER_VC("Inter coverage"),
    LOADED("Loaded");
    private String label;

    NormalizationType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
