#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java"
CONTROLLER = ROOT / "app/src/main/java/com/jepongdevxyz/idebuild/ProjectImportController.java"
TREE = ROOT / "app/src/main/java/com/jepongdevxyz/idebuild/SafProjectTreeCopier.java"

main = MAIN.read_text(encoding="utf-8")

assert "MAX_IMPORT_BYTES" not in main, "MainActivity must not impose a fixed project import byte ceiling"
assert "MAX_IMPORT_ENTRIES" not in main, "MainActivity must not impose a fixed project import entry ceiling"
assert "REQUEST_IMPORT_FOLDER" in main, "Folder import request code missing"
assert "Intent.ACTION_OPEN_DOCUMENT_TREE" in main, "SAF folder picker missing"
assert "Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP" in main, "Folder SAF must be API-gated"
assert "ProjectImportController" in main, "MainActivity must delegate import work to ProjectImportController"
assert "getContentResolver().openInputStream(uri)" not in main or "ProjectImportController" in main

assert CONTROLLER.exists(), "ProjectImportController.java missing"
controller = CONTROLLER.read_text(encoding="utf-8")
assert "ContentResolver" in controller
assert "openInputStream" in controller, "ZIP URI must be opened as a stream"
assert "ProjectImportService.importProject" in controller, "Controller must use atomic import service"
assert "NO_STORAGE_PROBE" not in controller, "Android controller should provide a real storage probe"
assert "getUsableSpace" in controller, "Controller must monitor available workspace storage"
assert "cancel()" in controller, "Cancelable import controller missing"
assert "content://" not in controller or "getPath" not in controller, "Do not convert content URIs to filesystem paths"

assert TREE.exists(), "SafProjectTreeCopier.java missing"
tree = TREE.read_text(encoding="utf-8")
assert "DocumentsContract.buildChildDocumentsUriUsingTree" in tree
assert "ContentResolver" in tree
assert "Cursor" in tree
assert "BufferedInputStream" in tree
assert "BufferedOutputStream" in tree
assert "byte[] buffer" in tree, "Folder import must use a reusable bounded buffer"
assert "read(buffer)" in tree or "read(buffer," in tree
assert "ArrayDeque" in tree, "Folder traversal should be iterative/bounded-stack"

print("IMPORT UI CONTRACT TEST PASSED")
