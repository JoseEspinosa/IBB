package org.broad.igv.hic.track;

import java.awt.*;
import java.text.NumberFormat;
import java.util.Map;

/**
 * chr1	x1	x2	chr2	y1	y2	color	observed	bl expected	donut expected	bl fdr	donut fdr
 *
 * @author jrobinso
 *         Date: 5/22/13
 *         Time: 8:51 AM
 *         <p/>
 *         Chr Chr pos pos observed expected1 expected2 fdr
 */
public class Feature2D {

    NumberFormat formatter = NumberFormat.getInstance();

    String chr1;
    int start1;
    int end1;
    String chr2;
    int start2;
    int end2;
    Color color;
    Map<String, String> attributes;


    public Feature2D(String chr1, int start1, int end1, String chr2, int start2, int end2, Color c,
                     Map<String, String> attributes) {
        this.chr1 = chr1;
        this.start1 = start1;
        this.end1 = end1;
        this.chr2 = chr2;
        this.start2 = start2;
        this.end2 = end2;
        this.color = (c == null ? Color.black : c);
        this.attributes = attributes;
    }


    public String getChr1() {
        return chr1;
    }

    public String getChr2() {
        return chr2;
    }

    public int getStart1() {
        return start1;
    }

    public int getStart2() {
        return start2;
    }

    public int getEnd1() {
        return end1;
    }

    public int getEnd2() {
        return end2;
    }

    public Color getColor() {
        return color;
    }

    public String tooltipText() {

        StringBuffer txt = new StringBuffer();
        txt.append("Feature<br>");

        txt.append(chr1 + ":" + formatter.format(start1 + 1));
        if ((end1 - start1) > 1) {
            txt.append("-" + formatter.format(end1));
        }
        txt.append("<br>");

        txt.append(chr2 + ":" + formatter.format(start2 + 1));
        if ((end2 - start2) > 1) {
            txt.append("-" + formatter.format(end2));
        }


        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            txt.append("<br>");
            txt.append(entry.getKey());
            txt.append(" = ");
            txt.append(entry.getValue());
        }

        return txt.toString();
    }

}
