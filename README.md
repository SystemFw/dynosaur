# Dynosaur

Dynosaur is a purely functional, native, non-blocking client for DynamoDb, based on cats-effect and fs2.

Please head over to the [microsite](https://ovotech.github.io/dynosaur/)


## Documentation workflow
In sbt, run

> docs/docusaurusCreateSite

Every time you need to modify the _structure_ of the site itself. You
don't need to run this every time you modify the content of existing
files.

In sbt, run

> docs/mdoc --watch 

Open a shell in the root of the project and run

> cd website
> yarn run start

The website should then open on localhost, and listen to changes,
including compiling scala snippets

