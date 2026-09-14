#!/usr/bin/env python3
from __future__ import annotations
import argparse, os, pathlib, re, zipfile

APP_ID=re.compile(r'^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$')
SHA=re.compile(r'^[0-9a-f]{40,64}$')

def main():
    ap=argparse.ArgumentParser(description='Stamp a package-specific terminal bootstrap for DevxyzIDE')
    ap.add_argument('--input',required=True,type=pathlib.Path)
    ap.add_argument('--output',required=True,type=pathlib.Path)
    ap.add_argument('--application-id',required=True)
    ap.add_argument('--arch',required=True,choices=['aarch64','arm'])
    ap.add_argument('--upstream-commit',required=True)
    a=ap.parse_args()
    if not APP_ID.fullmatch(a.application_id): raise SystemExit('invalid application id')
    if not SHA.fullmatch(a.upstream_commit.lower()): raise SystemExit('invalid upstream commit')
    if not a.input.is_file(): raise SystemExit('input bootstrap not found')
    a.output.parent.mkdir(parents=True,exist_ok=True)
    tmp=a.output.with_suffix(a.output.suffix+'.tmp')
    if tmp.exists(): tmp.unlink()
    meta=(
        'format=1\n'
        f'applicationId={a.application_id}\n'
        f'arch={a.arch}\n'
        f'upstreamCommit={a.upstream_commit}\n'
    ).encode()
    try:
        with zipfile.ZipFile(a.input,'r') as src, zipfile.ZipFile(tmp,'w') as dst:
            names=src.namelist()
            if 'devxyz-bootstrap.properties' in names:
                raise SystemExit('input is already Devxyz-stamped')
            info=zipfile.ZipInfo('devxyz-bootstrap.properties',(1980,1,1,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=0o644<<16
            dst.writestr(info,meta)
            for old in src.infolist():
                if old.filename.startswith('/') or '..' in pathlib.PurePosixPath(old.filename).parts:
                    raise SystemExit(f'unsafe input path: {old.filename}')
                data=src.read(old.filename) if not old.is_dir() else b''
                copy=zipfile.ZipInfo(old.filename,old.date_time)
                copy.compress_type=old.compress_type
                copy.external_attr=old.external_attr
                copy.comment=old.comment
                copy.extra=old.extra
                dst.writestr(copy,data)
        os.replace(tmp,a.output)
    finally:
        if tmp.exists(): tmp.unlink()
    print(a.output)

if __name__=='__main__': main()
