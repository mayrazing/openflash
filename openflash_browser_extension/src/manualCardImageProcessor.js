(() => {
  const MEBIBYTE = 1024 * 1024
  const DEFAULT_LIMITS = Object.freeze({
    maxImageCount: 10,
    maxSourceImageBytes: 20 * MEBIBYTE,
    targetImageBytes: 1 * MEBIBYTE,
    maxImageBytes: 5 * MEBIBYTE,
    maxTotalImageBytes: 20 * MEBIBYTE,
    maxImageEdge: 2000,
  })

  function createProcessor(options = {}) {
    const limits = { ...DEFAULT_LIMITS, ...(options.limits || {}) }
    const compressImage = options.compressImage || ((file) => compressWithCanvas(file, limits))

    async function prepareImages(images) {
      const source = Array.from(images || [])
      if (source.length > limits.maxImageCount) {
        throw new Error('manualCard.tooManyImages')
      }

      const prepared = []
      let totalBytes = 0
      for (const image of source) {
        const file = image?.file
        if (!file) throw new Error('manualCard.imageProcessFailed')
        if (fileSize(file) > limits.maxSourceImageBytes) {
          throw new Error('manualCard.imageTooLarge')
        }

        let compressed
        try {
          compressed = await compressImage(file)
        } catch {
          throw new Error('manualCard.imageProcessFailed')
        }
        if (!compressed || fileSize(compressed) > limits.maxImageBytes) {
          throw new Error('manualCard.imageTooLarge')
        }

        totalBytes += fileSize(compressed)
        if (totalBytes > limits.maxTotalImageBytes) {
          throw new Error('manualCard.imagesTooLarge')
        }
        prepared.push({ ...image, file: compressed })
      }
      return prepared
    }

    return { limits, prepareImages }
  }

  async function compressWithCanvas(file, limits) {
    if (typeof globalThis.createImageBitmap !== 'function' || !globalThis.document?.createElement) {
      return file
    }

    const bitmap = await globalThis.createImageBitmap(file)
    try {
      const longestEdge = Math.max(bitmap.width, bitmap.height)
      const initialScale = Math.min(1, limits.maxImageEdge / Math.max(1, longestEdge))
      let width = Math.max(1, Math.round(bitmap.width * initialScale))
      let height = Math.max(1, Math.round(bitmap.height * initialScale))

      if (initialScale === 1 && fileSize(file) <= limits.targetImageBytes) {
        return file
      }

      const canvas = globalThis.document.createElement('canvas')
      const encode = async (quality) => {
        canvas.width = width
        canvas.height = height
        const context = canvas.getContext('2d', { alpha: false })
        if (!context) throw new Error('canvas unavailable')
        context.fillStyle = '#ffffff'
        context.fillRect(0, 0, width, height)
        context.drawImage(bitmap, 0, 0, width, height)
        return canvasToBlob(canvas, quality)
      }

      let result = null
      for (const quality of [0.88, 0.78, 0.68, 0.58, 0.48]) {
        result = await encode(quality)
        if (fileSize(result) <= limits.targetImageBytes) return result
      }

      while (fileSize(result) > limits.targetImageBytes && Math.max(width, height) > 800) {
        width = Math.max(1, Math.round(width * 0.85))
        height = Math.max(1, Math.round(height * 0.85))
        result = await encode(0.72)
      }
      return result
    } finally {
      bitmap.close?.()
    }
  }

  function canvasToBlob(canvas, quality) {
    return new Promise((resolve, reject) => {
      canvas.toBlob((blob) => {
        if (blob) resolve(blob)
        else reject(new Error('image encode failed'))
      }, 'image/jpeg', quality)
    })
  }

  function fileSize(file) {
    const size = Number(file?.size)
    return Number.isFinite(size) && size >= 0 ? size : 0
  }

  const defaultProcessor = createProcessor()
  globalThis.OpenFlashManualCardImageProcessor = {
    DEFAULT_LIMITS,
    createProcessor,
    prepareImages: defaultProcessor.prepareImages,
  }
})()
