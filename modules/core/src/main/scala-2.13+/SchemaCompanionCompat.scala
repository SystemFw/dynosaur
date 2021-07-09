package dynosaur

trait SchemaCompanionCompat {

  implicit def scalaEnum[E <: Enumeration](implicit
      e: ValueOf[E]
  ): Schema[e.value.Value] =
    Schema[String].imapErr(str =>
      e.value.values
        .find(_.toString == str)
        .toRight(
          Schema.ReadError(
            s"Unknown type of ${e.toString}: $str. Supported values: ${e.value.values.mkString(", ")}"
          )
        )
    )(_.toString)

}
