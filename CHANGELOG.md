# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [0.1.0-alpha.15] - 2021-10-02
- fix bug in `=>>` with small sequences
- more docks and docs/shootout.md
- discussion on comparing `|>>` and `=>>` happened on clojureverse: [Fight Night](https://clojureverse.org/t/parallel-transducing-context-fight-night-pipeline-vs-fold/8208)

## [0.1.0-alpha.13] - 2021-09-25
- reverted names back to `=>>` and `|>>`
- fixed bug with `=>>` fold impl, limiting smallest partition to parallelism count

## [0.1.0-alpha.12] - 2021-09-22
- Added tests
- Adopted Sean Corfield's deps-new lib template

## [0.1.0-alpha.9] - 2021-09-20
### Changed
- Major code cleanup

[0.1.0-alpha.9]: https://github.com/johnmn3/injest/compare/v0.1-alpha.8...v0.1-alpha.9
[0.1.0-alpha.9]: https://github.com/johnmn3/injest/compare/v0.1-alpha.9...0.1.0-alpha.13
