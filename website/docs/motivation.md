# Motivation

The typical approach most libraries use for codecs involves
`Encoder/Decoder` typeclasses, sometimes including automatic derivation.
This approach has the following drawbacks:
- Automatic derivation is _opaque_ : you cannot easily read how your
  format looks like, you need to recall the implicit mapping rules
  between your data and the format.
- Automatic derivation is _brittle_: generally harmless
  transformations like rename refactoring on your data can silently
  break your format.
- Automatic derivation is _inflexible_ : it cannot cover many useful
  transformations on your format like different naming, encoding of
  ADTs, flattening some records, approach to optionality and so on.
- Juggling different formats for the same data is cumbersome.
- On the other hand, writing explicit encoders and decoders is
  annoying because you need to keep them in sync, and the required
  code is similar enough to be tedious, but different enough to be error prone.  
  Even without this duplication, the process is still made hard by the
  fact that you are dealing with the practical details of traversing a
  low level data structure, such as DynamoDb AttributeValue.
  
As a result, people abuse automatic derivation, and end up with either
ugly serialised data and a nice code model, or nice serialised data
and an ugly code model.   
The schema DSL provided by Dynosaur, on the other hand, allows you to
be flexible in how you define your serialised data, without
duplicating code for encoders and decoders, and with a declarative
focus on the _structure_ of the data, rather than the traversal of a
low level representation.
