/**
 *  Copyright (c) 2022, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the BM25 best match
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {
    public double k1;
    public double b;
    public double k3;
    public RetrievalModelBM25(double k1, double b, double k3){
        // constructor to create BM25 model with parameters
        this.k1 = k1;
        this.b = b;
        this.k3 = k3;
    }
    public String defaultQrySopName () {
        return new String ("#sum");
    }

}
