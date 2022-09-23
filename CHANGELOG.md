<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# pycharm-typing-imp Changelog

## [Unreleased]


## 1.0.0 — 2022/09/23
### BREAKING CHANGES
 - Added support for 2022.3, requiring a source/target Java compatibility of 17. Minimum supported PyCharm version is now 2022.3.


## 0.2.1 — 2022/08/11
### Fixed
 - Ensure proper `instance` and `owner` arguments are passed to implicit descriptor `__get__` calls


## 0.2.0 — 2022/08/07
### Added
 - Properly substitute generics in implicit descriptor `__get__` calls (see [GH#1](https://github.com/theY4Kman/pycharm-typing-imp/issues/1))

### Changed
 - When running in an application where an issue has been fixed, prevent loading of extraneous extensions.


## 0.1.4 — 2022/08/02
### Fixed
 - Remove usages of internal APIs (namely, `PyTypeUtil`)


## 0.1.3 — 2022/07/30
### Changed
 - Renamed plugin to "Python Typing Imp"


## 0.1.2 — 2022/07/27
### Changed
 - Add plugin icon


## 0.1.1 — 2022/06/29
### Fixed
 - Bump minimum supported PyCharm version to 2021.1.3


## 0.1.0 — 2022/06/29
### Added
 - Add proper typing support for `__getattr__` and `__getattribute__` return types
 - Provide correct typing for `dict.values` iteration
