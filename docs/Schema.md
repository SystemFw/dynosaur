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
val schema: Schema[Auth] = Schema.oneOf { alt =>
  val error = Schema.record[Auth.Error] { field =>
    field("reason", _.reason).map(Auth.Error.apply)
   }
   
  val user = Schema.record[Auth.User] { field =>
    (
      field("id", _.id),
      field("name", _.name)
    ).mapN(Auth.User.apply)
  }
  
  alt(error tag "error") |+| alt(user tag "user") 
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

```

although in many cases its two specialised variants `imap` and
`imapErr` are sufficient:

```scala
class Schema[A] {
  def imap[B](f: A => B)(g: B => A): Schema[B]
  def imapErr[B](f: A => Either[ReadError, B])(g: B => A): Schema[B]

```

`imap` defines an isomorphism between `A` and `B`, which often arises
when using newtypes such as:

```scala mdoc
case class EventId(value: String)
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
    case _ => none
  }
}

def switchSchema = Schema.str.imapErr { s =>
   Switch.parse(s).toRight(ReadError()) // TODO s"$s is not a valid Switch"
 }(_.toString)
```

<details>
<summary>Click to show the resulting AttributeValue</summary>

```scala mdoc:to-string
val a = switchSchema.write(Switch.On)
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
fooSchema.write(Foo("value of Foo", 1))
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
which takes three arguments:

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
  Schema.record { field =>
   (
     field("n", _.n)(Schema.num),
     field("foo", _.foo) {
       Schema.record { field =>
         (
          field("a", _.a)(Schema.str),
          field("b",_.b)(Schema.num)
         ).mapN(Foo.apply)
       }
     }
    ).mapN(Bar.apply)
  }
```
<details>
<summary>Click to show the resulting AttributeValue</summary>

```scala mdoc:to-string
val bar = Bar(10, Foo("value of Foo", 40))
nestedSchema.write(bar)
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

## Implicit vs explicit schemas in `field`

In general, `Schema` is not a typeclass since there often are multiple
different encodings for the same type, but at the same time typing
`Schema.str` everywhere gets old quickly.
The `field` builder is designed to take the schema of the field as its
sole implicit argument, so that you can pass schemas implicitly or
explicitly at ease.  

> The recommended guideline is to pass schemas for primitives
implicitly, and schemas for your own datatypes explicitly.

This is how the previous schema would look like with the proposed
guideline:

```scala mdoc:compile-only
Schema.record[Bar] { field =>
 (
   field("n", _.n),
   field("foo", _.foo) {
     Schema.record { field =>
       (
         field("a", _.a),
         field("b", _.b)
       ).mapN(Foo.apply)
     }
   }
 ).mapN(Bar.apply)
}
```

Additionally, `Schema.apply` can be used to fetch a schema from the
implicit scope, for example:

```scala mdoc:compile-only
Schema[String].imap(EventId.apply)(_.value)
```

## Additional structure

The monadic nature of the `field` builder allows to give additional
structure to the serialised record without affecting the code
representation. For example, given our `Foo`:

```scala mdoc:compile-only
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

```scala mdoc:silent
val randomEventId = "14tafet143ba"
val envelopeSchema = Schema.record[Foo] { field =>
  field("eventId", _ => randomEventId) *> field("payload", x => x)(fooSchema)
}
```

<details>
<summary>Click to show the resulting AttributeValue</summary>

```scala mdoc:to-string
envelopeSchema.write(Foo("value of Foo", 150))
```
</details>

A particularly common scenario is wrapping an entire schema in a
record with a single key, so `dynosaur` exposed a `tag` method on
`Schema` for this purpose.

```scala mdoc:silent
val taggedSchema = envelopeSchema.tag("event")
```

<details>
<summary>Click to show the resulting AttributeValue</summary>

```scala mdoc:to-string
taggedSchema.write(Foo("value of Foo", 150))
```
</details>

Finally, it's worth specifying the meaning of `pure`, e.g. :
```scala mdoc:compile-only
Schema.record[Foo](_.pure(Foo("a", 1)))
```

because we have never called `field.apply`, the resulting schema will
output the empty record during the encoding phase, and always succeed
with `Foo("a", 1)` during the decoding phase. As we will see later in
this document, this behaviour will prove useful when dealing with
objects in ADTs.

## Constant fields

So far, decoding records has been entirely based on the _key_ of each
field, letting the value be anything that can be converted to the
desired type. However, sometimes we need to assert that a field
contains a specific constant, and fail decoding if any other value is
found.  
Although this logic can be expressed entirely in terms of `field` and
`imapErr`, `field` offers a dedicated method for this scenario,
`field.const`.  
For example, asserting that our `Foo` has `version: 1.0` is as simple as:

```scala mdoc:silent
val versionedFooSchema = Schema.record[Foo] { field =>
 field.const("version", "1.0") *> (
   field("a", _.a),
   field("b", _.b)
 ).mapN(Foo.apply)
}
```

<details>
<summary>Click to show the resulting AttributeValue</summary>

```scala mdoc:to-string
versionedFooSchema.write(Foo("value of Foo", 300))
```
</details>

Note how the resulting record has a `version` field set to `1.0`, the
use of `const` guarantees that any other value will result in a
`ReadError`. Equality is performed using `==`.

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
show to use empty records, add a combinator probably
incorporate this in the ambiguity section:
naive |+| works, but what about ambiguity and what about objects?

For the `type` encoding, objects just use field.const("type").as(object) and no other fields
for the map encoding, just `unit.tag("thing").imap...`, abbreviated as `singleton...`

## withDefault

## sequences
    hello

