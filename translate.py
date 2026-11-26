import re
import os

# Files to process
UI_FILE = "app/src/main/java/com/mediaorganizer/app/ui/MediaOrganizerUi.kt"

# Read the file
with open(UI_FILE, 'r') as f:
    content = f.read()

# We need to find all strings inside Text("...", ...) or Text(text = "...", ...)
# But be careful not to break interpolation like "Found ${count}"
# For simplicity, we'll use regex to find static strings

pattern = r'Text\(\s*(?:text\s*=\s*)?"([^"\$]+)"'
matches = set(re.findall(pattern, content))

print(f"Found {len(matches)} strings:")
for m in matches:
    print(f" - {m}")
