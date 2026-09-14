from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
errors=[]
for p in (root/'app/src/main/java').rglob('*.java'):
    s=p.read_text(errors='ignore')
    if '->' in s: errors.append(f'lambda syntax: {p.name}')
    if '::' in s: errors.append(f'method reference: {p.name}')
    for bad in ('import androidx.','import com.google.android.material','import io.github.rosemoe'):
        if bad in s: errors.append(f'external Android import: {p.name}')
gradle=(root/'app/build.gradle').read_text()
for coord in ('androidx.','com.google.android.material','io.github.rosemoe'):
    if coord in gradle: errors.append('external dependency '+coord)
xml='\n'.join(p.read_text(errors='ignore') for p in (root/'app/src/main/res').rglob('*.xml'))
for token,msg in [('com.google.android.material','Material XML widget'),('io.github.rosemoe','Sora XML widget'),('xmlns:app=','support app namespace')]:
    if token in xml: errors.append(msg)
if "sourceCompatibility JavaVersion.VERSION_1_7" not in gradle: errors.append('not Java 7 source')
root_gradle=(root/'build.gradle').read_text()
if "com.android.tools.build:gradle:3.2.1" not in root_gradle: errors.append('AGP is not AIDE legacy profile')
wrapper=(root/'gradle/wrapper/gradle-wrapper.properties').read_text()
if 'gradle-4.6-all.zip' not in wrapper: errors.append('Gradle wrapper is not 4.6')
if errors: raise SystemExit('AIDE LEGACY SOURCE FAIL:\n- '+'\n- '.join(errors))
print('AIDE LEGACY SOURCE PASS')
