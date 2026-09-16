from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
workflow = (ROOT / '.github/workflows/android-full-verification.yml').read_text(encoding='utf-8')
generator = (ROOT / 'host-tests/GenerateTemplateProject.java').read_text(encoding='utf-8')

required_workflow_tokens = [
    'DevxyzClassicJavaSample',
    'classic',
    'com.jepongdevxyz.devxyzsampleclassic',
    'Build generated Classic Java sample',
    'generated-classic-build.log',
    'generated-classic-badging.txt',
]
for token in required_workflow_tokens:
    assert token in workflow, f'Full verification workflow must cover Classic Java E2E via {token}'

assert '"classic".equals(args[1])' in generator, 'Template CI utility must materialize the production CLASSIC_JAVA template'
assert 'ProjectTemplateGenerator.Template.CLASSIC_JAVA' in generator, 'Classic CI project must use production template code'

required_release_tokens = [
    'package-source:',
    'needs: [build, templates, emulator]',
    'DevxyzIDE-verified-source.zip',
    'git archive',
    'sha256sum',
    'DevxyzIDE-verified-source',
    'docs/CAPABILITY_MATRIX.md',
    'docs/LIMITATIONS.md',
    'docs/VERIFICATION_REPORT.md',
]
for token in required_release_tokens:
    assert token in workflow, f'Full verification workflow must gate the final verified source artifact via {token}'

print('FULL VERIFICATION CONTRACT TESTS PASSED')
