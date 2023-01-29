/**
 *  Copyright (c) 2022, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    if (r instanceof RetrievalModelIndri) {
      // for best-match algo, we use min to capture docs with some terms but not all
      return this.docIteratorHasMatchMin (r);
    }

    return this.docIteratorHasMatchFirst (r);
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

    if (r instanceof RetrievalModelBM25) {
      return this.getScoreBM25 (r);
    }
    if (r instanceof RetrievalModelIndri) {
      return this.getScoreIndri (r);
    }
    //  STUDENTS::
    //  Add support for other retrieval models here.

    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }

  /**
   *  getDefaultScore for the docid for under given model
   *  @param r The retrieval model that determines how scores are calculated.
   *  @param docid the document ID to calculate default score
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getDefaultScore (RetrievalModel r, long docid) throws IOException {
    // default score is really only a thing for Indri, so throw exception for other models
    if (r instanceof RetrievalModelIndri){

      return this.getDefaultScoreIndri (r, docid);
    }
    else {
      throw new IllegalArgumentException
              (r.getClass().getName() +
                      " doesn't support the default score calculation under SCORE operator.");
    }
  }

  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {

    //  Unranked Boolean systems return 1 for all matches.
    //
    //  Other retrieval models must do more work.  To help students
    //  understand how to implement other retrieval models, this
    //  method does a little more work.  
    //
    //  Java knows that the (only) query argument is a Qry object, but
    //  it does not know what type.  We know that SCORE operators can
    //  only have a single QryIop object as its child.  Cast the query
    //  argument to QryIop so that we can access its inverted list.

    QryIop q_0 = (QryIop) this.args.get (0);
    return 1.0;
  }

  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {

    //  Ranked Boolean systems return the TF for the corresponding match
    //  We know that SCORE operators can only have a single QryIop object as its child.
    //  Cast the query argument to QryIop so that we can access its inverted list.

    //  when getScore is called, the operator should know about the document ID
    QryIop q_0 = (QryIop) this.args.get (0);
    // already know the match when this function is called, stored under the cache
    int tf = q_0.docIteratorGetMatchPosting().tf;

    return tf;
  }

  /**
   *  getScore for the BM25 retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreBM25 (RetrievalModel r) throws IOException {

    //  BM25 Model return IDF*DF(adj)*Query Weight for each term
    //  We know that SCORE operators can only have a single QryIop object as its child.
    //  Cast the query argument to QryIop so that we can access its inverted list.

    RetrievalModelBM25 rbm25 = (RetrievalModelBM25) r;
    //  when getScore is called, the operator should know about the document ID
    QryIop q_0 = (QryIop) this.args.get (0);
    // already know the match exists when this function is called, stored under the cache
    int tf = q_0.docIteratorGetMatchPosting().tf;

    double N = Idx.getNumDocs();
    double doclen_d = Idx.getFieldLength(q_0.getField(), q_0.docIteratorGetMatch());
    double avg_doclen = Idx.getSumOfFieldLengths(q_0.getField()) / (float) Idx.getDocCount (q_0.getField());
    double df = q_0.getDf();

    // calculate three parts separately
    // NOTE for the idf part, we need to make sure it's none zero
    double idf = Math.max(0, Math.log((N-df+0.5)/(df+0.5)));
    double tf_w = tf/(tf+rbm25.k1*((1-rbm25.b)+rbm25.b*(doclen_d/avg_doclen)));
    // assuming qtf = 1 for all BM25 models, then k3 does not matter here (rbm25.k3+1)/(rbm25.k3+1);
    double user_w = 1.0;

    return idf*tf_w*user_w;
  }

  /**
   *  getScore for the Indri retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreIndri (RetrievalModel r) throws IOException {

    //  If the function get hits, the match must be found
    //  We know that SCORE operators can only have a single QryIop object as its child.
    //  Cast the query argument to QryIop so that we can access its inverted list.

    RetrievalModelIndri rindri = (RetrievalModelIndri) r;
    //  when getScore is called, the operator should know about the document ID
    QryIop q_0 = (QryIop) this.args.get (0);
    double ctf = q_0.getCtf();
    if (ctf == 0){ctf = 0.5;}
    // already know the match when this function is called, stored under the cache
    int tf = q_0.docIteratorGetMatchPosting().tf;
    double length_d = Idx.getFieldLength(q_0.getField(), q_0.docIteratorGetMatch());

    double p_mle_corpus = ctf/Idx.getSumOfFieldLengths(q_0.getField());
    double score = (1.0-rindri.lambda)*(tf+(rindri.miu*p_mle_corpus))/(length_d+rindri.miu)+
            rindri.lambda*p_mle_corpus;
//    if (q_0.docIteratorGetMatch() == 291129){
//      System.out.println("docid 291129 score "+score+" and corpus MLE is "+p_mle_corpus);
//    }
    if ((length_d == 0) && (rindri.miu==0)){score = rindri.lambda*p_mle_corpus;}

    return score;
  }

  public double getDefaultScoreIndri (RetrievalModel r, long docid) throws IOException {

    //  Default Score for Indri - setting tf = 0
    //  wrap model as indri model and the pointer as Iop type
    RetrievalModelIndri rindri = (RetrievalModelIndri) r;
    QryIop q_0 = (QryIop) this.args.get (0);
    double ctf = q_0.getCtf();
    if (ctf == 0){ctf = 0.5;}
    // set tf=0, then the score would be
    // (1-lambda)*miu*Pmle(q|C)/(length_d+miu)+ lambdaPmle(q|C)
    double length_d = Idx.getFieldLength(q_0.getField(), (int) docid);
    double p_mle_corpus = ctf/Idx.getSumOfFieldLengths(q_0.getField());
    double default_score = (1.0-rindri.lambda)*(rindri.miu*p_mle_corpus)/(length_d+rindri.miu)+
            rindri.lambda*p_mle_corpus;
    // when division by 0 happens
    if ((length_d == 0) && (rindri.miu==0)){default_score = rindri.lambda*p_mle_corpus;}
//    if (default_score > 1) {System.out.println(docid);}

    return default_score;
  }
  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);

    /*
     *  STUDENTS:: In HW2 during query initialization you may find it
     *  useful to have this SCORE node precompute and cache some
     *  values that it will use repeatedly when calculating document
     *  scores.  It won't change your results, but it will improve the
     *  speed of your software.
     */
  }

}
