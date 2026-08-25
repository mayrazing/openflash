import { useEffect, useState } from 'react'
import { buildApiUrl } from '../db/database'
import { appWarn } from '../lib/appLog'
import { createSseClient, subscribeToAccountInvalidation } from './sseClient'
import { EMPTY_SSE, SseContext } from './sseState'

/** 登录后建立全局唯一 SSE 连接，供插件按事件名订阅。 */
export function SseProvider({ enabled, children }) {
  const [client, setClient] = useState(null)

  useEffect(() => {
    if (!enabled) {
      setClient(null)
      return
    }
    if (typeof EventSource === 'undefined') {
      appWarn(60001, '当前浏览器不支持 SSE 通知')
      return
    }

    const nextClient = createSseClient(
      buildApiUrl('/api/sse/notifications'),
      EventSource,
      connected => appWarn(60004, connected
        ? 'SSE notification connection interrupted'
        : 'SSE notification connection failed'),
    )
    const unsubscribeAccountInvalidation = subscribeToAccountInvalidation(nextClient)
    setClient(nextClient)
    return () => {
      unsubscribeAccountInvalidation()
      nextClient.close()
    }
  }, [enabled])

  return (
    <SseContext.Provider value={client ?? EMPTY_SSE}>
      {children}
    </SseContext.Provider>
  )
}
