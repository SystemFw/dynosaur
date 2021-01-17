# DynamoValue

`dynosaur` represents DynamoDb data with the `DynamoValue` type.

A primary goal of its design is painless interoperability with the AWS
SDK, therefore `DynamoValue` is not an ADT, but a wrapper around the
SDK's `AttributeValue`.

If you have an `AttributeValue`, you can convert to `DynamoValue` via
`apply`:

```scala mdoc:to-string
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import dynosaur.DynamoValue

val av = AttributeValue.builder.s("hello").build
val dv = DynamoValue(av)
```

and back to `AttributeValue` with `_.value`

```scala mdoc:to-string
val av2 = dv.value
```

Of course, the reason `DynamoValue` exists is to provide a nicer Scala
experience, and you can construct one directly with methods in the
companion object, which all take Scala types, instead of using the Java builder.

Here's an example:

```scala mdoc:silent
val V = DynamoValue

val ex = V.m(
  "id" -> V.s("61c9f0d406a3"),
   "no" -> V.n(1245),
   "items" -> V.l(
      V.m(
        "id" -> V.s("93ed9348f407"),
        "price" -> V.n(3)
      ),
      V.m(
        "id" -> V.s("96d5e9ed1db8"),
        "price" -> V.n(50)
      )
   )
)
```

`toString` gives you a pretty printed string:

```scala mdoc:to-string
ex.toString
```

when reading, `DynamoValue` mirrors the `AttributeValue` convention
`s`, `n`, `m` etc. for accessors methods, except with Scala types. All
the methods return `Option`, so you don't have to do any checking
before using them.

Let's read the `id` of the first item:

```scala mdoc
val firstId: Option[String] =
  for {
    ex <- ex.m
    items <- ex.get("items").flatMap(_.l)
    firstItem <- items.headOption.flatMap(_.m)
    id <- firstItem.get("id").flatMap(_.s)
  } yield id
```

Finally, there is a general `fold` method as a replacement for direct
pattern matching. It takes a function for each of the cases:

```scala
class DynamoValue {
  
  ...
  
  def fold[A](
      s: String => A,
      n: DynamoValue.Number => A,
      bool: Boolean => A,
      l: List[DynamoValue] => A,
      m: Map[String, DynamoValue] => A,
      nul: Unit => A,
      b: ByteVector => A,
      bs: NonEmptySet[ByteVector] => A,
      ns: NonEmptySet[DynamoValue.Number] => A,
      ss: NonEmptySet[String] => A
  ): A
}
```
