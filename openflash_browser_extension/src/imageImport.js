import { api, uploadImageFile } from './apiClient.js'
import {
  MAX_DATA_IMAGE_SOURCE_LENGTH,
  MAX_REMOTE_IMAGE_SOURCE_LENGTH,
  MAX_SELECTION_IMAGE_SOURCE_TOTAL_LENGTH,
} from './selectionAdapter.js'

export const MAX_IMAGE_SOURCES = 20
export const MAX_LOCAL_IMAGE_BYTES = 8 * 1024 * 1024
const MAX_LOCAL_IMAGE_TOTAL_BYTES = 20 * 1024 * 1024
const LOCAL_FETCH_TIMEOUT_MS = 10_000

/**
 * 将图片来源列表按协议拆分为远程 URL 和本地数据源。
 * 远程：http/https；本地：data:/blob:。
 * @param {string[]} sources
 * @returns {{ remoteUrls: string[], localSources: string[] }}
 */
export function splitImageSources(sources) {
  const remoteUrls = []
  const localSources = []
  for (const source of sources || []) {
    if (/^https?:\/\//i.test(source)) {
      remoteUrls.push(source)
    } else if (/^(data|blob):/i.test(source)) {
      localSources.push(source)
    }
  }
  return { remoteUrls, localSources }
}

/**
 * 汇总导入结果为统一的返回格式。
 * @param {string[]} sideAImage
 * @param {number} failedCount
 * @returns {{ sideAImage: string[], failedCount: number }}
 */
export function summarizeImageImport(sideAImage, failedCount) {
  return { sideAImage, failedCount }
}

/**
 * 导入图片：远程 URL 只走带 SSRF 防护的服务端转存；
 * 本地 data:/blob: 走客户端上传。
 * 使用真实 apiClient 依赖。
 * @param {string} baseUrl
 * @param {string[]} imageSources
 * @returns {Promise<{ sideAImage: string[], failedCount: number }>}
 */
export async function importImages(baseUrl, imageSources) {
  return importImagesWithDeps(baseUrl, imageSources, {
    transferImages: api.transferImages,
    fetchBlob: fetchLocalBlob,
    uploadImageFile,
  })
}

/**
 * 可注入依赖的导入图片实现，便于测试。
 * @param {string} baseUrl
 * @param {string[]} imageSources
 * @param {{ transferImages: Function, fetchBlob: Function, uploadImageFile: Function }} deps
 * @returns {Promise<{ sideAImage: string[], failedCount: number }>}
 */
export async function importImagesWithDeps(baseUrl, imageSources, deps) {
  const supportedSources = []
  let omittedSupportedCount = 0
  const candidates = Array.isArray(imageSources) ? imageSources : []
  let retainedSourceLength = 0
  for (let index = 0; index < candidates.length; index += 1) {
    const source = candidates[index]
    if (!/^https?:\/\//i.test(source) && !/^(data|blob):/i.test(source)) continue
    const sourceLimit = /^https?:\/\//i.test(source)
      ? MAX_REMOTE_IMAGE_SOURCE_LENGTH
      : MAX_DATA_IMAGE_SOURCE_LENGTH
    if (source.length > sourceLimit) {
      omittedSupportedCount += 1
      continue
    }
    if (retainedSourceLength + source.length > MAX_SELECTION_IMAGE_SOURCE_TOTAL_LENGTH) {
      omittedSupportedCount += 1
      continue
    }
    if (supportedSources.length < MAX_IMAGE_SOURCES) {
      supportedSources.push({ source, index })
      retainedSourceLength += source.length
    } else {
      omittedSupportedCount += 1
    }
  }
  const remoteEntries = supportedSources.filter(({ source }) => /^https?:\/\//i.test(source))
  const localEntries = supportedSources.filter(({ source }) => /^(data|blob):/i.test(source))
  const importedByIndex = new Map()
  let failedCount = omittedSupportedCount

  if (remoteEntries.length > 0) {
    const remoteUrls = remoteEntries.map(({ source }) => source)
    try {
      const response = await deps.transferImages(baseUrl, remoteUrls)
      const results = response.results || []
      for (let index = 0; index < remoteEntries.length; index += 1) {
        const entry = remoteEntries[index]
        const result = results[index] || { sourceUrl: entry.source, success: false }
        if (result.success && result.url) {
          importedByIndex.set(entry.index, result.url)
        } else {
          failedCount += 1
        }
      }
    } catch {
      // 远程 URL 不允许用扩展权限回退 fetch, 否则会绕过服务端 SSRF 防护.
      failedCount += remoteEntries.length
    }
  }

  let importedLocalBytes = 0
  for (const entry of localEntries) {
    try {
      const remainingBytes = MAX_LOCAL_IMAGE_TOTAL_BYTES - importedLocalBytes
      const imported = await uploadSource(
        baseUrl,
        entry.source,
        deps,
        Math.min(MAX_LOCAL_IMAGE_BYTES, remainingBytes),
      )
      importedLocalBytes += imported.bytes
      importedByIndex.set(entry.index, imported.url)
    } catch {
      failedCount += 1
    }
  }

  const sideAImage = supportedSources
    .map(({ index }) => importedByIndex.get(index))
    .filter(Boolean)

  return summarizeImageImport(sideAImage, failedCount)
}

/**
 * 客户端下载图片 blob 并上传到服务端。
 * @param {string} baseUrl
 * @param {string} source
 * @param {{ fetchBlob: Function, uploadImageFile: Function }} deps
 * @returns {Promise<string>} 服务端返回的 URL
 */
async function uploadSource(baseUrl, source, deps, maxBytes) {
  if (!/^(data|blob):/i.test(source) || maxBytes <= 0) {
    throw new Error('unsupported local image source')
  }
  if (/^data:/i.test(source) && source.length > Math.ceil(maxBytes * 4 / 3) + 1024) {
    throw new Error('local image exceeds size limit')
  }
  const blob = await deps.fetchBlob(source, maxBytes)
  if (!blob || !Number.isFinite(blob.size) || blob.size > maxBytes) {
    throw new Error('local image exceeds size limit')
  }
  const file = typeof File === 'function'
    ? new File([blob], 'image.jpg', { type: blob.type || 'image/jpeg' })
    : blob
  return { url: await deps.uploadImageFile(baseUrl, file), bytes: blob.size }
}

/** 只读取 data/blob 本地源, 带总超时和流式字节上限. */
async function fetchLocalBlob(source, maxBytes) {
  if (!/^(data|blob):/i.test(source)) throw new Error('remote fetch is forbidden')
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), LOCAL_FETCH_TIMEOUT_MS)
  try {
    const response = await fetch(source, { signal: controller.signal })
    if (!response.ok) throw new Error('local image fetch failed')
    const declaredBytes = Number(response.headers.get('content-length'))
    if (Number.isFinite(declaredBytes) && declaredBytes > maxBytes) {
      throw new Error('local image exceeds size limit')
    }
    if (!response.body?.getReader) {
      const blob = await response.blob()
      if (blob.size > maxBytes) throw new Error('local image exceeds size limit')
      return blob
    }
    const reader = response.body.getReader()
    const chunks = []
    let totalBytes = 0
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      totalBytes += value.byteLength
      if (totalBytes > maxBytes) {
        await reader.cancel()
        throw new Error('local image exceeds size limit')
      }
      chunks.push(value)
    }
    return new Blob(chunks, { type: response.headers.get('content-type') || 'application/octet-stream' })
  } finally {
    clearTimeout(timeoutId)
  }
}
