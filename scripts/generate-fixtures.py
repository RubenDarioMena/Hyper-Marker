#!/usr/bin/env python3
"""Genera ZIP pequeños y deterministas; nunca extrae sus entradas."""
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
root = Path(__file__).resolve().parents[1] / 'fixtures'
def write_zip(path, entries):
    with ZipFile(path, 'w') as archive:
        for name, data, mode in entries:
            info = ZipInfo(name, (2026, 10, 19, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = mode << 16
            archive.writestr(info, data)
project = [(p.relative_to(root/'project').as_posix(), p.read_bytes(), 0o100644) for p in sorted((root/'project').rglob('*')) if p.is_file()]
write_zip(root/'bundle/project.zip', project)
write_zip(root/'bundle/wrapped.zip', [('wrapped/'+name, data, mode) for name,data,mode in project])
write_zip(root/'bundle/ambiguous.zip', [('a.html', b'<h1>A</h1>', 0o100644), ('b.html', b'<h1>B</h1>', 0o100644)])
write_zip(root/'malicious/traversal.zip', [('../escape.txt', b'reject', 0o100644)])
write_zip(root/'malicious/absolute.zip', [('/escape.txt', b'reject', 0o100644)])
write_zip(root/'malicious/symlink.zip', [('link', b'../escape.txt', 0o120777)])
write_zip(root/'malicious/collision.zip', [('index.html', b'a', 0o100644), ('INDEX.html', b'b', 0o100644)])
