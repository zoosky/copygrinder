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

import akka.actor.Status.Success
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.copygrinder.impure.copybean.CopybeanFactory
import org.copygrinder.impure.system.{Configuration, SiloScope}
import org.copygrinder.pure.copybean.exception.{CopybeanNotFound, SiloNotInitialized}
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, Copybean, CopybeanType, FieldType}
import org.json4s.ext.EnumNameSerializer
import org.json4s.jackson.Serialization._
import org.json4s.{DefaultFormats, Formats}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class PersistenceService(
  config: Configuration, hashedFileResolver: HashedFileResolver, copybeanFactory: CopybeanFactory) extends LazyLogging {

  implicit def json4sJacksonFormats: Formats = DefaultFormats + new EnumNameSerializer(FieldType)

  def fetch(id: String)(implicit siloScope: SiloScope): Copybean = {
    checkSiloExists()
    val file = hashedFileResolver.locate(id, "json", siloScope.beanDir)

    if (!file.exists()) {
      throw new CopybeanNotFound()
    } else {
      val json = FileUtils.readFileToString(file)
      read[Copybean](json)
    }
  }

  def cachedFetch(id: String)(implicit siloScope: SiloScope): Future[Copybean] = siloScope.beanCache(id) {
    fetch(id)
  }

  def store(anonCopybean: AnonymousCopybean)(implicit siloScope: SiloScope): String = {
    val copybean = copybeanFactory.create(anonCopybean)
    store(copybean)
  }

  def store(copybean: Copybean)(implicit siloScope: SiloScope): String = {
    val f = Future {
      val file = hashedFileResolver.locate(copybean.id, "json", siloScope.beanDir)
      siloScope.beanGitRepo.add(file, write(copybean))
    }.zip(Future {
      siloScope.indexer.addCopybean(copybean)
    })

    Await.ready(f, 5 seconds)

    copybean.id
  }

  def find()(implicit siloScope: SiloScope): Future[Seq[Copybean]] = {
    checkSiloExists()
    val copybeanIds = siloScope.indexer.findCopybeanIds()
    fetchCopybeans(copybeanIds)
  }

  def find(params: Seq[(String, String)])(implicit siloScope: SiloScope): Future[Seq[Copybean]] = {
    checkSiloExists()
    val copybeanIds = siloScope.indexer.findCopybeanIds(params)
    fetchCopybeans(copybeanIds)
  }

  protected def fetchCopybeans(copybeanIds: Seq[String])(implicit siloScope: SiloScope): Future[Seq[Copybean]] = {
    val futures = copybeanIds.map(id => {
      cachedFetch(id)
    })
    Future.sequence(futures)
  }

  def store(copybeanType: CopybeanType)(implicit siloScope: SiloScope): Future[(Unit, Unit)] = {
    Future {
      val file = new File(siloScope.typesDir, "/" + copybeanType.id + ".json")
      siloScope.typeGitRepo.add(file, write(copybeanType))
    }.zip(Future {
      siloScope.indexer.addType(copybeanType)
    })
  }

  protected def checkSiloExists()(implicit siloScope: SiloScope) = {
    if (!siloScope.root.exists) {
      throw new SiloNotInitialized()
    }
  }

}
