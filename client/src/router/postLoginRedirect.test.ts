import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  consumePostLoginRedirect,
  forgetPostLoginRedirect,
  rememberPostLoginRedirect,
} from './postLoginRedirect'

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    fetchSession: vi.fn(),
    enroll: vi.fn(),
    verify: vi.fn(),
    register: vi.fn(),
  },
}))

describe('post-login redirect', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('returns the remembered path, query and hash included', () => {
    rememberPostLoginRedirect('/wallets/42?tab=ledger#lot-7')

    expect(consumePostLoginRedirect()).toBe('/wallets/42?tab=ledger#lot-7')
  })

  it('clears the path once it is consumed', () => {
    rememberPostLoginRedirect('/strategies/7/plan')

    consumePostLoginRedirect()

    expect(consumePostLoginRedirect()).toBeNull()
  })

  it('forgets a remembered path', () => {
    rememberPostLoginRedirect('/wallets/42')

    forgetPostLoginRedirect()

    expect(consumePostLoginRedirect()).toBeNull()
  })

  it.each([
    '//evil.com',
    '/\\evil.com',
    'https://evil.com',
    '/no-such-page',
    '/login',
    '/pending-approval',
  ])('refuses to remember %s', (path) => {
    rememberPostLoginRedirect(path)

    expect(consumePostLoginRedirect()).toBeNull()
  })

  it.each(['//evil.com', '/\\evil.com', 'https://evil.com', '/no-such-page', '/login'])(
    'refuses to return a crafted stored value %s',
    (path) => {
      sessionStorage.setItem('investlog.postLoginRedirect', path)

      expect(consumePostLoginRedirect()).toBeNull()
      expect(sessionStorage.getItem('investlog.postLoginRedirect')).toBeNull()
    },
  )

  it('treats unavailable storage as nothing remembered', () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError')
    })
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError')
    })

    expect(() => rememberPostLoginRedirect('/wallets/42')).not.toThrow()
    expect(consumePostLoginRedirect()).toBeNull()

    getItem.mockRestore()
    setItem.mockRestore()
  })
})
