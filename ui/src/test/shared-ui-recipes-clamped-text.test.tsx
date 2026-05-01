import { act, fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ClampedText } from '../shared/ui/recipes/ClampedText'

/** In jsdom, `scrollHeight` and `clientHeight` are both 0 by default. To
 *  test the overflow-detection branch we stub them. */
function stubDimensions(selector: string, scrollHeight: number, clientHeight: number) {
  Object.defineProperty(Element.prototype, 'scrollHeight', {
    configurable: true,
    get() {
      if (this instanceof HTMLElement && this.matches(selector)) return scrollHeight
      return 0
    },
  })
  Object.defineProperty(Element.prototype, 'clientHeight', {
    configurable: true,
    get() {
      if (this instanceof HTMLElement && this.matches(selector)) return clientHeight
      return 0
    },
  })
}

describe('ClampedText recipe', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    // Reset stubs between tests.
    Object.defineProperty(Element.prototype, 'scrollHeight', { configurable: true, value: 0 })
    Object.defineProperty(Element.prototype, 'clientHeight', { configurable: true, value: 0 })
  })

  it('renders the text content', () => {
    render(<ClampedText text="hello world" />)
    expect(screen.getByText('hello world')).toBeInTheDocument()
  })

  it('does NOT render a toggle when content fits within maxLines', () => {
    stubDimensions('[data-testid="clamped-text-body"]', 40, 40)
    render(<ClampedText text="short" />)
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('renders a Show more toggle when content overflows maxLines', () => {
    stubDimensions('[data-testid="clamped-text-body"]', 200, 80)
    render(<ClampedText text="long content" />)
    const button = screen.getByRole('button')
    expect(button.textContent).toBe('Show more')
  })

  it('toggles to Show less when expanded', () => {
    stubDimensions('[data-testid="clamped-text-body"]', 200, 80)
    render(<ClampedText text="long content" />)
    const body = screen.getByTestId('clamped-text-body')
    expect(body.getAttribute('data-expanded')).toBe('false')
    act(() => {
      fireEvent.click(screen.getByRole('button'))
    })
    expect(body.getAttribute('data-expanded')).toBe('true')
    expect(screen.getByRole('button').textContent).toBe('Show less')
  })

  it('applies monospace class when monospace prop is set', () => {
    render(<ClampedText text="x" monospace />)
    expect(screen.getByTestId('clamped-text-body').className).toMatch(/font-mono/)
  })

  it('respects custom maxLines', () => {
    stubDimensions('[data-testid="clamped-text-body"]', 200, 80)
    render(<ClampedText text="x" maxLines={6} />)
    const body = screen.getByTestId('clamped-text-body')
    expect(body.style.webkitLineClamp).toBe('6')
  })
})
