import java.io.*;

/**
 *  The Sum operator for BM25 retrieval models.
 */
public class QrySopSum extends QrySop {

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
        else
            return this.docIteratorHasMatchAll (r);
    }
    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore (RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25 (r);
        }

        else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    public double getDefaultScore (RetrievalModel r, long docid) throws IOException {
        if (r instanceof RetrievalModelIndri){
            // we don't really need to implement #SUM for Indri
            return 0.0;
        }
        else {
            throw new IllegalArgumentException
                    (r.getClass().getName() +
                            " doesn't support the default score calculation under SCORE operator.");
        }
    }

    private double getScoreBM25 (RetrievalModel r) throws IOException {
        //  BM25 calculates score depending on b, k1 and k3 value
        //  for each term that appear in doc and query, calculate it using
        //  [log(N-df+0.5/df+0.5)]*[tf/(tf+k((1-b)+b*doclen/avg_doclen)]*[(k3+1)/k3])
        //  NOTE: we assume qtf is 1 for all input queries to BM25 model

        int docid = this.docIteratorGetMatch ();
        double score = 0.0;

        for (int i=0; i<this.args.size(); i++) {

            //  Java knows that the i'th query argument is a Qry object, but
            //  it does not know what type.  We know that SUM operators can
            //  only have QrySop objects as children.  Cast the i'th query
            //  argument to QrySop so that we can call its getScore method.

            QrySop q_i = (QrySop) this.args.get(i);

            //  If the i'th query argument matches this document, update the
            //  score by adding up to previous score

            if (q_i.docIteratorHasMatch (r) &&
                    (q_i.docIteratorGetMatch () == docid)) {
                score = score+q_i.getScore (r);}
        }

        return score;
    }

}
