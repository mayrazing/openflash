export class RequestError extends Error {
  constructor(status, code) {
    super(status === 401 ? 'Authentication required' : `Request failed (${status})`)
    this.name = 'RequestError'
    this.status = status
    this.code = code ?? null
  }
}

function parseJsonSafely(text) {
  try {
    return text ? JSON.parse(text) : null
  } catch {
    return null
  }
}

export async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
  })
  const payload = parseJsonSafely(await response.text())

  if (!response.ok || payload?.code !== 200) {
    throw new RequestError(response.status, payload?.code)
  }

  return payload.data ?? null
}
