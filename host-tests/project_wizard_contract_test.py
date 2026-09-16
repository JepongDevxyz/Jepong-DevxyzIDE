from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = (ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java').read_text()

assert 'Modern AndroidX Kotlin' in main, 'New Project wizard must expose the existing Kotlin template'
assert 'ProjectTemplateGenerator.Template.MODERN_ANDROIDX_KOTLIN' in main, 'Kotlin wizard row must map to the real Kotlin generator'
assert 'New AndroidX Kotlin project' in main, 'Project details dialog must identify Kotlin projects accurately'

print('PROJECT WIZARD CONTRACT TESTS PASSED')
