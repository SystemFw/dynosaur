package dynosaur

object data {
  val tyrannosaurusRexDv = DynamoValue.m(
    "name" -> DynamoValue.s("Foolio"),
    "age" -> DynamoValue.n(20000000),
    "victims" -> DynamoValue.n(9)
  )

  val parasaurolophusDv = DynamoValue.m(
    "name" -> DynamoValue.s("Cantolio"),
    "age" -> DynamoValue.n(25000000),
    "songs" -> DynamoValue.n(9)
  )

  val allosaurus = Allosaurus(
    name = "Cantolio",
    age = 25000000,
    attacks = 99
  )
  val allosaurusDv = DynamoValue.m(
    "name" -> DynamoValue.s("Cantolio"),
    "age" -> DynamoValue.n(25000000),
    "attacks" -> DynamoValue.n(99)
  )

  val tyrannosaurusRexWithTagDv = DynamoValue.m(
    "tyrannosaurus-rex" -> tyrannosaurusRexDv
  )

  val allosaurusWithTagDv = DynamoValue.m(
    "allosaurus" -> allosaurusDv
  )

  val parasaurolophusWithTagDv = DynamoValue.m(
    "parasaurolophus" -> parasaurolophusDv
  )

  val allosaurusesDv = DynamoValue.l(
    (0 until 10)
      .map(_ => allosaurusDv)
      .toList
  )

  val dynosaursWithTagDv = DynamoValue.l(
    tyrannosaurusRexWithTagDv,
    allosaurusWithTagDv,
    parasaurolophusWithTagDv
  )

  val string = "dynosaur"
  val stringDv = DynamoValue.s("dynosaur")
  val stringsDv = DynamoValue.l((0 until 10).map { idx =>
    DynamoValue.s(s"test-$idx")
  }.toList)

  val dynosaurWithDiscriminatorDv = DynamoValue.m(
    "name" -> DynamoValue.s("Foolio"),
    "age" -> DynamoValue.n(20000000),
    "victims" -> DynamoValue.n(9),
    "kind" -> DynamoValue.s("tyrannosaurus-rex")
  )

}
