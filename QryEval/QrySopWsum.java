import java.io.*;

/**
 *  The WSUM operator for all retrieval models.
 */
public class QrySopWsum extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
        if ((r instanceof RetrievalModelIndri) | (r instanceof RetrievalModelBM25)) {
            // for best-match algo, we use min to capture docs with some terms but not all
            return this.docIteratorHasMatchMin (r);
        }
        else ////Q should we through exception instead?
            return this.docIteratorHasMatchAll (r);
    }
    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore (RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri (r);
        }

        else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    public double getDefaultScore (RetrievalModel r, long docid) throws IOException {
        if (r instanceof RetrievalModelIndri){
            // get the length of this query
            double score = 0.0;
            // iterate through each term
            for (int i=0; i<this.args.size(); i++) {
                QrySop q_i = (QrySop) this.args.get(i);
                double weight = q_i.getWeight();
                score += weight * q_i.getDefaultScore(r,docid);
            }
            return score;
        }
        else {
            throw new IllegalArgumentException
                    (r.getClass().getName() +
                            " doesn't support the default score calculation under SCORE operator.");
        }
    }

    private double getScoreIndri (RetrievalModel r) throws IOException {
        // set initial score to be 0 for summing
        double score = 0.0;
        double q_score;
        // Note that the getScoreIndri would not be called on doc with none of the query term
        // so there must be at least one Qry has matched on the docid
        int docid = this.docIteratorGetMatch ();
        for (int i=0; i<this.args.size(); i++) {
            QrySop q_i = (QrySop) this.args.get(i);
            if (q_i.docIteratorHasMatch (r)  && (q_i.docIteratorGetMatch () == docid)){
                q_score = q_i.getScore(r);
            }
            else{q_score = q_i.getDefaultScore(r, docid);}
            // difference here is:
            // 1. to multiply by the weight
            // 2. sum the scores rather than multiply
            // 3. no power of 1/|q|
            score += q_i.getWeight() * q_score;
        }
        return score;
    }


}
