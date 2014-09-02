/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.copygrinder.impure.copybean.search

import java.io.File

import com.softwaremill.macwire.MacwireMacros._
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import org.copygrinder.pure.copybean.model.Copybean
import org.copygrinder.pure.copybean.search.DocumentBuilder
import org.copygrinder.impure.system.Configuration

class Indexer {

  protected lazy val config = wire[Configuration]

  protected lazy val documentBuilder = wire[DocumentBuilder]

  protected lazy val analyzer = new KeywordAnalyzer()

  protected lazy val indexDirectory = FSDirectory.open(new File(config.indexRoot))

  protected lazy val indexWriterConfig = new IndexWriterConfig(Version.LUCENE_4_9, analyzer)

  protected lazy val indexWriter = new IndexWriter(indexDirectory, indexWriterConfig)

  protected lazy val trackingIndexWriter = new TrackingIndexWriter(indexWriter)

  protected lazy val searcherManager = new SearcherManager(indexWriter, true, new SearcherFactory())

  protected lazy val indexRefresher = new ControlledRealTimeReopenThread[IndexSearcher](
    trackingIndexWriter, searcherManager, 60.00, 0.1
  )

  protected var reopenToken = 0L

  indexRefresher.start()

  protected def close() = {
    indexRefresher.interrupt()
    indexRefresher.close()

    indexWriter.commit()
    indexWriter.close()
  }

  def addCopybean(copybean: Copybean): Unit = {
    val doc = documentBuilder.buildDocument(copybean)
    reopenToken = trackingIndexWriter.addDocument(doc)
    indexWriter.commit()
  }

  def findCopybeanIds(): Seq[String] = {
    val query = new MatchAllDocsQuery
    doQuery(query)
  }


  def findCopybeanIds(field: String, phrase: String): Seq[String] = {
    val query = new PhraseQuery
    query.add(new Term(s"contains.$field", phrase))
    doQuery(query)
  }

  def doQuery(query: Query): Seq[String] = {
    indexRefresher.waitForGeneration(reopenToken)
    val indexSearcher = searcherManager.acquire()
    try {
      val docs = indexSearcher.search(query, config.indexMaxResults)
      val copybeanIds = docs.scoreDocs.map(scoreDoc => {
        val doc = indexSearcher.getIndexReader.document(scoreDoc.doc)
        doc.get("id")
      })
      copybeanIds
    } finally {
      searcherManager.release(indexSearcher)
    }
  }


}
