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
package org.copygrinder.pure.copybean.model

import org.copygrinder.pure.copybean.model.Cardinality.Cardinality

case class CopybeanType(
 id: String,
 displayName: Option[String] = None,
 instanceNameFormat: Option[String] = None,
 fields: Option[Seq[CopybeanFieldDef]] = None,
 tags: Option[Seq[String]] = None,
 cardinality: Cardinality
 ) {

}


object Cardinality extends Enumeration {

  type Cardinality = Value

  val One, Many = Value

}
