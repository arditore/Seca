# Contributing to Seca

Pull requests are welcome and several of the things in the apps came from
them: the German, Polish and Turkish translations, the caller's name sent by
the carrier, and a number of fixes. If you have found something wrong or
built something you think belongs here, send it.

## Before you spend an evening on it

For anything larger than a fix, **open an issue first** and say what you
have in mind. Not for permission — to find out whether it collides with
something already half-built, and whether the shape is one that fits. It is
a short conversation that has saved people a lot of wasted work.

## What the code has to do

Run these before sending; they are the same gates a release goes through,
and `warningsAsErrors` is on, so a warning is a failure:

```sh
./gradlew test
./gradlew check
```

Beyond that:

- **Every language, or none.** A string added to `values/strings.xml` has to
  exist in `values-fr`, `values-es`, `values-pl`, `values-de` and
  `values-tr`. There is a test that fails otherwise. If you cannot write a
  translation, say so in the pull request and it will be handled; do not
  leave the key out.
- **Comments say why, not what.** The code says what it does. A comment
  earns its place by recording the thing that is not visible: the bug that
  made this necessary, the option that was rejected, the platform behaviour
  being worked around.
- **No new dependency without a reason in the pull request**, and nothing
  from Google Play services — there is a build check that fails on it.
- **No analytics, no telemetry, no crash reporting that leaves the phone.**
  This is not negotiable and it is the whole point of the project.

## The part about ownership

By sending a contribution you agree that:

1. It is yours to give — you wrote it, or you have the right to contribute
   it, and it does not carry anybody else's code under terms that conflict
   with the licences in `NOTICE`.
2. You grant **arditore** a perpetual, worldwide, irrevocable, royalty-free
   licence to use your contribution, to distribute it, and **to license it
   under other terms**, including future versions of the GPL and AGPL.
3. You keep your own copyright and may do whatever you like with your own
   work elsewhere. You are not signing it away; you are making sure one
   person can keep answering for the whole.

Point 2 is the one that needs explaining, because it is unusual to ask and
easy to read as a grab. It exists so that the project has a single person
able to act: to change licence if a dependency forces it, to relicense if
the AGPL is superseded, and — the reason it was written — **to enforce the
licence against a company that takes this code, closes it and ships it with
data collection in it**. A project whose copyright is spread across a dozen
people cannot do that quickly, or sometimes at all.

Sign your commits off to say you agree:

```sh
git commit -s
```

which adds `Signed-off-by:` with your name and e-mail.

Contributions made before this file existed are not covered by it. They
remain their authors' copyright under the licences in `NOTICE`, which is why
relicensing today would still mean asking them.

## Style

Written English in code, comments, commit messages and documentation.
Interface text in English, French, Spanish, Polish, German, Turkish, Simplified
Chinese and Persian.

Commit messages say what changed and why, in sentences. Look at the history
for the register; a one-line `fix: bug` is not it.

## What will be turned down

- Anything that sends data off the phone without the owner asking
- A dependency on Google Play services or any proprietary library
- A feature that only works on one manufacturer's phone, unless it degrades
  cleanly everywhere else
- Code with no comments where the reasoning is not obvious from reading it
