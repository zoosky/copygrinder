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
package org.copygrinder.pure.copybean.persistence.model

import org.copygrinder.pure.copybean.model.{Copybean, CopybeanType, ReifiedCopybean}

case class PersistableObject protected(beanOrType: Either[ReifiedCopybean, CopybeanType]) {

  def bean: ReifiedCopybean = {
    beanOrType.left.get
  }

  def cbType: CopybeanType = {
    beanOrType.right.get
  }

}

object PersistableObject {

  def apply(copybean: ReifiedCopybean): PersistableObject = {
    new PersistableObject(Left(copybean))
  }

  def apply(cbType: CopybeanType): PersistableObject = {
    new PersistableObject(Right(cbType))
  }

}