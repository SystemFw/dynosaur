## Documentation

Docs are based on:

- `docsify`, a _dynamic_ , markdown based generator.
- `mdoc`, typechecked scala/markdown compiler

### Releasing docs

During the `release` task, `mdoc` takes `.md` from `modules/docs`,
compiles them and sends them do `/docs`, where `docsify` can serve
them without any build. The `/docs` folder is deployed automatically
by Github Pages.

Note: Don't PR things in `/docs`, or they will be published once they
get merged to `main`, even though the corresponding commit might not
be a tagged release (so the docs will be more recent to the latest
official version)

### Local doc flow

The input files are in `modules/docs`.
`_coverpage.md` and `_sidebar.md` are special, and control the site structure.

For live preview, start a webserver in `/docs` , e.g. with

> python -m SimpleHttpServer 8000

and in `sbt`, run `docs/mdoc --watch`

## Publishing

Push a tag on the `main` branch to release.
It will fail if semver isn't respected wrt bincompat.

To change/add branches to release:

> ThisBuild / spiewakMainBranches := Seq("main", "develop")

To relax semver:

> ThisBuild / strictSemVer := false

To publish snapshot on every main build:

> ThisBuild / spiewakCiReleaseSnapshots := true

Caveat:
If you are publishing snapshots, you need to make sure that new
commits are fully built before you push a proper release tag: push
`main`, wait for the snapshot release to complete, and then push the
tag.
Daniel will get around to fixing this someday...

## Initial setup

Do this once, when setting up the repo.

Make sure the default branch is named `main`

Generate ci definitions

> sbt githubWorkflowGenerate

and commit the results.

Then, configure the following encrypted secrets within GitHub Actions:

- SONATYPE_USERNAME
- SONATYPE_PASSWORD
- PGP_SECRET

Your repo -> settings -> left sidebar -> Secrets -> new repository secret.

You can obtain the PGP_SECRET by running

> gpg --export-secret-keys | base64.

Please make sure that this key is not password protected in the export
(GitHub Actions itself will encrypt it).


## Links

https://github.com/djspiewak/sbt-spiewak
https://github.com/djspiewak/sbt-github-actions
https://docs.github.com/en/free-pro-team@latest/actions/reference/encrypted-secrets
https://docsify.js.org/#/
https://scalameta.org/mdoc/
