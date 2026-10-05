# Git workflow

## Solo v1 exception

Local milestone commits on `develop` are authorized during initial solo development, without a PR/reviewer. This does not authorize pushing, publishing, direct staging/master commits, or self-merging PRs. Use the normal flow once collaborating.

## Branches and review

- `develop`: active development. Start `feature/<name>` or `fix/<name>` here.
- `staging`: pre-production QA.
- `master`: production. Publish/deploy from here only.

Normal flow: feature/fix → develop → staging → master, through PRs. Hotfixes start at master and merge back into both develop and staging. No direct staging/master commits.

Keep PRs focused. Explain behavior, validation, and limitations; link issues. Require at least one reviewer, no self-merge or force-push after review. Never auto-merge security-sensitive changes.

## Commits and versions

Use `<type>: <short imperative description>`. Types: feat, fix, hotfix, refactor, chore, docs, test. No secrets or unrelated changes. Enable hooks with `git config core.hooksPath .githooks`.

Use SemVer tags `vMAJOR.MINOR.PATCH`; prereleases use suffixes such as `v0.1.0-alpha.1`. After reviewed promotion to master, tag the matching version and changelog. Release CI checks/builds the tag and prepares a draft; stable versions require signed distribution. Maintainers review and publish. See [releases](releases.md).

Configure branch protections in GitHub: PRs, passing checks, no direct pushes, with a scoped solo-v1 exception for develop. Repository files do not enable hosted protections.
