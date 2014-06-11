package edu.stanford.nlp.neural.rnn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;

import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;

/**
 * This class stores the best K ngrams for each class for a model.  It
 * does so by keeping priority queues of the best K trees seen,
 * eliminating duplicates.
 * <br>
 * The interface is not too advanced at the moment.  To use, first
 * create it with the number of classes and the counts of the ngrams,
 * and then add trees one at a time with countTree.  The results can
 * be extracted with toString().  There is no way to directly access
 * the internal results.
 *
 * @author John Bauer
 */
public class TopNGramRecord {
  /** how many ngrams to store for each class */
  final private int ngramCount;

  /** How many classes we are storing */
  final private int numClasses;

  /** Longest ngram to keep */
  final private int maximumLength;

  Map<Integer, Map<Integer, PriorityQueue<Tree>>> classToNGrams = Generics.newHashMap();

  public TopNGramRecord(int numClasses, int ngramCount, int maximumLength) {
    this.numClasses = numClasses;
    this.ngramCount = ngramCount;
    this.maximumLength = maximumLength;
    for (int i = 0; i < numClasses; ++i) {
      Map<Integer, PriorityQueue<Tree>> innerMap = Generics.newHashMap();
      classToNGrams.put(i, innerMap);
    }
  }

  /**
   * Adds the tree and all its subtrees to the appropriate
   * PriorityQueues for each predicted class.
   */
  public void countTree(Tree tree) {
    for (int i = 0; i < numClasses; ++i) {
      countTreeHelper(tree, i, classToNGrams.get(i));
    }
  }

  private int countTreeHelper(Tree tree, int prediction, Map<Integer, PriorityQueue<Tree>> ngrams) {
    if (tree.isLeaf()) {
      return 1;
    }
    int treeSize = 0;
    for (Tree child : tree.children()) {
      treeSize += countTreeHelper(child, prediction, ngrams);
    }
    if (maximumLength > 0 && treeSize > maximumLength) {
      return treeSize;
    }
    PriorityQueue<Tree> queue = getPriorityQueue(treeSize, prediction, ngrams);
    // TODO: should we allow classes which aren't the best possible
    // class for this tree to be included in the results?
    if (!queue.contains(tree)) {
      queue.add(tree);
    }
    if (queue.size() > ngramCount) {
      queue.poll();
    }
    return treeSize;
  }

  private PriorityQueue<Tree> getPriorityQueue(int size, int prediction, Map<Integer, PriorityQueue<Tree>> ngrams) {
    PriorityQueue<Tree> queue = ngrams.get(size);
    if (queue != null) {
      return queue;
    }
    queue = new PriorityQueue<Tree>(ngramCount + 1, scoreComparator(prediction));
    ngrams.put(size, queue);
    return queue;
  }

  private Comparator<Tree> scoreComparator(final int prediction) { 
    return new Comparator<Tree>() {
      public int compare(Tree tree1, Tree tree2) {
        double score1 = RNNCoreAnnotations.getPredictions(tree1).get(prediction);
        double score2 = RNNCoreAnnotations.getPredictions(tree2).get(prediction);
        if (score1 < score2) {
          return -1;
        } else if (score1 > score2) {
          return 1;
        } else {
          return 0;
        }
      }
    };
  }

  public String toString() {
    StringBuilder result = new StringBuilder();
    for (int prediction = 0; prediction < numClasses; ++prediction) {
      result.append("Best scores for class " + prediction + "\n");
      Map<Integer, PriorityQueue<Tree>> ngrams = classToNGrams.get(prediction);
      for (Map.Entry<Integer, PriorityQueue<Tree>> entry : ngrams.entrySet()) {
        ArrayList<Tree> trees = new ArrayList<Tree>(entry.getValue());
        Collections.sort(trees, scoreComparator(prediction));
        result.append("  Len " + entry.getKey() + "\n");
        for (int i = trees.size() - 1; i >= 0; i--) {
          Tree tree = trees.get(i);
          result.append("    " + Sentence.listToString(tree.yield()) + "  [" + RNNCoreAnnotations.getPredictions(tree).get(prediction) + "]\n");
        }
      }
    }
    return result.toString();
  }
}