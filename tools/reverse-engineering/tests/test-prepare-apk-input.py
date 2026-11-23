#!/usr/bin/env python3
"""Regression tests for APK/XAPK/APKS input normalization using a fake aapt."""
import os, subprocess, sys, tempfile, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
PREP=ROOT/"tools/reverse-engineering/prepare-apk-input.py"

with tempfile.TemporaryDirectory() as td:
    root=Path(td)
    fake=root/"aapt"
    fake.write_text("""#!/usr/bin/env python3
import sys,zipfile,re
p=sys.argv[-1]
with zipfile.ZipFile(p) as z:
    raw=z.read('AndroidManifest.xml').decode()
m=re.search(r'PACKAGE=([A-Za-z0-9_.]+)',raw)
print("package: name='%s' versionCode='1'" % (m.group(1) if m else 'unknown'))
""")
    fake.chmod(0o755)
    def apk(path, package="org.example.app"):
        with zipfile.ZipFile(path,"w") as z:
            z.writestr("AndroidManifest.xml","PACKAGE="+package)
            z.writestr("classes.dex",b"dex")
    one=root/"one.apk"; apk(one)
    out=root/"out-one"
    subprocess.run([sys.executable,str(PREP),str(one),str(out),str(fake)],check=True)
    assert (out/"splits/base.apk").exists()
    bundle=root/"sample.xapk"
    with zipfile.ZipFile(bundle,"w") as z:
        for name in ["base.apk","config.arm64_v8a.apk","config.xxhdpi.apk"]:
            p=root/name; apk(p)
            z.write(p,name)
    out2=root/"out-bundle"
    subprocess.run([sys.executable,str(PREP),str(bundle),str(out2),str(fake)],check=True)
    assert len(list((out2/"splits").glob("*.apk")))==3
    assert (out2/"split-inventory.tsv").read_text().count("\n")==4
    bad=root/"bad.zip"
    with zipfile.ZipFile(bad,"w") as z:
        p=root/"base.apk"; apk(p,"org.example.app"); z.write(p,"base.apk")
        p=root/"config.apk"; apk(p,"org.other.app"); z.write(p,"config.apk")
    result=subprocess.run([sys.executable,str(PREP),str(bad),str(root/"out-bad"),str(fake)],capture_output=True,text=True)
    assert result.returncode != 0 and "Package mismatch" in result.stderr
print("APK input normalization tests passed.")
