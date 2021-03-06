/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.search.similarities;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;

/**
 * BM25 Similarity. Introduced in Stephen E. Robertson, Steve Walker,
 * Susan Jones, Micheline Hancock-Beaulieu, and Mike Gatford. Okapi at TREC-3.
 * In Proceedings of the Third <b>T</b>ext <b>RE</b>trieval <b>C</b>onference (TREC 1994).
 * Gaithersburg, USA, November 1994.
 */
public class BM25Similarity extends Similarity {
  private final float k1;
  private final float b;

  /**
   * BM25 with the supplied parameter values.
   * @param k1 Controls non-linear term frequency normalization (saturation).
   * @param b Controls to what degree document length normalizes tf values.
   * @throws IllegalArgumentException if {@code k1} is infinite or negative, or if {@code b} is 
   *         not within the range {@code [0..1]}
   */
  public BM25Similarity(float k1, float b) {
    if (Float.isFinite(k1) == false || k1 < 0) {
      throw new IllegalArgumentException("illegal k1 value: " + k1 + ", must be a non-negative finite value");
    }
    if (Float.isNaN(b) || b < 0 || b > 1) {
      throw new IllegalArgumentException("illegal b value: " + b + ", must be between 0 and 1");
    }
    this.k1 = k1;
    this.b  = b;
  }
  
  /** BM25 with these default values:
   * <ul>
   *   <li>{@code k1 = 1.2}</li>
   *   <li>{@code b = 0.75}</li>
   * </ul>
   */
  public BM25Similarity() {
    this(1.2f, 0.75f);
  }
  
  /** Implemented as <code>log(1 + (docCount - docFreq + 0.5)/(docFreq + 0.5))</code>. */
  protected float idf(long docFreq, long docCount) {
    return (float) Math.log(1 + (docCount - docFreq + 0.5D)/(docFreq + 0.5D));
  }
  
  /** The default implementation returns <code>1</code> */
  protected float scorePayload(int doc, int start, int end, BytesRef payload) {
    return 1;
  }
  
  /** The default implementation computes the average as <code>sumTotalTermFreq / docCount</code> */
  protected float avgFieldLength(CollectionStatistics collectionStats) {
    return (float) (collectionStats.sumTotalTermFreq() / (double) collectionStats.docCount());
  }
  
  /** 
   * True if overlap tokens (tokens with a position of increment of zero) are
   * discounted from the document's length.
   */
  protected boolean discountOverlaps = true;

  /** Sets whether overlap tokens (Tokens with 0 position increment) are 
   *  ignored when computing norm.  By default this is true, meaning overlap
   *  tokens do not count when computing norms. */
  public void setDiscountOverlaps(boolean v) {
    discountOverlaps = v;
  }

  /**
   * Returns true if overlap tokens are discounted from the document's length. 
   * @see #setDiscountOverlaps 
   */
  public boolean getDiscountOverlaps() {
    return discountOverlaps;
  }
  
  /** Cache of decoded bytes. */
  private static final float[] LENGTH_TABLE = new float[256];

  static {
    for (int i = 0; i < 256; i++) {
      LENGTH_TABLE[i] = SmallFloat.byte4ToInt((byte) i);
    }
  }


  @Override
  public final long computeNorm(FieldInvertState state) {
    final int numTerms = discountOverlaps ? state.getLength() - state.getNumOverlap() : state.getLength();
    return SmallFloat.intToByte4(numTerms);
  }

  /**
   * Computes a score factor for a simple term and returns an explanation
   * for that score factor.
   * 
   * <p>
   * The default implementation uses:
   * 
   * <pre class="prettyprint">
   * idf(docFreq, docCount);
   * </pre>
   * 
   * Note that {@link CollectionStatistics#docCount()} is used instead of
   * {@link org.apache.lucene.index.IndexReader#numDocs() IndexReader#numDocs()} because also 
   * {@link TermStatistics#docFreq()} is used, and when the latter 
   * is inaccurate, so is {@link CollectionStatistics#docCount()}, and in the same direction.
   * In addition, {@link CollectionStatistics#docCount()} does not skew when fields are sparse.
   *   
   * @param collectionStats collection-level statistics
   * @param termStats term-level statistics for the term
   * @return an Explain object that includes both an idf score factor 
             and an explanation for the term.
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
    final long df = termStats.docFreq();
    final long docCount = collectionStats.docCount();
    final float idf = idf(df, docCount);
    return Explanation.match(idf, "idf, computed as log(1 + (N - n + 0.5) / (n + 0.5)) from:",
        Explanation.match(df, "n, number of documents containing term"),
        Explanation.match(docCount, "N, total number of documents with field"));
  }

  /**
   * Computes a score factor for a phrase.
   * 
   * <p>
   * The default implementation sums the idf factor for
   * each term in the phrase.
   * 
   * @param collectionStats collection-level statistics
   * @param termStats term-level statistics for the terms in the phrase
   * @return an Explain object that includes both an idf 
   *         score factor for the phrase and an explanation 
   *         for each term.
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats[]) {
    double idf = 0d; // sum into a double before casting into a float
    List<Explanation> details = new ArrayList<>();
    for (final TermStatistics stat : termStats ) {
      Explanation idfExplain = idfExplain(collectionStats, stat);
      details.add(idfExplain);
      idf += idfExplain.getValue();
    }
    return Explanation.match((float) idf, "idf, sum of:", details);
  }

  @Override
  public final SimWeight computeWeight(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    Explanation idf = termStats.length == 1 ? idfExplain(collectionStats, termStats[0]) : idfExplain(collectionStats, termStats);
    float avgdl = avgFieldLength(collectionStats);

    float[] cache = new float[256];
    for (int i = 0; i < cache.length; i++) {
      cache[i] = k1 * ((1 - b) + b * LENGTH_TABLE[i] / avgdl);
    }
    return new BM25Stats(collectionStats.field(), boost, k1, idf, avgdl, cache);
  }

  @Override
  public final SimScorer simScorer(SimWeight stats, LeafReaderContext context) throws IOException {
    BM25Stats bm25stats = (BM25Stats) stats;
    return new BM25DocScorer(bm25stats, context.reader().getNormValues(bm25stats.field));
  }
  
  private class BM25DocScorer extends SimScorer {
    private final BM25Stats stats;
    private final float weightValue; // boost * idf * (k1 + 1)
    private final NumericDocValues norms;
    /** precomputed cache for all length values */
    private final float[] lengthCache;
    /** precomputed norm[256] with k1 * ((1 - b) + b * dl / avgdl) */
    private final float[] cache;
    
    BM25DocScorer(BM25Stats stats, NumericDocValues norms) throws IOException {
      this.stats = stats;
      this.weightValue = stats.weight;
      this.norms = norms;
      lengthCache = LENGTH_TABLE;
      cache = stats.cache;
    }
    
    @Override
    public float score(int doc, float freq) throws IOException {
      // if there are no norms, we act as if b=0
      double norm;
      if (norms == null) {
        norm = k1;
      } else {
        boolean found = norms.advanceExact(doc);
        assert found;
        norm = cache[((byte) norms.longValue()) & 0xFF];
      }
      return weightValue * (float) (freq / (freq + norm));
    }

    @Override
    public float maxScore(float maxFreq) {
      // TODO: leverage maxFreq and the min norm from the cache
      return weightValue;
    }

    @Override
    public Explanation explain(int doc, Explanation freq) throws IOException {
      List<Explanation> subs = new ArrayList<>();
      subs.addAll(stats.explain());
      Explanation tfExpl = explainTF(doc, freq);
      subs.add(tfExpl);
      return Explanation.match(stats.weight * tfExpl.getValue(),
          "score(doc="+doc+",freq="+freq.getValue()+"), product of:", subs);
    }
    
    private Explanation explainTF(int doc, Explanation freq) throws IOException {
      List<Explanation> subs = new ArrayList<>();
      subs.add(freq);
      subs.add(Explanation.match(k1, "k1, term saturation parameter"));
      if (norms == null) {
        subs.add(Explanation.match(0, "b, field omits length norms"));
        return Explanation.match(
            (float) (freq.getValue() / (freq.getValue() + (double) k1)),
            "tf, computed as freq / (freq + k1) from:", subs);
      } else {
        boolean found = norms.advanceExact(doc);
        assert found;
        byte norm = (byte) norms.longValue();
        float doclen = lengthCache[norm & 0xff];
        subs.add(Explanation.match(b, "b, length normalization parameter"));
        if ((norm & 0xFF) > 39) {
          subs.add(Explanation.match(doclen, "dl, length of field (approximate)"));
        } else {
          subs.add(Explanation.match(doclen, "dl, length of field"));
        }
        subs.add(Explanation.match(stats.avgdl, "avgdl, average length of field"));
        float normValue = k1 * ((1 - b) + b * doclen / stats.avgdl);
        return Explanation.match(
            (float) (freq.getValue() / (freq.getValue() + (double) normValue)),
            "tf, computed as freq / (freq + k1 * (1 - b + b * dl / avgdl)) from:", subs);
      }
    }

  }
  
  /** Collection statistics for the BM25 model. */
  private static class BM25Stats extends SimWeight {
    /** field name, for pulling norms */
    private final String field;
    /** query boost */
    private final float boost;
    /** k1 value for scale factor */
    private final float k1;
    /** BM25's idf */
    private final Explanation idf;
    /** The average document length. */
    private final float avgdl;
    /** precomputed norm[256] with k1 * ((1 - b) + b * dl / avgdl) */
    private final float[] cache;
    /** weight (idf * boost) */
    private final float weight;

    BM25Stats(String field, float boost, float k1, Explanation idf, float avgdl, float[] cache) {
      this.field = field;
      this.boost = boost;
      this.idf = idf;
      this.avgdl = avgdl;
      this.k1 = k1;
      this.cache = cache;
      this.weight = (k1 + 1) * boost * idf.getValue();
    }

    private List<Explanation> explain() {
      List<Explanation> subs = new ArrayList<>();
      // scale factor
      subs.add(Explanation.match(k1 + 1, "scaling factor, k1 + 1"));
      // query boost
      if (boost != 1.0f) {
        subs.add(Explanation.match(boost, "boost"));
      }
      // idf
      subs.add(idf);
      return subs;
    }
  }


  @Override
  public String toString() {
    return "BM25(k1=" + k1 + ",b=" + b + ")";
  }
  
  /** 
   * Returns the <code>k1</code> parameter
   * @see #BM25Similarity(float, float) 
   */
  public final float getK1() {
    return k1;
  }
  
  /**
   * Returns the <code>b</code> parameter 
   * @see #BM25Similarity(float, float) 
   */
  public final float getB() {
    return b;
  }
}
