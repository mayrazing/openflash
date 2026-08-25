import { Component } from 'react'

/** 隔离单个插件块异常，避免一个插件拖垮整页。 */
export default class PluginSlotErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError() {
    return { hasError: true }
  }

  componentDidCatch(error) {
    if (typeof console !== 'undefined') {
      console.error('Plugin slot render failed', this.props.pluginId, error)
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="rounded-lg border border-app-separator bg-app-surface-secondary px-3 py-2 text-sm text-app-danger">
          Plugin failed to render.
        </div>
      )
    }
    return this.props.children
  }
}
