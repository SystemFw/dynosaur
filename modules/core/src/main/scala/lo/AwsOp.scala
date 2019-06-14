/*
 * Copyright 2019 OVO Energy
 *
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

package dynosaur
package lo

import model._

trait AwsOp[Req, Res] {
  def target: String
}

object AwsOp {

  def instance[Req, Res](target_ : String): AwsOp[Req, Res] =
    new AwsOp[Req, Res] {
      val target = target_
    }

  implicit val put: AwsOp[PutItemRequest, PutItemResponse] =
    instance("DynamoDB_20120810.PutItem")

  implicit val get: AwsOp[GetItemRequest, GetItemResponse] =
    instance("DynamoDB_20120810.GetItem")

  implicit val delete: AwsOp[DeleteItemRequest, DeleteItemResponse] =
    instance("DynamoDB_20120810.DeleteItem")

  implicit val update: AwsOp[UpdateItemRequest, UpdateItemResponse] =
    instance("DynamoDB_20120810.UpdateItem")

  implicit val batchWrite
      : AwsOp[BatchWriteItemsRequest, BatchWriteItemsResponse] =
    instance("DynamoDB_20120810.BatchWriteItem")
}
