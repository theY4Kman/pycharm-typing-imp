# Python Typing Imp plugin

![Build](https://github.com/theY4Kman/pycharm-typing-imp/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/com.y4kstudios.pycharmtypingimp.svg)](https://plugins.jetbrains.com/plugin/com.y4kstudios.pycharmtypingimp)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/com.y4kstudios.pycharmtypingimp.svg)](https://plugins.jetbrains.com/plugin/com.y4kstudios.pycharmtypingimp)

<!-- Plugin description -->
Improve the typing inference in PyCharm (or the Python plugin in other IDEs). Does the things PyCharm should do, before JetBrains can add them to mainline.

Current feature set:
 - `__getattr__` and `__getattribute__` typing ([PY-21069](https://youtrack.jetbrains.com/issue/PY-21069/Annotated-return-types-for-getattr-and-getattribute-methods-are-not-taken-into-account-by-type-checker))
 - Fixed: ~Proper iteration typing for `dict.values` ([PY-52656](https://youtrack.jetbrains.com/issue/PY-52656/Incorrect-dictvalues-return-type))~
<!-- Plugin description end -->

## Installation

- Using IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "PyCharm Typing Imp"</kbd> >
  <kbd>Install Plugin</kbd>
  
- Manually:

  Download the [latest release](https://github.com/theY4Kman/pycharm-typing-imp/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>
