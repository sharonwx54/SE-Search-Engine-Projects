/**
 *  Copyright (c) 2022, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchMin (r);
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
      return this.getScoreIndri (r);
    }
    //  STUDENTS::
    //  Add support for other retrieval models here.

    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the OR operator.");
    }
  }

  public double getDefaultScore (RetrievalModel r, long docid) throws IOException {
    if (r instanceof RetrievalModelIndri){
      double score = 1.0;
      // iterate through each term
      for (int i=0; i<this.args.size(); i++) {
        QrySop q_i = (QrySop) this.args.get(i);
        // calculate the multiplication first
        score = score * (1- q_i.getDefaultScore(r,docid));
      }
      // for OR, at the end, we subtract the value using 1
      return (1.0-score);
    }
    else {
      throw new IllegalArgumentException
              (r.getClass().getName() +
                      " doesn't support the default score calculation under SCORE operator.");
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
    //
    //  Other retrieval models must do more work.  To help students
    //  understand how to implement other retrieval models, this
    //  method uses a more general solution.  OR takes the maximum
    //  of the scores from its children query nodes.

    double score = 0.0;
    int docid = this.docIteratorGetMatch ();

    for (int i=0; i<this.args.size(); i++) {

      //  Java knows that the i'th query argument is a Qry object, but
      //  it does not know what type.  We know that OR operators can
      //  only have QrySop objects as children.  Cast the i'th query
      //  argument to QrySop so that we can call its getScore method.

      QrySop q_i = (QrySop) this.args.get(i);

      //  If the i'th query argument matches this document, update the
      //  score.

      if (q_i.docIteratorHasMatch (r) &&
          (q_i.docIteratorGetMatch () == docid)) {
        score = Math.max (score, q_i.getScore (r));
      }
    }

    return score;
  }

  private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    //  Ranked Boolean systems have various scores depending on the TF.  QryEval
    //  only calls getScore for documents that match, so if we get
    //  here, the document matches, and we take the max frequency among the matched doc
    //  OR takes the maximum of the scores from its children query nodes.

    double score = 0.0;
    int docid = this.docIteratorGetMatch ();

    for (int i=0; i<this.args.size(); i++) {

      //  Java knows that the i'th query argument is a Qry object, but
      //  it does not know what type.  We know that OR operators can
      //  only have QrySop objects as children.  Cast the i'th query
      //  argument to QrySop so that we can call its getScore method.

      QrySop q_i = (QrySop) this.args.get(i);

      //  If the i'th query argument matches this document, update the
      //  score.

      if (q_i.docIteratorHasMatch (r) &&
              (q_i.docIteratorGetMatch () == docid)) {
        score = Math.max (score, q_i.getScore (r));
      }
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


    for (int i=0; i<this.args.size(); i++) {
      QrySop q_i = (QrySop) this.args.get(i);
      if (q_i.docIteratorHasMatch (r)  &&
              (q_i.docIteratorGetMatch () == docid)){
        q_score = 1.0-q_i.getScore(r);
      }
      else{
        q_score = 1.0- q_i.getDefaultScore(r,docid);}

      score *= q_score;
    }
    return (1.0-score);
  }

}
