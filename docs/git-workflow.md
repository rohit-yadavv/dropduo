# Git standards and workflow

## Solo v1 exception

For initial development only, the user authorizes coherent milestone commits directly on `develop`. No PR/reviewer is required for these local commits. This exception does not authorize direct commits to staging/master, pushing, publishing, or self-merging future PRs. Transition to the normal workflow when collaboration begins.

## Long-lived branches

| Branch | Purpose |
|---|---|
| develop | Active development and integration |
| staging | Pre-production testing and QA |
| master | Production-ready code only |

No direct commits to staging or master. Normal changes require PRs.

## Short-lived branches

| Type | Name | From | Into |
|---|---|---|---|
| Feature | feature/<short-desc> | develop | develop |
| Fix | fix/<short-desc> | develop | develop |
| Hotfix | hotfix/<short-desc> | master | master, then develop and staging |

Standard: feature/fix → develop → staging → master. Hotfix: hotfix → master → develop → staging. Always back-merge hotfixes. Reserve hotfix for production-critical bugs.

## Commits

`<type>: <short description>`

Types: feat, fix, hotfix, refactor, chore, docs, test. Use an imperative concise description. Examples: `feat: add trusted device pairing`, `fix: resume interrupted transfers safely`. Never include secrets or unrelated changes. Hook setup: `git config core.hooksPath .githooks`.

## Pull requests

One feature/fix per PR. Explain what, why, and impact; link an issue when applicable. Include validation and limitations. Minimum one reviewer, no self-merge, no force-push after review.

## Versions and releases

Tags: vMAJOR.MINOR.PATCH. Breaking change → major; feature → minor; fix → patch. Pre-release builds use v0.1.0-alpha.N. Merge staging into master, create the version tag, push the tag, publish/deploy from master only. Example commands: `git tag v1.0.0`, `git push origin v1.0.0`. CI checks the release branch before producing public release assets.

## Hosted protections

Protect develop, staging, and master: PR required, checks pass, no direct pushes. Solo exception may require a temporary scoped bypass on develop. Configure these in the repository host once a remote exists; files alone do not enable protections.

Avoid long-running feature branches, mixed-feature PRs, direct staging/master commits, and hotfixes without back-merging.
