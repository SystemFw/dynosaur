# Inspiration

The approach of using `GADT`s for schemas and free constructions for
records was pioneered by the
[xenomorph](https://github.com/nuttycom/xenomorph) library, however
the approach used here is different along at least two axes:

- It focuses on representing data in a specific format
  (AttributeValue) rather than providing a schema to be reused for
  multiple formats. This results in much greater control over the
  data, and a simpler api for users.
- The implementation differs in several aspects including improved
  inference and a more flexible encoding of sums.

The invariant combinators (`imap`, `imapErr`, `xmap`) and the
integration of implicit and explicit codecs is influenced by
[scodec](https://github.com/scodec/scodec).
