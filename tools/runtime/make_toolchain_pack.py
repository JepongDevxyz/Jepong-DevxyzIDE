#!/usr/bin/env python3
"""Build a deterministic DevxyzIDE verified toolchain-pack ZIP from a directory."""
from __future__ import annotations
import argparse, hashlib, os, pathlib, re, zipfile

TARGET_RE = re.compile(r"^toolchains/[A-Za-z0-9._/-]+$")

def sha256_file(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()

def collect(root: pathlib.Path):
    files=[]
    for p in root.rglob('*'):
        if p.is_file():
            rel=p.relative_to(root).as_posix()
            if rel == 'devxyz-toolchain.properties':
                raise ValueError('input tree must not contain devxyz-toolchain.properties')
            if rel.startswith('../') or '/..' in rel:
                raise ValueError(f'unsafe relative path: {rel}')
            files.append((rel,p))
    files.sort(key=lambda x:x[0])
    if not files:
        raise ValueError('input tree has no files')
    return files

def make_manifest(target: str, files) -> bytes:
    if not TARGET_RE.fullmatch(target) or '..' in target.split('/'):
        raise ValueError('target must be a safe path below toolchains/')
    lines=['format=1', f'target={target}']
    for rel,p in files:
        lines.append(f'file.{rel}={sha256_file(p)}')
    return ('\n'.join(lines)+'\n').encode('utf-8')

def create_pack(root: pathlib.Path, target: str, output: pathlib.Path):
    root=root.resolve()
    if not root.is_dir():
        raise ValueError('input must be a directory')
    files=collect(root)
    manifest=make_manifest(target, files)
    output.parent.mkdir(parents=True, exist_ok=True)
    tmp=output.with_suffix(output.suffix+'.tmp')
    if tmp.exists(): tmp.unlink()
    try:
        with zipfile.ZipFile(tmp,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            info=zipfile.ZipInfo('devxyz-toolchain.properties',(1980,1,1,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=0o644 << 16
            z.writestr(info,manifest)
            for rel,p in files:
                info=zipfile.ZipInfo(rel,(1980,1,1,0,0,0))
                info.compress_type=zipfile.ZIP_DEFLATED
                mode=p.stat().st_mode & 0o777
                info.external_attr=(mode or 0o644) << 16
                z.writestr(info,p.read_bytes())
        os.replace(tmp,output)
    finally:
        if tmp.exists(): tmp.unlink()

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--input',required=True,type=pathlib.Path)
    ap.add_argument('--target',required=True)
    ap.add_argument('--output',required=True,type=pathlib.Path)
    a=ap.parse_args()
    create_pack(a.input,a.target,a.output)
    print(a.output)

if __name__=='__main__': main()
