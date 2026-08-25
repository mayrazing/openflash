import { Toast } from 'konsta/react'

const colorsByLevel = {
  success: { bgIos: '!bg-app-success-fill', textIos: '!text-app-on-success' },
  warning: { bgIos: '!bg-app-warning-fill', textIos: '!text-app-on-warning' },
  error: { bgIos: '!bg-app-danger-fill', textIos: '!text-app-on-danger' },
}

/** 渲染页面保存结果 Toast。 */
export default function PageNotificationToast({ notification, opened }) {
  const colors = colorsByLevel[notification.level] || colorsByLevel.error

  return (
    <Toast
      opened={opened}
      position="right"
      colors={colors}
      role="status"
      aria-live="polite"
      className="pointer-events-none z-[2147483647] [&_.k-glass]:!max-w-[min(720px,calc(100vw-48px))] [&_.k-glass>div]:!p-0"
    >
      <span className="px-9 py-6 text-[28px] leading-[1.45] font-medium">
        {notification.message}
      </span>
    </Toast>
  )
}
