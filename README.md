# WStream (Accessibility NDJSON Stream)

**A tiny Android AccessibilityService** that streams visible list items (e.g., WhatsApp chats) as NDJSON over TCP (port 7912).

## How it works
- Listens to `TYPE_VIEW_SCROLLED` / `TYPE_WINDOW_CONTENT_CHANGED` events
- Extracts visible list item texts + bounds
- Streams frames as NDJSON on TCP 7912

## Build (GitHub Actions)
Push to `main` and download APK from **Actions → Artifacts**.

## Local build
- Android Studio: Open → Run
- CLI: `./gradlew assembleDebug`

## Run
1. Install APK and enable **Settings → Accessibility → WStream**.
2. Connect USB and run:

```bash
adb forward tcp:7912 tcp:7912   # or: adb reverse tcp:7912 tcp:7912
```

3. Connect a client and read frames line-by-line (NDJSON). Example Python client:

```python
import socket, json, hashlib
seen=set(); idx=0
def key(it): 
    b=it.get("bounds",{}); return hashlib.sha1((it.get("title","")+"|"+str(b.get("t"))+"|"+str(b.get("b"))).encode()).hexdigest()
s=socket.create_connection(("127.0.0.1",7912)); f=s.makefile()
for line in f:
    frame=json.loads(line)
    for it in frame["items"]:
        k=key(it)
        if k not in seen:
            seen.add(k)
            print(f"[{idx:05d}] {it.get('title','')} pos={it.get('pos')}")
            idx+=1
```

## Notes
- To target only a specific app (e.g., WhatsApp), set `android:packageNames="com.whatsapp"` in `res/xml/accessibility_service_config.xml`.
- This project avoids committing Gradle wrapper. The workflow generates it on CI.
