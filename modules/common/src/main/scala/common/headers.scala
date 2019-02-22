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

import org.http4s._
import syntax.all._
import Header.Raw
import util.{CaseInsensitiveString, Writer}

import cats.implicits._

object headers extends HttpCodecs {

  object `X-Amz-Date` extends HeaderKey.Singleton {
    type HeaderT = `X-Amz-Date`

    val name: CaseInsensitiveString = "X-Amz-Date".ci

    def matchHeader(header: Header): Option[`X-Amz-Date`] = header match {
      case h: `X-Amz-Date` => h.some
      case Raw(n, _) if n == name =>
        header.parsed.asInstanceOf[`X-Amz-Date`].some
      case _ => None
    }

    def parse(s: String): ParseResult[`X-Amz-Date`] =
      HttpCodec[HttpDate].parse(s).map(`X-Amz-Date`.apply)

    def unsafeFromInstant(instant: Instant): `X-Amz-Date` = {
      `X-Amz-Date`(HttpDate.unsafeFromInstant(instant))
    }

    def unsafeFromDateTime(dateTime: OffsetDateTime): `X-Amz-Date` = {
      `X-Amz-Date`(HttpDate.unsafeFromZonedDateTime(dateTime.toZonedDateTime))
    }

    def unsafeFromDateTime(dateTime: ZonedDateTime): `X-Amz-Date` = {
      `X-Amz-Date`(HttpDate.unsafeFromZonedDateTime(dateTime))
    }
  }

  final case class `X-Amz-Date`(date: HttpDate) extends Header.Parsed {
    def key: `X-Amz-Date`.type = `X-Amz-Date`

    def renderValue(writer: Writer): writer.type = writer << date
  }

  object `X-Amz-Content-SHA256` extends HeaderKey.Singleton {
    type HeaderT = `X-Amz-Content-SHA256`

    val name: CaseInsensitiveString = "X-Amz-Content-SHA256".ci

    def matchHeader(header: Header): Option[`X-Amz-Content-SHA256`] =
      header match {
        case h: `X-Amz-Content-SHA256` => h.some
        case Raw(n, _) if n == name =>
          header.parsed.asInstanceOf[`X-Amz-Content-SHA256`].some
        case _ => None
      }

    def parse(s: String): ParseResult[`X-Amz-Content-SHA256`] =
      `X-Amz-Content-SHA256`(s).asRight
  }

  final case class `X-Amz-Content-SHA256`(hashedContent: String)
      extends Header.Parsed {
    def key: `X-Amz-Content-SHA256`.type = `X-Amz-Content-SHA256`

    def renderValue(writer: Writer): writer.type = writer << hashedContent
  }

  object `X-Amz-Security-Token` extends HeaderKey.Singleton {
    type HeaderT = `X-Amz-Security-Token`

    val name: CaseInsensitiveString = "X-Amz-Security-Token".ci

    def matchHeader(header: Header): Option[`X-Amz-Security-Token`] =
      header match {
        case h: `X-Amz-Security-Token` => h.some
        case Raw(n, _) if n == name =>
          header.parsed.asInstanceOf[`X-Amz-Security-Token`].some
        case _ => None
      }

    def parse(s: String): ParseResult[`X-Amz-Security-Token`] =
      HttpCodec[SessionToken].parse(s).map(`X-Amz-Security-Token`.apply)
  }

  final case class `X-Amz-Security-Token`(sessionToken: SessionToken)
      extends Header.Parsed {
    def key: `X-Amz-Security-Token`.type = `X-Amz-Security-Token`

    def renderValue(writer: Writer): writer.type = writer << sessionToken
  }

  object `X-Amz-Target` extends HeaderKey.Singleton {
    type HeaderT = `X-Amz-Target`

    val name: CaseInsensitiveString = "X-Amz-Target".ci

    def matchHeader(header: Header): Option[`X-Amz-Target`] =
      header match {
        case h: `X-Amz-Target` => h.some
        case Raw(n, _) if n == name =>
          header.parsed.asInstanceOf[`X-Amz-Target`].some
        case _ => None
      }

    def parse(s: String): ParseResult[`X-Amz-Target`] =
      `X-Amz-Target`(s).asRight
  }

  final case class `X-Amz-Target`(target: String) extends Header.Parsed {
    def key: `X-Amz-Target`.type = `X-Amz-Target`

    def renderValue(writer: Writer): writer.type = writer << target
  }
}
