package org.broad.igv.hic.data;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author jrobinso
 * @date Aug 10, 2010
 */
public class Block {

    private int number;

    List<ContactRecord> records;

    public Block(int number) {
        this.number = number;
        records = new ArrayList<ContactRecord>();
    }

    public Block(int number, List<ContactRecord> records) {
        this.number = number;
        this.records = records;
    }

    public int getNumber() {
        return number;
    }


    public Collection<ContactRecord> getContactRecords() {
        return records;
    }




}
