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
package org.copygrinder.impure.system

import java.io.File

import akka.actor.{ActorContext, Props}
import org.copygrinder.impure.api.CopygrinderApi
import org.copygrinder.impure.copybean.CopybeanFactory
import org.copygrinder.impure.copybean.persistence._
import org.copygrinder.impure.copybean.search.Indexer
import org.copygrinder.pure.copybean.model.{Copybean, CopybeanType}
import org.copygrinder.pure.copybean.persistence.IdEncoderDecoder
import org.copygrinder.pure.copybean.search.{DocumentBuilder, QueryBuilder}
import spray.caching.{Cache, LruCache}

import scala.concurrent.ExecutionContext

class DefaultWiring {

  lazy val globalModule = new GlobalModule

  lazy val persistenceServiceModule = new PersistenceServiceModule(globalModule)

  lazy val serverModule = new ServerModule(globalModule, persistenceServiceModule)
}


class GlobalModule() {
  lazy val configuration = new Configuration()
}


class ServerModule(globalModule: GlobalModule, persistenceServiceModule: PersistenceServiceModule) {
  val actorSystemInit = new ActorSystemInit()

  implicit val actorSystem = actorSystemInit.init()

  val siloScopeFactory = new SiloScopeFactory(
    persistenceServiceModule.documentBuilder, persistenceServiceModule.queryBuilder, globalModule.configuration
  )

  def copygrinderApiFactory(ac: ActorContext): CopygrinderApi = {
    new CopygrinderApi(ac, persistenceServiceModule.persistenceService, siloScopeFactory)
  }

  lazy val routeExecutingActor = Props(new RouteExecutingActor(copygrinderApiFactory))

  lazy val routingActor = Props(new RoutingActor(routeExecutingActor, globalModule.configuration))

  lazy val serverInit = new ServerInit(globalModule.configuration, routingActor)

}


class PersistenceServiceModule(globalModule: GlobalModule) {

  lazy val hashedFileResolver = new HashedFileResolver()

  lazy val idEncoderDecoder = new IdEncoderDecoder()

  lazy val copybeanFactory = new CopybeanFactory(idEncoderDecoder)

  lazy val documentBuilder = new DocumentBuilder()

  lazy val queryBuilder = new QueryBuilder()

  lazy val persistenceService = new PersistenceService(
    globalModule.configuration, hashedFileResolver, copybeanFactory
  )

}

class SiloScope(siloId: String, documentBuilder: DocumentBuilder, queryBuilder: QueryBuilder, config: Configuration) {

  lazy val root = new File(new File(config.copybeanDataRoot), siloId).getAbsolutePath

  lazy val indexDir = new File(root,  "index/")

  lazy val indexer = new Indexer(indexDir, documentBuilder, queryBuilder, config.indexMaxResults)

  lazy val beanCache: Cache[Copybean] = LruCache()

  lazy val typeCache: Cache[CopybeanType] = LruCache()

  lazy val beanDir = new File(root, "copybeans/")

  lazy val beanGitRepo = new GitRepo(beanDir, new FileRepositoryBuilderWrapper())

  lazy val typesDir = new File(root, "types/")

  lazy val typeGitRepo = new GitRepo(typesDir, new FileRepositoryBuilderWrapper())

}

class SiloScopeFactory(documentBuilder: DocumentBuilder, queryBuilder: QueryBuilder, config: Configuration) {

  lazy val siloScopeCache: Cache[SiloScope] = LruCache()

  def build(siloId: String)(implicit ex: ExecutionContext): SiloScope = {
    siloScopeCache(siloId) {
      new SiloScope(siloId, documentBuilder, queryBuilder, config)
    }.value.get.get
  }

}