/**
 *  Copyright (c) 2022, Carnegie Mellon University.  All Rights Reserved.
 */
import javax.management.QueryEval;
import java.io.*;
import java.util.*;
import java.util.Map;
/**
 *  An object that stores parameters for the learning-to-rank
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelLeToR extends RetrievalModel {
    public String toolkit;
    // for BM25
    public double k1;
    public double b;
    public double k3;
    // for Indri
    public double lambda;
    public int miu;
    // for features
    public int featureNum = 20;
    public HashSet<Integer> featureSet = new HashSet<>();
    public HashMap<String, HashMap<String, String>> relevanceJudges = new HashMap<>();
    /***
     public String trainQueryFile = null;
     public String trainQRelFile = null;
     public String featureVFile = null;
     public String modelFile = null;
     public String testFeatureVFile = null;
     public String testDocScoreFile = null;
     public String svmLearnPath = null;
     public String svmClassifyPath = null;
     public double svmParamC;
     public int ranklibModel;
     public String ranklibMetric2t;
     ***/

    public RetrievalModelLeToR(Map<String, String> parameters){

        // constructor to create Learn to Rank model with parameters
        for(int f=1;f<=this.featureNum;f++){
            this.featureSet.add(f);
        }
        if (parameters.get("ltr:featureDisable") != null){
            // remove features if disabled
            String[] disableF = parameters.get("ltr:featureDisable").split(",");
            for(int i=0;i<disableF.length;i++){this.featureSet.remove(Integer.parseInt(disableF[i]));}
            this.featureNum = this.featureNum- disableF.length;
            System.out.println(this.featureSet);
        }
        // for Indri
        if ((parameters.get("Indri:mu") != null) && (parameters.get("Indri:lambda") != null)){
            this.miu = Integer.parseInt(parameters.get("Indri:mu"));
            this.lambda = Double.parseDouble(parameters.get("Indri:lambda"));
        }
        // for BM25
        if ((parameters.get("BM25:k_1") != null) && (parameters.get("BM25:b") != null)
                && (parameters.get("BM25:k_3") != null)){
            this.k1 = Double.parseDouble(parameters.get("BM25:k_1"));
            this.b = Double.parseDouble(parameters.get("BM25:b"));
            this.k3 = Double.parseDouble(parameters.get("BM25:k_3"));
        }
        // getting all files
        this.toolkit = parameters.get("ltr:toolkit");
        /***
         this.trainQueryFile = parameters.get("ltr:trainingQueryFile");
         this.trainQRelFile = parameters.get("ltr:trainingQrelsFile");
         this.featureVFile = parameters.get("ltr:trainingFeatureVectorsFile");
         this.modelFile = parameters.get("ltr:modelFile");
         this.testFeatureVFile = parameters.get("ltr:testingFeatureVectorsFile");
         this.testDocScoreFile = parameters.get("ltr:testingDocumentScores");
         this.svmLearnPath = parameters.get("ltr:svmRankLearnPath");
         this.svmClassifyPath = parameters.get("ltr:svmRankClassifyPath");
         this.svmParamC = Double.parseDouble(parameters.get("svmRankParamC"));
         this.ranklibModel = Integer.parseInt(parameters.get("ltr:RankLib:model"));
         this.ranklibMetric2t = parameters.get("ltr:RankLib:metric2t");
         ***/

    }

    public void runLeToR(Map<String, String> parameters) throws Exception, IOException {
        initRelevanceJudges(parameters.get("ltr:trainingQrelsFile"));
        processQuery(parameters, "train");
        if (this.toolkit.equals("SVMRank")){
            trainSVMRank(parameters);
            System.out.println("Training via SVM");
        }
        else {
            trainRankLib(parameters);
            System.out.println("Training via RankLib");
        }
        processQuery(parameters, "test");
        if (this.toolkit.equals("SVMRank")){
            testSVMClassify(parameters);
        }
        else {
            testRankLib(parameters);
        }
        reRank(parameters);

    }

    public void initRelevanceJudges(String fileName) throws IOException {
        HashMap<String, HashMap<String, String>> rj = new HashMap<>();
        BufferedReader input = null;
        try {
            input = new BufferedReader(new FileReader(fileName));
            String line = null;
            while ((line = input.readLine()) != null){
                String[] info = line.split(" ");
                String qid = info[0].trim();
                String rjScore = info[3].trim();
                // relabel -2
                if (rjScore.equals("-2")){rjScore = "0";}
                HashMap<String, String> relevanceDocs = rj.getOrDefault(qid, new HashMap<String, String>());
                relevanceDocs.put(info[2].trim(), rjScore);
                rj.put(qid, relevanceDocs);

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            input.close();
        }
        // set relevanceJudge Hashmap to RJ
        this.relevanceJudges = rj;

    }

    public void trainSVMRank(Map<String, String> parameters) throws Exception{
        // first get all input parameters associated with SVM Rank
        String svmLearnPath = parameters.get("ltr:svmRankLearnPath");
        String trainFeatureVFile = parameters.get("ltr:trainingFeatureVectorsFile");
        String modelFile = parameters.get("ltr:modelFile");
        double svmParamC = Double.parseDouble(parameters.get("ltr:svmRankParamC"));
        // running svm rank learn

        Utils.runExternalProcess ("svm_rank_learn",
                new String[] {svmLearnPath, "-c", String.valueOf(svmParamC), trainFeatureVFile,
                        modelFile});


    }

    public void testSVMClassify(Map<String, String> parameters) throws Exception{
        String svmClassifyPath = parameters.get("ltr:svmRankClassifyPath");
        String testFeatureVFile = parameters.get("ltr:testingFeatureVectorsFile");
        String testDocScoreFile = parameters.get("ltr:testingDocumentScores");
        String modelFile = parameters.get("ltr:modelFile");
        // running svm rank classify
        Utils.runExternalProcess ("svm_rank_classify",
                new String[] {svmClassifyPath, testFeatureVFile, modelFile, testDocScoreFile});

    }

    public void trainRankLib(Map<String, String> parameters) throws Exception{
        // first get all input parameters associated with SVM Rank
        String rankLibID = parameters.get("ltr:RankLib:model");
        String trainFeatureVFile = parameters.get("ltr:trainingFeatureVectorsFile");
        String modelFile = parameters.get("ltr:modelFile");
        String rankLibMetric = parameters.get("ltr:RankLib:metric2t");
        // running RankLib learn
        if (rankLibMetric == null){
            // System.out.println("Rank MODEL is "+rankLibID);
            // ListNet ignore metric2t
            ciir.umass.edu.eval.Evaluator.main (
                    new String[] {"-ranker", rankLibID, "-train", trainFeatureVFile, "-save", modelFile });
        }
        else{
            ciir.umass.edu.eval.Evaluator.main (
                    new String[] {"-ranker", rankLibID, "-metric2t", rankLibMetric,
                            "-train", trainFeatureVFile, "-save", modelFile } );
        }
    }

    public void testRankLib(Map<String, String> parameters) throws Exception{
        // first get all input parameters associated with SVM Rank
        String testFeatureVFile = parameters.get("ltr:testingFeatureVectorsFile");
        String testDocScoreFile = parameters.get("ltr:testingDocumentScores");
        String modelFile = parameters.get("ltr:modelFile");
        String rankLibMetric = parameters.get("ltr:RankLib:metric2t");
        if (rankLibMetric == null) {
            ciir.umass.edu.eval.Evaluator.main (
                new String[] {"-load", modelFile, "-rank", testFeatureVFile,
                        "-score", testDocScoreFile});}
        // running RankLib learn
        else{
            ciir.umass.edu.eval.Evaluator.main (
                new String[] {"-load", modelFile, "-rank", testFeatureVFile,
                        "-metric2t", rankLibMetric, "-score", testDocScoreFile});
        }
    }

    public void processQuery(Map<String, String> parameters, String queryType) throws Exception{
        BufferedReader input = null;
        PrintWriter writer = null;
        String query_file = parameters.get("ltr:trainingQueryFile");
        if (queryType.equals("test")){
            RetrievalModelBM25 bm25 = new RetrievalModelBM25(this.k1, this.b, this.k3);
            QryEval.processQueryFile(parameters, bm25);
            initRelevanceJudges(parameters.get("trecEvalOutputPath"));
            query_file = parameters.get("queryFilePath");
        }

        try {
            String qLine = null;
            input = new BufferedReader(new FileReader(query_file));
            String featureWriteFile = parameters.get("ltr:trainingFeatureVectorsFile");
            if (queryType.equals("test")){
                featureWriteFile = parameters.get("ltr:testingFeatureVectorsFile");
            }
            writer = new PrintWriter(featureWriteFile, "UTF-8");

            while ((qLine = input.readLine()) != null) {
                String[] pair = qLine.split(":");

                if (pair.length != 2) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Each line must contain one ':'.");
                }
                String qid = pair[0];
                //if (queryType.equals("test")){System.out.println("Current Qid is "+qid);}
                String query = pair[1];
                String[] queryTerms = QryParser.tokenizeString(query);
                //System.out.println("Post Tokenized Term "+Arrays.toString(queryTerms));
                // create hashmap for features
                HashMap<String, HashMap<Integer, Double>> featureMap = new HashMap<>();
                // getting relevant docs for the QID - format externalId: relevant score
                HashMap<String, String> rjDocs = this.relevanceJudges.get(qid);
                //System.out.println("Rel Judge is");
                //System.out.println(rjDocs);
                for(Map.Entry<String, String> doc_rjScore: rjDocs.entrySet()){
                    String externalId = doc_rjScore.getKey();
                    HashMap<Integer, Double> doc_fMap = calcFeatures(queryTerms, externalId);
                    featureMap.put(externalId, doc_fMap);
                }
                // normalize features
                if (this.toolkit.equals("SVMRank")){
                    //System.out.println("Start normalizing");
                    normalizeFeatures(qid, featureMap, writer);
                }
                else {
                    //normalizeFeatures(qid, featureMap, writer);
                    writeOrigFeatures(qid, featureMap, writer);
                }

            }
            writer.close();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        input.close();
    }

    public HashMap<Integer, Double> calcFeatures(String[] queryTerms, String externalId)
            throws Exception {
        int docid;
        try {
            docid = Idx.getInternalDocid(externalId);
            if (docid < 0){return null;}
            HashMap<Integer, Double> featureMap = new HashMap<>();
            // feature 1: spam score
            if (this.featureSet.contains(1)){
                if (Idx.getAttribute ("spamScore", docid) != null){
                    int spamScore = Integer.parseInt (Idx.getAttribute ("spamScore", docid));
                    featureMap.put(1, 1.0*spamScore);
                }
                else {
                    featureMap.put(1, -1.0);
                }
            }
            // feature 2: URL depth
            String rawUrl = Idx.getAttribute ("rawUrl", docid);
            if (this.featureSet.contains(2)){
                // subtract 2 for the http:// >>> DON'T subtract
                if (rawUrl != null) {
                    int depth = rawUrl.replaceAll("[^/]", "").length();
                    featureMap.put(2, 1.0*depth);
                }
                else {
                    featureMap.put(2, -1.0);
                }
            }
            // feature 3: wikipedia
            if (this.featureSet.contains(3)){
                if (rawUrl != null) {
                    int urlWikiScore = 0;
                    if (rawUrl.contains("wikipedia.org")) {urlWikiScore=1;}
                    featureMap.put(3, 1.0*urlWikiScore);
                }
                else {
                    featureMap.put(3, -1.0);
                }
            }
            // feature 4: PageRank
            if (this.featureSet.contains(4)){
                if (Idx.getAttribute ("PageRank", docid) != null){
                    float prScore = Float.parseFloat (Idx.getAttribute ("PageRank", docid));
                    featureMap.put(4, 1.0*prScore);
                }
                else {
                    featureMap.put(4, -1.0);
                }
            }
            // feature 5: BM25 score - body
            if (this.featureSet.contains(5)){
                double bm25Score = getScoreBM25(docid, queryTerms,"body");
                featureMap.put(5, 1.0*bm25Score);
            }
            // feature 6: Indri score - body
            if (this.featureSet.contains(6)){
                double indriScore = getScoreIndri(docid, queryTerms, "body");
                featureMap.put(6, 1.0*indriScore);
            }
            // feature 7: TermOverlap score - body
            if (this.featureSet.contains(7)){
                double termOverlapScore = getTermOverlap(docid, queryTerms, "body");
                featureMap.put(7, 1.0*termOverlapScore);
            }
            // feature 8: BM25 score - title
            if (this.featureSet.contains(8)){
                double bm25ScoreTitle = getScoreBM25(docid, queryTerms,"title");
                featureMap.put(8, 1.0*bm25ScoreTitle);
            }
            // feature 9: Indri score - title
            if (this.featureSet.contains(9)){
                double indriScoreTitle = getScoreIndri(docid, queryTerms, "title");
                featureMap.put(9, 1.0*indriScoreTitle);
            }
            // feature 10: TermOverlap score - title
            if (this.featureSet.contains(10)){
                double termOverlapScoreTitle = getTermOverlap(docid, queryTerms, "title");
                featureMap.put(10, 1.0*termOverlapScoreTitle);
            }
            // feature 11: BM25 score - url
            if (this.featureSet.contains(11)){
                double bm25ScoreUrl = getScoreBM25(docid, queryTerms,"url");
                featureMap.put(11, 1.0*bm25ScoreUrl);
            }
            // feature 12: Indri score - url
            if (this.featureSet.contains(12)){
                double indriScoreUrl = getScoreIndri(docid, queryTerms, "url");
                featureMap.put(12, 1.0*indriScoreUrl);
            }
            // feature 13: TermOverlap score - url
            if (this.featureSet.contains(13)){
                double termOverlapScoreUrl = getTermOverlap(docid, queryTerms, "url");
                featureMap.put(13, 1.0*termOverlapScoreUrl);
            }
            // feature 14: BM25 score - inlink
            if (this.featureSet.contains(14)){
                double bm25ScoreInlink = getScoreBM25(docid, queryTerms,"inlink");
                featureMap.put(14, 1.0*bm25ScoreInlink);
            }
            // feature 15: Indri score  - inlink
            if (this.featureSet.contains(15)){
                double indriScoreInlink = getScoreIndri(docid, queryTerms, "inlink");
                featureMap.put(15, 1.0*indriScoreInlink);
            }
            // feature 16: TermOverlap score - inlink
            if (this.featureSet.contains(16)){
                double termOverlapScoreInlink = getTermOverlap(docid, queryTerms, "inlink");
                featureMap.put(16, 1.0*termOverlapScoreInlink);
            }
            // feature 17: customize - # number of keyword term

            if (this.featureSet.contains(17)){
                if (Idx.getFieldLength("keywords", docid) > 0){
                double keywordTermNum = Idx.getFieldLength("keywords", docid);
                featureMap.put(17, Math.sqrt(1.0*keywordTermNum));
                }
                //double keywordAppearRate = getTermKeywordRate(docid, queryTerms);
                else{featureMap.put(17, -1.0);}
            }

            // feature 18: customize - # RankBoolean AND on Body
            if (this.featureSet.contains(18)){
                double rankedBoolScore = getScoreRankedBool(docid, queryTerms, "body");
                featureMap.put(18, 1.0*rankedBoolScore);
            }

            // feature 19: customize - std of term frequency in Body
            if (this.featureSet.contains(19)){
                double termFreqStd = getTermFreqStd(docid, queryTerms, "body");
                featureMap.put(19, 1.0*termFreqStd);
            }
            /***

             // feature 19: customize - std of term frequency in Body
             if (this.featureSet.contains(19)){
             double termFreqStd = getTermFreqStd(docid, queryTerms, "body");;
             featureMap.put(19, 1.0*termFreqStd);
             }
             // feature 19: customize - average term length in the body
             if (this.featureSet.contains(19)){
             double avgTermLength = getAvgTermLength(docid, queryTerms, "body");;
             featureMap.put(19, 1.0*avgTermLength);
             }

            // feature 20: customize - % stopword length in body
            if (this.featureSet.contains(20)){
                double stopwordFrac = getStopwordFrac(docid,  "body");
                featureMap.put(20, 1.0*stopwordFrac);
            } ***/
            // feature 20: customize - authority of inlinks
            if (this.featureSet.contains(20)){
                double inlinkAuth = getInlinkAuthority(docid);
                featureMap.put(20, 1.0*inlinkAuth);
            }

            return featureMap;

        }
        catch(Exception e){
            throw new RuntimeException(e);
        }

    }

    public void normalizeFeatures(String qid, HashMap<String, HashMap<Integer, Double>> featureMap, PrintWriter writer)
            throws Exception {
        HashMap<Integer, Double> minFVals = new HashMap<>();
        HashMap<Integer, Double> maxFVals = new HashMap<>();
        HashMap<Integer, Double> minmaxFVals = new HashMap<>();
        try{
            // initialize value
            for(int f: this.featureSet){
                minFVals.put(f, Double.MAX_VALUE);
                maxFVals.put(f, -Double.MAX_VALUE);
            }
            // get min and max
            for(Map.Entry<String, HashMap<Integer, Double>> docFeatures: featureMap.entrySet()) {
                HashMap<Integer, Double> featureVals = docFeatures.getValue();
                if(featureVals == null) continue;
                for(int f: this.featureSet){
                    double featureVal = featureVals.get(f);
                    if(featureVal != -1.0) {
                        // then the feature exists
                        minFVals.put(f, Math.min(minFVals.get(f), featureVal));
                        maxFVals.put(f, Math.max(maxFVals.get(f), featureVal));
                    }
                }
            }
            for(int f: this.featureSet){
                minmaxFVals.put(f, maxFVals.get(f) - minFVals.get(f));
            }
            // now normalizing
            for(Map.Entry<String, HashMap<Integer, Double>> docFeatures: featureMap.entrySet()) {
                HashMap<Integer, Double> featureVals = docFeatures.getValue();
                if(featureVals == null) continue;
                String externalId = docFeatures.getKey();
                String rjScore = this.relevanceJudges.get(qid).get(externalId);
                //String resultString = String.format("%s qid:%s ", rjScore, qid);
                String resultString = rjScore+" qid:"+qid+" ";

                for(int f: this.featureSet){
                    double featureVal = featureVals.get(f);
                    double valDiff = minmaxFVals.get(f);
                    if((featureVal != -1.0) && (valDiff != 0)) {
                        //   resultString += String.format("%d:%f ", f, (featureVal - minFVals.get(f)) / valDiff);
                        resultString += f+":"+((featureVal - minFVals.get(f)) / valDiff)+" ";
                    }
                    else {resultString += f+":0.0 ";}
                }
                resultString += "# "+externalId;
                writer.println(resultString);
            }

        } catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public void writeOrigFeatures(String qid, HashMap<String, HashMap<Integer, Double>> featureMap, PrintWriter writer)
            throws Exception {
        try{
            // now normalizing
            for(Map.Entry<String, HashMap<Integer, Double>> docFeatures: featureMap.entrySet()) {
                HashMap<Integer, Double> featureVals = docFeatures.getValue();
                if(featureVals == null) continue;
                String externalId = docFeatures.getKey();
                String rjScore = this.relevanceJudges.get(qid).get(externalId);
                //String resultString = String.format("%s qid:%s ", rjScore, qid);
                String resultString = rjScore+" qid:"+qid+" ";

                for(int f: this.featureSet){
                    double featureVal = featureVals.get(f);
                    if(featureVal != -1.0) {
                        //   resultString += String.format("%d:%f ", f, (featureVal - minFVals.get(f)) / valDiff);
                        resultString += f+":"+featureVal+" ";
                    }
                    else {resultString += f+":0.0 ";}
                }
                resultString += "# "+externalId;
                writer.println(resultString);
            }

        } catch(Exception e){
            throw new RuntimeException(e);
        }
    }


    public void reRank(Map<String, String> parameters) throws Exception {
        BufferedReader test_score = null;
        BufferedReader test_feature = null;
        PrintWriter writer = new PrintWriter(parameters.get("trecEvalOutputPath"), "UTF-8");
        int outputLen = Integer.parseInt(parameters.get("trecEvalOutputLength"));
        try {
            test_score = new BufferedReader(new FileReader(parameters.get("ltr:testingDocumentScores")));
            test_feature = new BufferedReader(new FileReader(parameters.get("ltr:testingFeatureVectorsFile")));
            String score_line = null, feature_line = null;

            ScoreList rerank_list = new ScoreList();
            String currQid = "X";
            boolean initialQid = true;
            while(((score_line = test_score.readLine()) != null) &&
                    ((feature_line = test_feature.readLine()) != null)) {
                String[] features = feature_line.split(" ");
                String qid = features[1].split(":")[1];
                String externalId = features[features.length - 1].trim();
                int docid = Idx.getInternalDocid(externalId);
                double doc_score = 0.0;
                if (this.toolkit == "SVMRank"){
                    doc_score = Double.parseDouble(score_line.trim());
                }
                else {
                    String[] doc_scores = score_line.split("\t");
                    //System.out.println("Current Score Line is "+doc_scores[doc_scores.length-1]);
                    doc_score = Double.parseDouble(doc_scores[doc_scores.length-1].trim());
                }
                if (! qid.equals(currQid)){
                    rerank_list.sort();
                    rerank_list.truncate(outputLen);
                    if (initialQid) {initialQid = false;}
                    else{
                        QryEval.writeIntoTrecEval(writer, currQid, rerank_list);
                    }
                    currQid = qid;
                    rerank_list = new ScoreList();
                }
                rerank_list.add(docid, doc_score);
            }
            rerank_list.sort();
            rerank_list.truncate(outputLen);
            QryEval.writeIntoTrecEval(writer, currQid, rerank_list);
            writer.close();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
        test_score.close();
        test_feature.close();

    }

    public double getScoreBM25 (int docid, String[] queryTerms, String field) throws IOException {
        //  TermVector version of BM25
        TermVector tv = new TermVector(docid, field);
        if(tv.stemsLength() <= 0) {return -1.0;}
        double score = 0.0;
        double doclen_d = Idx.getFieldLength(field, docid);
        double avg_doclen = Idx.getSumOfFieldLengths(field) / (float) Idx.getDocCount (field);
        double N = Idx.getNumDocs();
        //  BM25 Model return IDF*DF(adj)*Query Weight for each term
        for (String term: queryTerms){
            int termIdx = tv.indexOfStem(term);
            if(termIdx != -1){
                // if the term exists
                int tf = tv.stemFreq(termIdx);
                // calculate three parts separately
                // NOTE for the idf part, we need to make sure it's none zero
                double df = tv.stemDf(termIdx);
                double idf = Math.max(0, Math.log((N-df+0.5)/(df+0.5)));
                double tf_w = tf/(tf+this.k1*((1-this.b)+this.b*(doclen_d/avg_doclen)));
                // assuming qtf = 1 for all BM25 models, then k3 does not matter here (rbm25.k3+1)/(rbm25.k3+1);
                double user_w = 1.0;
                score += idf*tf_w*user_w;

            }
        }
        return score;
    }

    public double getScoreIndri (int docid, String[] queryTerms, String field) throws IOException {

        //  TermVector version of Indri
        TermVector tv = new TermVector(docid, field);
        if(tv.stemsLength() <= 0) {return -1.0;}
        double score = 1.0;
        double doclen_d = Idx.getFieldLength(field, docid);
        int nonexistTerm = 0;
        for (String term: queryTerms){
            double termScore;
            int termIdx = tv.indexOfStem(term);
            double ctf = Idx.getTotalTermFreq(field, term);
            if (ctf == 0){ctf = 0.5;}
            double p_mle_corpus = ctf/Idx.getSumOfFieldLengths(field);
            if(termIdx != -1){
                // if the term exists
                double tf = tv.stemFreq(termIdx);
                termScore = (1.0-this.lambda)*(tf+(this.miu*p_mle_corpus))/(doclen_d+this.miu)+
                        this.lambda*p_mle_corpus;
            }
            else{
                nonexistTerm+=1;
                termScore = (1.0-this.lambda)*(this.miu*p_mle_corpus)/(doclen_d+this.miu)+
                        this.lambda*p_mle_corpus;
            }
            if ((doclen_d == 0) && (this.miu==0)){
                // NOTE we need to make sure it's none zero`
                termScore = this.lambda*p_mle_corpus;
            }

            score *= termScore;
        }
        if (nonexistTerm == queryTerms.length){
            score = 0.0;}
        else {
            score = Math.pow(score, 1.0/queryTerms.length);
        }

        return score;
    }

    public double getTermOverlap (int docid, String[] queryTerms, String field) throws IOException {

        TermVector tv = new TermVector(docid, field);
        if(tv.stemsLength() <= 0) {return -1.0;}
        double score = 0.0;
        for (String term: queryTerms){
            int termIdx = tv.indexOfStem(term);
            if (termIdx != -1) {
                score+=1;
            }
        }
        return score;
    }

    public double getScoreRankedBool (int docid, String[] queryTerms, String field) throws IOException {
        TermVector tv = new TermVector(docid, field);
        double score = Double.MAX_VALUE;
        if(tv.stemsLength() <= 0) {return -1.0;}
        for (String term: queryTerms){
            int termIdx = tv.indexOfStem(term);
            if (termIdx != -1) {
                score = Math.min(score, tv.stemFreq(termIdx));}
        }
        if (score == Double.MAX_VALUE) {score = 0.0;}

        return score;
    }

    public double getAvgTermFreq (int docid, String[] queryTerms, String field) throws IOException {
        TermVector tv = new TermVector(docid, field);
        int totalTF = 0;
        int termCount = 0;
        if(tv.stemsLength() <= 0) {return -1.0;}
        for (String term: queryTerms){
            int termIdx = tv.indexOfStem(term);
            if (termIdx != -1) {
                termCount+=1;
                totalTF+=tv.stemFreq(termIdx);}
        }
        if (termCount == 0){return 0.0;}
        return (double) totalTF / (double) termCount;
    }

    public double getTermKeywordRate (int docid, String[] queryTerms) throws IOException {
        TermVector tv = new TermVector(docid, "keywords");
        if(tv.stemsLength() <= 0) {return -1.0;}
        double score = 0.0;
        for (String term: queryTerms){
            int termIdx = tv.indexOfStem(term);
            if (termIdx != -1) {
                score+=1;
            }
        }
        return score/(double) tv.stemsLength();
    }

    public double getTermFreqStd (int docid, String[] queryTerms, String field) throws IOException {
        double avgTF = getAvgTermFreq(docid, queryTerms, field);
        if(avgTF <= 0.0) {return -1.0;}
        // not need to check stem length given avgTF
        TermVector tv = new TermVector(docid, field);
        int termCount = tv.stemsLength();
        if (termCount == 0) {return -1.0;}
        double std = 0.0;
        for (int i=0;i<termCount;i++){
            int stemIdx = tv.stemAt(i);
            if (stemIdx > 0) {
                int freq = tv.stemFreq(stemIdx);
                std += Math.pow(avgTF - freq, 2);
            }
        }
        if (std == termCount*termCount) {return -1.000000000001;}
        return -Math.sqrt(std)/termCount;
    }

    public double getAvgTermLength (int docid, String[] queryTerms, String field) throws IOException {
        // not need to check stem length given avgTF
        TermVector tv = new TermVector(docid, field);
        if(tv.stemsLength() <= 0) {return -1.0;}
        int termCount = tv.positionsLength();
        int termTotalLength = 0, stopCount = 0;

        for (int i=0;i<termCount;i++){
            int stemIdx = tv.stemAt(i);
            if (stemIdx == 0) {stopCount++;}
            else{
            String term = tv.stemString(stemIdx);
            termTotalLength += term.length();
            }
        }
        if (termCount == stopCount) {
            return -1.0;
        }
        double avgLength = termTotalLength / (termCount-stopCount);

        return 1.0/avgLength;
    }

    public double getStopwordFrac (int docid, String field) throws IOException {
        TermVector tv = new TermVector(docid, field);
        if(tv.stemsLength() <= 0) {return -1.0;}
        double wordLen = 0.0;
        for (int i = 1; i < tv.stemsLength(); i++) {
            wordLen += tv.stemAt(i);
        }
        double stopwordLen = tv.positionsLength() - wordLen;
        return stopwordLen / (double) tv.positionsLength();
    }

    public double getInlinkAuthority (int docid) throws IOException {
        TermVector tv = new TermVector(docid, "inlink");
        if(tv.stemsLength() <= 0) {return -1.0;}
        double score = 0.0;
        for (int i = 1; i < tv.stemsLength(); i++) {
            int stemIdx = tv.stemAt(i);
            //System.out.println(stemIdx);
            if (stemIdx > 0) {
                String inlink = tv.stemString(stemIdx);
                //System.out.println(inlink);
                if (inlink.equals("gov") || inlink.equals("edu") ||
                        inlink.equals("org") || inlink.contains(".org")) {
                    score+=1;
                }
                else if (inlink.equals("net") || inlink.equals("com") || inlink.contains(".com")) {score+=0.2;}
            }
        }
        return score;
    }

    public String defaultQrySopName () {
        return new String ("#and");
    }

}
