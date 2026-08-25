const DATABASE_NAME = 'openflash-tts-audio'
const DATABASE_VERSION = 1
const STORE_NAME = 'audio'

/**
 * 创建按文本保存 WAV Blob 的浏览器本地缓存.
 * IndexedDB 不可用时返回空结果, 让发音流程继续请求后端.
 */
export function createTtsAudioCache(indexedDb = globalThis.indexedDB) {
  let databasePromise = null

  function openDatabase() {
    if (!indexedDb) return Promise.resolve(null)
    if (databasePromise) return databasePromise

    databasePromise = new Promise((resolve, reject) => {
      const request = indexedDb.open(DATABASE_NAME, DATABASE_VERSION)
      request.onupgradeneeded = () => {
        const database = request.result
        if (!database.objectStoreNames.contains(STORE_NAME)) {
          database.createObjectStore(STORE_NAME)
        }
      }
      request.onsuccess = () => {
        const database = request.result
        database.onversionchange = () => {
          database.close()
          databasePromise = null
        }
        resolve(database)
      }
      request.onerror = () => {
        databasePromise = null
        reject(request.error ?? new Error('无法打开 TTS 浏览器缓存'))
      }
      request.onblocked = () => {
        databasePromise = null
        reject(new Error('TTS 浏览器缓存升级被阻塞'))
      }
    })
    return databasePromise
  }

  async function get(normalizedText) {
    const database = await openDatabase()
    if (!database) return null
    return new Promise((resolve, reject) => {
      const request = database.transaction(STORE_NAME, 'readonly')
        .objectStore(STORE_NAME)
        .get(normalizedText)
      request.onsuccess = () => resolve(request.result instanceof Blob ? request.result : null)
      request.onerror = () => reject(request.error ?? new Error('读取 TTS 浏览器缓存失败'))
    })
  }

  async function put(normalizedText, audioBlob) {
    const database = await openDatabase()
    if (!database) return false
    return new Promise((resolve, reject) => {
      const transaction = database.transaction(STORE_NAME, 'readwrite')
      transaction.objectStore(STORE_NAME).put(audioBlob, normalizedText)
      transaction.oncomplete = () => resolve(true)
      transaction.onerror = () => reject(transaction.error ?? new Error('写入 TTS 浏览器缓存失败'))
      transaction.onabort = () => reject(transaction.error ?? new Error('写入 TTS 浏览器缓存被中止'))
    })
  }

  async function remove(normalizedText) {
    const database = await openDatabase()
    if (!database) return false
    return new Promise((resolve, reject) => {
      const transaction = database.transaction(STORE_NAME, 'readwrite')
      transaction.objectStore(STORE_NAME).delete(normalizedText)
      transaction.oncomplete = () => resolve(true)
      transaction.onerror = () => reject(transaction.error ?? new Error('删除 TTS 浏览器缓存失败'))
      transaction.onabort = () => reject(transaction.error ?? new Error('删除 TTS 浏览器缓存被中止'))
    })
  }

  return { get, put, remove }
}

export const ttsAudioCache = createTtsAudioCache()
