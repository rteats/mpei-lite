# MPEI Neo icon catalog

All interface icons use **Google Material Symbols Outlined**, `FILL=0`,
`wght=400`, `GRAD=0`, `opsz=24` (one fixed optical size at every dp size).

- Official icon browser: https://fonts.google.com/icons
- Official source: https://github.com/google/material-design-icons/tree/master/symbols/web
- License: Apache-2.0
- Android resources: `app/src/main/res/drawable/ms_<symbol>.xml`
- Kotlin registry + Compose wrapper: `app/src/main/java/com/rteats/mpeineo/AppIcons.kt`
- The launcher icon is independent of this UI icon system and remains unchanged.
- Downloaded schedule weeks live under Android `cacheDir/schedule-cache` and are
  pruned automatically; the app has no manual schedule-cache clearing control.

## Screen usage

| AppIcons key | Material Symbols name | UI usage |
| --- | --- | --- |
| `Schedule` | `date_range` | Schedule tab |
| `Bars` | `account_balance` | BARS tab, settings group |
| `Mail` | `mail` | Mail tab and system notifications |
| `Settings` | `settings` | Settings tab |
| `Back` | `arrow_back` | Back from search, mail, BARS |
| `Forward` | `arrow_forward` | BARS grade navigation |
| `OpenPortal` | `open_in_new` | Shared BARS/OSÉP floating action |
| `Confirm` | `check` | Week-pull threshold reached |
| `ExpandMore` | `keyboard_arrow_down` | Expand/next indicator |
| `ExpandLess` | `keyboard_arrow_up` | Collapse/previous indicator |
| `Favorite` | `star` | Favorites and favorite toggles |
| `Overflow` | `more_vert` | Favorite menu |
| `Edit` | `edit` | Rename favorite |
| `Search` | `search` | Search field |
| `Refresh` | `refresh` | Schedule refresh |
| `Location` | `location_on` | Lesson location |
| `Person` | `person` | Lecturer |
| `GroupList` | `list` | Lesson groups |
| `Attachment` | `attach_file` | Mail attachment |
| `Download` | `download` | Download attachment and app update |
| `Logout` | `logout` | Disconnect mail |
| `About` | `info` | About group |
| `CheckUpdates` | `sync` | Check app updates |
| `ClearLog` | `delete_sweep` | Clear diagnostic log |
| `Copy` | `content_copy` | Copy log |
| `Diagnostics` | `bug_report` | Diagnostics group |
| `InstallUpdate` | `install_mobile` | Install app update |
| `Notifications` | `notifications` | Mail notifications setting |
| `Updates` | `system_update` | App update group and BARS refresh preference |

## Requesting changes

Specify `UI location → Material Symbols name`. Example:
`Schedule bottom navigation → calendar_month`.

The replacement should update the checked-in Vector Drawable and this catalog,
keeping the existing composable's dimensions, colors, content descriptions, animations,
and actions. No font dependency or optical-size variants should be introduced.

**Resource IDs must not be `const val`.** This project sets
`android.nonFinalResIds=true`, and compile-time inlining can capture placeholder
zero IDs. `AppIcons` deliberately uses `val X: Int get() = R.drawable.ms_x`
to resolve the generated resource IDs at runtime. A device instrumentation
test covers all catalog entries (`MaterialSymbolsResourceTest`).
