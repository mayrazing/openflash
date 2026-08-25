export function normalizePlatformAiPage(value) {
  const runtimeAvailable = value?.runtimeAvailable === true
  const connections = Array.isArray(value?.connections)
    ? value.connections
      .filter(connection => connection?.source === 'PLATFORM')
      .map(connection => ({
        ...connection,
        offerings: Array.isArray(connection.offerings)
          ? connection.offerings.filter(offering => offering?.source === 'PLATFORM')
          : [],
      }))
    : []
  return {
    runtimeStatus: typeof value?.runtimeStatus === 'string' ? value.runtimeStatus : 'ERROR',
    runtimeAvailable,
    connections,
  }
}

export function canCreateCodexConnection(page) {
  return page?.runtimeAvailable === true
    && !page.connections?.some(connection => (
      connection.kind === 'CLI' && connection.protocol === 'CODEX_APP_SERVER'
    ))
}

export function connectionCreatePayload(type, baseUrl, sortOrder, displayName = null) {
  if (type === 'API') {
    return {
      kind: 'API',
      protocol: 'ANTHROPIC',
      cliKey: null,
      displayName,
      baseUrl,
      sortOrder,
    }
  }
  if (type === 'CODEX') {
    return {
      kind: 'CLI',
      protocol: 'CODEX_APP_SERVER',
      cliKey: 'codex',
      displayName: null,
      baseUrl: null,
      sortOrder,
    }
  }
  throw new Error(`Unsupported platform connection type: ${type}`)
}
