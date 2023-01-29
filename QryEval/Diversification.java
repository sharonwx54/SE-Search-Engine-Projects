import java.io.*;
import java.util.*;
import java.util.Map;


public class Diversification {
    public double lambda;
    public String algo = "", intentFile = "", initRankFile = "";
    public RetrievalModel model;
    public boolean scale;
    public int resultRankLen = 0, inputRankLen = 0;
    public HashMap<String, ScoreList> initRank = null;
    public HashMap<String, HashMap<String, ScoreList>> initIntent = null;
    public  HashMap<String, ArrayList<String>> qryIntentContent, qryIntentId;

    public Diversification(Map<String, String> parameters, RetrievalModel model){
        this.lambda = Double.parseDouble(parameters.get("diversity:lambda"));
        this.model = model;
        this.algo = parameters.get("diversity:algorithm").toLowerCase();
        this.scale = false;
        this.intentFile = parameters.get("diversity:intentsFile");
        if (parameters.get("diversity:initialRankingFile") != null){
            // not necessarily has item
            this.initRankFile = parameters.get("diversity:initialRankingFile");
        }
        this.inputRankLen = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
        this.resultRankLen = Integer.parseInt(parameters.get("diversity:maxResultRankingLength"));
    }

    public void runDiversification(Map<String, String> parameters) throws Exception {
        if (this.initRankFile.equals("")){
            // if no initial ranking file provided, run query by model
            initIntent(this.intentFile);
        }
        else{
            // initialize ranking
            initRank(this.initRankFile);
        }

        processQuery(parameters);
    }

    public void initRank(String fileName) throws Exception{
        this.initRank = new HashMap<>();
        this.initIntent = new HashMap<>();
        this.qryIntentId = new HashMap<>();
        BufferedReader input = null;

        try {
            //System.out.println("Initializing Ranking");
            input = new BufferedReader(new FileReader(fileName));
            String line = null;
            ScoreList rankS = new ScoreList();
            String currQid = "X";
            while ((line = input.readLine()) != null){
                String[] info = line.split(" ");
                String qid = info[0].trim();
                String intent_id = "";
                int docid = Idx.getInternalDocid(info[2].trim());
                double score = Double.parseDouble(info[4].trim());
                if (currQid.equals("X")){
                    // to start, set currQid to initial intake qid
                    currQid = qid;
                }
                // check if the queryID is a new start
                if (! qid.equals(currQid)){
                    // wrapping up last QID
                    // check the score is rank or intent
                    if (currQid.contains(".")){
                        // this is intent
                        String[] ids = currQid.split("\\.");
                        String query_id = ids[0];
                        intent_id = ids[1];
                        // for intent, check if qid exists
                        if (! this.qryIntentId.containsKey(query_id)) {
                            this.qryIntentId.put(query_id, new ArrayList<>());
                            this.initIntent.put(query_id, new HashMap<>());
                        }
                        this.qryIntentId.get(query_id).add(intent_id);
                        this.initIntent.get(query_id).put(intent_id, rankS);
                    }
                    else {
                        // for query rank, no need to check as each qid should only have one scorelist
                        this.initRank.put(currQid, rankS);
                    }
                    // after storing, reset
                    currQid = qid;
                    rankS = new ScoreList();
                }
                // regardless, add the score to ScoreList
                rankS.add(docid, score);
            }
            // handling the last score
            if (! currQid.equals("X")){
                if (currQid.contains(".")){
                    // this is intent
                    String[] ids = currQid.split("\\.");
                    String query_id = ids[0];
                    // for intent, check if qid exists
                    if (! this.qryIntentId.containsKey(query_id)) {
                        this.qryIntentId.put(query_id, new ArrayList<>());
                        this.initIntent.put(query_id, new HashMap<>());
                    }
                    this.qryIntentId.get(query_id).add(ids[1]);
                    this.initIntent.get(query_id).put(ids[1], rankS);
                }
                else{
                    this.initRank.put(currQid, rankS);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            input.close();
        }
    }

    public void initIntent(String fileName) throws Exception{
        // only call if initRank is not called
        this.qryIntentId = new HashMap<>();
        this.qryIntentContent = new HashMap<>();

        BufferedReader input = null;
        try {
            //System.out.println("Initializing Intent");
            input = new BufferedReader(new FileReader(fileName));
            String line = null;
            while ((line = input.readLine()) != null){
                String[] info = line.split(":");
                String[] ids = info[0].trim().split("\\.");
                String qid = ids[0].trim();
                String intent_id = ids[1].trim();
                String content = info[1].trim();
                // System.out.println(qid+" & "+intent_id);
                // check if the queryID is a new start
                if (! this.qryIntentId.containsKey(qid)){
                    this.qryIntentId.put(qid, new ArrayList<>());
                    //System.out.println("adding QID "+qid);
                }
                if (! this.qryIntentContent.containsKey(qid)){
                    this.qryIntentContent.put(qid, new ArrayList<>());
                }
                this.qryIntentId.get(qid).add(intent_id);
                this.qryIntentContent.get(qid).add(content);
            }
            //System.out.println("Finish initializing Intent with IntentNum "+this.qryIntentId.size());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            input.close();
        }
    }

    public ArrayList<ArrayList<Double>> truncateScale(ScoreList qScores, HashMap<String, ScoreList> intentScores){
        ArrayList<ArrayList<Double>> trunScores = truncateRank(qScores, intentScores);
        if (this.scale) {
            //System.out.println("Entering Scaling");
            double scaleBase = getScaleBase(trunScores, intentScores.size());
            System.out.println("Scaling Based is "+scaleBase);
            for (int i = 0; i < trunScores.size(); i++) {
                for (int j = 0; j < intentScores.size() + 1; j++)
                    trunScores.get(i).set(j, trunScores.get(i).get(j) / scaleBase);
            }
        }

        return trunScores;
    }

    public double getScaleBase(ArrayList<ArrayList<Double>> trunScores, int intentNum){
        double scaleBase = 0.0;
        ArrayList<Double> accScores = new ArrayList<>(Collections.nCopies(intentNum+1, 0.0));
        for (int i=0;i<trunScores.size();i++){
            ArrayList<Double> qScores = trunScores.get(i);
            for (int j=0;j<intentNum;j++){
                accScores.set(j, qScores.get(j)+accScores.get(j));
            }
        }
        scaleBase = Collections.max(accScores);

        return scaleBase;
    }

    public ArrayList<ArrayList<Double>> truncateRank(ScoreList qScores, HashMap<String, ScoreList> intentScores){
        // get min length to truncate
        int rankLen = Math.min(this.inputRankLen, qScores.size());
        //System.out.println("Truncating Ranking to "+rankLen);
        // create hashmap and list of list to store
        HashMap<Integer, Integer> docRank = new HashMap<>();
        ArrayList<ArrayList<Double>> trunScores = new ArrayList<>();

        for(int i = 0;i < rankLen;i++){
            docRank.put(qScores.getDocid(i), i);
            double score = qScores.getDocidScore(i);
            if(score > 1.0) {this.scale = true;}
            // first create a empty list of array
            ArrayList<Double> rankIscores = new ArrayList<>(Collections.nCopies(intentScores.size()+1,0.0));
            rankIscores.set(0, score);
            // set the first element to be original query score
            trunScores.add(rankIscores);
        }
        for(String intentId: intentScores.keySet()){
            ScoreList intentSL = intentScores.get(intentId);
            for(int j=0;j<rankLen && j<intentSL.size();j++){
                int docid = intentSL.getDocid(j);
                if(docRank.containsKey(docid)){
                    int rank = docRank.get(docid);
                    double score = intentSL.getDocidScore(j);
                    if(score > 1.0) {this.scale = true;}
                    // update intent query score
                    trunScores.get(rank).set(Integer.parseInt(intentId), score);
                }
            }
        }

        return trunScores;
    }



    public void processQuery(Map<String, String> parameters) throws Exception{
        BufferedReader input = null;
        String query_file = parameters.get("queryFilePath");
        PrintWriter writer = new PrintWriter(parameters.get("trecEvalOutputPath"), "UTF-8");
        int outputLen = Integer.parseInt(parameters.get("trecEvalOutputLength"));

        try {
            String qLine = null;
            input = new BufferedReader(new FileReader(query_file));

            while ((qLine = input.readLine()) != null) {
                String[] pair = qLine.split(":");

                if (pair.length != 2) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Each line must contain one ':'.");
                }
                String qid = pair[0];
                String query = pair[1];
                // initialize scores
                ScoreList qryScores;
                // initial rankings for intents
                HashMap<String, ScoreList> intentScores;
                ArrayList<String> intentIds = this.qryIntentId.get(qid);
                // check init ranking or intent file
                if(! this.initRankFile.equals("")){
                    // fetch info if provided
                    qryScores = this.initRank.get(qid);
                    intentScores = this.initIntent.get(qid);
                }
                else {
                    //System.out.println("Getting Ranking by Retrieval");
                    qryScores = QryEval.processQuery(query, this.model);
                    qryScores.sort();
                    intentScores = new HashMap<>();
                    for (int i=0;i<intentIds.size();i++){
                        // use intent to retrieve
                        String intent = this.qryIntentContent.get(qid).get(i);
                        ScoreList intentSL = QryEval.processQuery(intent, this.model);
                        intentSL.sort();
                        intentScores.put(intentIds.get(i), intentSL);
                    }
                }
                // create document ranking
                HashMap<Integer, Integer> docRank = new HashMap<>();
                for(int s=0; s<qryScores.size(); s++){
                    docRank.put(s, qryScores.getDocid(s));
                }
                // Now scale the scores if needed
                ArrayList<ArrayList<Double>> trunScores = truncateScale(qryScores, intentScores);

                // perform diversification
                ScoreList diverseSL = new ScoreList();
                if (this.algo.equals("xquad")){
                    //System.out.print("Running XQUAD");
                    diverseSL = XQUAD(docRank, trunScores);

                }
                else if (this.algo.equals("pm2")){
                    diverseSL = PM2(docRank, trunScores);
                }
                else {
                    System.out.println("Invalid Diversification Algo");
                }
                diverseSL.sort();
                diverseSL.truncate(outputLen);
                QryEval.writeIntoTrecEval(writer, qid, diverseSL);

            }
            writer.close();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        input.close();
    }


    public ScoreList XQUAD(HashMap<Integer, Integer> docRank, ArrayList<ArrayList<Double>> trunScores){
        ScoreList diverseSL = new ScoreList();
        int intentNum = trunScores.get(0).size()-1;
        double intentW = 1.0/intentNum;

        // create a list for current coverage
        ArrayList<Double> coverScores = new ArrayList<>(Collections.nCopies(intentNum+1, 1.0));
        // add document to diversify to a hashset to avoid any duplicates
        HashSet<Integer> toDiverseDocs = new HashSet<>();
        for(int i=0; i<trunScores.size(); i++) {
            toDiverseDocs.add(i);
        }
        // output length is limited
        int outputLen = Math.min(this.resultRankLen, toDiverseDocs.size());
        while (diverseSL.size() < outputLen){
            int maxDocid = -1;
            double maxDocScore = -Double.MAX_VALUE;
            for(int docid: toDiverseDocs){
                ArrayList<Double> qScores = trunScores.get(docid);
                double diverseScore = 0.0;
                for (int i=1;i<=intentNum;i++){
                    // multiply weight for each docid, not each intent
                    diverseScore += intentW * qScores.get(i) * coverScores.get(i);;
                }
                double currScore = (1-this.lambda)*qScores.get(0)+this.lambda*diverseScore;
                if (maxDocScore < currScore) {
                    maxDocScore = currScore;
                    maxDocid=docid;
                }
            }
            // then update the coverage list
            for(int c=1;c<=intentNum;c++){
                coverScores.set(c, coverScores.get(c)*(1.0-trunScores.get(maxDocid).get(c)));
            }
            // at the end, remove the max docid from the list of to diverse
            toDiverseDocs.remove(maxDocid);
            diverseSL.add(docRank.get(maxDocid), maxDocScore);
        }

        return diverseSL;

    }


    public ScoreList PM2(HashMap<Integer, Integer> docRank, ArrayList<ArrayList<Double>> trunScores){
        ScoreList diverseSL = new ScoreList();
        int intentNum = trunScores.get(0).size()-1;

        // create a list for slot saving
        ArrayList<Double> slotScores = new ArrayList<>(Collections.nCopies(intentNum+1, 0.0));
        // add document to diversify to a hashset to avoid any duplicates
        HashSet<Integer> toDiverseDocs = new HashSet<>();
        for(int i=0; i<trunScores.size(); i++) {
            toDiverseDocs.add(i);
        }
        // output length is limited
        int outputLen = Math.min(this.resultRankLen, toDiverseDocs.size());
        double vi = 1.0*outputLen/intentNum;

        while (diverseSL.size() < outputLen){
            // first select the max intent
            int maxIntent = -1;
            double maxPriority = -Double.MAX_VALUE;
            HashMap<Integer, Double> priorities = new HashMap<>();
            for (int i=1;i<=intentNum;i++){
                 double qt = vi/(2*slotScores.get(i)+1);
                 priorities.put(i, qt);
                 if (maxPriority < qt){
                     maxPriority = qt;
                     maxIntent = i;
                 }
            }

            // calculate score for each doc
            int maxDocid = -1;
            double maxDocScore = -Double.MAX_VALUE;
            for(int docid: toDiverseDocs){
                ArrayList<Double> qScores = trunScores.get(docid);
                double maxIntentScore = maxPriority*qScores.get(maxIntent);
                double otherIntentScore = 0.0;
                for (int i=1;i<=intentNum;i++){
                    // exclude max intent
                    if (i != maxIntent){
                        otherIntentScore += priorities.get(i)*qScores.get(i);
                    }
                }
                double currScore = (1-this.lambda)*otherIntentScore+this.lambda*maxIntentScore;
                if (maxDocScore < currScore) {
                    maxDocScore = currScore;
                    maxDocid=docid;
                }
            }

             // NOTE there could be the case that all intent score is 0
             // then we want to preserve the ordering and give a score
            if(maxDocScore == 0.0){
                System.out.println("Max Doc Score is hitting ZERO");
                for(int d=0;d<trunScores.size() && diverseSL.size()<outputLen; d++){
                    if(toDiverseDocs.contains(d)){
                        // 0.8 is somewhat random, believe any number here works
                        double score = 0.8*diverseSL.getDocidScore(diverseSL.size()-1);
                        diverseSL.add(docRank.get(d), score);
                        toDiverseDocs.remove(d);
                    }
                }
                return diverseSL;
            }

            // update the slot for every intent
            ArrayList<Double> qScoresMaxDocid = trunScores.get(maxDocid);
            double adjustBased = 0.0;
            for (int m=1;m<=intentNum;m++){
                adjustBased+=qScoresMaxDocid.get(m);
            }
            for (int n=1;n<=intentNum;n++){
                slotScores.set(n, slotScores.get(n)+qScoresMaxDocid.get(n)/adjustBased);
            }

            // at the end, remove the max docid from the list of to diverse
            toDiverseDocs.remove(maxDocid);
            diverseSL.add(docRank.get(maxDocid), maxDocScore);
        }

        return diverseSL;

    }



}
