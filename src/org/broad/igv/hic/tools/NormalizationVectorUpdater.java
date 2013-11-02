package org.broad.igv.hic.tools;

import org.broad.igv.Globals;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.hic.HiC;
import org.broad.igv.hic.HiCZoom;
import org.broad.igv.hic.NormalizationType;
import org.broad.igv.hic.data.*;
import org.broad.igv.tdf.BufferedByteWriter;
import org.broad.igv.util.Pair;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

/**
 * Update an existing hic file with new normalization vectors (included expected value vectors)
 *
 * @author jrobinso
 *         Date: 2/8/13
 *         Time: 8:34 PM
 */
public class NormalizationVectorUpdater {


    public static void main(String[] args) throws IOException {

        String path = args[0];

        if (args.length > 1) {
            updateHicFile(path, Integer.valueOf(args[1]));
        }
        else updateHicFile(path);

    }


    public static void updateHicFile(String path) throws IOException {
        updateHicFile(path, -100);
    }

    public static void updateHicFile(String path, int genomeWideResolution) throws IOException {
        DatasetReaderV2 reader = new DatasetReaderV2(path);


        Dataset ds = reader.read();

        if (reader.getVersion() < 5) {
            throw new RuntimeException("This file is version " + reader.getVersion() +
                    ". Only versions 5 and greater are supported at this time.");
        }

        List<Chromosome> chromosomes = ds.getChromosomes();


        // chr -> frag count map.  Needed for expected value calculations
        Map<String, Integer> fragCountMap = reader.getFragCountMap();

        List<HiCZoom> resolutions = new ArrayList<HiCZoom>();
        resolutions.addAll(ds.getBpZooms());
        resolutions.addAll(ds.getFragZooms());

        // Keep track of chromosomes that fail to converge, so we don't try them at higher resolutions.
        Set<Chromosome> krBPFailedChromosomes = new HashSet<Chromosome>();
        Set<Chromosome> krFragFailedChromosomes = new HashSet<Chromosome>();

        BufferedByteWriter normVectorBuffer = new BufferedByteWriter();
        List<NormalizationVectorIndexEntry> normVectorIndex = new ArrayList<NormalizationVectorIndexEntry>();
        Map<String, NormalizationVector> normalizationVectors = new HashMap<String, NormalizationVector>();
        List<ExpectedValueCalculation> expectedValueCalculations = new ArrayList<ExpectedValueCalculation>();


        // Loop through resolutions
        for (HiCZoom zoom : resolutions) {

            // Optionally compute genome-wide normalizaton
            if (genomeWideResolution > 0 && zoom.getUnit() == HiC.Unit.BP && zoom.getBinSize() >= genomeWideResolution) {

                // do all four genome-wide normalizations
                NormalizationType[] types = {NormalizationType.GW_KR, NormalizationType.GW_VC,
                                             NormalizationType.INTER_KR, NormalizationType.INTER_VC};

                for (NormalizationType normType:types) {

                    Pair<Map<Chromosome, NormalizationVector>, ExpectedValueCalculation> wgVectors = getWGVectors(ds, zoom, normType);

                    Map<Chromosome, NormalizationVector> nvMap = wgVectors.getFirst();
                    for (Chromosome chromosome : nvMap.keySet()) {

                        NormalizationVector nv = nvMap.get(chromosome);
                        normalizationVectors.put(nv.getKey(), nv);

                        int position = normVectorBuffer.bytesWritten();
                        writeNormalizationVector(normVectorBuffer, nv.getData());

                        int sizeInBytes = normVectorBuffer.bytesWritten() - position;
                        normVectorIndex.add(new NormalizationVectorIndexEntry(
                                normType.toString(), chromosome.getIndex(), zoom.getUnit().toString(), zoom.getBinSize(), position, sizeInBytes));
                    }

                    expectedValueCalculations.add(wgVectors.getSecond());
                }

            }
            System.out.println();
            System.out.print("Calculating norms for zoom " + zoom);

            if (genomeWideResolution == 0 && zoom.getUnit() == HiC.Unit.FRAG) continue;
            Set<Chromosome> failureSet = zoom.getUnit() == HiC.Unit.FRAG ? krFragFailedChromosomes : krBPFailedChromosomes;

            Map<String, Integer> fcm = zoom.getUnit() == HiC.Unit.FRAG ? fragCountMap : null;

            ExpectedValueCalculation evVC = new ExpectedValueCalculation(chromosomes, zoom.getBinSize(), fcm, NormalizationType.VC);
            ExpectedValueCalculation evVCSqrt = new ExpectedValueCalculation(chromosomes, zoom.getBinSize(), fcm, NormalizationType.VC_SQRT);
            ExpectedValueCalculation evKR = new ExpectedValueCalculation(chromosomes, zoom.getBinSize(), fcm, NormalizationType.KR);

            // Loop through chromosomes
            for (Chromosome chr : chromosomes) {

                if (chr.getName().equals(Globals.CHR_ALL)) continue;

                Matrix matrix = ds.getMatrix(chr, chr);

                if (matrix == null) continue;
                MatrixZoomData zd = matrix.getZoomData(zoom);

                NormalizationCalculations nc = new NormalizationCalculations(zd);


                double[] vc = nc.computeVC();
                double[] vcSqrt = new double[vc.length];
                for (int i = 0; i < vc.length; i++) vcSqrt[i] = Math.sqrt(vc[i]);

                double vcFactor = nc.getSumFactor(vc);
                double vcSqrtFactor = nc.getSumFactor(vcSqrt);

                for (int i = 0; i < vc.length; i++) {
                    vc[i] = vc[i] * vcFactor;
                    vcSqrt[i] = vcSqrt[i] * vcSqrtFactor;
                }

                NormalizationVector nv = new NormalizationVector(NormalizationType.VC, chr.getIndex(), zoom.getUnit(), zoom.getBinSize(), vc);
                NormalizationVector nvSqrt = new NormalizationVector(NormalizationType.VC_SQRT, chr.getIndex(), zoom.getUnit(), zoom.getBinSize(), vcSqrt);

                normalizationVectors.put(nv.getKey(), nv);
                normalizationVectors.put(nvSqrt.getKey(), nvSqrt);

                int position = normVectorBuffer.bytesWritten();
                writeNormalizationVector(normVectorBuffer, vc);

                int sizeInBytes = normVectorBuffer.bytesWritten() - position;
                normVectorIndex.add(new NormalizationVectorIndexEntry(
                        NormalizationType.VC.toString(), chr.getIndex(), zoom.getUnit().toString(), zoom.getBinSize(), position, sizeInBytes));

                position = normVectorBuffer.bytesWritten();
                writeNormalizationVector(normVectorBuffer, vcSqrt);

                sizeInBytes = normVectorBuffer.bytesWritten() - position;
                normVectorIndex.add(new NormalizationVectorIndexEntry(
                        NormalizationType.VC_SQRT.toString(), chr.getIndex(), zoom.getUnit().toString(), zoom.getBinSize(), position, sizeInBytes));


                Iterator<ContactRecord> iter = zd.contactRecordIterator();

                final int chrIdx = chr.getIndex();
                while (iter.hasNext()) {
                    ContactRecord cr = iter.next();
                    int x = cr.getBinX();
                    int y = cr.getBinY();
                    final float counts = cr.getCounts();
                    if (isValidNormValue(vc[x]) & isValidNormValue(vc[y])) {
                        double value = counts / (vc[x] * vc[y]);
                        evVC.addDistance(chrIdx, x, y, value);
                    }

                    if (isValidNormValue(vcSqrt[x]) && isValidNormValue(vcSqrt[y])) {
                        double valueSqrt = counts / (vcSqrt[x] * vcSqrt[y]);
                        evVCSqrt.addDistance(chrIdx, x, y, valueSqrt);
                    }
                }

                // KR normalization
                if (!failureSet.contains(chr)) {
                    double[] kr = nc.computeKR();
                    if (kr == null) {
                        failureSet.add(chr);
                    } else {
                        double krFactor = nc.getSumFactor(kr);

                        for (int i = 0; i < kr.length; i++) {
                            kr[i] = kr[i] * krFactor;
                        }

                        nv = new NormalizationVector(NormalizationType.KR, chr.getIndex(), zoom.getUnit(), zoom.getBinSize(), kr);
                        normalizationVectors.put(nv.getKey(), nv);

                        position = normVectorBuffer.bytesWritten();
                        writeNormalizationVector(normVectorBuffer, kr);
                        sizeInBytes = normVectorBuffer.bytesWritten() - position;
                        final NormalizationVectorIndexEntry normalizationVectorIndexEntry1 =
                                new NormalizationVectorIndexEntry(NormalizationType.KR.toString(), chr.getIndex(), zoom.getUnit().toString(),
                                        zoom.getBinSize(), position, sizeInBytes);
                        normVectorIndex.add(normalizationVectorIndexEntry1);

                        iter = zd.contactRecordIterator();

                        while (iter.hasNext()) {
                            ContactRecord cr = iter.next();
                            int x = cr.getBinX();
                            int y = cr.getBinY();
                            double value = cr.getCounts() / (kr[x] * kr[y]);
                            evKR.addDistance(chrIdx, x, y, value);
                        }

                    }
                }
            }

            if (evVC.hasData()) {
                expectedValueCalculations.add(evVC);
            }
            if (evVCSqrt.hasData()) {
                expectedValueCalculations.add(evVCSqrt);
            }
            if (evKR.hasData()) {
                expectedValueCalculations.add(evKR);
            }
        }


        //BufferedByteWriter normSumBuffer = new BufferedByteWriter();
        //writeNormSums(chromosomes, ds, resolutions, normalizationVectors, normSumBuffer);

        int version = reader.getVersion();
        long filePosition = reader.getNormFilePosition();
        reader.close();

        update(path, version, filePosition, expectedValueCalculations, normVectorIndex,
                normVectorBuffer.getBytes());
    }

    static void writeNormSums(
            List<Chromosome> chromosomes, Dataset ds, List<HiCZoom> zooms, Map<String, NormalizationVector> normVectors,
            BufferedByteWriter buffer) throws IOException {


        List<NormalizedSum> sums = new ArrayList<NormalizedSum>();
        // Conventions:  chromosomes[0] == Chr_ALL.  Other  chromosomes in increasing order
        for (int i = 1; i < chromosomes.size(); i++) {

            // Start at i+1, don't need this for intra
            for (int j = i; j < chromosomes.size(); j++) {
                // Normalized sums (used to compute averages for expected values)

                Chromosome chr1 = chromosomes.get(i);
                Chromosome chr2 = chromosomes.get(j);
                for (HiCZoom zoom : zooms) {

                    String vcKey1 = NormalizationVector.getKey(NormalizationType.VC, chr1.getIndex(), zoom.getUnit().toString(), zoom.getBinSize());
                    NormalizationVector vcVector1 = normVectors.get(vcKey1);

                    String vcKey2 = NormalizationVector.getKey(NormalizationType.VC, chr2.getIndex(), zoom.getUnit().toString(), zoom.getBinSize());
                    NormalizationVector vcVector2 = normVectors.get(vcKey2);

                    String vcSqrtKey1 = NormalizationVector.getKey(NormalizationType.VC_SQRT, chr1.getIndex(), zoom.getUnit().toString(), zoom.getBinSize());
                    NormalizationVector vcSqrtVector1 = normVectors.get(vcSqrtKey1);

                    String vcSqrtKey2 = NormalizationVector.getKey(NormalizationType.VC_SQRT, chr2.getIndex(), zoom.getUnit().toString(), zoom.getBinSize());
                    NormalizationVector vcSqrtVector2 = normVectors.get(vcSqrtKey2);

                    String krKey1 = NormalizationVector.getKey(NormalizationType.KR, chr1.getIndex(), zoom.getUnit().toString(), zoom.getBinSize());
                    NormalizationVector krVector1 = normVectors.get(krKey1);

                    String krKey2 = NormalizationVector.getKey(NormalizationType.KR, chr2.getIndex(), zoom.getUnit().toString(), zoom.getBinSize());
                    NormalizationVector krVector2 = normVectors.get(krKey2);

                    double[] vc1 = vcVector1.getData();
                    double[] vc2 = vcVector2.getData();
                    double[] vcSqrt1 = vcSqrtVector1.getData();
                    double[] vcSqrt2 = vcSqrtVector2.getData();
                    double[] kr1 = krVector1 == null ? null : krVector1.getData();
                    double[] kr2 = krVector2 == null ? null : krVector2.getData();

                    MatrixZoomData zd2 = ds.getMatrix(chr1, chr2).getZoomData(zoom);
                    Iterator<ContactRecord> iter2 = zd2.contactRecordIterator();

                    double vcSum = 0;
                    double krSum = 0;
                    double vcSqrtSum = 0;
                    while (iter2.hasNext()) {
                        ContactRecord cr = iter2.next();
                        int x = cr.getBinX();
                        int y = cr.getBinY();
                        if (vc1 != null && vc2 != null && !Double.isNaN(vc1[x]) && !Double.isNaN(vc2[y]) &&
                                vc1[x] > 0 && vc2[y] > 0) {
                            // want total sum of matrix, not just upper triangle
                            if (x == y) {
                                vcSum += cr.getCounts() / (vc1[x] * vc2[y]);
                            } else {
                                vcSum += 2 * cr.getCounts() / (vc1[x] * vc2[y]);
                            }
                        }
                        if (vcSqrt1 != null && vcSqrt2 != null && !Double.isNaN(vcSqrt1[x]) && !Double.isNaN(vcSqrt2[y]) &&
                                vcSqrt1[x] > 0 && vcSqrt2[y] > 0) {
                            // want total sum of matrix, not just upper triangle
                            if (x == y) {
                                vcSqrtSum += cr.getCounts() / (vcSqrt1[x] * vcSqrt2[y]);
                            } else {
                                vcSqrtSum += 2 * cr.getCounts() / (vcSqrt1[x] * vcSqrt2[y]);
                            }
                        }
                        if (kr1 != null && kr2 != null && !Double.isNaN(kr1[x]) && !Double.isNaN(kr2[y]) &&
                                kr1[x] > 0 && kr2[y] > 0) {
                            // want total sum of matrix, not just upper triangle
                            if (x == y) {
                                krSum += cr.getCounts() / (kr1[x] * kr2[y]);
                            } else {
                                krSum += 2 * cr.getCounts() / (kr1[x] * kr2[y]);
                            }
                        }
                    }

                    if (vcSum > 0) {
                        sums.add(new NormalizedSum("VC", chr1.getIndex(), chr2.getIndex(), zoom.getUnit().toString(),
                                zoom.getBinSize(), vcSum));
                    }
                    if (vcSqrtSum > 0) {
                        sums.add(new NormalizedSum("VC_SQRT", chr1.getIndex(), chr2.getIndex(), zoom.getUnit().toString(),
                                zoom.getBinSize(), vcSqrtSum));
                    }

                    if (krSum > 0) {
                        sums.add(new NormalizedSum("KR", chr1.getIndex(), chr2.getIndex(), zoom.getUnit().toString(),
                                zoom.getBinSize(), krSum));

                    }
                }
            }

        }

        buffer.putInt(sums.size());
        for (NormalizedSum sum : sums) {
            buffer.putNullTerminatedString(sum.type);
            buffer.putInt(sum.chr1Idx);
            buffer.putInt(sum.chr2Idx);
            buffer.putNullTerminatedString(sum.unit);
            buffer.putInt(sum.resolution);
            buffer.putDouble(sum.value);
        }

    }


    static void update(String hicfile,
                       int version,
                       final long filePosition,
                       List<ExpectedValueCalculation> expectedValueCalculations,
                       List<NormalizationVectorIndexEntry> normVectorIndex,
                       byte[] normVectorBuffer) throws IOException {

        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(hicfile, "rw");

            if (version < 6) {
                // Update version
                // Master index
                raf.getChannel().position(4);
                BufferedByteWriter buffer = new BufferedByteWriter();
                buffer.putInt(6);
                raf.write(buffer.getBytes());
            }

            BufferedByteWriter buffer = new BufferedByteWriter();
            writeExpectedValues(buffer, expectedValueCalculations);

            byte[] evBytes = buffer.getBytes();
            raf.getChannel().position(filePosition);
            raf.write(evBytes);

            // Get the size of the index in bytes, to compute an offset for the actual entries.
            buffer = new BufferedByteWriter();
            writeNormIndex(buffer, normVectorIndex);
            long normVectorStartPosition = raf.getChannel().position() + buffer.bytesWritten();

            // Update index entries
            for (NormalizationVectorIndexEntry entry : normVectorIndex) {
                entry.position += normVectorStartPosition;
            }

            // Now write for real
            buffer = new BufferedByteWriter();
            writeNormIndex(buffer, normVectorIndex);
            raf.write(buffer.getBytes());

            // Finally the norm vectors
            raf.write(normVectorBuffer);

        } finally {
            if (raf != null) raf.close();
        }
    }

    /**
     * Compute the size of the index in bytes.  This is needed to set offsets for the actual index entries.  The
     * easiest way to do this is to write it to a buffer and check the size
     *
     * @param normVectorIndex
     * @return
     */
    private static void writeNormIndex(BufferedByteWriter buffer, List<NormalizationVectorIndexEntry> normVectorIndex) throws IOException {
        buffer.putInt(normVectorIndex.size());
        for (NormalizationVectorIndexEntry entry : normVectorIndex) {
            buffer.putNullTerminatedString(entry.type);
            buffer.putInt(entry.chrIdx);
            buffer.putNullTerminatedString(entry.unit);
            buffer.putInt(entry.resolution);
            buffer.putLong(entry.position);
            buffer.putInt(entry.sizeInBytes);
        }
    }


    private static void writeExpectedValues(BufferedByteWriter buffer, List<ExpectedValueCalculation> expectedValueCalculations) throws IOException {

        buffer.putInt(expectedValueCalculations.size());
        for (ExpectedValueCalculation ev : expectedValueCalculations) {

            ev.computeDensity();

            buffer.putNullTerminatedString(ev.getType().toString());

            int binSize = ev.getGridSize();
            String unit = ev.isFrag ? "FRAG" : "BP";

            buffer.putNullTerminatedString(unit);
            buffer.putInt(binSize);

            // The density values
            double[] expectedValues = ev.getDensityAvg();
            buffer.putInt(expectedValues.length);
            for (int i = 0; i < expectedValues.length; i++) {
                buffer.putDouble(expectedValues[i]);
            }

            // Map of chromosome index -> normalization factor
            Map<Integer, Double> normalizationFactors = ev.getChrScaleFactors();
            buffer.putInt(normalizationFactors.size());
            for (Map.Entry<Integer, Double> normFactor : normalizationFactors.entrySet()) {
                buffer.putInt(normFactor.getKey());
                buffer.putDouble(normFactor.getValue());
            }
        }
    }

    private static void writeNormalizationVector(BufferedByteWriter buffer, double[] values) throws IOException {
        buffer.putInt(values.length);
        for (int i = 0; i < values.length; i++)
            buffer.putDouble(values[i]);
    }


    /**
     * Compute the whole-genome normalization and expected value vectors and return as a pair (normalization vector first)
     */

    static Pair<Map<Chromosome, NormalizationVector>, ExpectedValueCalculation> getWGVectors(Dataset dataset,
                                                                                             HiCZoom zoom,
                                                                                             NormalizationType norm) {

        boolean includeIntra = false;
        if (norm == NormalizationType.GW_KR || norm == NormalizationType.GW_VC) {
            includeIntra = true;
        }
        final List<Chromosome> chromosomes = dataset.getChromosomes();
        final int resoslution = zoom.getBinSize();
        final ArrayList<ContactRecord> recordArrayList = createWholeGenomeRecords(dataset, chromosomes, zoom, includeIntra);

        int totalSize = 0;
        for (Chromosome c1 : chromosomes) {
            if (c1.getName().equals(Globals.CHR_ALL)) continue;
            totalSize += c1.getLength() / resoslution + 1;
        }


        NormalizationCalculations calculations = new NormalizationCalculations(recordArrayList, totalSize);
        double[] vector = calculations.getNorm(norm);


        ExpectedValueCalculation expectedValueCalculation = new ExpectedValueCalculation(chromosomes, resoslution, null, norm);
        int addY = 0;
        // Loop through chromosomes
        for (Chromosome chr : chromosomes) {

            if (chr.getName().equals(Globals.CHR_ALL)) continue;
            final int chrIdx = chr.getIndex();
            Matrix matrix = dataset.getMatrix(chr, chr);

            if (matrix == null) continue;
            MatrixZoomData zd = matrix.getZoomData(zoom);
            Iterator<ContactRecord> iter = zd.contactRecordIterator();
            while (iter.hasNext()) {
                ContactRecord cr = iter.next();
                int x = cr.getBinX();
                int y = cr.getBinY();
                final double vx = vector[x + addY];
                final double vy = vector[y + addY];
                if (isValidNormValue(vx) && isValidNormValue(vy)) {
                    double value = cr.getCounts() / (vx * vy);
                    expectedValueCalculation.addDistance(chrIdx, x, y, value);
                }
            }

            addY += chr.getLength() / resoslution + 1;
        }

        // Split normalization vector by chromosome
        Map<Chromosome, NormalizationVector> normVectorMap = new LinkedHashMap<Chromosome, NormalizationVector>();
        int location1 = 0;
        for (Chromosome c1 : chromosomes) {
            if (c1.getName().equals(Globals.CHR_ALL)) continue;
            int chrBinned = c1.getLength() / resoslution + 1;
            double[] chrNV = new double[chrBinned];
            for (int i = 0; i < chrNV.length; i++) {
                chrNV[i] = vector[location1];
                location1++;
            }
            normVectorMap.put(c1, new NormalizationVector(norm, c1.getIndex(), zoom.getUnit(), resoslution, chrNV));
        }

        return new Pair(normVectorMap, expectedValueCalculation);
    }

    private static boolean isValidNormValue(double v) {
        return v > 0 && !Double.isNaN(v);
    }


    private static ArrayList<ContactRecord> createWholeGenomeRecords(Dataset dataset, List<Chromosome> tmp, HiCZoom zoom, boolean includeIntra) {
        ArrayList<ContactRecord> recordArrayList = new ArrayList<ContactRecord>();
        int addX = 0;
        int addY = 0;
        for (Chromosome c1 : tmp) {
            if (c1.getName().equals(Globals.CHR_ALL)) continue;
            for (Chromosome c2 : tmp) {
                if (c2.getName().equals(Globals.CHR_ALL)) continue;
                if (c1.getIndex() < c2.getIndex() || (c1.equals(c2) && includeIntra)) {
                    Matrix matrix = dataset.getMatrix(c1, c2);
                    if (matrix != null) {
                        MatrixZoomData zd = matrix.getZoomData(zoom);
                        if (zd != null) {
                            Iterator<ContactRecord> iter = zd.contactRecordIterator();
                            while (iter.hasNext()) {
                                ContactRecord cr = iter.next();
                                int binX = cr.getBinX() + addX;
                                int binY = cr.getBinY() + addY;
                                recordArrayList.add(new ContactRecord(binX, binY, cr.getCounts()));
                            }
                        }
                    }
                }
                addY += c2.getLength() / zoom.getBinSize() + 1;
            }
            addX += c1.getLength() / zoom.getBinSize() + 1;
            addY = 0;
        }
        return recordArrayList;
    }


    static class NormalizationVectorIndexEntry {
        String type;
        int chrIdx;
        String unit;
        int resolution;
        long position;
        int sizeInBytes;

        NormalizationVectorIndexEntry(String type, int chrIdx, String unit, int resolution, long position, int sizeInBytes) {
            this.type = type;
            this.chrIdx = chrIdx;
            this.unit = unit;
            this.resolution = resolution;
            this.position = position;
            this.sizeInBytes = sizeInBytes;
        }

        @Override
        public String toString() {
            return type + " " + chrIdx + " " + unit + " " + resolution + " " + position + " " + sizeInBytes;
        }
    }

    static class NormalizedSum {
        String type;
        int chr1Idx;
        int chr2Idx;
        String unit;
        int resolution;
        double value;

        NormalizedSum(String type, int chr1Idx, int chr2Idx, String unit, int resolution, double value) {
            this.type = type;
            this.chr1Idx = chr1Idx;
            this.chr2Idx = chr2Idx;
            this.unit = unit;
            this.resolution = resolution;
            this.value = value;
        }
    }


}
