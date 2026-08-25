import { publishSessionInvalidation } from '../auth/sessionInvalidation.js'

/** 创建单个 SSE 连接，并向业务模块提供按事件名订阅能力。 */
export function createSseClient(url, EventSourceImpl, onConnectionError) {
  const source = new EventSourceImpl(url, { withCredentials: true })
  let connected = false

  source.onopen = () => {
    connected = true
  }
  source.onerror = () => {
    onConnectionError(connected)
    connected = false
  }

  return {
    subscribe(eventName, listener) {
      source.addEventListener(eventName, listener)
      return () => source.removeEventListener(eventName, listener)
    },
    close() {
      source.close()
    },
  }
}

/** 将账号失效 SSE 消息发布到统一会话失效通道。 */
export function subscribeToAccountInvalidation(client) {
  return client.subscribe('account-invalidated', event => {
    try {
      publishSessionInvalidation(JSON.parse(event.data))
    } catch {
      // 无效 SSE payload 不应影响连接上的其他业务通知。
    }
  })
}
