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

import model.Credentials._

import java.time._
import format.DateTimeFormatter

import org.http4s._
import util.Writer

import scala.util.Try
import cats.implicits._

trait HttpCodecs {

  implicit val httpDateHttpCodec: HttpCodec[HttpDate] =
    new HttpCodec[HttpDate] {

      private val dateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

      override def parse(s: String): ParseResult[HttpDate] =
        Try(ZonedDateTime.parse(s, dateTimeFormatter)).toEither
          .leftMap(
            _ =>
              ParseFailure(
                "Error to parse a datetime",
                s"The string `$s` is not a valid datetime"))
          .flatMap(a => HttpDate.fromEpochSecond(a.toEpochSecond))

      override def render(writer: Writer, t: HttpDate): writer.type = {
        val formattedDateTime =
          Instant
            .ofEpochSecond(t.epochSecond)
            .atOffset(ZoneOffset.UTC)
            .format(dateTimeFormatter)
        writer << formattedDateTime
      }
    }

  implicit val sessionTokenHttpCodec: HttpCodec[SessionToken] =
    new HttpCodec[SessionToken] {

      override def parse(s: String): ParseResult[SessionToken] =
        SessionToken(s).asRight

      override def render(writer: Writer, t: SessionToken): writer.type =
        writer << t.value
    }

}
