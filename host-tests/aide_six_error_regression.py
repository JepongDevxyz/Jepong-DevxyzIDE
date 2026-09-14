from pathlib import Path
root=Path(__file__).resolve().parents[1] / 'app/src/main/java/com/jepongdevxyz/idebuild'
b=(root/'BuildRunner.java').read_text()
t=(root/'core/toolchain/ToolchainPackInstaller.java').read_text()
assert 'runBuild(final File projectRoot, final File appFilesDir, final String task, final boolean offline, final Listener listener)' in b, 'BuildRunner captured parameters are not final for AIDE Java 7 parser'
assert 'Files.copy(' not in t, 'ToolchainPackInstaller still uses java.nio.file.Files.copy, rejected by target AIDE environment'
print('AIDE 6-error regression: PASS')
r=(root/'core/toolchain/RuntimePackDownloader.java').read_text()
assert 'java.nio.file.Files.move' not in r, 'RuntimePackDownloader still uses java.nio.file.Files.move, rejected by target AIDE environment'
assert 'StandardCopyOption' not in r, 'RuntimePackDownloader still uses StandardCopyOption, rejected by target AIDE environment'
