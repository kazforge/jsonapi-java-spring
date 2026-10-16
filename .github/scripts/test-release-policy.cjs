// Run with release-please on NODE_PATH; no GitHub token or network calls are used.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {buildStrategy} = require('release-please/build/src/factory');
const {TagName} = require('release-please/build/src/util/tag-name');
const {parseConventionalCommits} = require('release-please/build/src/commit');
const config = JSON.parse(fs.readFileSync('release-please-config.json')).packages['.'];

async function check(message, previous, expected) {
  const options = Object.fromEntries(Object.entries(config).map(([key, value]) =>
    [key.replace(/-([a-z])/g, (_, letter) => letter.toUpperCase()), value]));
  const strategy = await buildStrategy({
    ...options, path: '.', targetBranch: 'main',
    github: {repository: {owner: 'kazforge', repo: 'jsonapi-java-spring', defaultBranch: 'main'}}
  });
  const release = previous ? {tag: TagName.parse('v' + previous), sha: 'abc', notes: ''} : undefined;
  const commits = parseConventionalCommits([{sha: '1234567', message, files: []}]);
  const result = await strategy.buildReleasePullRequest(commits, release);
  assert.equal(result?.version.toString(), expected, message);
  if (result) {
    assert.ok(result.updates.some(update => update.path === 'gradle.properties'));
    assert.ok(result.updates.some(update => update.path === 'CHANGELOG.md'));
    assert.equal(result.group, undefined, 'must not produce a snapshot PR');
  }
}

(async () => {
  await check('feat: initial WebMVC adapter', undefined, '0.1.0');
  await check('feat: new capability', '0.1.0', '0.2.0');
  await check('fix: correct behavior', '0.1.0', '0.1.1');
  await check('perf: faster response', '0.1.0', '0.1.1');
  await check('revert: remove a fix', '0.1.0', '0.1.1');
  await check('feat!: change contract', '0.1.0', '0.2.0');
  await check('fix: change contract\n\nBREAKING CHANGE: incompatible behavior', '0.1.0', '0.2.0');
  await check('feat!: change contract', '1.0.0', '2.0.0');
  for (const type of ['docs', 'chore', 'build', 'ci', 'test', 'refactor']) {
    await check(type + ': internal change', '0.1.0', undefined);
  }
  console.log('14 release policy scenarios passed');
})().catch(error => { console.error(error); process.exit(1); });
