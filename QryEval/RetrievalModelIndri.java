/**
 *  Copyright (c) 2022, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the Indri best match
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {
    public double lambda;
    public int miu;
    public RetrievalModelIndri(int miu, double lambda){
        // constructor to create BM25 model with parameters
        this.miu = miu;
        this.lambda = lambda;
    }
    public String defaultQrySopName () {
        return new String ("#and");
    }

}
