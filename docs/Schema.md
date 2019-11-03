---
id: schema
title: Encoding and decoding
---

`Dynosaur` design for codecs (pioneered by the
[xenomorph](https://github.com/nuttycom/xenomorph) library) is based
on defining _schemas_ for your data, rather than your typical
`Encoder/Decoder` typeclasses.
The central type of the DSL is `Schema[A]`, which you can think of as
either a representation of `A`, or a recipe for _both_ encoding and
decoding `A`s to and from `AttributeValue`.

**Note:**  basic familiarity with `cats` typeclasses like `Monoid` and
`Applicative` is required.

## Setup

We are going to need the following imports:

```scala mdoc
import dynosaur.codec.Schema
import cats.implicits._
```

We will also define `.read` and `.write` helpers to run the examples
in this page, but you are not going to need them in your own code.  
For the time being, we will ignore potential errors, and show the
output as `Json` instead of the `AttributeValue` ADT to help with
readability.

<details>
<summary>Click to expand</summary>

```scala mdoc
implicit class Codecs[A](schema: Schema[A]) {
  import dynosaur.codec.{Encoder, Decoder}
  import dynosaur.model.AttributeValue
  import dynosaur.lo.codec._
  import io.circe._, syntax._
  
  def write(v: A): Json = 
    Encoder.fromSchema(schema)
    .write(v)
    .map(_.asJson)
    .toOption.get
    
  def read(v: Json): A = {
    for {
      av <- v.as[AttributeValue].toOption
      a <- Decoder.fromSchema(schema).read(av).toOption
    } yield a
  }.get
}
```
</details>

## Quick example 

Given this simple ADT

```scala mdoc
sealed trait Auth
object Auth {
  case class Error(reason: String) extends Auth
  case class User(id: Int, name: String) extends Auth
}

```
We define a schema for it

```scala mdoc:silent
val schema: Schema[Auth] = {
  import Schema._

  val error = record[Auth.Error] { field =>
    field("reason", _.reason)(str).map(Auth.Error.apply)
   }
   
  val user = record[Auth.User] { field =>
    (
      field("id", _.id)(num),
      field("name", _.name)(str)
    ).mapN(Auth.User.apply)
  }
  
  oneOf { alt =>
    alt(error tag "error") |+| alt(user tag "user") 
  }
}
```

Which can then be used for both encoding and decoding:

<details>
<summary>Click to show the resulting AttributeValue</summary>

```scala mdoc:to-string
val u = Auth.User(303, "tim")
val e = Auth.Error("Unauthorized")

schema.write(u)
schema.read(schema.write(u))
schema.write(e)
schema.read(schema.write(e))
```
</details>

In the rest of the document, we will only show encoding since decoding
comes for free, unless there is something specific to point out about
the behaviour of the decoder.

## Motivation

The typical approach most libraries use for codecs involves
`Encoder/Decoder` typeclasses, sometimes including automatic derivation.
This approach has the following drawbacks:
- Automatic derivation is useful, but it's not flexible enough to
  cover many potential transformations you want to do on the serialised
  data, like changing field names, flattening some records, adding
  extra information, or have different encodings for sums types.
- Writing explicit encoders and decoders is annoying because you need
  to keep them in sync, and the required code is similar enough to be
  tedious, but different enough to be error prone.  
  Even without this duplication, the process is still made hard by the
  fact that you are dealing with the practical details of traversing a
  low level data structure like Json or AttributeValue.
  
As a result, people abuse automatic derivation, and end up with either
ugly serialised data and a nice code model, or nice serialised data
and an ugly code model.   
The schema DSL provided by Dynosaur, on the other hand, allows you to
be flexible in how you define your serialised data, without
duplicating code for encoders and decoders, and with a declarative
focus on the _structure_ of the data, rather than the traversal of a
low level representation.

## Primitives

The simplest instances of `Schema` are primitives, for example `str`
represents the ability to encode and decode an arbitrary `String`.
The following primitives are supported:
```scala
def str: Schema[String]
...
```

## Bidirectional mappings

New schemas can be created from existing ones by declaring a
bidirectional mapping between them.  
The most general way is using the `xmap` method on `Schema`:
```scala
class Schema[A] {
  def xmap[B](f: A => Either[ReadError, B])(g: B => Either[WriteError, A]): Schema[B]
  ...
}
```

although in many cases its two specialised variants `imap` and
`imapErr` are sufficient:

```scala
class Schema[A] {
  ...
  def imap[B](f: A => B)(g: B => A): Schema[B]
  def imapErr[B](f: A => Either[ReadError, B])(g: B => A): Schema[B]
  ...
}
```

`imap` defines an isomorphism between `A` and `B`, which often arises
when using newtypes such as:

```scala mdoc
case class EventId(id: String)
```

We would like to keep the specialised representation of `EventId` in
our code, but represent it as a simple `String` in Dynamo, without the
extra nesting.

```scala mdoc:silent
val eventIdSchema = Schema.str.imap(EventId.apply)(_.value)
```
<details>
<summary>Click to show the resulting AttributeValue</summary>

```scala mdoc:to-string
eventIdSchema.write(EventId("event-1234"))
```
</details>

`imapErr` encodes the common case where encoding cannot fail but
decoding can, as seen, for example, in enums:

```scala mdoc:silent
import dynosaur.codec.ReadError

sealed trait Switch
object Switch {
  case object On extends Switch
  case object Off extends Switch

  def parse: String => Option[Switch] = _.trim.toLowerCase match {
    case "on" => On.some
    case "off" => Off.some
    case _ => None
  }
}

def switchSchema = Schema.str.imapErr(_.toString) { s =>
   Switch.parse(s).toRight(ReadError()) // TODO s"$s is not a valid Switch"
 }
```

<details>
<summary>Click to show the resulting AttributeValue</summary>

```scala mdoc:to-string
val a = switchSchema.write(On)
```
</details>


## Records

Let's have a look at records with a case class example:
```scala mdoc
case class Foo(a: String, b: Int)
```
whose `Schema[Foo]` can be defined as:

```scala mdoc:silent
val fooSchema = Schema.record[Foo] { field =>
 (
  field("a", _.a)(Schema.str),
  field("b", _.b)(Schema.num)
 ).mapN(Foo.apply)
}

```

<details>
<summary>Click to show the resulting AttributeValue</summary>

```scala mdoc:to-string
fooSchema.write(Foo("a", 1))
```
</details>

The central component is the record builder:

```scala mdoc:compile-only
Schema.record[Foo] { field =>
  ???
}
```

Which states that the type `Foo` is represented by a record, and gives
you the `field` builder to create fields by calling its various
methods. The primary method is `apply`

```scala mdoc:compile-only
Schema.record[Foo] { field =>
 val b = field("b", _.b)(Schema.num)
  
 ???
}
```
it takes three arguments:

1. the name of the field in the resulting `AttributeValue`
2. A function to access the field during the encoding phase, in this case `Foo => Int`
3. the schema of the field, which is `Schema[Int]` is this case

Once we have declared our fields, we need to tell `dynosaur` how to
combine them into a `Foo` during the decoding phase. Luckily, the
computations returned by `field.apply` are monadic, so we can use
`mapN` from cats:

```scala mdoc:silent
Schema.record[Foo] { field =>
 (
  field("a",_.a)(Schema.str),
  field("b", _.b)(Schema.num)
 ).mapN(Foo.apply)
}
```

These definitions nest in the obvious way:

```scala mdoc:silent
case class Bar(n: Int, foo: Foo)
val nestedSchema: Schema[Bar] =
  record[Bar] { field =>
    field("n", _.n)(Schema.num)
    field("foo", _.foo) {
      record[Foo]{ field =>
        (
         field("a", _.a)(str),
         field("b",_.b)(num)
        ).mapN(Foo.apply)
      }
    }
  }
```
<details>
<summary>Click to show the resulting AttributeValue</summary>

```scala mdoc:to-string
val bar = Bar(10, Foo("value", 40))
nestedSchema.write(bar)
```
</details>

**Notes:**
- `record` is designed to help type inference as much as possible, but
  you **have** to specify which type your schema is for, either with an
  ascription or an annotation. If you don't do that, your
  accessor functions inside `field` will not infer.
  ```scala
  val good = Schema.record[Foo] { field => ???}
  val alsoGood: Schema[Foo] = Schema.record { field => ??? }
  val bad = Schema.record { field => ??? }
  ```
- You can name the builder that `record` gives you however you want
  obviously, but `field` is nice and descriptive.


## Extra information (TODO show nested version, + tagging)

It's easy to add data to the serialised record that isn't present in
the code representation, because we have the entire `Monad` api
at our disposal.   
For example let's say we want to add a random `eventId` to our record,
that we don't care about in our model: we can use `*>`, a variant of
`mapN` (also provided by `cats`) which discards the left-hand side.

```scala mdoc:to-string
val randomEventId = "14tafet143ba"

Schema.record[Foo] { field =>
  field("eventId", _ => randomEventId)(Schema.str) *>
  (
    field("a", _.a)(Schema.str),
    field("b", _.b)(Schema.num)
  ).mapN(Foo.apply)
}.write(Foo("foo", 345))
```

## Constants

field.const

## Optional fields

field.optional
make the point about optional fields vs optional values

## Case classes with more than 22 fields

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

## Coproducts
example, show oneOf builder, show |+|, talk about how to merge and Prisms

## Tagging
    
How about moving tagging to the record section as "additional structure", then 
we could have a "ambiguity" section here showing tagging or type fields with tools already used

## type field
moved above

## detail choice for objects
we've seen enums (all obj) and hierarchies with case classes, what about mixed?
can use empty records or string

## withDefault

## sequences
    hello

