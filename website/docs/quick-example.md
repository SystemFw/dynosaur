# Quick example

The design of `Dynosaur` is based on defining _schemas_ for your data,
rather than your typical `Encoder/Decoder` typeclasses. Here's a quick
example, and you can read on for in depth documentation.

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
import dynosaur._
import cats.syntax.all._

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

```scala mdoc:to-string
val u = Auth.User(303, "tim")
val e = Auth.Error("Unauthorized")

schema.write(u)
schema.write(u).flatMap(schema.read)
schema.write(e)
schema.write(e).flatMap(schema.read)
```
