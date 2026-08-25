(() => {
  /** 创建内容脚本保存器：把未上传图片转 data URL，缓存已上传 URL，向 Background 发保存消息。 */
  function createSaver() {
    const uploadedByImageId = {}
    let saving = false

    async function save({ baseUrl, deckId, state }) {
      if (saving) return null
      if (!globalThis.OpenFlashManualCardEditor.hasAnyContent(state)) {
        throw new Error('manualCard.emptyContent')
      }
      saving = true
      try {
        const sideAImages = state.a.images.filter((image) => !uploadedByImageId[image.id])
        const sideBImages = state.b.images.filter((image) => !uploadedByImageId[image.id])
        if (sideAImages.length || sideBImages.length) {
          const prepared = await globalThis.OpenFlashManualCardImageProcessor.prepareImages([
            ...sideAImages,
            ...sideBImages,
          ])
          const preparedById = Object.fromEntries(prepared.map((image) => [image.id, image]))
          const uploadResponse = await chrome.runtime.sendMessage({
            type: 'OPENFLASH_MANUAL_CARD_UPLOAD_IMAGES',
            baseUrl,
            sideAImages: await imagesToTransferPayload(sideAImages.map((image) => preparedById[image.id])),
            sideBImages: await imagesToTransferPayload(sideBImages.map((image) => preparedById[image.id])),
          })
          if (!uploadResponse?.ok) {
            const error = new Error(uploadResponse?.message || 'manualCard.saveFailed')
            error.code = uploadResponse?.code
            throw error
          }
          Object.assign(uploadedByImageId, uploadResponse.uploadedByImageId || {})
        }

        const createResponse = await chrome.runtime.sendMessage({
          type: 'OPENFLASH_MANUAL_CARD_CREATE',
          baseUrl,
          deckId,
          payload: globalThis.OpenFlashManualCardEditor.buildPayload(state, uploadedByImageId),
        })
        if (!createResponse?.ok) {
          const error = new Error(createResponse?.message || 'manualCard.saveFailed')
          error.code = createResponse?.code
          throw error
        }
        return createResponse.card
      } finally {
        saving = false
      }
    }

    return { save, getUploadedByImageId: () => ({ ...uploadedByImageId }) }
  }

  async function imagesToTransferPayload(images) {
    const payload = []
    for (const image of images) {
      const dataUrl = await fileToDataUrl(image.file)
      payload.push({
        id: image.id,
        name: image.file?.name || 'image.jpg',
        type: image.file?.type || 'image/jpeg',
        dataUrl,
      })
    }
    return payload
  }

  function fileToDataUrl(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result || ''))
      reader.onerror = () => reject(new Error('manualCard.imageProcessFailed'))
      reader.readAsDataURL(file)
    })
  }

  globalThis.OpenFlashManualCardSave = { createSaver }
})()
