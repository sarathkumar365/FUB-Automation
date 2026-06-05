import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AppErrorFallback } from './AppErrorFallback'

/**
 * Top-level class error boundary. The router's `errorElement` handles errors
 * inside the route tree; this guards everything around it (providers, router
 * mount) so an unexpected throw renders the branded fallback, not a white page.
 */
export class AppErrorBoundary extends Component<{ children: ReactNode }, { hasError: boolean; error: unknown }> {
  state = { hasError: false, error: undefined as unknown }

  static getDerivedStateFromError(error: unknown) {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (import.meta.env.DEV) {
      console.error('App error boundary:', error, info)
    }
  }

  render() {
    return this.state.hasError ? <AppErrorFallback error={this.state.error} /> : this.props.children
  }
}
