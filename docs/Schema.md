---
id: schema
title: Encoding and decoding
---


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
   import cats.free.Free
   import Schema.structure.Field

   val fieldExample: Free[Field[Foo, ?], String] =
     Schema.field("a", Schema.str, _.a) 
   ```
   Which represents an computation that accesses
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
   import cats.free.Free
   import Schema.structure.Field

   val b: Free[Field[Foo, ?], Int] = 
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
   `field` returns `Free` computations, which are instances of
   `Applicative` and allow us to do:

   ```scala mdoc:compile-only
   import cats.free.Free
   import Schema.structure.Field

   val foo: Free[Field[Foo, ?], Foo] = 
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
We will use `for` for convenience.

```scala mdoc:to-string
Schema.record[Error] { field =>
  for {
    code <- field("code", Schema.num, _.code)
    msg <- field("msg", Schema.str, _.msg.value)
  } yield Error(code, Msg(msg))
}.write(errMsg)
```

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

### Extra information

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

### Constants

### Coproducts

Since DynamoDB uses a JSON-like format, there are different ways to
encode coproducts. The recommended one looks like this:

```scala mdoc:compile-only
sealed trait Auth
case class Error(reason: String) extends Auth
case class User(id: Int, name: String) extends Auth

def error: Schema[Error] = ???
def user: Schema[User] = ???

import Schema._

val schema: Schema[Auth] = oneOf { alt =>
  alt(error tag "error") |+| alt(user tag "user") 
}
```

The first thing we need to make this work is a way to go from
`Schema[Subtype]` to `Schema[Supertype]`, which we can do by
introducing the concept of a `Prism`.  
Let's focus on `Error` and `Auth`, starting with decoding: if you have
a `Decoder[Error]` and you need a `Decoder[Auth]`, it means that after
you decoded an `Error`, you need to transform it into an `Auth`, i.e.
you need a `Error => Auth`.  
The other direction however, is a bit different: if you have an
`Encoder[Error]` and you want to build an `Encoder[Auth]`, you first
need to figure out if the `Auth` you have is indeed an `Error` (and not a
`User`), and only then you can use the encoder for errors that you
have. This decision can be represented with a `Auth => Option[Error]`.

Given that a `Schema` needs to produce both an `Encoder` and a `Decoder`,
we need to have both functions, which as it turns out form a well-known 
abstraction called `Prism`. `Dynosaur` defines a very simple `Prism` as:
```scala mdoc:compile-only
case class Prism[A, B](tryGet: A => Option[B], inject: B => A)
```

One can define `Prism`s for several things, but the one between an ADT
and one of its cases is particularly common and straighforward.

```scala mdoc:compile-only
import dynosaur.codec.Prism

sealed trait A
case class B() extends A

val p: Prism[A, B] = Prism.fromPartial[A, B] {
  case b: B => b
}(b => b: A)
```

In fact, it is so common that `Dynosaur` can derive it automatically
if you ask for one implicitly:

```scala mdoc
import dynosaur.codec.Prism

sealed trait PrismExample
case class Case1(v: String) extends PrismExample
case class Case2(i: Int) extends PrismExample

val c1: PrismExample = Case1("hello")

val p = implicitly[Prism[PrismExample, Case1]]

p.tryGet(c1).map(_.v)
p.inject(Case1("yes"))
p.tryGet(Case2(3))
```

So we know how to write a schema for the individual cases, we know
that `Prism` encodes their relationship with the supertype, all that's
left is a concept of choice, which is expressed by the `alt`
constructor.

```scala mdoc:compile-only
import cats.data.Chain
import Schema.structure.Alt

sealed trait Auth
case class Error(reason: String) extends Auth
case class User(id: Int, name: String) extends Auth

def error: Schema[Error] = ???
def user: Schema[User] = ???

def errorCase: Chain[Alt[Auth]] = Schema.alt(error)
def userCase: Chain[Alt[Auth]] = Schema.alt(user)
def allCases: Chain[Alt[Auth]] = errorCase |+| userCase
def authSchema: Schema[Auth] = Schema.alternatives(allCases)
```

In the above snippet:
-  `Chain[Alt[Auth]]` is the type of computations that express _choice
   between subtypes of `Auth`_.
- `alt` takes as arguments the schema of the subtype and an implicit,
  automatically derived `Prism`.
- The `Monoid` instance for `Chain` expresses choice using `|+|` to mean "or"
- The `Schema.alternatives` constructor builds a schema out of a set of choices

When encoding, we will do the equivalent of pattern matching to select
the right encoder. When decoding, we will try each decoder until we
find a successful one, or fail if none of the alternatives
successfully decodes our data. In a following section we will see how
to minimise the work decoders have to do.

**Note**: Unfortunately, it's up to you to make sure that you cover
all the cases of the ADT in your chain of `|+|`. If you don't,
encoding will graciously fail if you try to encode a case you have not
covered. We do not have a way to express the equivalent of a pattern
matching exhaustiveness check.


### Coproduct inference

Similarly to products, coproducts expressed as above also suffer from
extra annotation clutter, and we employ a similar fix, compare
`clutter` to `noClutter` in the snippet below:

```scala mdoc:compile-only
sealed trait Auth
case class Error(reason: String) extends Auth
case class User(id: Int, name: String) extends Auth

def error: Schema[Error] = ???
def user: Schema[User] = ???

def clutter: Schema[Auth] = Schema.alternatives {
  Schema.alt[Auth](error) |+| Schema.alt[Auth](user)
}

def noClutter: Schema[Auth] = Schema.oneOf { alt =>
  alt(error) |+| alt(user)
}

// note the [Auth] annotation
def noClutter2 = Schema.oneOf[Auth] { alt =>
  alt(error) |+| alt(user)
}
```

### Tagging

In the example above we simply composed the schemas of the subtypes
with `|+|`, with no further modifications.  
This can work in simple cases, but it has two drawbacks:
- A decoder might have to do a lot of work decoding a bunch of fields,
  before realising it needs to fallback to the next one.
- It's not possible to distinguish between two cases with the same
  representation.

Here is a quick example of the second issue:

```scala mdoc:silent
sealed trait A
case class B(v: String) extends A
case class C(v: String) extends A

val ambiguous: Schema[A] = Schema.oneOf { alt =>
  val b: Schema[B] = Schema.record { field =>
   field("v", Schema.str, _.v).map(B.apply)
  }
  val c: Schema[C] = Schema.record { field =>
   field("v", Schema.str, _.v).map(C.apply)
  }
  
  alt(b) |+| alt(c)
}
```

`a` needs to distinguish between `b` and `c` when decoding, but their encoded form is the same:

```scala mdoc:to-string
ambiguous.write(B("hello"))
ambiguous.write(C("hello"))
```

Therefore we need a way to _tag_ each schema before using `|+|` to
clearly distinguish between them. As a bonus, the decoder has to do
(potentially a lot) less work because it can fallback to the next case
after analysing only the tag, instead of the whole record.

There are various strategies for tagging, the one we recommend is to
create a record with one key corresponding to the name of the
coproduct case, like so:

```scala mdoc:silent
val tagged: Schema[A] = Schema.oneOf { alt =>
  // same as before
  val b: Schema[B] = Schema.record { field =>
   field("v", Schema.str, _.v).map(B.apply)
  }
  val c: Schema[C] = Schema.record { field =>
   field("v", Schema.str, _.v).map(C.apply)
  }
  // but we tag them
  val taggedB = Schema.record[B] { field =>
    field("b", b, x => x)
  }
  val taggedC = Schema.record[C] { field =>
    field("c", c, x => x)
  }
  // using the tags
  alt(taggedB) |+| alt(taggedC)
}
```

Which results in correct behaviour:

```scala mdoc:to-string
val out = tagged.write(B("hello"))
tagged.read(out)
```

This is common enough to be worth a `tag` method on `Schema`, which is what
you saw in the initial example.

```scala mdoc:silent
import Schema._

val betterTagged: Schema[A] = oneOf { alt =>
  val b = record[B] { field =>
   field("v", str, _.v).map(B.apply)
  }
  val c = record[C] { field =>
   field("v", str, _.v).map(C.apply)
  }
  
  alt(b tag "b") |+| alt(c tag "c")
}
```


### Flexible tagging

The tagging schema above is the recommended one, but by no means the
only way to do it, here is another common way by having a record with
a field named "type" to discriminate, and a field "payload" for the
actual content.

TODO once I have constants

### Encoding objects

You can encode object as empty records or Strings

### Optional

When reading if will retun None on `Nil` or missing key, when writing you decide

### Defaults on error

withDefault

### Sequences
