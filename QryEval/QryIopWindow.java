/**
 *  Copyright (c) 2022, Carnegie Mellon University.  All Rights Reserved.
 */
import javax.swing.*;
import java.io.*;
import java.util.*;

/**
 *  The Window operator for all retrieval models.
 */
public class QryIopWindow extends QryIop {
    public int distance;
    public QryIopWindow(int distance){
        // constructor to create WINDOW operator with input distance
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
     *  Helper Function to checking if any of the inverted list has exhausted
     *  ONLY return true if all query inverted lists has location iterator pointing to somewhere not null
     */
    public boolean checkAnyExhausted () {
        for (int j = 0; j < this.args.size(); j++) {
            QryIop q_j = (QryIop) this.args.get(j);
            if (!q_j.locIteratorHasMatch()) {
                return false;
            }}
        return true;
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
        // WINDOW operator should take at least 2 arguments, else we simply
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
                List<Integer> max_locids = new ArrayList<Integer>();
                // we loop into the location. Note all Qry is now pointing to the same docid already
                // via the call of checkDocidExist
                // while none of iterator exhaust
                boolean not_exhausted = checkAnyExhausted();
                while (not_exhausted) {
                    // set up
                    int min_qry_idx = 0;
                    int max_qry_idx = 0;
//                    int max_locid = 0;
//                    int min_locid = 0;
                    // loop through entire query list to get max and min
                    for (int j = 0; j < this.args.size(); j++) {
                        QryIop q_j = (QryIop) this.args.get(j);
                        // has match must work here under not_exhausted condition
                        int locid_j = q_j.locIteratorGetMatch();
//                        max_locid = ((QryIop) this.args.get(max_qry_idx)).locIteratorGetMatch();
//                        min_locid = ((QryIop) this.args.get(min_qry_idx)).locIteratorGetMatch();
                        // update the iterator holding min and max location id
                        if (((QryIop) this.args.get(min_qry_idx)).locIteratorGetMatch() > locid_j) {
                            min_qry_idx = j;
                        } else if (((QryIop) this.args.get(max_qry_idx)).locIteratorGetMatch() < locid_j) {
                            max_qry_idx = j;
                        }
                        // otherwise, current locid falling in the min-max range
                    }
                    int max_locid = ((QryIop) this.args.get(max_qry_idx)).locIteratorGetMatch();
                    int min_locid = ((QryIop) this.args.get(min_qry_idx)).locIteratorGetMatch();

                    // check if the min-max range is within window range
                    if (Math.abs(max_locid - min_locid) < this.distance) {
                        // if we have a match, add max locid to list and advance all iterators
                        max_locids.add(max_locid);
                        // move the all loc iterator to the next location
                        for (int k = 0; k < this.args.size(); k++) {
                            Qry q_k = this.args.get(k);
                            ((QryIop) q_k).locIteratorAdvance();
                        }
                    } else {
                        // WINDOW does not match, then move the min locid if not exhausted
                        ((QryIop) this.args.get(min_qry_idx)).locIteratorAdvance();
                    }
                    // at the end, check if every node after the loop is not exhausted
                    not_exhausted = checkAnyExhausted();
                }

                // append the doc-loc posting to inverted list, if not empty
                if (max_locids.size() > 0) {
                    // make sure the list of locid is sorted
                    Collections.sort (max_locids);
                    this.invertedList.appendPosting (q_0_docid, max_locids);
                }
            }
            // regardless we have a match or not, at the end, we advance the leftmost doc iterator
            // passing the current matched docid
            q_0.docIteratorAdvancePast(q_0_docid);
        }

    }
}

