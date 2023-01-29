/**
 *  Copyright (c) 2022, Carnegie Mellon University.  All Rights Reserved.
 */
import java.io.*;
import java.util.*;

/**
 *  The Near operator for all retrieval models.
 */
public class QryIopNear extends QryIop {
    public int distance;
    public QryIopNear(int distance){
        // constructor to create NEAR operator with input distance
        this.distance = distance;
    }

    /**
     *  Helper Function to checking if docid exist in the inverted list of input QryIop
     *  assuming the inverted list is already sorted by docid
     */
    public boolean checkDocidExist (int docid, Qry q) {
        // advance the input qry to anchored docid
        q.docIteratorAdvanceTo(docid);
        // check if Qry q is pointing to any docid
        // if not, the doc iterator of q is exhausted, and hence no match possible
        if (! q.docIteratorHasMatch(null)) {return false;}
        // get the docid where q is pointing to, this should be docid if docid exists
        // in the inverted list of Qry q, else it would surpass docid
        int q_docid = q.docIteratorGetMatch();
        return q_docid == docid;
    }

    /**
     *  Helper Function to checking if locid between two terms satisfies the distance restriction
     */
    public boolean checkNearSatisfy (int locid, Qry q, int distance) {

        // first advance q to pass the left hand side locid
        ((QryIop) q).locIteratorAdvancePast(locid);
        // check if Qry q is pointing to any locid
        // if not, the loc iterator of q is exhausted, and hence no match possible
        if (!((QryIop) q).locIteratorHasMatch()) {return false;}
        // get the current locid of q, this would be the closest locid to input locid
        int q_locid = ((QryIop) q).locIteratorGetMatch();
        return (q_locid-locid <= distance);
    }

    /**
     *  Evaluate the query operator; the result is an internal inverted
     *  list that may be accessed via the internal iterators.
     *  @throws IOException Error accessing the Lucene index.
     */
    protected void evaluate () throws IOException {

        //  Create an empty inverted list.  If there are no query arguments,
        //  this is the final result.

        this.invertedList = new InvList (this.getField());
        // NEAR operator should take at least 2 arguments, else we simply
        // return the empty invertedList
        if (args.size () <= 1) {
            return;
        }

        // Anchor the initial Qry and loop through the remaining Qry
        Qry q_0 = this.args.get(0);
        // loop until the anchored inverted list is exhausted
        while (q_0.docIteratorHasMatch(null)) {
            // get the current leftmost docid id and search for remaining Qry for same docid
            int q_0_docid = q_0.docIteratorGetMatch ();
            boolean docid_all_match = true;
            // starting from the next Qry
            for (int i=1; i<this.args.size(); i++){
                Qry q_i = this.args.get(i);
                docid_all_match = checkDocidExist(q_0_docid, q_i);
                // as long as we have an inverted list without the docid, we break the for loop
                if (!docid_all_match)
                    break;
            }
            if (docid_all_match){
                // once we find a common docid among all inverted lists,
                // create new position list per docid to record potential match
                List<Integer> rightmost_locids = new ArrayList<Integer>();
                // we loop into the location. Note all Qry is now pointing to the same docid already
                // via the call of checkDocidExist
                while (((QryIop) q_0).locIteratorHasMatch()){
                    int curr_locid = ((QryIop) q_0).locIteratorGetMatch();
                    boolean locid_near_match = true;
                    for (int j=1; j<this.args.size(); j++){
                        Qry q_j = this.args.get(j);
                        // as long as one locid fails, we break the for loop
                        locid_near_match = checkNearSatisfy(curr_locid, q_j, this.distance);
                        // either NEAR does not match, or q_j is exhausted and would not have a match
                        if (!locid_near_match)
                            break;
                        // if near is match, set the current locid to be the righter one for the next iteration
                        curr_locid = ((QryIop) q_j).locIteratorGetMatch();
//                        locid_near_match = checkNearSatisfy(curr_locid, q_j, this.distance);
//                        curr_locid = ((QryIop) q_j).locIteratorGetMatch();
                        // check if the q_j is exhausted after advancing, and if so
                        // break the for loop
//                        if (!((QryIop) q_j).locIteratorHasMatch()) {break;}
                    }
                    if (locid_near_match) {
                        // record the right most location
                        int rightmost_loc = curr_locid;
                        rightmost_locids.add(rightmost_loc);
                        // move the all loc iterator to the next location
                        for (int k = 0; k < this.args.size(); k++) {
                            Qry q_k = this.args.get(k);
                            ((QryIop) q_k).locIteratorAdvance();
                        }
                    }
                    // if there exists unmatched distance, then we advance ONLY the anchor iterator
                    else{((QryIop) q_0).locIteratorAdvance();}
                }
                // append the doc-loc posting to inverted list, if not empty
                if (rightmost_locids.size() > 0) {
                    // make sure the list of locid is sorted
                    Collections.sort (rightmost_locids);
                    this.invertedList.appendPosting (q_0_docid, rightmost_locids);
                }
            }
            // regardless we have a match or not, at the end, we advance the leftmost doc iterator
            // passing the current matched docid
            q_0.docIteratorAdvancePast(q_0_docid);
            }

        }
    }

