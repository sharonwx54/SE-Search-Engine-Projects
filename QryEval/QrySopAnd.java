import java.io.*;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
        if (r instanceof RetrievalModelIndri | r instanceof  RetrievalModelBM25) {
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

        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean (r);
        }
        if (r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean (r);
        }
        if (r instanceof RetrievalModelIndri) {
            //System.out.println("Entering AND operator");
            return this.getScoreIndri (r);
        }

        //  STUDENTS::
        //  Add support for other retrieval models here.

        else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    public double getDefaultScore (RetrievalModel r, long docid) throws IOException {

        if (r instanceof RetrievalModelIndri) {
            // get the length of this query
            int query_size = this.args.size();
            double score = 1.0;
            // iterate through each term
            for (int i=0; i<query_size; i++) {
                QrySop q_i = (QrySop) this.args.get(i);

                score *= Math.pow(q_i.getDefaultScore(r,docid), 1.0/query_size);
            }
            return score;
        }

        else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the Default Score for AND operator.");
        }
    }

    /**
     *  getScore for the UnrankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
        //  Unranked Boolean systems only have two scores:
        //  1 (document matches) and 0 (document doesn't match).  QryEval
        //  only calls getScore for documents that match, so if we get
        //  here, the document matches, and its score should be 1.  The
        //  most efficient implementation returns 1 from here.

        double score = 1.0;

        return score;
    }

    private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
        //  Ranked Boolean systems have various scores depending on the TF.  QryEval
        //  only calls getScore for documents that match, so if we get
        //  here, the document matches, and we take the min frequency among the matched docs.
        //  AND takes the minimum of the scores from its children query nodes.

        QrySop q_0 = (QrySop) this.args.get(0);
        double score = q_0.getScore(r);

        for (int i=1; i<this.args.size(); i++) {

            //  Java knows that the i'th query argument is a Qry object, but
            //  it does not know what type.  We know that AND operators can
            //  only have QrySop objects as children.  Cast the i'th query
            //  argument to QrySop so that we can call its getScore method.

            QrySop q_i = (QrySop) this.args.get(i);

            //  Don't need to check if match as in the AND, each QrySop must has match.
            score = Math.min (score, q_i.getScore (r));
        }

        return score;
    }

    private double getScoreIndri (RetrievalModel r) throws IOException {
        // set initial score to be 1 for mulplication
        double score = 1.0;
        double q_score;
        // Note that the getScoreIndri would not be called on doc with none of the query term
        // so there must be at least one Qry has matched on the docid
        int docid = this.docIteratorGetMatch ();
        int query_size = this.args.size();
        for (int i=0; i<query_size; i++) {
            QrySop q_i = (QrySop) this.args.get(i);
            if (q_i.docIteratorHasMatch (r) &&
                    (q_i.docIteratorGetMatch () == docid)){
                q_score = Math.pow(q_i.getScore(r), 1.0/query_size);
            }
            else{q_score = Math.pow(q_i.getDefaultScore(r,docid), 1.0/query_size);}
            score *= q_score;
        }
        //if (docid == 291129){System.out.println("AND score is "+score);}
        return score;
    }



}
