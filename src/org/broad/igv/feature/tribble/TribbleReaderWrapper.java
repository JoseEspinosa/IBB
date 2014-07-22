package org.broad.igv.feature.tribble;

import htsjdk.tribble.CloseableTribbleIterator;
import htsjdk.tribble.Feature;
import htsjdk.tribble.FeatureReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author jrobinso
 *         Date: 5/8/13
 *         Time: 11:02 AM
 */
public class TribbleReaderWrapper  implements IGVFeatureReader {


    FeatureReader<Feature> wrappedReader;

    public TribbleReaderWrapper(FeatureReader<Feature> wrappedReader) {
        this.wrappedReader = wrappedReader;
    }

    @Override
    public Iterator<Feature> query(String chr, int start, int end) throws IOException {

        // Tribble iterators must be closed, so we need to copy the features and insure closure before exiting.
        CloseableTribbleIterator<Feature> iter = null;
        try {
            iter = wrappedReader.query(chr, start, end);
            List<Feature> featureList = new ArrayList<Feature>();
            while (iter.hasNext()) {
                Feature f = iter.next();
                if (f.getStart() > end) {
                    break;
                } else if (f.getEnd() < start) {
                    continue;
                } else {
                    featureList.add(f);
                }
            }
            return featureList.iterator();
        } finally {
            if(iter != null) iter.close();
        }
    }

    @Override
    public Iterator<Feature> iterator() throws IOException {
        // Note: Technically this is a file handle leak as the "close" method of the tribble iterator is not called.
        // In practice this is not a problem as the iterator() method is only called by batch programs transversing
        // the entire file.   It is none-the-less a file handle leak that should be addressed at some point.
        return wrappedReader.iterator();
    }

    @Override
    public List<String> getSequenceNames() {
        return wrappedReader.getSequenceNames();
    }

    @Override
    public Object getHeader() {
        return wrappedReader.getHeader();
    }
}
