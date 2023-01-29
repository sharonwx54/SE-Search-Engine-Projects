/*
 *  Copyright (c) 2022, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.10.
 *
 *  Compatible with Lucene 8.1.1.
 */
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
//import java.time.*;
import java.nio.charset.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *  This software illustrates the architecture for the portion of a
 *  search engine that evaluates queries.  It is a guide for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 */
public class QryEval {

  //  --------------- Constants and variables ---------------------

  private static final String USAGE =
          "Usage:  java QryEval paramFile\n\n";

  //  --------------- Methods ---------------------------------------
  private static CharsetEncoder asciiEncoder =
          Charset.forName("US-ASCII").newEncoder();

  public static boolean isAsciiString (String s) {
    return asciiEncoder.canEncode(s);
  }

  /**
   *  @param args The only argument is the parameter file name.
   *  @throws Exception Error accessing the Lucene index.
   */
  public static void main(String[] args) throws Exception {

    //  This is a timer that you may find useful.  It is used here to
    //  time how long the entire program takes, but you can move it
    //  around to time specific parts of your code.

    Timer timer = new Timer();
    timer.start ();
//    Instant before = Instant.now();


    //  Check that a parameter file is included, and that the required
    //  parameters are present.  Just store the parameters.  They get
    //  processed later during initialization of different system
    //  components.

    if (args.length < 1) {
      throw new IllegalArgumentException (USAGE);
    }

    Map<String, String> parameters = readParameterFile (args[0]);

    //  Open the index and initialize the retrieval model.

    Idx.open (parameters.get ("indexPath"));
    RetrievalModel model = initializeRetrievalModel (parameters);
    String isDiverse = parameters.get("diversity");
    // handle LeToR
    if(model instanceof RetrievalModelLeToR){
      RetrievalModelLeToR letor = (RetrievalModelLeToR) model;
      letor.runLeToR(parameters);
    }
    // handle Diversification
    else if (isDiverse != null){
      if (isDiverse.equalsIgnoreCase("true")){
        Diversification diverseQ = new Diversification(parameters, model);
        diverseQ.runDiversification(parameters);
      }
      else{
        processQueryFile(parameters, model);
      }
    }
    //  Perform experiments.
    else{
      processQueryFile(parameters, model);
    }


    //  Clean up.
//    Instant after = Instant.now();
    timer.stop ();
    System.out.println ("Time:  " + timer);
//    long delta = Duration.between(before, after).toMillis();
//    System.out.println ("Time MS:  " + delta);
  }

  /**
   *  Allocate the retrieval model and initialize it using parameters
   *  from the parameter file.
   *  @return The initialized retrieval model
   *  @throws IOException Error accessing the Lucene index.
   */
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
          throws IOException {

    RetrievalModel model = null;
    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();
    System.out.print(modelString);

    if (modelString.equals("unrankedboolean")) {

      model = new RetrievalModelUnrankedBoolean();

      //  If this retrieval model had parameters, they would be
      //  initialized here.

    }
    //  STUDENTS::  Add new retrieval models here.
    else if (modelString.equals("rankedboolean")) {

      model = new RetrievalModelRankedBoolean();

      //  If this retrieval model had parameters, they would be
      //  initialized here.

    }
    else if (modelString.equals("bm25")) {
      double k1 = Double.parseDouble(parameters.get ("BM25:k_1"));
      double k3 = Double.parseDouble(parameters.get ("BM25:k_3"));
      double b = Double.parseDouble(parameters.get ("BM25:b"));

      model = new RetrievalModelBM25(k1, b, k3);

    }
    else if (modelString.equals("indri")) {
      int miu = Integer.parseInt(parameters.get ("Indri:mu"));
      double lambda = Double.parseDouble(parameters.get ("Indri:lambda"));
      model = new RetrievalModelIndri(miu, lambda);

    }

    else if (modelString.equals("ltr")) {
      model = new RetrievalModelLeToR(parameters);
    }
    else {

      throw new IllegalArgumentException
              ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }

    return model;
  }

  /**
   * Print a message indicating the amount of memory used. The caller can
   * indicate whether garbage collection should be performed, which slows the
   * program but reduces memory usage.
   *
   * @param gc
   *          If true, run the garbage collector before reporting.
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc)
      runtime.gc();

    System.out.println("Memory used:  "
            + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }

  /**
   * Process one query.
   * @param qString A string that contains a query.
   * @param model The retrieval model determines how matching and scoring is done.
   * @return Search results
   * @throws IOException Error accessing the index
   */
  static ScoreList processQuery(String qString, RetrievalModel model)
          throws IOException {

    String defaultOp = model.defaultQrySopName ();
    qString = defaultOp + "(" + qString + ")";
    Qry q = QryParser.getQuery (qString);

    // Show the query that is evaluated

    System.out.println("    --> " + q);

    if (q != null) {

      ScoreList results = new ScoreList ();

      if (q.args.size () > 0) {		// Ignore empty queries

        q.initialize (model);

        while (q.docIteratorHasMatch (model)) {
          int docid = q.docIteratorGetMatch ();
          double score = ((QrySop) q).getScore (model);
          results.add (docid, score);
          q.docIteratorAdvancePast (docid);
        }
      }

      return results;
    } else
      return null;
  }


  /**
   *  Process the query file.
   *  @param parameters with query file, truncate size and other info on the input parameters
   *  @param model A retrieval model that will guide matching and scoring
   *  @throws IOException Error accessing the Lucene index.
   */
  static void processQueryFile(Map<String, String> parameters, //String queryFilePath, String outputLen,
                               RetrievalModel model)
          throws IOException {

    BufferedReader input = null;

    try {
      String qLine = null;

      input = new BufferedReader(new FileReader(parameters.get("queryFilePath")));
//      System.out.println(Idx.getSumOfFieldLengths("body") / (float) Idx.getDocCount ("body"));
      //  Each pass of the loop processes one query.
      PrintWriter writer = new PrintWriter(parameters.get("trecEvalOutputPath"), "UTF-8");
//      PrintWriter writer_qry = new PrintWriter("TEST_DIR/HW4-Exp-1.1b.qry", "UTF-8");

      // check if we want to perform query expansion
      String performExp =  parameters.get("prf");
      Map<String, ScoreList> expandScoreLists = null;
      int expTermNum = 0;
      int expDocNum = 0;
      double mu = 0;
      double origQueryW = 1.0;
      String expFile = null;
      PrintWriter queryWriter = null;
      if ((performExp != null) && performExp.equals("Indri")){

        expTermNum = Integer.parseInt(parameters.get("prf:numTerms"));
        expDocNum = Integer.parseInt(parameters.get("prf:numDocs"));
        mu = Double.parseDouble(parameters.get("prf:Indri:mu"));
        origQueryW = Double.parseDouble(parameters.get("prf:Indri:origWeight"));
        expFile = parameters.get("prf:initialRankingFile");
        queryWriter = new PrintWriter(parameters.get("prf:expansionQueryFile"));
        // get the scorelists of term for query expansion, per query ID
        if (expFile != null){
          expandScoreLists = getTopRankScoreLists(expFile);
          System.out.println("Using pre-rank info for expansion");
        }

      }
      while ((qLine = input.readLine()) != null) {

        printMemoryUsage(false);
        System.out.println("Query " + qLine);
        String[] pair = qLine.split(":");

        if (pair.length != 2) {
          throw new IllegalArgumentException
                  ("Syntax error:  Each line must contain one ':'.");
        }

        String qid = pair[0];
        String query = pair[1];
        if ((performExp != null) && performExp.equals("Indri")){
          ScoreList expTerm = null;
          String origQuery = new String(query);
          if (expandScoreLists != null){
            expTerm = expandScoreLists.get(qid);
            //System.out.println("Getting the expanded scorelist by prerank");
          }
          else{
            expTerm = processQuery(origQuery, model);
            //System.out.println("Getting the expanded scorelist by original query");
          }
          // sort and select only top doc for expansion
          expTerm.sort();
          expTerm.truncate(expDocNum);
          // run query expansion to generate expanded query
          String expQuery = runIndriQueryExpansion(expDocNum, expTermNum, mu, expTerm);
          query = "#WAND ( "+origQueryW+" #AND ( "+origQuery+
                  " ) "+(1.0-origQueryW)+" "+expQuery+" )";
          // write the query to the file and handling the query id issue
          writeIntoQuery(queryWriter, qid, expQuery);
          System.out.println("Expanded Query is "+expQuery);
        }
        int outputLen = Integer.parseInt(parameters.get("trecEvalOutputLength"));
//        String structured_str = QueryMultiRep(query, 0.05, 0.15, 0.4, 0.4);
//        String structured_str = QueryBySDM(query, 0.45, 0.45, 0.1);
//        writer_qry.println(qid+": "+structured_str);
        ScoreList results = processQuery(query, model);

        if (results != null) {
          // sort the result before the print
          results.sort ();
          // cap the result based on parameters
          results.truncate(outputLen);
          writeIntoTrecEval(writer, qid, results);
//          printResults(qid, results);
          System.out.println(); //>>> enable later
        }
      }
      writer.close();
      if ((performExp != null) && performExp.equals("Indri")){queryWriter.close();}
//      writer_qry.close();
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
    }
  }


  /**
   **/
  static Map<String, ScoreList> getTopRankScoreLists(String expFile)  throws IOException {

    BufferedReader input = null;
    Map<String, ScoreList> results = new HashMap<String, ScoreList>();
    try {
      String qLine = null;
      //int rankLen = Integer.parseInt(parameters.get("prf:numDocs"));
      input = new BufferedReader(new FileReader(expFile));
      while ((qLine = input.readLine()) != null) {
        String[] docinfo = qLine.split(" ");
        ScoreList result = null;
        // we need this extra step to remove 0
        String queryID = String.valueOf(Integer.parseInt(docinfo[0]));
        String extDocid = docinfo[2];
        int docid = Idx.getInternalDocid(extDocid);
        if (results.containsKey(queryID)){
          result = results.get(queryID);
        }
        else{
          result = new ScoreList();
          results.put(queryID, result);
        }
        result.add(docid, Double.parseDouble(docinfo[4]));
      }

    }  catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      input.close();
    }
    return results;
  }

  /****/
  static String runIndriQueryExpansion (int expDocNum, int expTermNum, double mu, ScoreList expTerms)
          throws IOException{
    // create a mapping between each term and the corresponding score
    Map<String, Double> termScoreMap = new HashMap<String, Double>();
    // for each document, create TermVector
    for (int i=0; i<expDocNum; i++){
      TermVector docTV = new TermVector(expTerms.getDocid(i), "body");
      for (int j=1;j<docTV.stemsLength();j++){
        String term = docTV.stemString(j);
        //System.out.println("Current Term is "+term);
        // only add term if it's all ASCII and has not appear yet
        if (!termScoreMap.containsKey(term) && isAsciiString(term) &&
                term.indexOf('.') < 0 && term.indexOf(',') < 0){
          termScoreMap.put(term, 0.0);
        }
      }
    }
    // now calculate the score for each term using Indri method
    // we need to loop through all docs again because even if the term does not appear in this doc
    // it still has a score
    for (int k=0;k<expDocNum;k++){
      int docid = expTerms.getDocid(k);
      TermVector docTV = new TermVector(docid, "body");
      double doclen = Idx.getFieldLength("body", docid);
      double indri_doc_score = expTerms.getDocidScore(k);
      for (String t: termScoreMap.keySet()){
        int tf = 0;
        int tIdx = docTV.indexOfStem(t);
        double ctf = Idx.getTotalTermFreq("body", t);
        double p_mle = ctf / Idx.getSumOfFieldLengths("body");
        if (tIdx > 0){tf = docTV.stemFreq(tIdx);}
        double indri_prf_score = (tf+mu*p_mle)/(doclen+mu);
        double indri_idf_score = 1.0/p_mle;
        double indri_term_score = indri_doc_score*indri_prf_score*Math.log(indri_idf_score);
        // add the score to the term-score map
        termScoreMap.put(t,termScoreMap.get(t)+indri_term_score);
      }

    }
    // select the terms for query expansion based on the result and write to qryOut
    Stream<Map.Entry<String, Double>> topTerm = termScoreMap.entrySet().stream().sorted(
            Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(expTermNum);
    // create the weight - term string into a string of query
    DecimalFormat df = new DecimalFormat("#0.0000");
    String expQuery = topTerm.map(ent -> df.format(ent.getValue()) +" "+ent.getKey()).collect(
            Collectors.joining(" "));

    return " #WAND ( " + expQuery+ " )";

  }
  /**
   * Print the query results.
   *
   * STUDENTS::
   * This is not the correct output format. You must change this method so
   * that it outputs in the format specified in the homework page, which is:
   *
   * QueryID Q0 DocID Rank Score RunID
   *
   * @param queryName
   *          Original query.
   * @param result
   *          A list of document ids and scores
   * @throws IOException Error accessing the Lucene index.
   */
  static void printResults(String queryName, ScoreList result) throws IOException {

    System.out.println(queryName + ":  ");
    if (result.size() < 1) {
      System.out.println(queryName+" Q0 dummy 1 0 HW2-Run");
    } else {
      // NOTE the result ScoreList should have been sorted descending, so ranking is easy to add
      for (int i = 0; i < result.size(); i++) {
        int curr_rank = i+1;
        String output_str = queryName+" Q0 "+Idx.getExternalDocid(result.getDocid(i)) + " "
                +curr_rank+" " +result.getDocidScore(i)+" HW2-Run";
        System.out.println(output_str);
      }
    }
  }

  /**
   * Write the query results into trec_eval file in the format of
   * QueryID Q0 DocID Rank Score RunID
   *
   * @param filename
   *          The trec_eval file to save result
   * @param queryName
   *          Original query.
   * @param result
   *          A list of document ids and scores
   * @throws IOException Error accessing the Lucene index.
   */
  static void writeIntoTrecEval(PrintWriter writer, String queryName, ScoreList result) throws IOException {

    if (result.size() < 1) {
      writer.println(queryName+" Q0 OOONothingReturn 1 0 HW5");
    } else {
      // NOTE the result ScoreList should have been sorted descending, so ranking is easy to add
      for (int i = 0; i < result.size(); i++) {
        int curr_rank = i+1;
        String output_str = queryName+" Q0 "+Idx.getExternalDocid(result.getDocid(i)) + " "
                +curr_rank+" " +result.getDocidScore(i)+" HW5";
        writer.println(output_str);
      }
    }
    //System.out.println("Finishing Writing to file for "+queryName);
  }

  static void writeIntoQuery(PrintWriter writer, String queryName, String expQuery) throws IOException {
    writer.write(queryName + ": " + expQuery + "\n");
  }
  /**
   *  Read the specified parameter file, and confirm that the required
   *  parameters are present.  The parameters are returned in a
   *  HashMap.  The caller (or its minions) are responsible for processing
   *  them.
   *  @return The parameters, in <key, value> format.
   */
  private static Map<String, String> readParameterFile (String parameterFileName)
          throws IOException {

    Map<String, String> parameters = new HashMap<String, String>();
    File parameterFile = new File (parameterFileName);

    if (! parameterFile.canRead ()) {
      throw new IllegalArgumentException
              ("Can't read " + parameterFileName);
    }

    //  Store (all) key/value parameters in a hashmap.

    Scanner scan = new Scanner(parameterFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split ("=");
      parameters.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());

    scan.close();

    //  Confirm that some of the essential parameters are present.
    //  This list is not complete.  It is just intended to catch silly
    //  errors.

    if (! (parameters.containsKey ("indexPath") &&
            parameters.containsKey ("queryFilePath") &&
            parameters.containsKey ("trecEvalOutputPath") &&
            parameters.containsKey ("trecEvalOutputLength") &&
            parameters.containsKey ("retrievalAlgorithm"))) {
      throw new IllegalArgumentException
              ("Required parameters were missing from the parameter file.");
    }

    return parameters;
  }

  /**
   *  Function to create multi-representation query by input weights on different representation of the term
   **/
  public static String QueryMultiRep (String queryString, double url, double title, double body, double keywords){
    String[] bow = queryString.split(" ");
    String structured_rep = "#AND ( ";
    for (int i=0;i<bow.length;i++){
      structured_rep += "#WSUM ( ";
      structured_rep += String.valueOf(url) + " "+bow[i]+".url ";
      structured_rep += String.valueOf(title) + " "+bow[i]+".title ";
      structured_rep += String.valueOf(body) + " "+bow[i]+".body ";
      structured_rep += String.valueOf(keywords) + " "+bow[i]+".keywords ) ";
    }
    structured_rep += " ) ";
    return structured_rep;
  }

  /**
   *  Function to create SDM query by input weights on three parts
   */
  public static String QueryBySDM (String queryString, double term, double near, double window){
    String[] bow = queryString.split(" ");
    if (bow.length == 1){return queryString;}
    String structured_rep = " #WAND ( ";
    String term_str = String.valueOf(term)+" #AND ( "+ queryString+" ) ";
    String near_str = String.valueOf(near)+" #AND ( ";
    String window_str = String.valueOf(window)+" #AND ( ";
    for (int i=0;i<(bow.length-1);i++){
      near_str += " #NEAR/1 ( "+bow[i]+" "+bow[i+1]+" )";
      window_str += " #WINDOW/8 ( "+bow[i]+" "+bow[i+1]+" )";
    }
    near_str += " ) ";
    window_str += " ) ";
    structured_rep += term_str + near_str + window_str + " ) ";

    return structured_rep;

  }


}
