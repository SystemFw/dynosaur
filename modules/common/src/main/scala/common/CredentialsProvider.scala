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

import model._
import Credentials._

import cats.effect.Sync
import cats.implicits._
import com.amazonaws.auth.{
  AWSSessionCredentials,
  AWSCredentialsProvider,
  DefaultAWSCredentialsProviderChain
}

trait CredentialsProvider[F[_]] {
  def get: F[Credentials]
}

object CredentialsProvider {

  def default[F[_]: Sync]: CredentialsProvider[F] =
    fromAwsCredentialProvider[F](new DefaultAWSCredentialsProviderChain)

  // TODO refresh creds automagically
  def fromAwsCredentialProvider[F[_]](
      awsCredentialsProvider: AWSCredentialsProvider)(
      implicit F: Sync[F]): CredentialsProvider[F] =
    new CredentialsProvider[F] {
      override def get: F[Credentials] =
        F.delay(awsCredentialsProvider.getCredentials).map {
          case creds: AWSSessionCredentials =>
            Credentials(
              AccessKeyId(creds.getAWSAccessKeyId),
              SecretAccessKey(creds.getAWSSecretKey),
              SessionToken(creds.getSessionToken).some)
          case creds =>
            Credentials(
              AccessKeyId(creds.getAWSAccessKeyId),
              SecretAccessKey(creds.getAWSSecretKey),
              None)
        }
    }

  def resolveFromEnvironmentVariables: Option[Credentials] = {

    val accessKeyIdEnv = "AWS_ACCESS_KEY_ID"
    val secretAccessKey = "AWS_SECRET_ACCESS_KEY"
    val sessionTokenEnv = "AWS_SESSION_TOKEN"

    // These are legacy
    val accessKey = "AWS_ACCESS_KEY"
    val secretKeyEnv = "AWS_SECRET_KEY"

    (
      sys.env
        .get(accessKeyIdEnv)
        .orElse(sys.env.get(accessKey))
        .map(AccessKeyId.apply),
      sys.env
        .get(secretKeyEnv)
        .orElse(sys.env.get(secretAccessKey))
        .map(SecretAccessKey.apply))
      .mapN { (accessKeyId, secretAccessKey) =>
        val sessionToken = sys.env
          .get(sessionTokenEnv)
          .map(SessionToken.apply)
        Credentials(accessKeyId, secretAccessKey, sessionToken)
      }
  }

  def resolveFromSystemProperties: Option[Credentials] = {

    val accessKeyIdProperty = "aws.accessKeyId"
    val secretKeyProperty = "aws.secretKey"
    val sessionTokenProperty = "aws.sessionToken"

    (
      sys.props.get(accessKeyIdProperty).map(AccessKeyId.apply),
      sys.props.get(secretKeyProperty).map(SecretAccessKey.apply))
      .mapN { (accessKeyId, secretAccessKey) =>
        val sessionToken = sys.props
          .get(sessionTokenProperty)
          .map(SessionToken.apply)

        Credentials(accessKeyId, secretAccessKey, sessionToken)
      }
  }

}
