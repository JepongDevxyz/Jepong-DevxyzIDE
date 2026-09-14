from pathlib import Path
import subprocess, tempfile, zipfile

ROOT=Path(__file__).resolve().parents[1]
TOOL=ROOT/'tools/runtime/stamp_bootstrap.py'
with tempfile.TemporaryDirectory(prefix='devxyz-stamp-') as td:
    td=Path(td)
    src=td/'bootstrap.zip'
    dst=td/'stamped.zip'
    with zipfile.ZipFile(src,'w') as z:
        z.writestr('bin/sh',b'sh')
        z.writestr('SYMLINKS.txt','bin/sh←bin/shell\n')
    subprocess.run(['python3',str(TOOL),'--input',str(src),'--output',str(dst),
                    '--application-id','com.jepongdevxyz.idebuild','--arch','aarch64',
                    '--upstream-commit','610af608b4a3b1127244e90edf8b7be2bf94fafa'],check=True)
    with zipfile.ZipFile(dst) as z:
        meta=z.read('devxyz-bootstrap.properties').decode()
        assert 'format=1\n' in meta
        assert 'applicationId=com.jepongdevxyz.idebuild\n' in meta
        assert 'arch=aarch64\n' in meta
        assert 'upstreamCommit=610af608b4a3b1127244e90edf8b7be2bf94fafa\n' in meta
        assert z.read('bin/sh') == b'sh'
print('BOOTSTRAP STAMP TESTS PASSED')
