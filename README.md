# Python Typing Imp plugin

![Build](https://github.com/theY4Kman/pycharm-typing-imp/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/com.y4kstudios.pycharmtypingimp.svg)](https://plugins.jetbrains.com/plugin/com.y4kstudios.pycharmtypingimp)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/com.y4kstudios.pycharmtypingimp.svg)](https://plugins.jetbrains.com/plugin/com.y4kstudios.pycharmtypingimp)

<!-- Plugin description -->
Improve the typing inference in PyCharm (or the Python plugin in other IDEs). Does the things PyCharm should do, before JetBrains can add them to mainline.

Current feature set:
 - `__getattr__` and `__getattribute__` typing ([PY-21069](https://youtrack.jetbrains.com/issue/PY-21069/Annotated-return-types-for-getattr-and-getattribute-methods-are-not-taken-into-account-by-type-checker))
 - Generic substitutions for implicit descriptor `__get__` calls ([PY-55531](https://youtrack.jetbrains.com/issue/PY-55531/Pycharm-cant-handle-typing-of-get-method-of-descriptors))
 - Fixed: ~Proper iteration typing for `dict.values` ([PY-52656](https://youtrack.jetbrains.com/issue/PY-52656/Incorrect-dictvalues-return-type))~
<!-- Plugin description end -->

## Installation

- Using IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "PyCharm Typing Imp"</kbd> >
  <kbd>Install Plugin</kbd>
  
- Manually:

  Download the [latest release](https://github.com/theY4Kman/pycharm-typing-imp/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

# Want something else fixed?

I've used PyCharm (and other JetBrains IDEs) exclusively for over a decade now. Occasionally, I'd notice some incorrect behaviour and would open an issue in YouTrack for it. A handful of months... or years later, I would be overjoyed to see my issue fixed in the IDE.

After so many years of professional Python development, though, my impatience with incorrect behaviour greatly increased. I've since developed the [pytest imp](https://github.com/theY4Kman/pycharm-pytest-imp) plugin and another internal PyCharm plugin, and my impatience is now met with (possibly foolhardy) confidence I can fix some of PyCharm's incorrect behaviour. I submit my first Python-related PR to intellij-community, and watched it languish for months.

Finally, I decided to take matters into my own hands, and build a plugin devoted solely to correcting faulty behaviour of the Python plugin — because I know what it's like to see an issue, report it, and then have to live with it for months or years. I can't blame JetBrains for the state of affairs; after all, the Python plugin has an _incredibly immense_ surface area, and there's just no way to service everything all at once. But if I have the option and ability _not_ to wait, I'll take it.

And so, if you notice some incorrect behaviour that's impacting your work or pleasure, _please_ report it on YouTrack; then open an issue here with a link to the YouTrack issue. I'll see if the behaviour can be corrected through the extension points exposed by PyCharm/the Python plugin.

(I ask that a YouTrack issue is filed, because I know that, _eventually_, the issue will be fixed in the IDE; and I can disable this plugin's fix for an issue when running the build with the official fix. The current architecture of this plugin employs a different extension point implementation class for each separate issue. When a YouTrack issue is updated with value(s) for "Included in build", I add a branch to that issue's EP implementation class to prevent its loading when the application's version info matches what's in "Included in build".)
