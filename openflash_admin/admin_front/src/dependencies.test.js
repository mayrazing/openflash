import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const ADMIN_ONLY_TEST_DEPENDENCIES = new Set(['jsdom'])

async function readJson(path) {
  return JSON.parse(await readFile(new URL(path, import.meta.url), 'utf8'))
}

test('admin direct dependencies use the same ranges and locked versions as openflash_front', async () => {
  const [adminPackage, adminLock, frontPackage, frontLock] = await Promise.all([
    readJson('../package.json'),
    readJson('../package-lock.json'),
    readJson('../../../openflash_user/openflash_front/package.json'),
    readJson('../../../openflash_user/openflash_front/package-lock.json'),
  ])

  for (const dependencyType of ['dependencies', 'devDependencies']) {
    for (const [name, range] of Object.entries(adminPackage[dependencyType])) {
      if (dependencyType === 'devDependencies' && ADMIN_ONLY_TEST_DEPENDENCIES.has(name)) continue
      assert.equal(range, frontPackage[dependencyType][name], `${name} range differs`)
      assert.equal(
        adminLock.packages[`node_modules/${name}`].version,
        frontLock.packages[`node_modules/${name}`].version,
        `${name} locked version differs`,
      )
    }
  }
})
