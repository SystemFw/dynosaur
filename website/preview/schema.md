## Basics

The design of `Dynosaur` is centred around `Schema[A]`, which you can
think of as either a representation of `A`, or a recipe for _both_
encoding and decoding `A`s.

For the remainder of this document, we're going to assume very basic
familiarity with `cats` typeclasses such as `Monoid` and
`Applicative`, and the following two imports:

```scala
import dynosaur._
import cats.syntax.all._
```

So let's start by declaring a simple schema for integers:

```scala
val simpleSchema: Schema[Int] =
  Schema[Int] // provided by the library
```

and use it to encode something:

```scala
simpleSchema.write(1)
// res0: Either[Schema.WriteError, DynamoValue] = Right("N": "1")
```

The result is of type `Either[WriteError, DynamoValue]`, where
`DynamoValue` is a thin wrapper over DynamoDb `AttributeValue`, which
offers pretty-printing among other things.

The same schema can be used for decoding:

```scala
val myInt = DynamoValue.n(15)
// myInt: DynamoValue = "N": "15"
simpleSchema.read(myInt)
// res1: Either[Schema.ReadError, Int] = Right(15)
```

which means we get roundtrip for free:

```scala
simpleSchema.write(1).flatMap(simpleSchema.read)
// res2: Either[Schema.DynosaurError, Int] = Right(1)
```

We are now ready to move on to exploring different ways of creating
our schemas.

> **Notes:**
> - Since decoding comes for free, in the rest of the document we will
>   only show encoding, unless there is something specific to point out
>   about the behaviour of the decoder.
> - To avoid cluttering, the `DynamoValue` output will appear in expandable
>   snippets like this one:
>    <details>
>    <summary>Click to expand</summary>
>
>    ```
>    I'm a snippet!
>    ```
>
>    </details>
> -  With the exception of recursive schemas, which are treated later,
>    it's best to declare schemas as `val`, to allow `Dynosaur` to
>    cache some transformations

## Passthrough schema

The simplest possible schema is the passthrough schema, which you can obtain
by calling:

```scala
Schema[DynamoValue]
```

you won't be using it very often, but it can come in handy when your code needs
to work directly with the low level representation, instead of custom data types.

## Primitives

`Dynosaur` provides schemas for the following Scala primitive types:

```scala
 Schema[Boolean]
 Schema[String]
 Schema[Int]
 Schema[Long]
 Schema[Double]
 Schema[Float]
 Schema[Short]
```

## Bidirectional mappings

New schemas can be created from existing ones by declaring a
bidirectional mapping between them.  
The most general way is using the `xmap` method on `Schema`:
```scala
sealed trait Schema[A] {
  def xmap[B](f: A => Either[ReadError, B])(g: B => Either[WriteError, A]): Schema[B]
  ...
```

although in many cases its two specialised variants `imap` and
`imapErr` are sufficient:

```scala
sealed trait Schema[A] {
  def imap[B](f: A => B)(g: B => A): Schema[B]
  def imapErr[B](f: A => Either[ReadError, B])(g: B => A): Schema[B]
  ...
```

#### imap

`imap` defines an isomorphism between `A` and `B`, which often arises
when using newtypes such as:

```scala
case class EventId(value: String)
```

We would like to keep the specialised representation of `EventId` in
our code, but represent it as a simple `String` in Dynamo, without the
extra nesting.

```scala
val eventIdSchema = Schema[String].imap(EventId.apply)(_.value)
```
<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
eventIdSchema.write(EventId("event-1234"))
// res4: Either[Schema.WriteError, DynamoValue] = Right("S": "event-1234")
eventIdSchema.read(DynamoValue.s("event-5678"))
// res5: Either[Schema.ReadError, EventId] = Right(EventId(event-5678))
```
</details>

#### imapErr

`imapErr` encodes the common case where encoding cannot fail but
decoding can, as seen, for example, in enums:

```scala
sealed trait Switch
object Switch {
  case object On extends Switch
  case object Off extends Switch

  def parse: String => Option[Switch] = _.trim.toLowerCase match {
    case "on" => On.some
    case "off" => Off.some
    case _ => none
  }
}

def switchSchema = Schema[String].imapErr { s =>
   Switch.parse(s).toRight(Schema.ReadError()) // TODO s"$s is not a valid Switch"
 }(_.toString)
```

<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
val a = switchSchema.write(Switch.On)
// a: Either[Schema.WriteError, DynamoValue] = Right("S": "On")
a.flatMap(switchSchema.read)
// res6: Either[Schema.DynosaurError, Switch] = Right(On)
switchSchema.read(DynamoValue.s("blub"))
// res7: Either[Schema.ReadError, Switch] = Left(dynosaur.Schema$ReadError)
```
</details>


## Records

Let's have a look at records with a case class example:
```scala
case class Foo(a: String, b: Int)
```
whose `Schema[Foo]` can be defined as:

```scala
val fooSchema = Schema.record[Foo] { field =>
  (
    field("a", _.a)(Schema[String]),
    field("b", _.b)(Schema[Int])
  ).mapN(Foo.apply)
}

```

<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
fooSchema.write(Foo("value of Foo", 1))
// res8: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "a": { "S": "value of Foo" },
//   "b": { "N": "1" }
// })
```
</details>

The central component is `Schema.record`:

```scala
Schema.record[Foo] { field =>
  ???
}
```

Which states that the type `Foo` is represented by a record, and gives
you the `field` builder to create fields by calling its various
methods. The primary method is `apply`:

```scala
Schema.record[Foo] { field =>
  val b = field("b", _.b)(Schema[Int])
  ???
}
```
which takes three arguments:

1. the name of the field in the resulting `DynamoValue`
2. A function to access the field during the encoding phase, in this case `Foo => Int`
3. the schema of the field, which is `Schema[Int]` is this case

Once we have declared our fields, we need to tell `dynosaur` how to
combine them into a `Foo` during the decoding phase. Luckily, the
computations returned by `field.apply` are applicative, so we can use
`mapN` from cats:

```scala
Schema.record[Foo] { field =>
  (
    field("a",_.a)(Schema[String]),
    field("b", _.b)(Schema[Int])
  ).mapN(Foo.apply)
}
```

These definitions nest in the obvious way:

```scala
case class Bar(num: Int, foo: Foo)
val nestedSchema: Schema[Bar] =
  Schema.record { field =>
    (
      field("num", _.num)(Schema[Int]),
      field("foo", _.foo) {
        Schema.record { field =>
          (
            field("a", _.a)(Schema[String]),
            field("b",_.b)(Schema[Int])
          ).mapN(Foo.apply)
        }
      }
    ).mapN(Bar.apply)
}
```
<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
val bar = Bar(10, Foo("value of Foo", 40))
// bar: Bar = Bar(10,Foo(value of Foo,40))
nestedSchema.write(bar)
// res12: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "num": { "N": "10" },
//   "foo": {
//     "M": {
//       "a": { "S": "value of Foo" },
//       "b": { "N": "40" }
//     }
//   }
// })
```
</details>

and you can simply use `map` for a record with only one field:

```scala
case class Baz(word: String)
val bazSchema = Schema.record[Baz] { field =>
  field("word", _.word)(Schema[String]).map(Baz.apply)
}
```
<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
bazSchema.write(Baz("hello"))
// res13: Either[Schema.WriteError, DynamoValue] = Right("M": { "word": { "S": "hello" } })
```
</details>

> **Notes:** 
> - `record` is designed to help type inference as much as possible, but
  you **have** to specify which type your schema is for, either with an
  ascription or an annotation. If you don't do that, your
  accessor functions inside `field` will not infer:
    ```scala
      val good = Schema.record[Foo] { field => ???}
      val alsoGood: Schema[Foo] = Schema.record { field => ??? }
      val bad = Schema.record { field => ??? }
    ```
> - You can name the builder that `record` gives you however you want
  obviously, but `field` is nice and descriptive.

### Implicit vs explicit schemas in `field`

In general, `Schema` is not a typeclass since there often are multiple
different encodings for the same type, but at the same time typing
`Schema[String]` everywhere for primitive types whose encoding hardly
if ever changes gets old quickly.
The `field` builder is designed to take the schema of the field as its
sole implicit argument, so that you can pass schemas implicitly or
explicitly at ease.  

> The recommended guideline is to pass schemas for primitives
implicitly, and schemas for your own datatypes explicitly.

This is how the previous schema would look like with the proposed
guideline:

```scala
Schema.record[Bar] { field =>
  (
    field("num", _.num),
    field("foo", _.foo) {
      Schema.record { field =>
        (field("a", _.a), field("b", _.b)).mapN(Foo.apply)
      }
    }
  ).mapN(Bar.apply)
}
```

### Additional structure

The monadic nature of the `field` builder allows to give additional
structure to the serialised record without affecting the code
representation. For example, given our `Foo`:

```scala
case class Foo(a: String, b: Int)

val fooSchema = Schema.record[Foo] { field =>
 (
   field("a", _.a),
   field("b", _.b)
 ).mapN(Foo.apply)
}
```

We would like to produce a record that wraps `Foo` in an envelope
containing an `eventId` and a `payload`. We will take advantage of `*>`, a
variant of `mapN` from `cats` which discards the left-hand side of an
applicative computation:

```scala
val randomEventId = "14tafet143ba"
val envelopeSchema = Schema.record[Foo] { field =>
  field("eventId", _ => randomEventId) *> field("payload", x => x)(fooSchema)
}
```

<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
envelopeSchema.write(Foo("value of Foo", 150))
// res16: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "eventId": { "S": "14tafet143ba" },
//   "payload": {
//     "M": {
//       "a": { "S": "value of Foo" },
//       "b": { "N": "150" }
//     }
//   }
// })
```
</details>

A particularly common scenario is wrapping an entire schema in a
record with a single key, so `dynosaur` exposes a `tag` method on
`Schema` for this purpose.

```scala
val taggedSchema = envelopeSchema.tag("event")
```

<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
taggedSchema.write(Foo("value of Foo", 150))
// res17: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "event": {
//     "M": {
//       "eventId": {
//         "S": "14tafet143ba"
//       },
//       "payload": {
//         "M": {
//           "a": { "S": "value of Foo" },
//           "b": { "N": "150" }
//         }
//       }
//     }
//   }
// })
```
</details>

Finally, it's worth specifying the meaning of `pure`, e.g. :
```scala
Schema.record[Foo](_.pure(Foo("a", 1)))
```

because we have never called `field.apply`, the resulting schema will
output the empty record during the encoding phase, and always succeed
with `Foo("a", 1)` during the decoding phase. As we will see later in
this document, this behaviour will prove useful when dealing with
objects in ADTs.

### Constant fields

So far, decoding records has been entirely based on the _key_ of each
field, letting the value be anything that can be converted to the
desired type. However, sometimes we need to assert that a field
contains a specific constant, and fail decoding if any other value is
found.  
Although this logic can be expressed entirely in terms of `field.apply` and
`imapErr`, `field` offers a dedicated method for this scenario,
`field.const`.  
For example, asserting that our `Foo` has `version: 1.0` is as simple as:

```scala
val versionedFooSchema = Schema.record[Foo] { field =>
 field.const("version", "1.0") *> (
   field("a", _.a),
   field("b", _.b)
 ).mapN(Foo.apply)
}
```

<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
versionedFooSchema.write(Foo("value of Foo", 300))
// res19: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "a": { "S": "value of Foo" },
//   "b": { "N": "300" },
//   "version": { "S": "1.0" }
// })
```
</details>

Note how the resulting record has a `version` field set to `1.0`, the
use of `const` guarantees that any other value will result in a
`ReadError`. Equality is performed using `==`.

### Case classes with more than 22 fields

Scala's tuples have a hard limit of 22 elements, so if your case class has
more than 22 fields you won't be able to call `(f1, ..., f23).mapN`.  
Just use `for` for this case:

```scala
record[BigClass] { field =>
  for {
    f1 <- field(...)
    ...
    f23 <- field(...)
  } yield BigClass(f1, .., f23)
}
```

### Optional fields & nullable values

In order to fully capture the semantics of AttributeValue (which are
like JSON in this case), `dynosaur` draws a distinction between
_optional fields_ and _nullable values_:

- An optional field may or may not be part of the serialised record,
  but if it's there it cannot be `AttributeValue.Nul` for decoding to
  succeed.
- A nullable value can be `AttributeValue.Nul`, but it has to always
  be part of the record for decoding to succeed. It can also appear
  outside of records.

As a general rule, optional fields should be preferred. They can be
constructed by calling the `opt` method on the `field` builder, which
is exactly like `field.apply` except for the accessor function which
has type `Record => Option[Field]` instead of `Record => Field`.

```scala
case class Msg(body: String, topic: Option[String])

val msgSchemaOpt = Schema.record[Msg] { field =>
 (
   field("body", _.body),
   field.opt("topic", _.topic)
 ).mapN(Msg.apply)
}

```

<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
msgSchemaOpt.write(Msg("Topical message", "Interesting topic".some))
// res20: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "topic": { "S": "Interesting topic" },
//   "body": { "S": "Topical message" }
// })
msgSchemaOpt.write(Msg("Random message", None))
// res21: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "body": { "S": "Random message" }
// })
```
</details>

To create a nullable value instead, use `field.apply` as normal, but
call `_.nullable` on the schema passed to it. If you are passing the
schema implicitly, just pass `Schema.nullable` instead:

```scala
val msgSchemaNull = Schema.record[Msg] { field =>
 (
   field("body", _.body),
   field("topic", _.topic)(Schema.nullable)
 ).mapN(Msg.apply)
}

```

In this case, the call to `Schema.nullable` translates to `Schema[String].nullable`.

<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
msgSchemaNull.write(Msg("Topical message", "Interesting topic".some))
// res22: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "topic": { "S": "Interesting topic" },
//   "body": { "S": "Topical message" }
// })
msgSchemaNull.write(Msg("Random message", None))
// res23: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "topic": { "NULL": true },
//   "body": { "S": "Random message" }
// })
```
</details>

> **Notes:**
> - Because of the choice between optionality and nullability, there
>   is no inductive implicit instance of `Schema` for `Option`. Schema
>   has an implicitNotFound annotation to warn you to use `opt` or
>   `nullable`
> - If desired, one can be lenient and accept both missing and null fields.
    The following code favours missing fields on writes, but accepts both on reads:
      ```scala
       field
         .opt("topic", _.topic.map(_.some))(Schema.nullable)
         .map(_.flatten)
      ```
    whereas this one favours null fields on writes, equally accepting both on reads:
      ```scala
       field
         .opt("topic", _.topic.some)(Schema.nullable)
         .map(_.flatten)
      ```
>   These cases are rare enough, and at moment `dynosaur` does not offer a shortcut for them.
> TODO NULL:true, NULL:false is not supported

## Coproducts

Let's now move on to coproducts, by looking at this basic ADT:

```scala
sealed trait Basic
case class One(s: String) extends Basic
case class Two(n: Int) extends Basic
```

with the corresponding schema:

```scala
val basicADTSchema = Schema.oneOf[Basic] { alt =>
  val one = Schema.record[One]{ field => field("s", _.s).map(One.apply) }
  val two = Schema.record[Two]{ field => field("n", _.n).map(Two.apply) }

  alt(one) |+| alt(two)
}
```

<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
val one = One("this is one")
// one: One = One(this is one)
val two = Two(4)
// two: Two = Two(4)

basicADTSchema.write(one)
// res24: Either[Schema.WriteError, DynamoValue] = Right("M": { "s": { "S": "this is one" } })
basicADTSchema.write(one).flatMap(basicADTSchema.read)
// res25: Either[Schema.DynosaurError, Basic] = Right(One(this is one))
basicADTSchema.write(two)
// res26: Either[Schema.WriteError, DynamoValue] = Right("M": { "n": { "N": "4" } })
basicADTSchema.write(two).flatMap(basicADTSchema.read)
// res27: Either[Schema.DynosaurError, Basic] = Right(Two(4))
```
</details>


The definitions of `one` and `two` should be unsurprising, but we need
an additional combinator to express the concept of _choice_,
`Schema.oneOf`:

```scala
Schema.oneOf[Basic] { alt => }
```

Which states that `Basic` is a coproducts of several alternatives,
defined through the `alt` builder. The computations returned by `alt`
are monoids, so we can combine them through `|+|` to mean "orElse".
The `alt` builder takes two arguments:
- The schema of the alternative, for example `Schema[One]`
- An implicit `Prism[Basic, One]`, where `Prism` is defined by
   ```scala mdoc:compile-only
       case class Prism[A, B](tryGet: A => Option[B], inject: B => A)
   ```

> **Notes:**
> - `dynosaur` derives prisms automatically for ADTs, you don't need to do anything.
> - The same inference considerations of `record[Foo]` apply to `oneOf[Basic] `.
> - You need to make sure you handle all cases in `oneOf`, if you
>   forget to handle one, encoding will gracefully fail with a `WriteError`.

To see how the `Prism` shape arises when dealing with choice, consider this:
-  When decoding, we need to always transform the variant we have
   successfully decoded (e.g. `One`) into the coproduct (in this case
   `Basic`). This can be expressed as `B => A`
- When encoding, for each case we need to check whether the coproduct
  actually matches the given case (e.g. if it's `One` or not).  This
  can be expressed as `A => Option[B]`.
- A `Prism` packages these two functions into one entity, and gives us
  a structure for composition: when encoding, we will do the
  equivalent of pattern matching to select the right encoder. When
  decoding, we will try each decoder until we find a successful one,
  or fail if none of the alternatives successfully decodes our data.

The semantics described above are enough to encode _choice_, but there
is a final issue to solve: _ambiguity_. Consider this:

```scala
sealed trait A
case class B(v: String) extends A
case class C(v: String) extends A

val ambiguous: Schema[A] = Schema.oneOf { alt =>
  val b: Schema[B] = Schema.record { field => field("v", _.v).map(B.apply) }
  val c: Schema[C] = Schema.record { field => field("v", _.v).map(C.apply) }

  alt(b) |+| alt(c)
}
```

`a` needs to distinguish between `b` and `c` when decoding, but their
encoded form is the same:

<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
ambiguous.write(B("hello"))
// res28: Either[Schema.WriteError, DynamoValue] = Right("M": { "v": { "S": "hello" } })
ambiguous.write(C("hello"))
// res29: Either[Schema.WriteError, DynamoValue] = Right("M": { "v": { "S": "hello" } })
// gives incorrect result
ambiguous.write(C("hello")).flatMap(ambiguous.read)
// res30: Either[Schema.DynosaurError, A] = Right(B(hello))
```
</details>

`dynosaur` is expressive enough to solve this problem in several ways,
in this document we will have a look at two possible strategies:
**discriminator keys** and **discriminator fields**.

### Discriminator keys

We will use this ADT as our running example:

```scala
sealed trait Problem
case class Error(msg: String) extends Problem
case class Warning(msg: String) extends Problem
case object Unknown extends Problem
```

and once again, `Error` and `Warning` exhibit ambiguity:

```scala
val err = Schema.record[Error] { field =>
  field("msg", _.msg).map(Error.apply)
}
val warn = Schema.record[Warning] { field =>
  field("msg", _.msg).map(Warning.apply)
}
```

The discriminator key strategy simply consists in wrapping each case
in a single-field record, whose key is the name of the case.
We have already seen a combinator that can do this, the `tag` method
on `Schema`:

```scala
val err = Schema.record[Error] { field =>
  field("msg", _.msg).map(Error.apply)
}.tag("error")

val warn = Schema.record[Warning] { field =>
  field("msg", _.msg).map(Warning.apply)
}.tag("warning")
```

Now the two records have different keys ("error" vs "warning"), and
decoding is no longer ambiguous.  
The final question is how to encode `Unknown`, we need to `tag` a
schema that produces an empty record on encoding, and always succeeds
with `Unknown` on decoding, but as we saw in the `Additional
structure` section, these are _exactly_ the semantics of `field.pure`:

```scala
val unknown = Schema.record[Unknown.type](_.pure(Unknown)).tag("unknown")
```

The final schema looks like this:

```scala
val schemaWithKey = Schema.oneOf[Problem] { alt =>
  val err = Schema.record[Error] { field =>
    field("msg", _.msg).map(Error.apply)
  }.tag("error")

  val warn = Schema.record[Warning] { field =>
    field("msg", _.msg).map(Warning.apply)
  }.tag("warning")

  val unknown = Schema.record[Unknown.type] { field =>
    field.pure(Unknown)
  }.tag("unknown")

  alt(err) |+| alt(warn) |+| alt(unknown)
}

```

<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
val error = Error("this is an error")
// error: Error = Error(this is an error)
val warning = Warning("this is a warning")
// warning: Warning = Warning(this is a warning)

schemaWithKey.write(error)
// res34: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "error": {
//     "M": {
//       "msg": { "S": "this is an error" }
//     }
//   }
// })
schemaWithKey.write(error).flatMap(schemaWithKey.read)
// res35: Either[Schema.DynosaurError, Problem] = Right(Error(this is an error))
schemaWithKey.write(warning)
// res36: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "warning": {
//     "M": {
//       "msg": {
//         "S": "this is a warning"
//       }
//     }
//   }
// })
schemaWithKey.write(warning).flatMap(schemaWithKey.read)
// res37: Either[Schema.DynosaurError, Problem] = Right(Warning(this is a warning))
schemaWithKey.write(Unknown)
// res38: Either[Schema.WriteError, DynamoValue] = Right("M": { "unknown": { "M": {  } } })
schemaWithKey.write(Unknown).flatMap(schemaWithKey.read)
// res39: Either[Schema.DynosaurError, Problem] = Right(Unknown)
```
</details>


> **Notes:**
> - The discriminator key encoding is simple and convenient, but cannot
>   be used if your ADT is at the top level in your table, because
>   DynamoDB does not support attributes of type `M` as partition keys.

### Discriminator field

In the discriminator field approach, each record adds an additional
field (for example called "type") to disambiguate.  
The only thing to note is the use of `field.const` to make sure
decoding succeeds or fails based on the _specific value_ of the field,
and not just the fact that there is a field called "type" whose value
is a `String`. The rest just uses straightforward combinators from cats:
`map`, `*>`,`as`.

The schema looks like this:

```scala
val schemaWithField = Schema.oneOf[Problem] { alt =>
  val err = Schema.record[Error] { field =>
     field.const("type", "error") *> field("msg", _.msg).map(Error.apply)
  }

  val warn = Schema.record[Warning] { field =>
    field.const("type", "warning") *> field("msg", _.msg).map(Warning.apply)
  }

  val unknown = Schema.record[Unknown.type] { field =>
    field.const("type", "unknown").as(Unknown)
  }

  alt(err) |+| alt(warn) |+| alt(unknown)
}
```

<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
schemaWithField.write(error)
// res40: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "msg": { "S": "this is an error" },
//   "type": { "S": "error" }
// })
schemaWithField.write(error).flatMap(schemaWithField.read)
// res41: Either[Schema.DynosaurError, Problem] = Right(Error(this is an error))
schemaWithField.write(warning)
// res42: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "msg": { "S": "this is a warning" },
//   "type": { "S": "warning" }
// })
schemaWithField.write(warning).flatMap(schemaWithField.read)
// res43: Either[Schema.DynosaurError, Problem] = Right(Warning(this is a warning))
schemaWithField.write(Unknown)
// res44: Either[Schema.WriteError, DynamoValue] = Right("M": { "type": { "S": "unknown" } })
schemaWithField.write(Unknown).flatMap(schemaWithField.read)
// res45: Either[Schema.DynosaurError, Problem] = Right(Unknown)
```
</details>

> **Notes:**
> - The same idea behind this encoding can be used for other scenarios
>   as well, for example you could use a `version` field in conjuction
>   with `alt` to support multiple versions of the same data in a
>   single table.


## Sequences and Maps

`dynosaur` exposes implicit inductive instances for `List[A]`,
`Vector[A]` and `Seq[A]`, provided there is a `Schema[A]` in scope.
If you are passing schemas explicitly, you can call `asList`,
`asVector` or `asSeq` on a `Schema[A]` to obtain the corresponding
`Schema[Collection[A]]`.
The are all represented as `L` in `AttributeValue`:

<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
Schema[Vector[Int]].write(Vector(1, 2, 3))
// res46: Either[Schema.WriteError, DynamoValue] = Right("L": [
//   { "N": "1" },
//   { "N": "2" },
//   { "N": "3" }
// ])
fooSchema.asList.write(List(Foo("a", 1), Foo("b", 2), Foo("c", 3)))
// res47: Either[Schema.WriteError, DynamoValue] = Right("L": [
//   {
//     "M": {
//       "a": { "S": "a" },
//       "b": { "N": "1" }
//     }
//   },
//   {
//     "M": {
//       "a": { "S": "b" },
//       "b": { "N": "2" }
//     }
//   },
//   {
//     "M": {
//       "a": { "S": "c" },
//       "b": { "N": "3" }
//     }
//   }
// ])
```
</details>

Note that bytes do not fit the above description: the library has
separate instances for `Array[Byte]` and `scodec.bits.ByteVector`, and
both are represented as `B` in `AttributeValue`. This requires the
bytes to be base 64 encoded/decoded , which is done automatically for
you.

As with sequences, there is an inductive instance of
`Schema[Map[String, A]]` given `Schema[A]`, also available by calling
`asMap` on a schema.

<details>
<summary>Click to show the resulting DynamoValue</summary>

```scala
Schema[Map[String, Int]].write(Map("hello" -> 1))
// res48: Either[Schema.WriteError, DynamoValue] = Right("M": { "hello": { "N": "1" } })
fooSchema.asMap.write(Map("A foo" -> Foo("a", 1)))
// res49: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "A foo": {
//     "M": {
//       "a": { "S": "a" },
//       "b": { "N": "1" }
//     }
//   }
// })
```
</details>

> **Notes:**
> - If you need to represent a Map whose keys aren't directly
>   `String`, but instead newtypes or enums, just use
>   `imap`/`imapErr`/`xmap` on the Map schema.

## Recursive schemas

TODO use Schema.defer


## ByteSet, StringSet and NumberSet

TODO what to do about SS BS and NS?
NonEmpty vs what scanamo does (puts NULL, can conflict with Option)
implicit inductive instances on NonEmptySet[String], and NonEmptySet(numeric stuff), with corresponding as* methods
only on the appropriate schemas. If you have something you wish to represent as NS or SS, e.g. a
Set of newtypes, use imap appropriately on it (example with string sets?)

## Section with expandable examples using `for` only



