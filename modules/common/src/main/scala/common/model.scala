/*
 * Copyright 2018 OVO Energy
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

package com.ovoenergy.comms.aws
package common

object model {

  import Credentials._

  case class Credentials(
      accessKeyId: AccessKeyId,
      secretAccessKey: SecretAccessKey,
      sessionToken: Option[SessionToken] = None)

  object Credentials {

    case class AccessKeyId(value: String)

    case class SecretAccessKey(value: String) {
      override def toString = "SecretAccessKey(***)"
    }

    case class SessionToken(value: String) {
      override def toString = "SessionToken(***)"
    }

  }

  case class RequestId(value: String)

  case class Service(value: String)

  object Service {
    val S3: Service = Service("s3")
    val DynamoDb: Service = Service("dynamodb")
  }

  case class Region(value: String)

  object Region {
    val `us-west-2`: Region = Region("us-west-2")
    val `us-west-1`: Region = Region("us-west-1")
    val `us-east-2`: Region = Region("us-east-2")
    val `us-east-1`: Region = Region("us-east-1")
    val `ap-south-1`: Region = Region("ap-south-1")
    val `ap-northeast-2`: Region = Region("ap-northeast-2")
    val `ap-southeast-1`: Region = Region("ap-southeast-1")
    val `ap-southeast-2`: Region = Region("ap-southeast-2")
    val `ap-northeast-1`: Region = Region("ap-northeast-1")
    val `ca-central-1`: Region = Region("ca-central-1")
    val `cn-north-1`: Region = Region("cn-north-1")
    val `eu-central-1`: Region = Region("eu-central-1")
    val `eu-west-1`: Region = Region("eu-west-1")
    val `eu-west-2`: Region = Region("eu-west-2")
    val `eu-west-3`: Region = Region("eu-west-3")
    val `sa-east-1`: Region = Region("sa-east-1")
    val `us-gov-west-1`: Region = Region("us-gov-west-1")
  }

}
