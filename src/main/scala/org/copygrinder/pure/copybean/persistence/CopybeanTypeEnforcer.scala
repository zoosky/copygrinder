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
package org.copygrinder.pure.copybean.persistence

import org.copygrinder.pure.copybean.exception.TypeValidationException
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.validator.FieldValidator

class CopybeanTypeEnforcer() {

  protected val caster = new UntypedCaster()

  def enforceType(
   copybeanType: CopybeanType,
   copybean: Copybean,
   validatorBeans: Map[String, Copybean],
   validatorClassInstances: Map[String, FieldValidator]
   ): Set[String] = {
    if (copybeanType.fields.isDefined) {
      copybeanType.fields.get.map { fieldDef =>

        val fieldId = fieldDef.id
        val valueOpt = copybean.content.find(field => field._1 == fieldId)
        val ref = if (valueOpt.isDefined) {
          val value = valueOpt.get._2
          val fType = fieldDef.`type`
          checkField(fieldId, value, fType, fieldDef)
          if (fType == FieldType.Reference) {
            checkRefs(fieldDef, value)
          } else {
            None
          }
        } else {
          None
        }

        checkValidators(fieldDef, copybean, validatorBeans, validatorClassInstances)
        ref
      }.flatten.toSet
    } else {
      Set()
    }
  }

  protected def checkField(fieldId: String, value: Any, fType: FieldType.FieldType, fieldDef: CopybeanFieldDef) = {
    value match {
      case string: String =>
        if (fType != FieldType.String && fType != FieldType.Reference && fType != FieldType.Html) {
          throw new TypeValidationException(s"$fieldId must be a String but was: $value")
        }
      case int: Int =>
        if (fType != FieldType.Integer) {
          throw new TypeValidationException(s"$fieldId must be an Integer but was: $value")
        }
      case long: Long =>
        if (fType != FieldType.Long) {
          throw new TypeValidationException(s"$fieldId must be an Long but was: $value")
        }
      case map: Map[_, _] =>
        if (fType != FieldType.Reference && fType != FieldType.File && fType != FieldType.Image) {
          throw new TypeValidationException(s"$fieldId can not be a map: $value")
        } else if (fType == FieldType.File || fType == FieldType.Image) {
          val fileData = caster.castData[Map[String, String]](value, fieldId, fieldDef)
          if (fileData.get("filename").isEmpty) {
            throw new TypeValidationException(s"$fieldId is a file and requires a filename")
          }
          if (fileData.get("hash").isEmpty) {
            throw new TypeValidationException(s"$fieldId is a file and requires a hash")
          }
          val badKeys = fileData.keySet.filter(key => key != "filename" && key != "hash")
          if (badKeys.nonEmpty) {
            throw new TypeValidationException(s"$fieldId is a file and has unknown keys: ${badKeys.mkString(",")}")
          }
        }
      case seq: Seq[_] =>
        if (fType != FieldType.List) {
          throw new TypeValidationException(s"$fieldId must be a list, not: $value")
        }
      case null =>
      case _ =>
        throw new TypeValidationException(s"$fieldId with value $value was an unexpected type: ${value.getClass}")
    }
  }

  protected def checkRefs(fieldDef: CopybeanFieldDef, value: Any): Option[String] = {
    value match {
      case string: String =>
        if (fieldDef.`type` == FieldType.Reference && string.nonEmpty) {
          if (!string.startsWith("!REF!:")) {
            throw new TypeValidationException(
              s"${fieldDef.id} must be an Reference but didn't start with !REF!: $string"
            )
          } else {
            Option(string.replace("!REF!:", ""))
          }
        } else {
          None
        }
      case _ => None
    }
  }

  protected def checkValidators(
   field: CopybeanFieldDef,
   copybean: Copybean, validatorBeans: Map[String, Copybean],
   validatorClassInstances: Map[String, FieldValidator]) = {
    field.validators.map(_.map({
      validatorDef =>
        val typeId = validatorDef.`type`
        val validator = validatorBeans.getOrElse(s"validator.$typeId",
          throw new TypeValidationException(s"Couldn't find a validator of type '$typeId'")
        )
        if (validator.enforcedTypeIds.contains("classBackedFieldValidator")) {
          checkClassBackedValidator(copybean, field.id, validator, validatorDef, validatorClassInstances)
        } else {
          throw new TypeValidationException(s"Couldn't execute validator '${
            validator.id
          }'")
        }
    }))
  }

  protected def checkClassBackedValidator(
   copybean: Copybean,
   field: String,
   validator: Copybean,
   validatorDef: CopybeanFieldValidatorDef,
   validatorClassInstances: Map[String, FieldValidator]) = {
    val className = validator.content.getOrElse("class",
      throw new TypeValidationException(
        s"Couldn't find a class for validator '${validator.id}'"
      )
    )

    className match {
      case classNameString: String => {
        val validator = validatorClassInstances.getOrElse(classNameString,
          throw new TypeValidationException(s"Couldn't find a class for validator '${
            classNameString
          }'")
        )
        validator.validate(copybean, field, validatorDef.args)
      }
      case x => throw new TypeValidationException(
        s"Validator '${
          validator.id
        }' did not specify class as a String but the value '$x' which is a ${
          x.getClass
        }"
      )
    }
  }

}