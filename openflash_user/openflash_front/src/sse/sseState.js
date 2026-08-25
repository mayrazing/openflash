import { createContext, useContext } from 'react'

export const EMPTY_SSE = { subscribe: () => () => {} }
export const SseContext = createContext(EMPTY_SSE)

/** 返回全局 SSE 事件订阅接口。 */
export function useSse() {
  return useContext(SseContext)
}
