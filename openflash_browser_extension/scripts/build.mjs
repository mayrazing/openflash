import { copyFile, mkdir, rm } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { build } from 'vite'
import { syncAppleTheme } from './syncAppleTheme.mjs'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const outDir = resolve(root, 'dist')

const pageBuild = {
  configFile: false,
  root,
  base: './',
  publicDir: false,
  plugins: [react(), tailwindcss()],
  build: {
    emptyOutDir: false,
    modulePreload: false,
    outDir,
    rollupOptions: {
      input: {
        manualCard: resolve(root, 'manualCard.html'),
        popup: resolve(root, 'popup.html'),
        shortcutSetup: resolve(root, 'shortcutSetup.html'),
      },
    },
  },
}

const backgroundBuild = {
  configFile: false,
  publicDir: false,
  build: {
    emptyOutDir: false,
    outDir,
    lib: {
      entry: resolve(root, 'src/background.js'),
      formats: ['es'],
      fileName: () => 'assets/background.js',
    },
  },
}

const contentBuild = {
  configFile: false,
  define: { 'process.env.NODE_ENV': JSON.stringify('production') },
  publicDir: false,
  plugins: [react(), tailwindcss()],
  build: {
    emptyOutDir: false,
    outDir,
    lib: {
      entry: resolve(root, 'src/content/contentScriptEntry.jsx'),
      formats: ['iife'],
      name: 'OpenFlashContentScript',
      fileName: () => 'assets/contentScript.js',
    },
    rollupOptions: { output: { inlineDynamicImports: true } },
  },
}

await syncAppleTheme({ mode: 'check' })
await rm(outDir, { recursive: true, force: true })
await build(pageBuild)
await build(backgroundBuild)
await build(contentBuild)
await copyFile(resolve(root, 'manifest.json'), resolve(outDir, 'manifest.json'))
await mkdir(resolve(outDir, 'icons'), { recursive: true })
for (const size of [16, 32, 48, 128]) {
  const name = `icon-${size}.png`
  await copyFile(resolve(root, 'icons', name), resolve(outDir, 'icons', name))
}
