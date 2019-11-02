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
    field("reason", str, _.reason).map(Auth.Error.apply)
   }
   
  val user = record[Auth.User] { field =>
    (
      field("id", num, _.id),
      field("name", str, _.name)
    ).mapN(Auth.User.apply)
  }
  
  oneOf { alt =>
    alt(error tag "error") |+| alt(user tag "user") 
  }
}
```

Which can then be used for both encoding and decoding
```scala mdoc:to-string
val u = Auth.User(303, "tim")
val e = Auth.Error("Unauthorized")

schema.write(u)
schema.read(schema.write(u))
schema.write(e)
schema.read(schema.write(e))
```

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

## Isos

You can also introduce a schema is declaring a bidirectional
relationship between your data and one of the primitives.
talk about isos, xmap and const, can use enums for example, 
change const to eqv

example:
 newtype for iso (+ date for exceptions?)
 enums for epi
 think about eqv


## Records

Let's have a look at records with a case class example:
```scala mdoc
case class Foo(a: String, b: Int)
```
whose `Schema[Foo]` can be defined as:

```scala mdoc:silent
val fooSchema = Schema.record[Foo] { field =>
 (
  field("a", Schema.str, _.a),
  field("b", Schema.num, _.b)
 ).mapN(Foo.apply)
}

```
Resulting in the expected `AttributeValue` representation:
```scala mdoc:to-string
fooSchema.write(Foo("a", 1))
```

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
 val b = field("b", Schema.num, _.b)
  
 ???
}
```
it takes three arguments:

1. the name of the field in the resulting `AttributeValue`
2. the schema of the field, which is `Schema[Int]` is this case
3. a function to access the field during the encoding phase, in this
  case `Foo => Int` since the source type is `Foo` and the field is
  represented by a `Schema[Int]`

Once we have declared our fields, we need to tell `dynosaur` how to
combine them into a `Foo` during the decoding phase. Luckily, the
computations returned by `field.apply` are monadic, so we can use
`mapN` from cats:

```scala mdoc:silent
Schema.record[Foo] { field =>
 (
  field("a", Schema.str, _.a),
  field("b", Schema.num, _.b)
 ).mapN(Foo.apply)
}
```

TODO: they nest the obvious way (move Schema arg to a separate list)

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

## Nested records

Nested records naturally correspond to nested schemas

```scala mdoc:silent
case class Bar(n: Int, foo: Foo)
val nestedSchema: Schema[Bar] = {
  import Schema._
  // we could also reuse the one defined above, of course
  val foo = record[Foo] { field =>
    (
      field("a", str, _.a),
      field("b", num, _.b)
    ).mapN(Foo.apply)
  }
  
  record { field =>
    (
     field("n", num, _.n),
     field("foo", foo, _.foo) // we pass `foo` here
    ).mapN(Bar.apply)
  }
}
```
```scala mdoc:to-string
val bar = Bar(10, Foo("value", 40))
nestedSchema.write(bar)
```

## Flattening records

When modelling data in code, sometimes it's desirable to introduce newtypes
for extra precision:
```scala mdoc
case class Msg(value: String)
case class Error(code: Int, msg: Msg)
```
This would result in extra nesting in the AttributeValue, if encoded as shown
above
```scala mdoc:to-string
val errMsg = Error(2, Msg("problem"))

Schema.record[Error] { field =>
 (
   field("code", Schema.num, _.code),
   field(
     "msg",
     Schema.record[Msg](_("value", Schema.str, _.value).map(Msg.apply)),
     _.msg
   )
  ).mapN(Error.apply)
}.write(errMsg)
```

We'd like to keep the nesting in our code, but not in Dynamo.  
Here's one way to do it by changing the schema for `msg` to a `String`
and changing the accessor and constructor functions accordingly.
We will use `for` for convenience.

```scala mdoc:to-string
Schema.record[Error] { field =>
  for {
    code <- field("code", Schema.num, _.code)
    msg <- field("msg", Schema.str, _.msg.value)
  } yield Error(code, Msg(msg))
}.write(errMsg)
```

TODO can I use iso here? and remove this section


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
  field("eventId", Schema.str, _ => randomEventId) *>
  (
    field("a", Schema.str, _.a),
    field("b", Schema.num, _.b)
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
