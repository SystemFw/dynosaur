## NonEmptySet

`dynosaur` defines a `NonEmptySet` type to support the `Binary Set`,
`String Set` and `Number Set` DynamoDb types, which cannot be empty.

It's not really intended for general use, so its api is tiny:

- Given a `Set[A]`, you can call `NonEmptySet.fromSet` to get an `Option[NonEmptySet[A]]`
- Given an `Option[NonEmptySet[A]]`, you can call `NonEmptySet.toSet` to get a `Set[A]`
- You can create a `NonEmptySet` with `NonEmptySet.of`, which takes at least one element

> **Note:** `cats` already defines a `NonEmptySet`, but it
> constrains it to `SortedSet`, which makes its use awkward.
