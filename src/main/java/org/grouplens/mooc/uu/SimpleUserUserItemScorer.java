package org.grouplens.mooc.uu;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.History;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;

import javax.annotation.Nonnull;
import javax.inject.Inject;

/**
 * User-user item scorer.
 * @author Kunal Taneja
 */
public class SimpleUserUserItemScorer extends AbstractItemScorer {
    private final UserEventDAO userDao;
    private final ItemEventDAO itemDao;

    @Inject
    public SimpleUserUserItemScorer(UserEventDAO udao, ItemEventDAO idao) {
        userDao = udao;
        itemDao = idao;
    }
	/**
	 * Calculate the predicted scores of a set of items for the input user
	 */
    @Override
    public void score(long user, @Nonnull MutableSparseVector scores) {
        SparseVector userVector = getUserRatingVector(user);
        
        
        for (long itemToScore : scores.keyDomain()) {
        	double predectedRating = 0.0;//userVector.mean();
        	double weight = 0.0;
        	LongSet neighbours = getTopNeighbours(user, itemToScore);
        	//For each neighbour increment the predicted score
        	for (Long neighbour : neighbours) {
        		SparseVector neighbourVector = getUserRatingVector(neighbour);
				double neighbourMeanRating = neighbourVector.mean();
				double neighbourRating = neighbourVector.get(itemToScore);
				double offsetFromMean = neighbourRating - neighbourMeanRating;
				double similarity = similarityMap.get(neighbour);
				predectedRating = predectedRating + offsetFromMean*similarity;
				weight = weight + Math.abs(similarity);
			}
        	//divide by total weight and add mean ratings
        	predectedRating = predectedRating/weight+ userVector.mean();
        	scores.set(itemToScore, predectedRating);
		}
    }
    /*Stores the similarity of this user with every other users*/
    HashMap<Long,Double> similarityMap;
	/**
	 * Fins cosign similarity with this user with all other users and 
	 * returns a set of top 30 neighbours
	 * @param user
	 * @param itemToScore
	 * @return
	 */
    private LongSet getTopNeighbours(long user, long itemToScore) {
		// TODO Auto-generated method stub
    	SparseVector userVector = getUserRatingVector(user);
    	LongSet users = itemDao.getUsersForItem(itemToScore);
    	LongSet topNeighbours = new LongOpenHashSet();
    	
    	similarityMap = new HashMap<Long,Double>();
    	ValueComparator similarityComparator = new ValueComparator(similarityMap);
    	/*This map will sort the scores*/
    	TreeMap<Long, Double> sortedSimilarityMap = new TreeMap<Long, Double>(similarityComparator);
    	
    	
    	for (Long potentialNeighbour : users) {
    		if(user == potentialNeighbour)
    			continue;
			double similarity = calculateUserSomilarity(userVector, getUserRatingVector(potentialNeighbour));
			similarityMap.put(potentialNeighbour, similarity);
		}
    	sortedSimilarityMap.putAll(similarityMap);
    	int i =0;
    	//Adding top 30 neighbours to the resultSet
    	for (Long neighbour : sortedSimilarityMap.keySet()){
    		topNeighbours.add(neighbour);
    		if(i++ == 29)
    			break;
    	}
    	return topNeighbours;
	}

	private double calculateUserSomilarity(SparseVector userVector, SparseVector otherUserVector) {
		MutableSparseVector userCopy = userVector.mutableCopy();
		MutableSparseVector otherUserCopy = otherUserVector.mutableCopy();
		userCopy.subtract(MutableSparseVector.create(userCopy.keyDomain(), userVector.mean()));
		otherUserCopy.subtract(MutableSparseVector.create(otherUserCopy.keyDomain(), otherUserCopy.mean()));
		
		CosineVectorSimilarity similarity = new CosineVectorSimilarity();
		return similarity.similarity(userCopy, otherUserCopy);
	}

	/**
     * Get a user's rating vector.
     * @param user The user ID.
     * @return The rating vector.
     */
    private SparseVector getUserRatingVector(long user) {
        UserHistory<Rating> history = userDao.getEventsForUser(user, Rating.class);
        if (history == null) {
            history = History.forUser(user);
        }
        return RatingVectorUserHistorySummarizer.makeRatingVector(history);
    }
}
/*Used for sorting similarity scores*/
class ValueComparator implements Comparator<Long> {
    Map<Long, Double> base;
    public ValueComparator(Map<Long, Double> base) {
        this.base = base;
    }
    // Note: this comparator imposes orderings that are inconsistent with equals.    
    public int compare(Long a, Long b) {
        if (base.get(a) >= base.get(b)) {
            return -1;
        } else {
            return 1;
        } // returning 0 would merge keys
    }
}
