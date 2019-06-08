
# Encoding and decoding

`Dynosaur` design for codecs (pioneered by the
[xenomorph](https://github.com/nuttycom/xenomorph) library) is based
on defining _schemas_ for your data, rather than your typical
`Encoder/Decoder` typeclasses.

**Note:**  basic familiarity with `cats` typeclasses like `Monoid` and
`Applicative` is required.

## Setup

We are going to need the following imports:

```scala mdoc
import dynosaur.codec.Schema
import cats.implicits._
```

We will also define some helpers to run the examples in this page, but
you are not going to need them in your own code.  
For the time being, we will ignore potential errors, and show the
output as `Json` instead of the `AttributeValue` ADT to help with
readability.

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
    alt(tag("error")(error)) |+| alt(tag("user")(user)) 
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

## Schema

The central type of the DSL is `Schema[A]`, which you can think of as
either a representation of `A`, or a recipe for _both_ encoding and
decoding `A`s to and from `AttributeValue`.
The simplest instances of `Schema` are primitives, for example `str`
represents the ability to encode and decode an arbitrary `String`.

```scala mdoc:compile-only
val strings: Schema[String] = Schema.str
```

### Records

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

Let's unpack what's going on there.

1. The first component is `field`:
   ```scala mdoc:compile-only
   import Schema.structure.{Ap, Field}

   val fieldExample: Ap[Field[Foo, ?], String] =
     Schema.field("a", Schema.str, _.a) 
   ```
   Which represents an _applicative computation_ that accesses
   fields of `Foo`, and returns `String.`

   Points to note:
   - `"a"` is the name of the field in the serialised `AttributeValue`.
   - `Schema.str: Schema[String]` is the schema for the value of that
     field. It says that you will get a `String` when decoding, which is
     why `fieldExample` returns a `String` as a whole.
   - `_.a` describes how to access the `a` field when given a
       `Foo`. Because the value of the field is described by
       `Schema.str`, `_.a` needs to return a `String` or you will get a
       compile error.

   Another thing to point out is that the following will fail to compile,
   because Scala is not able to infer that the argument of `_.a` is of
   type `Foo`.

   ```scala mdoc:fail
   val failedFieldExample = Schema.field("a", Schema.str, _.a) 
   ```

   You can annotate the function directly, but `field` uses the partially
   applied type parameter trick as well.

   ```scala mdoc:compile-only
   val fieldExample = Schema.field[Foo]("a", Schema.str, _.a)
   ```
   We can define a field for `b` in a similar fashion.
   ```scala mdoc:compile-only
   import Schema.structure.{Ap, Field}

   val b: Ap[Field[Foo, ?], Int] = 
     Schema.field("b", Schema.num, _.b)
   ```

2. The second step is the ability to say that if we can decode a field
   named `"a"` as a `String`, and a field named `"b"` as an `Int`, we can
   decode a `Foo`.
   In pseudo-code:
   ```scala
   Decoded[String]
   Decoded[Int]
   (String, Int) => Foo
   ----
   Decoded[Foo]
   ```
   This is exactly the shape that `cats.Applicative` encodes, and our
   `field` returns applicative computations, which means we can do:

   ```scala mdoc:compile-only
   import Schema.structure.{Ap, Field}

   val foo: Ap[Field[Foo, ?], Foo] = 
    (
     Schema.field[Foo]("a", Schema.str, _.a),
     Schema.field[Foo]("b", Schema.num, _.b),
    ).mapN(Foo.apply)
   ```

   The `fields` constructor can turn the above into a `Schema[Foo]` 
   ```scala mdoc:compile-only
   val foo: Schema[Foo] = Schema.fields {
    (
     Schema.field[Foo]("a", Schema.str, _.a),
     Schema.field[Foo]("b", Schema.num, _.b),
    ).mapN(Foo.apply)
   }
   ```
   
3. Finally, the schema for `foo` is a bit cluttered by all those `[Foo]`
   ascriptions to help inference. You do need at least one due to
   fundamental limitations of type inference in Scala, but the library
   provides the `record` builder to minimise clutter for you.

   The basic idea is that instead of writing

   ```scala mdoc:compile-only
   val foo: Schema[Foo] = Schema.fields {
    (
     Schema.field[Foo]("a", Schema.str, _.a),
     Schema.field[Foo]("b", Schema.num, _.b),
    ).mapN(Foo.apply)
   }
   ```

   You can write

   ```scala mdoc:compile-only
   val foo: Schema[Foo] = Schema.record { field =>
    (
     field("a", Schema.str, _.a),
     field("b", Schema.num, _.b),
    ).mapN(Foo.apply)
   }
   ```
   You can even omit the type signature altogether as long as you
   put a type ascription on `record`, which is how our initial example
   schema looked like:

   ```scala mdoc:compile-only
   val foo = Schema.record[Foo] { field =>
    (
     field("a", Schema.str, _.a),
     field("b", Schema.num, _.b),
    ).mapN(Foo.apply)
   }
   ```

   Note that `field` there is the argument to the function passed to
   `record`, and not `Schema.field`. If you prefer, you can rename that
   to whatever you like.

### Nested records

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

### Flattening records

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

```scala mdoc:to-string
Schema.record[Error] { field =>
  (
    field("code", Schema.num, _.code),
    field("msg", Schema.str, _.msg.value)
  ).mapN((code, msg) => Error(code, Msg(msg)))
}.write(errMsg)
```

### Extra information

It's easy to add data to the serialised record that isn't present in
the code representation, because we have the entire `Applicative` api
at our disposal.   
Let's add a `version: 1.0` field to `Foo` using `*>`, a variant of
`mapN` (also provided by `cats`) which discards the left-hand side.

```scala mdoc:to-string
Schema.record[Foo] { field =>
  field("version", Schema.str, _ => "1.0") *>
  (
    field("a", Schema.str, _.a),
    field("b", Schema.num, _.b)
  ).mapN(Foo.apply)
}.write(Foo("foo", 345))
```

In this case is worth specifying something with respect to decoding:
`field("version", Schema.str, _ => "1.0")` means that the record
_must_ contain a field named `"version"`, but that the contents of
that field can be _any_ `String` (that's what `Schema.str` means),
even though when we serialise we put `"1.0"` there.  
Therefore, parsing `Foo` with the schema above will fail if the record
does not contain a field named `"version"`, but if it does it will
succeed no matter what the value of that field is, as long as it is a
`String`.  
Both the ability to assert that a field may not be there, and that the
value of a field should be a _specific_ `String` (or anything else)
are useful, they are treated further down in this document.


### Sums

### Optional

### Sequences
