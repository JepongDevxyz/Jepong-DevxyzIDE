from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
portrait = (ROOT / 'app/src/main/res/layout/activity_main.xml').read_text()
landscape = (ROOT / 'app/src/main/res/layout-land/activity_main.xml').read_text()
nav = (ROOT / 'app/src/main/java/com/jepongdevxyz/idebuild/WorkspaceNavigationButton.java').read_text()

for name, layout in (('portrait', portrait), ('landscape', landscape)):
    for workspace in ('workspace_files', 'workspace_search', 'workspace_git', 'workspace_build_tools', 'workspace_more'):
        token = 'android:id="@+id/%s"' % workspace
        assert layout.count(token) == 1, '%s must contain one real %s surface' % (name, workspace)

assert 'show(root, R.id.workspace_search);' in nav
assert 'show(root, R.id.workspace_git);' in nav
assert 'visible(root, R.id.workspace_search, target == R.id.workspace_search);' in nav
assert 'visible(root, R.id.workspace_git, target == R.id.workspace_git);' in nav
assert 'runAction(root, R.id.searchButton)' not in nav
assert 'runAction(root, R.id.gitButton)' not in nav

print('workspace navigation surface contract: OK')
