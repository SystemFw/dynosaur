/*
 * Copyright 2020 Fabio Labella
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

package dynosaur.path

import cats.parse._
import cats.syntax.all._
import dynosaur.DynamoValue

object DynamoValuePath {

  object DynamoValuePathParser {

    val dotP: Parser[Unit] = Parser.char('.')
    val openSquareBraceP: Parser[Unit] = Parser.char('[')
    val closeSquareBraceP: Parser[Unit] = Parser.char(']')
    val questionMarkP: Parser[Unit] = Parser.char('?')
    val starP: Parser[Unit] = Parser.char('*')
    val equalP: Parser[Unit] = Parser.char('=')
    val bangP: Parser[Unit] = Parser.char('!')
    val gtP = Parser.char('>')
    val ltP = Parser.char('<')
    val ampersandP = Parser.char('&')
    val pipeP = Parser.char('|')
    val dollarP = Parser.char('$')
    val atP = Parser.char('@')
    val columnP: Parser[Unit] = Parser.char(':')
    val openParenP: Parser[Unit] = Parser.char('(')
    val closeParenP: Parser[Unit] = Parser.char(')')
    val singleQuoteP: Parser[Unit] = Parser.char('\'')
    val whitespaceP: Parser[Unit] = Parser.charIn(' ', '\t').void
    val whitespacesP: Parser[Unit] = whitespaceP.rep(1).void

    val `[(` : Parser[Unit] =
      openSquareBraceP *> whitespacesP.rep0 *> openParenP
    val `[?(` =
      openSquareBraceP *> whitespacesP.rep0 *> questionMarkP *> whitespacesP.rep0 *> openParenP
    val `)]` = closeParenP *> whitespacesP.rep0 *> closeSquareBraceP

    val equalOpP: Parser[Op] =
      (equalP *> equalP).withContext("equalOpP").as(equalOp)
    val notEqualP: Parser[Op] =
      (bangP *> equalP).withContext("notEqualP").as(notEqualOp)
    val andOpP: Parser[Op] =
      (ampersandP *> ampersandP).withContext("andOpP").as(andOp)
    val orOpP: Parser[Op] = (pipeP *> pipeP).withContext("orOpP").as(orOp)

    // TODO implement these operators
    // val gtOpP: Parser[Op] = gtP.as{(l,r) =>
    //     l.
    // }
    // val ltOpP: Parser[Op] = ltP
    // val geOp: Parser[Op] = gtP *> equalP
    // val leOp: Parser[Op] = ltP *> equalP
    // val subsetofOpP: Parser[Op] = Parser.string("subsetof")
    // val containsOpP: Parser[Op] = Parser.string("contains")

    val inOpP: Parser[Op] = Parser.string("in").withContext("inOpP").as(inOp)
    val ninOpP: Parser[Op] =
      Parser.string("nin").withContext("ninOpP").as(ninOp)

    val sizeOpP: Parser[Op] =
      Parser.string("size").withContext("sizeOpP").as(sizeOp)

    val emptyOpP: Parser[Op] =
      Parser.string("empty").withContext("emptyOpP").as(emptyOp)

    val opP = Parser
      .oneOf(List(equalOpP, notEqualP, sizeOpP, emptyOpP, andOpP, orOpP))
      .withContext("opP")

    val notP = bangP.as(notOp).withContext("notOp")
    val unaryOpP: Parser[UnaryOp] = Parser.oneOf(notP :: Nil)

    val intP = (Parser.char('0').as(0) | (Parser
      .charWhere(c => c.isDigit && c != '0') ~ Parser
      .charWhere(c => c.isDigit)
      .rep0)
      .map { case (head, tail) =>
        (head :: tail).toList.mkString.toInt
      })
      .withContext("intP")

    val segmentP: Parser[String] =
      (Parser.charWhere(c =>
        c.isLetter | c == '_' | c == '-' | c == '&'
      ) ~ Parser
        .charWhere(c => c.isLetterOrDigit | c == '_' | c == '-' | c == '&')
        .rep0)
        .map { case (h, tail) =>
          (h :: tail).mkString
        }
        .withContext("segmentP")

    val stringP = Parser
      .until(singleQuoteP)
      .surroundedBy(singleQuoteP)
      .withContext("stringP")

    val booleanP =
      (Parser.string("true").as(true) | Parser.string("false").as(false))
        .withContext("booleanP")

    lazy val thisP: Parser[Exp] = atP.as(thisExp)

    lazy val rootP: Parser[Exp] = dollarP.as(rootExp)

    lazy val pathP = ((thisP | rootP) ~ Parser
      .recursive[Exp] { p =>
        (Parser.oneOf(
          List(
            (dotP *> segmentP).map(propertyExp).withContext(".property"),
            openSquareBraceP *> (
              Parser.oneOf(
                List(
                  stringP.map(propertyExp).withContext("['property']"),
                  intP.map(nThArrayItemExp).withContext("[num]"),
                  (questionMarkP *> openParenP *> whitespaceP.rep0 *> Parser
                    .defer(expP)
                    .map(filterExp) <* whitespaceP.rep0 <* closeParenP)
                    .withContext("[?(filter)]"),
                  Parser.defer(expP).map(propertyExp).withContext("[(exp)]")
                )
              )
            ) <* closeSquareBraceP
          )
        ) ~ p.rep0).map { case (h, tail) =>
          tail.foldLeft(h) { (s, x) => s.andThen(x) }
        }
      }
      .?).map {
      case (h, Some(t)) => h.andThen(t)
      case (h, None) => h
    }

    lazy val expP: Parser[Exp] = Parser.recursive[Exp] { recurse =>
      val intExpP = intP
        .withContext("intP")
        .map(_.toDouble)
        .map(numberExp)

      val stringExpP = stringP
        .withContext("stringP")
        .map(stringExp)

      val booleanExpP =
        booleanP
          .withContext("booleanP")
          .map(booleanExp)

      val literalExp = Parser.oneOf(intExpP :: stringExpP :: booleanExpP :: Nil)

      val parentsExpP =
        (openParenP *> whitespaceP.rep0) *> recurse <* (whitespaceP.rep0 <* closeParenP)
          .withContext("parentsExpP")

      val unaryOpExpP =
        ((unaryOpP <* whitespacesP.rep0) ~ (parentsExpP | pathP | literalExp))
          .withContext("unaryOpExpP")
          .map { case (op, value) =>
            unaryOpExp(op, value)
          }

      val opExpP =
        ((unaryOpExpP | parentsExpP | pathP | literalExp) ~ (whitespacesP *> opP <* whitespacesP) ~ recurse)
          .withContext("opExpP")
          .map { case ((l, op), r) =>
            opExp(op, l, r)
          }

      Parser.oneOf(
        opExpP.backtrack :: unaryOpExpP :: parentsExpP :: pathP :: literalExp :: Nil
      )
    }
  }

  sealed abstract trait ExpressionContext {
    def root: DynamoValue

    def withCurrent(value: DynamoValue): ExpressionContext =
      ExpressionContext.single(value, root)
    def withCurrent(values: List[DynamoValue]): ExpressionContext =
      ExpressionContext.multiple(values, root)

    def single: Option[ExpressionContext.Single] = fold(
      s => s.some,
      _ => none
    )

    def unsafeSingle: ExpressionContext.Single = single.get

    def multiple: Option[ExpressionContext.Multiple] = fold(
      _ => none,
      m => m.some
    )

    def unsafeMultiple: ExpressionContext.Multiple = multiple.get

    // TODO It should flatten as well
    def asListOfSingle: List[ExpressionContext.Single] = fold(
      List(_),
      m =>
        m.currents.map(current =>
          ExpressionContext.Single(current = current, root = m.root)
        )
    )

    def fold[A](
        onSingle: ExpressionContext.Single => A,
        onMultiple: ExpressionContext.Multiple => A
    ) = this match {
      case s: ExpressionContext.Single => onSingle(s)
      case m: ExpressionContext.Multiple => onMultiple(m)
    }
  }

  object ExpressionContext {
    case class Single(current: DynamoValue, root: DynamoValue)
        extends ExpressionContext

    case class Multiple(currents: List[DynamoValue], root: DynamoValue)
        extends ExpressionContext

    def single(current: DynamoValue, root: DynamoValue): ExpressionContext =
      Single(current, root)

    def multiple(
        current: List[DynamoValue],
        root: DynamoValue
    ): ExpressionContext = Multiple(current, root)
  }

  type Exp = ExpressionContext => ExpressionContext
  type Op = (DynamoValue, DynamoValue) => DynamoValue
  type UnaryOp = (DynamoValue) => DynamoValue

  val inOp: Op = (l, r) => {
    val result = List(
      r.l.exists(_.contains(l)),
      l.b.exists(b => r.bs.exists(_.value(b))),
      l.n.exists(n => r.ns.exists(_.value(n))),
      l.s.exists(s => r.ss.exists(_.value(s))),
      l.s.exists(s => r.m.exists(_.keySet.contains(s)))
    ).reduce(_ || _)

    DynamoValue.bool(result)
  }

  val ninOp: Op = (l, r) => DynamoValue.bool(!toBoolean(inOp(l, r)))

  val equalOp: Op = (l, r) => DynamoValue.bool(l == r)

  val notEqualOp: Op = (l, r) => DynamoValue.bool(l != r)

  val sizeOp: Op = (l, r) => {
    val result = r.n
      .flatMap(x => Either.catchNonFatal(x.value.toInt).toOption)
      .map { size =>
        List(
          l.s.exists(_.size == size),
          l.l.exists(_.size == size),
          l.ss.exists(_.value.size == size),
          l.b.exists(_.size == size),
          l.bs.exists(_.value.size == size),
          l.m.exists(_.size == size),
          l.ns.exists(_.value.size == size),
          l.ss.exists(_.value.size == size)
        ).reduce(_ || _)
      }
      .getOrElse(false)

    DynamoValue.bool(result)
  }

  // TODO If a property is null, dos it count as empty?
  val emptyOp: Op = (l, r) => {
    val expected = toBoolean(r)
    val result = List(
      l.s.exists(_.isEmpty),
      l.l.exists(_.isEmpty),
      l.ss.exists(_.value.isEmpty),
      l.b.exists(_.isEmpty),
      l.bs.exists(_.value.isEmpty),
      l.m.exists(_.isEmpty),
      l.ns.exists(_.value.isEmpty),
      l.ss.exists(_.value.isEmpty)
    ).reduce(_ || _)

    DynamoValue.bool(result == expected)
  }

  val andOp: Op = (l, r) => DynamoValue.bool(toBoolean(l) && toBoolean(r))
  val orOp: Op = (l, r) => DynamoValue.bool(toBoolean(l) || toBoolean(r))
  val notOp: UnaryOp = value => DynamoValue.bool(!toBoolean(value))

  def nThArrayItemExp(index: Int): Exp =
    propertyExp(ctx => ctx.withCurrent(DynamoValue.n(index)))

  def propertyExp(name: String): Exp =
    propertyExp(ctx => ctx.withCurrent(DynamoValue.s(name)))

  def propertyExp(exp: Exp): Exp = ctx => {

    def singleCase(sCtx: ExpressionContext.Single) = {
      val value = exp(ctx)

      val onL = (
        sCtx.current.l,
        value.single
          .flatMap(_.current.n)
          .flatMap(x => Either.catchNonFatal(x.value.toInt).toOption)
      ).mapN { (xs, index) =>
        xs.get(index.toLong).getOrElse(DynamoValue.nul)
      }

      val onM =
        (sCtx.current.m, value.single.flatMap(_.current.s)).mapN { (m, key) =>
          m.get(key).getOrElse(DynamoValue.nul)
        }

      onM
        .orElse(onL)
        .map(ctx.withCurrent)
        .getOrElse(ctx.withCurrent(DynamoValue.nul))
    }

    def multipleCase(mCtx: ExpressionContext.Multiple) = {
      mCtx.withCurrent(
        mCtx.asListOfSingle
          .map(x => singleCase(x).single)
          .flatten
          .map(_.current)
      )
    }

    ctx.fold(
      singleCase,
      multipleCase
    )

  }

  def filterExp(exp: Exp): Exp = ctx => {

    def singleCase(ctx: ExpressionContext.Single): ExpressionContext = {
      ctx.current.l
        .map { values =>
          val filteredValues = values.filter { x =>
            val sample = exp(ctx.withCurrent(x))
            toBoolean(sample.unsafeSingle.current)
          }

          ctx.withCurrent(filteredValues)
        }
        .getOrElse {
          if (toBoolean(exp(ctx).unsafeSingle.current)) {
            ctx
          } else {
            ctx.withCurrent(DynamoValue.nul)
          }
        }
    }

    def multipleCase(ctx: ExpressionContext.Multiple): ExpressionContext = {
      val values = ctx.currents
        .flatMap { x =>
          singleCase(
            ExpressionContext.Single(current = x, root = ctx.root)
          ).asListOfSingle
        }
        .map(_.current)

      ctx.withCurrent(values)

    }

    ctx.fold(
      singleCase,
      multipleCase
    )
  }

  def opExp(op: Op, l: Exp, r: Exp): Exp = { ctx =>
    ctx.withCurrent(
      op(l(ctx).unsafeSingle.current, r(ctx).unsafeSingle.current)
    )
  }

  def unaryOpExp(op: UnaryOp, l: Exp): Exp = { ctx =>
    ctx.withCurrent(
      op(l(ctx).unsafeSingle.current)
    )
  }

  def thisExp: Exp = identity

  def rootExp: Exp = ctx => ctx.withCurrent(ctx.root)

  def numberExp(value: Double): Exp = ctx =>
    ctx.withCurrent(DynamoValue.n(value))

  def booleanExp(value: Boolean): Exp = ctx =>
    ctx.withCurrent(DynamoValue.bool(value))

  def stringExp(value: String): Exp = ctx =>
    ctx.withCurrent(DynamoValue.s(value))

  // According to https://www.sitepoint.com/javascript-truthy-falsy/
  def toBoolean(dv: DynamoValue) = {
    val isFalse = List(
      dv.nul.isDefined,
      dv.bool.exists(_ == false),
      dv.s.exists(_.isEmpty),
      dv.n.exists(n => BigDecimal(n.value) === BigDecimal(0))
    ).reduce(_ || _)

    !isFalse
  }

  case class Matcher(expression: Exp) {
    def apply(dv: DynamoValue): DynamoValue = {
      val ctx = ExpressionContext.single(dv, dv)
      val result = expression(ctx)
      result.fold(
        s => s.current,
        m => DynamoValue.l(m.currents)
      )
    }
  }

  def parse(expression: String): Either[Parser.Error, Matcher] =
    DynamoValuePathParser.pathP.parseAll(expression).map(Matcher(_))

  def unsafeParse(expression: String): Matcher =
    parse(expression).fold(
      _ =>
        throw new RuntimeException(
          s"Error parsing DynamoValuPath: ${expression}"
        ),
      identity
    )

  def matchDynamoValue(
      source: DynamoValue,
      expression: String
  ): Either[Parser.Error, DynamoValue] = parse(expression).map { matcher =>
    matcher(source)
  }

}
