const IMAGE_TOKEN_PREFIX = '\uE000OFIMG:'
const IMAGE_TOKEN_SUFFIX = '\uE000'
const IMAGE_TOKEN_PATTERN = /\uE000OFIMG:(\d+)\uE000/g

export function imageToken(index) {
  return `${IMAGE_TOKEN_PREFIX}${index}${IMAGE_TOKEN_SUFFIX}`
}

export function hasImageTokens(text) {
  IMAGE_TOKEN_PATTERN.lastIndex = 0
  return IMAGE_TOKEN_PATTERN.test(String(text || ''))
}

export function stripImageTokens(text) {
  IMAGE_TOKEN_PATTERN.lastIndex = 0
  return String(text || '').replace(IMAGE_TOKEN_PATTERN, '')
}

export function splitFaceContent(text, images = []) {
  const source = String(text || '')
  const imageList = Array.isArray(images) ? images : (images ? [images] : [])
  const segments = []
  const usedImages = new Set()
  let cursor = 0
  let match

  IMAGE_TOKEN_PATTERN.lastIndex = 0
  while ((match = IMAGE_TOKEN_PATTERN.exec(source)) !== null) {
    pushTextSegment(segments, source.slice(cursor, match.index))
    const index = Number(match[1])
    if (imageList[index]) {
      segments.push({ type: 'image', src: imageList[index], index })
      usedImages.add(index)
    }
    cursor = match.index + match[0].length
  }
  pushTextSegment(segments, source.slice(cursor))

  imageList.forEach((src, index) => {
    if (!usedImages.has(index)) segments.push({ type: 'image', src, index })
  })

  return segments
}

function pushTextSegment(segments, text) {
  if (text) segments.push({ type: 'text', text })
}
