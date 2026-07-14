## Task 10: Add Required Permissions — Report

**Status:** Complete

### Changes Made

- **WAKE_LOCK permission:** Added `<uses-permission android:name="android.permission.WAKE_LOCK" />` — was missing.
- **INTERNET permission:** Already declared (line 5). No change needed.

### Commit

```
18d3714 chore: ensure required permissions for streaming
```
