package org.broad.igv.hic.data;

import org.apache.commons.math.stat.StatUtils;
import org.broad.igv.hic.HiC;

import org.broad.igv.feature.Chromosome;
import org.broad.igv.hic.matrix.BasicMatrix;
import org.broad.igv.hic.matrix.DiskResidentBlockMatrix;
import org.broad.igv.hic.matrix.DiskResidentRowMatrix;
import org.broad.igv.hic.matrix.InMemoryMatrix;
import org.broad.igv.hic.tools.HiCTools;
import org.broad.igv.hic.tools.NormalizationCalculations;
import org.broad.igv.util.ParsingUtils;
import org.broad.igv.util.collections.DoubleArrayList;
import org.broad.igv.util.collections.DownsampledDoubleArrayList;
import org.broad.igv.util.collections.FloatArrayList;
import org.broad.igv.util.collections.IntArrayList;
import org.broad.tribble.util.LittleEndianInputStream;
import org.broad.tribble.util.LittleEndianOutputStream;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;

/**
 * @author jrobinso
 *         Date: 6/29/12
 *         Time: 2:27 PM
 */
public class ScratchPad {

    public static void main(String[] args) throws IOException {
        String path="/Users/nchernia/Documents/MATLAB/chr1_small.txt";
        //String path = "/Users/jrobinso/projects/hic/bin_chr14_1M.bin";
        //String path = "/Users/jrobinso/projects/hic/chr14_5e3_N17658_output 2.bin";
        //String path = "/Users/jrobinso/projects/hic/pearsons_14__14_100000.bin";
        //createBlockIndexedFile(f, null, 50);
        //BasicMatrix bm = readPearsons(path);
        // System.out.println(bm.getColumnDimension());
        //readPearsons(path);
        //writeHeader("/Users/jrobinso/projects/hic/header.bin");
        //testReadPy();

        //JFileChooser fc = new JFileChooser("/Users/neva/Downloads/fragmaps_30");
        //int retVal = fc.showOpenDialog(null);
        //if (retVal == JFileChooser.APPROVE_OPTION) {

        File file = new File(path);

        int totSize=2600;
        SparseSymmetricMatrix matrix= new SparseSymmetricMatrix(totSize);
        readSparse(file, matrix);


        float[] crossings  = new float[totSize];
        float[] crossings_right = new float[totSize];
        int length_cutoff=20;
        int preferred_window=500;
        int gap=5;
        int smooth_span=5;

        SparseSymmetricMatrix L = new SparseSymmetricMatrix(totSize);
        for (int i=10; i<=totSize; i++) {
            int window =  totSize-i-gap < i-gap?totSize-i-gap:i-gap;
            if (window > preferred_window) window = preferred_window;
            float[] thisRow = matrix.getRow(i);
            float[] A = new float[gap+window+1];

            int count=0;
            for (int j=i-gap; j>= i-window; j--) {
                A[count++] = thisRow[j];
            }
            count = 0;
            for (int k=i+gap; k<= i+window; i++) {
                float preference = (A[count] - thisRow[k]) / (A[count] + thisRow[k]);
                L.set(i, k, preference);
            }
        }


        int window=500;


        // dynamic programming.  what we want is to take a matrix M and create a matrix S
        // such that S(i,j) = -1 * mean of the sign of the upper + mean of sign of lower.
        // for entry 0,0 - no triangles.
        // perhaps best way to step is from diagonal
        // 1 step away: (entry 6).  a=2, b=3.  l=1;  l_half = 0.  L(2, 2:3) = 5 6; 8.  consider my sign and the sign of
        // those to my right and down.  (not the mean?)  for each diagonal, know the count. one pass for the sum, next pass for the count.
        // my value is sum of signs.  similarly for the bottom triangle.
        //
        //   1 2 3 4
        //     5 6 7
        //       8 9
        //         10
        for (int a=0; a <totSize; a++) {

            int endPt = (totSize +a)/2;
            if (endPt > a+window) endPt = a+window;
            for (int b=a; b <=endPt; b++) {
               int l=b-a;
               int l_half = l/2;

               // from L, we want the upper triangle - 2 3 4 5 6 7 8;   4 5 6 7 8;       6 7 8;    8
                // B is -1 * mean of the sign of upper + mean of the sign of the lower
                // B1 is the variance of upper matrix + variance of lower matrix   -- E[x^2] - E[x]^2
                // B2 is mean of lower - mean of upper - mean middle


       // upper_matrix=(L(a:a+l_half,a:a+l)); %rectangular matrix, need the upper triangle of it (can't use triu in this case)
       // lower_matrix=(L(a+l_half:b,b:b+l));
       // middle_matrix=L(a:b,a:b);

      //  I=(1:size(upper_matrix,1))'; %these are matrices which hold the index of each entry in the upper and lower matrices
      //  J=1:size(upper_matrix,2);
      //  II= (1:size(middle_matrix,1))'; %these matrices hold the row (II) and col (JJ) index of the middle matrix
      //  JJ= (1:size(middle_matrix,2));

        //upper_inds=find(repmat(2*I,1,length(J))<=repmat(J,length(I),1)); %these are the indices to get the upper triangle from the upper matrix
         //       lower_inds=find(repmat(2*I,1,length(J))>(repmat(J,length(I),1)+1));
       // middle_inds=find(repmat(2*II,1,length(JJ))>(repmat(JJ,length(II),1)+1)& repmat(II,1,length(JJ))<(repmat(JJ,length(II),1)+1))    ;
       // alpha=mean(upper_matrix(upper_inds)); %takes the mean of the upper triangle matrix - should be large and negative for blocks
        //        beta=mean(lower_matrix(lower_inds)); %of lower - should be large and positive for bloks
        //        gamma=mean(middle_matrix(middle_inds)); %of middle- this should be small for blocks

        //B(a,b)=-1*sum(sign(upper_matrix(upper_inds)))/length(upper_inds)+sum(sign(lower_matrix(lower_inds)))/length(lower_inds); %average sign in upper and lower, should all be pos for lower, all neg for upper
       // B1(a,b)=var(upper_matrix(upper_inds))+var(lower_matrix(lower_inds)); %want small variance
       // B2(a,b)=beta-alpha-gamma; %want beta large and alpha large and negative

                //

            }
        }
        /*
        %%
        %find peaks using a threshold

                Bnew=B2+B-B1; %the block score matrix
        thresh=1.7;
        [peaks_a, peaks_b]=ind2sub(size(Bnew),find(Bnew>thresh));
        vals=Bnew(Bnew>thresh);


        results=[peaks_a,peaks_b];

        results=sort(results,2);

            */



        PrintWriter pw = new PrintWriter("/Users/nchernia/Documents/MATLAB/output.txt");
        L.print(pw);

    }

    public static void readSparse(File file, SparseSymmetricMatrix A) throws IOException {

        Scanner scanner = new Scanner(file);
        String line;
        while (scanner.hasNextLine()) {
            line = scanner.nextLine();
            StringTokenizer tokenizer = new StringTokenizer(line);
            if (tokenizer.countTokens() != 3) {
                System.err.println("Corrupt line in file " + file.getName() + ": " + line);
                System.exit(1);
            }
            int x = Integer.valueOf(tokenizer.nextToken());
            int y = Integer.valueOf(tokenizer.nextToken());
            float value = Float.valueOf(tokenizer.nextToken());
            A.set(x,y,value);

        }
        scanner.close();


        System.out.print("Done reading ");

    }




    public static void writeHeader(String path) throws IOException {

        File f = new File(path);

        FileOutputStream fos = new FileOutputStream(f);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        LittleEndianOutputStream los = new LittleEndianOutputStream(bos);

        // Magic number - 4 bytes
        los.writeByte('h');
        los.writeByte('i');
        los.writeByte('c');
        los.writeByte(0);

        // Version number
        los.writeInt(1);

        // Genome --
        los.writeString("hg19");

        // Chromosomes
        los.writeString("14");
        los.writeString("14");

        // Resolution (bin size)
        los.writeInt(5000);

        // Statistics, other attributes
        los.writeFloat(-0.004103539418429137f);
        los.writeFloat(0.03536746241152287f);
        los.writeInt(21458);  // # rows, assuming square matrix

        los.close();
        bos.close();
        fos.close();

    }

    public static BasicMatrix readPearsons(String path) throws IOException {

        // Peak at file to determine version
        BufferedInputStream bis = null;
        int magic;
        int version;
        try {
            InputStream is = ParsingUtils.openInputStream(path);
            bis = new BufferedInputStream(is);
            LittleEndianInputStream les = new LittleEndianInputStream(bis);

            magic = les.readInt();

            if (magic == 6515048) {
                version = les.readInt();
            } else {
                throw new RuntimeException("Unsupported format: " + path);
            }
        } finally {
            if (bis != null)
                bis.close();
        }

        if (version == 1) {
            return new DiskResidentRowMatrix(path);

        } else {
            return new DiskResidentBlockMatrix(path);
        }


    }




    static class SparseSymmetricMatrix {

        IntArrayList rows1 = null;
        IntArrayList cols1 = null;
        FloatArrayList values1 = null;
        IntArrayList rows2 = null;
        IntArrayList cols2 = null;
        FloatArrayList values2 = null;
        int totSize;


        public SparseSymmetricMatrix(int totSize) {
            rows1 = new IntArrayList();
            cols1 = new IntArrayList();
            values1 = new FloatArrayList();
            this.totSize = totSize;
        }

        public void set(int row, int col, float v) {

            if (!Float.isNaN(v)) {
                if (rows2 == null) {
                    try {
                        rows1.add(row);
                        cols1.add(col);
                        values1.add(v);
                    }
                    catch (NegativeArraySizeException error) {
                        rows2 = new IntArrayList();
                        cols2 = new IntArrayList();
                        values2 = new FloatArrayList();
                        rows2.add(row);
                        cols2.add(col);
                        values2.add(v);
                    }
                }
                else {
                    rows2.add(row);
                    cols2.add(col);
                    values2.add(v);
                }
            }
        }

        public float[] getRow(int rowNum) {

            float[] result = new float[totSize];

            int size = rows1.size();
            for (int i=0; i<size; i++) {
                if (rows1.get(i) == rowNum) result[cols1.get(i)] = values1.get(i);
            }
            if (rows2 != null) {
                size = rows2.size();
                for (int i=0; i<size; i++) {
                    if (rows2.get(i) == rowNum) result[cols2.get(i)] = values2.get(i);
                }
            }
            return result;

        }



        public double[] multiply(double[] vector) {

            double[] result = new double[vector.length];
            Arrays.fill(result, 0);

            int[] rowArray1 = rows1.toArray();
            int[] colArray1 = cols1.toArray();
            float[] valueArray1 = values1.toArray();

            int n = rowArray1.length;
            for (int i = 0; i < n; i++) {
                int row = rowArray1[i];
                int col = colArray1[i];
                float value = valueArray1[i];
                result[row] += vector[col] * value;

                if (row != col) {
                    result[col] += vector[row] * value;
                }
            }
            if (rows2 != null) {
                int[] rowArray2 = rows2.toArray();
                int[] colArray2 = cols2.toArray();
                float[] valueArray2 = values2.toArray();
                int n2 = rowArray2.length;
                for (int j = 0; j < n2; j++) {
                    int row = rowArray2[j];
                    int col = colArray2[j];
                    float value = valueArray2[j];
                    result[row] += vector[col] * value;

                    if (row != col) {
                        result[col] += vector[row] * value;
                    }
                }
            }

            return result;
        }

        public void print() {
            print(new PrintWriter(System.out));
        }

        public void print(PrintWriter pw) {
            for (int i=0; i<totSize; i++) {
                float[] row = getRow(i);
                for (int j=0; j<totSize; j++) {
                    pw.print(row[j] + " ");
                }
                pw.println();
            }
            pw.close();
        }

    }
}
