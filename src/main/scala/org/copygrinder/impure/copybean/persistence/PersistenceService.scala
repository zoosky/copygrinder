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
package org.copygrinder.impure.copybean.persistence

import java.io.File

import org.apache.commons.io.FileUtils
import org.copygrinder.impure.copybean.CopybeanFactory
import org.copygrinder.impure.copybean.search.Indexer
import org.copygrinder.impure.system.Configuration
import org.copygrinder.pure.copybean.exception.CopybeanNotFound
import org.copygrinder.pure.copybean.model.{CopybeanType, AnonymousCopybean, Copybean}
import org.json4s.jackson.Serialization._
import org.json4s.{DefaultFormats, Formats}
import spray.caching.Cache

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PersistenceService(
  config: Configuration, hashedFileResolver: HashedFileResolver, copybeanFactory: CopybeanFactory, indexer: Indexer,
  beanCache: Cache[Copybean], typeCache: Cache[CopybeanType], gitRepoFactory: (File) => GitRepo
  ) {

  protected lazy val repoDir = new File(config.copybeanDataRoot + "/default/").getAbsoluteFile

  protected lazy val beanDir = new File(repoDir, "copybeans/")

  protected lazy val beanGitRepo = gitRepoFactory(beanDir)

  protected lazy val typesDir = new File(repoDir, "types/")

  protected lazy val typeGitRepo = gitRepoFactory(typesDir)

  protected implicit def json4sJacksonFormats: Formats = DefaultFormats

  def fetch(id: String): Copybean = {
    val file = hashedFileResolver.locate(id, "json", beanDir)

    if (!file.exists()) {
      throw new CopybeanNotFound()
    } else {
      val json = FileUtils.readFileToString(file)
      read[Copybean](json)
    }
  }

  def cachedFetch(id: String): Future[Copybean] = beanCache(id) {
    fetch(id)
  }

  def store(anonCopybean: AnonymousCopybean): Future[String] = {
    val copybean = copybeanFactory.create(anonCopybean)
    store(copybean).map(_ => copybean.id)
  }

  def store(copybean: Copybean): Future[_] = {
    Future {
      val file = hashedFileResolver.locate(copybean.id, "json", beanDir)
      beanGitRepo.add(file, write(copybean))
    }.zip(Future {
      indexer.addCopybean(copybean)
    })
  }

  def find(): Future[Seq[Copybean]] = {
    val copybeanIds = indexer.findCopybeanIds()
    fetchCopybeans(copybeanIds)
  }

  def find(params: Seq[(String, String)]): Future[Seq[Copybean]] = {
    val copybeanIds = indexer.findCopybeanIds(params)
    fetchCopybeans(copybeanIds)
  }

  protected def fetchCopybeans(copybeanIds: Seq[String]): Future[Seq[Copybean]] = {
    val futures = copybeanIds.map(id => {
      cachedFetch(id)
    })
    Future.sequence(futures)
  }

  def store(copybeanType: CopybeanType): Future[(Unit, Unit)] = {
    Future {
      val file = new File(typesDir, "/" + copybeanType.id + ".json")
      typeGitRepo.add(file, write(copybeanType))
    }.zip(Future {
      indexer.addType(copybeanType)
    })
  }

}
