### Task 10: Add Required Permissions

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Verify WAKE_LOCK permission exists**

```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

If not present, add it.

- [ ] **Step 2: Verify INTERNET permission exists**

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

If not present, add it.

- [ ] **Step 3: Commit if changes made**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "chore: ensure required permissions for streaming"
```
