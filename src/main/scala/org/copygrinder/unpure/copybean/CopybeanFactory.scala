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
package org.copygrinder.unpure.copybean

import java.util.UUID

import com.softwaremill.macwire.MacwireMacros._
import org.copygrinder.pure.copybean.model.{Copybean, CoreCopybean}
import org.copygrinder.pure.copybean.persistence.IdEncoderDecoder

class CopybeanFactory {

  lazy val idEncoderDecoder = wire[IdEncoderDecoder]

  def create(c: CoreCopybean):Copybean = {
    new Copybean(idEncoderDecoder.encodeUuid(UUID.randomUUID()), c.enforcedTypeIds, c.contains)
  }
}