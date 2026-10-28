# CLAUDE.md

This file provides guidance to [Claude Code](https://claude.com/product/claude-code) when working with code in this repository.
It is not intended for human eyes.

### Maintenance

You (robot or human) have standing permission to update this file without asking.
Add important patterns, gotchas, or context that would help future sessions.
Keep it concise and actionable.

## Project overview

This is Eunomia, a Scala 3 library for filtering, ordering and paging lists of items, whether in memory,
in a database or over the wire, for full stack websites built on Slick and Laminar, and optionally Tapir. It is in beta.

- `core` (`com.alecdorrington.eunomia`, JVM + JS) - `model/ListQuery`, `Filter`, `Order`, `Page`, `ListSchema` for
  in-memory evaluation, `ListReply` as either the whole list or one `Window`, and `api/ListParameters`, a query as the parameters of a
  request (written by `of`, read back by `parse`, whatever framework serves the request).
- `server` (`com.alecdorrington.eunomia.server`) - `SqlLists` runs a query in SQL over the host's JDBC profile.
- `tapir` (`com.alecdorrington.eunomia.tapir`, JVM + JS) - `ListApi`, the endpoint input and output for Tapir, built
  on `ListParameters`. The only module that depends on Tapir: keep it that way, so that a host serving its endpoints
  with anything else never needs it. `ListReply`'s Tapir schema is written by hand, as its circe codec is
  (`{"window": ...}` or `{"whole": [...]}`): a derived one documents the cases' fields instead, which is not what is sent.
  Tapir once also brought `scala-java-time` (and with it `java.util.Locale`) to the JS side of everything, silently:
  nothing in `core` or `client` may use `java.util.Locale` or `java.time`, which Scala.js lacks without a library.
  `ListSchema.contains` folds case with `regionMatches` for this reason, and the default-locale test is JVM only
  (`core/src/test/scalajvm`). A missing class shows only when JS is linked, as in `eunomiaCoreJS/test`.
- `client` (`com.alecdorrington.eunomia.client`) - `ListSource`, `ListState` and an unstyled `Table`. A server's
  list is read with the private `Fetched`, on the browser's `fetch`, as laminext has no release for Laminar 18.
  `ListSource` was named `Source` once and clashed with Laminar's `L.*`; don't reintroduce that.
  An endpoint's list is reloaded by its `reloads` stream (the host says when, as nothing here sees writes),
  keeping the rows on screen until the answer arrives; `ListSource.reshown` reloads when the page is looked
  at again. The first request of an endpoint's load isn't abandoned when the query changes: once it
  answers, `remote` catches up with the query as it stands, and an error gives way to the next query.
  Don't put a query-following switch above the first request instead: a whole-list reply can't stop it
  without feedback state, so a short list would send a request per query. A page starting past the end
  of a list is answered with the last that holds anything (`Page.within`), by `ListSchema.run` and `SqlLists`
  alike, so that a list shrinking under its reader never leaves them on an empty page; keep the two
  paths agreeing (the agreement test in `SqlListsSuite`).

See [README.md](README.md) for how a host wires it up.

### Where this code lives

Eunomia is developed here: make every change in this repository, by pull request. The shared configuration (workflows,
Scalafmt, IDE settings, `project/plugins-common.sbt`, `project/plugins-library.sbt`, `LICENSE.md`, `CONTRIBUTING.md`)
is synced into it from [Scala Library Config](https://github.com/SgtSwagrid/scala-library-config) by
[GitHub Graph](https://github.com/SgtSwagrid/github-graph), which overwrites local edits, so change those files there.
`build.sbt`, `release.sbt`, `project/Dependencies.scala`, `project/plugins-scalajs.sbt`, `README.md`, this file and the
sources belong to the library.

Other projects use Eunomia as a dependency from Maven Central, never from source, so a change one of them needs reaches
it only through a release (below).

### Build

- `eunomiaCore` and `eunomiaTapir` are `projectMatrix`es (JVM + JS; the JS rows are `eunomiaCoreJS` and `eunomiaTapirJS`),
  `eunomiaServer` is JVM, `eunomiaClient` is Scala.js, and the root project `eunomia` only aggregates them and is never published.
- Project ids are prefixed with the library's name (`eunomiaCore`, ...), so that a build including this one by reference
  keeps names such as `server`, `client` and `common` free for its own projects.
- Each matrix pins `sourceDirectory` to `(ThisBuild / baseDirectory) / "<module>" / "src"`. Don't remove it: sbt 2.0.8
  resolves a matrix's sources against the working directory, which is not this build's when another includes it,
  and the library then compiles to an empty JAR without a single error of its own.
- The matrices' sources sit in their packages' directories (`core/src/main/scala/com/alecdorrington/eunomia/`), with no
  `packagePrefix`: IntelliJ's sbt import never gives a prefix to the module holding the sources the JVM and JS share,
  so every `package` line there was flagged on each fresh import.
- Nothing here should assume a host, a database or a JDBC profile.
- Versions come from git tags (`sbt-ci-release`); publishing a GitHub release publishes to Maven Central (*Releases*
  below).

### Releases

1. Land the changes on `main` by pull request, the last of them setting the README's installation lines to the new
   version. Eunomia is in beta, versioned by early semver (`versionScheme`): while it is `0.x`, the minor version goes
   up for any change that can break a user's build or behaviour, and the patch version for anything else.
2. Publish a GitHub release of `main` tagged `v<version>`, with notes written for a user, as the earlier releases' are
   (the installation lines, then what is Changed, Added and Fixed). `publish.yml` then publishes
   `eunomia-core` and `eunomia-tapir` (each for the JVM and JS), `eunomia-server` and `eunomia-client` to Maven Central through `sbt-ci-release`, which takes the version from the tag, and `docs.yml`
   publishes the Scaladoc at [alecdorrington.com/eunomia](https://alecdorrington.com/eunomia/).
3. Once Maven Central serves the new version, bump it in each project using Eunomia, by pull request there. Their
   Scala Steward would otherwise propose it on its next weekly run. A version that breaks callers needs their code
   changed too.

## Instructions

### Compilation and Diagnostics

- When the user asks for help with a compilation or type error, start by running `sbt compile` to see the error for yourself.
  If there are many errors, making it unclear which one the user is referring to, ask them to clarify, and then focus only on that issue.
- IntelliJ MCP integration is active. When a request seems to implicitly refer to something the user is looking at, always check
  `mcp__ide__getDiagnostics` first to see which file(s) are open and get associated diagnostics (errors, warnings, and info hints with line numbers).

#### Testing

- After making code changes, always run `sbt compile` to verify that issues are fixed and no new ones are introduced.
- Repeatedly retry upon failure until the build succeeds. If you are unsure how to fix an issue, ask for help or refer to existing code for examples.
- Before trying to fix an error, make sure you first understand it fully.
- You should never report that a feature is complete without testing it first.

### Code Style

- You must read the [Code Style Guidelines](docs/STYLE_GUIDE.md).
- Document every public type and member: a summary, then `@param` for each explicit parameter,
  `@tparam` for each type parameter, and `@return` for any result but `Unit`, each one short
  sentence. Summaries read: types "A ...", values "The ...", Booleans "Whether ...", methods a
  third-person verb ("Sends ..."), never "Returns ...". Private members get a comment only for a
  non-obvious contract or gotcha, usually in one sentence.

### Pull Requests

When asked to publish the code changes, your task is to open one or more pull requests (PRs) to merge the changes into `main` on GitHub:

- Use `git` to check what has changed as compared to the `main` branch on `origin`.
- If the changes are thematically linked, they can be published as a single PR.
- Otherwise, you'll need to divide the changes into multiple PRs using your own judgement.
- Each PR should have a singular focus, shouldn't break anything, and should be able to be merged independently.
- Ensure that all code is staged, committed and pushed. Ensure no new files are left uncommitted, and no debug code is left in the codebase.
- When creating a PR, ensure that the title and description are clear, informative, and comprehensive.
- All feature/bugfix/etc branch names should be formatted as "feature_<short description>" or "fix_<short description>" or similar.
- All PR titles should be formatted as "[<scope>] <Short summary>", e.g. "[renderer] Fixed colour inversion bug."
- Use the `gh` CLI to do the above.
