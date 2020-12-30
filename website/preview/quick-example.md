# Quick example

The design of `Dynosaur` is based on defining _schemas_ for your data,
rather than your typical `Encoder/Decoder` typeclasses. Here's a quick
example, and you can read on for in depth documentation.

Given this simple ADT

```scala
sealed trait Auth
object Auth {
  case class Error(reason: String) extends Auth
  case class User(id: Int, name: String) extends Auth
}
```

We define a schema for it

```scala
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

```scala
val u = Auth.User(303, "tim")
// u: Auth.User = User(303,tim)
val e = Auth.Error("Unauthorized")
// e: Auth.Error = Error(Unauthorized)

schema.write(u)
// res0: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "user": {
//     "M": {
//       "name": { "S": "tim" },
//       "id": { "N": "303" }
//     }
//   }
// })
schema.write(u).flatMap(schema.read)
// res1: Either[Schema.DynosaurError, Auth] = Right(User(303,tim))
schema.write(e)
// res2: Either[Schema.WriteError, DynamoValue] = Right("M": {
//   "error": {
//     "M": {
//       "reason": { "S": "Unauthorized" }
//     }
//   }
// })
schema.write(e).flatMap(schema.read)
// res3: Either[Schema.DynosaurError, Auth] = Right(Error(Unauthorized))
```
