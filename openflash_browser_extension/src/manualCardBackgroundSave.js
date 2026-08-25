/**
 * 创建 Background 保存处理器：上传 data URL 图片，或用完整 payload 创建卡片。
 */
export function createManualCardBackgroundSaveHandler(deps) {
  const limits = {
    maxImageCount: 10,
    maxImageBytes: 5 * 1024 * 1024,
    maxTotalImageBytes: 20 * 1024 * 1024,
    ...(deps.imageLimits || {}),
  }
  return async function handleManualCardBackgroundSave(message, sender) {
    const isManualCardWrite = message?.type === 'OPENFLASH_MANUAL_CARD_UPLOAD_IMAGES'
      || message?.type === 'OPENFLASH_MANUAL_CARD_CREATE'
    if (!isManualCardWrite) return false
    if (!deps.isTrustedSender(sender)) throw new Error('untrusted manual card sender')

    if (message?.type === 'OPENFLASH_MANUAL_CARD_UPLOAD_IMAGES') {
      return uploadImages(message, deps, limits)
    }
    if (message?.type === 'OPENFLASH_MANUAL_CARD_CREATE') {
      const card = await deps.createImportedCard(message.baseUrl, message.deckId, message.payload)
      return { ok: true, card }
    }
  }
}

async function uploadImages(message, deps, limits) {
  const images = [
    ...(Array.isArray(message.sideAImages) ? message.sideAImages : []),
    ...(Array.isArray(message.sideBImages) ? message.sideBImages : []),
  ]
  const validated = validateImages(images, limits)
  const uploadedByImageId = {}
  for (const image of validated) {
    uploadedByImageId[image.source.id] = await deps.uploadImageFile(
      message.baseUrl,
      dataUrlImageToBlob(image),
    )
  }
  return { ok: true, uploadedByImageId }
}

function validateImages(images, limits) {
  if (images.length > limits.maxImageCount) {
    throw new Error('manualCard.tooManyImages')
  }
  let totalBytes = 0
  return images.map((image) => {
    const parsed = parseDataUrlImage(image, limits.maxImageBytes)
    totalBytes += parsed.byteLength
    if (totalBytes > limits.maxTotalImageBytes) {
      throw new Error('manualCard.imagesTooLarge')
    }
    return parsed
  })
}

function parseDataUrlImage(image, maxImageBytes) {
  const dataUrl = String(image?.dataUrl || '')
  const commaIndex = dataUrl.indexOf(',')
  const header = commaIndex >= 0 ? dataUrl.slice(0, commaIndex) : ''
  if (!/^data:image\/[a-z0-9.+-]+;base64$/i.test(header)) {
    throw new Error('manualCard.imageProcessFailed')
  }
  const encodedLength = dataUrl.length - commaIndex - 1
  const maxEncodedLength = Math.ceil(maxImageBytes * 4 / 3) + 4
  if (encodedLength <= 0 || encodedLength > maxEncodedLength) {
    throw new Error('manualCard.imageTooLarge')
  }
  const padding = dataUrl.endsWith('==') ? 2 : dataUrl.endsWith('=') ? 1 : 0
  const byteLength = Math.floor(encodedLength * 3 / 4) - padding
  if (byteLength > maxImageBytes) {
    throw new Error('manualCard.imageTooLarge')
  }
  return {
    source: image,
    mediaType: header.slice(5, -7),
    encoded: dataUrl.slice(commaIndex + 1),
    byteLength,
  }
}

function dataUrlImageToBlob(image) {
  let decoded
  try {
    decoded = atob(image.encoded)
  } catch {
    throw new Error('manualCard.imageProcessFailed')
  }
  const bytes = Uint8Array.from(decoded, (char) => char.charCodeAt(0))
  const blob = new Blob([bytes], { type: image.mediaType || 'image/jpeg' })
  blob.name = image.source.name || 'image.jpg'
  return blob
}
