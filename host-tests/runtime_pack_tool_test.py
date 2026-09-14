from pathlib import Path
import hashlib, subprocess, tempfile, zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT/'tools/runtime/make_toolchain_pack.py'

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

with tempfile.TemporaryDirectory(prefix='devxyz-pack-tool-') as td:
    td=Path(td)
    tree=td/'tree'
    (tree/'bin').mkdir(parents=True)
    (tree/'lib').mkdir()
    (tree/'bin/java').write_bytes(b'java-binary')
    (tree/'lib/modules').write_bytes(b'modules-data')
    (tree/'bin/java').chmod(0o755)
    out1=td/'one.zip'
    out2=td/'two.zip'
    args=['python3',str(TOOL),'--input',str(tree),'--target','toolchains/jdk17','--output',str(out1)]
    subprocess.run(args,check=True,capture_output=True,text=True)
    args[-1]=str(out2)
    subprocess.run(args,check=True,capture_output=True,text=True)
    assert out1.read_bytes() == out2.read_bytes(), 'toolchain pack must be deterministic'
    with zipfile.ZipFile(out1) as z:
        names=z.namelist()
        assert names == ['devxyz-toolchain.properties','bin/java','lib/modules'], names
        manifest=z.read('devxyz-toolchain.properties').decode()
        assert 'format=1\n' in manifest
        assert 'target=toolchains/jdk17\n' in manifest
        assert f'file.bin/java={sha(b"java-binary")}\n' in manifest
        assert f'file.lib/modules={sha(b"modules-data")}\n' in manifest

    bad=td/'bad.zip'
    proc=subprocess.run(['python3',str(TOOL),'--input',str(tree),'--target','../escape','--output',str(bad)],capture_output=True,text=True)
    assert proc.returncode != 0, 'unsafe target must fail'
    assert not bad.exists(), 'unsafe target must not create output'

print('RUNTIME PACK TOOL TESTS PASSED')
