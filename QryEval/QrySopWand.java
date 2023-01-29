import java.io.*;

/**
 *  The WAND operator for all retrieval models.
 */
public class QrySopWand extends QrySop {

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
            double score = 1.0;
            // iterate through each term
            for (int i=0; i<this.args.size(); i++) {
                QrySop q_i = (QrySop) this.args.get(i);
                double weight = q_i.getWeight();
                score *= Math.pow(q_i.getDefaultScore(r,docid), weight);
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
        // set initial score to be 1 for mulplication
        double score = 1.0;
        double q_score;
        // Note that the getScoreIndri would not be called on doc with none of the query term
        // so there must be at least one Qry has matched on the docid
        int docid = this.docIteratorGetMatch ();
//        if (docid == 291129){
//        System.out.println("Current WLen is "+this.args.size());}
        for (int i=0; i<this.args.size(); i++) {
            QrySop q_i = (QrySop) this.args.get(i);
            double weight = q_i.getWeight();
            if (q_i.docIteratorHasMatch (r)  &&
                    (q_i.docIteratorGetMatch () == docid)){
                q_score = Math.pow(q_i.getScore(r), weight);
              //  if (docid == 488252){
               // System.out.println(Idx.getExternalDocid(docid)+" has match and weight is "+weight);
               // System.out.println(Idx.getExternalDocid(docid)+" has match and score is "+q_score);}
            }
            else{
                q_score = Math.pow(q_i.getDefaultScore(r,docid), weight);
//                System.out.println(Idx.getExternalDocid(docid)+" has NO match and default score is "+q_score);
            }
       //     if (docid == 488252){System.out.println("WAND score is "+q_i.getDisplayName()+"weight "+weight+"score "+q_score);}
            // difference here is to power the weight and multiply the scores
            score = score*q_score;
        }
        //if (docid == 488252){System.out.println("WAND score is "+score);}
        return score;
    }


}
