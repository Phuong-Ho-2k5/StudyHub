export const TOKEN_STORAGE_KEY = 'studyhub_access_token'

const JWT_SECTION_PATTERN = /^[A-Za-z0-9_-]+$/

function decodeSection(section) {
  const base64 = section.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0))

  return JSON.parse(new TextDecoder().decode(bytes))
}

export function readToken(token) {
  if (typeof token !== 'string') {
    return null
  }

  const sections = token.split('.')
  if (
    sections.length !== 3
    || sections.some((section) => !section || !JWT_SECTION_PATTERN.test(section))
  ) {
    return null
  }

  try {
    const header = decodeSection(sections[0])
    const payload = decodeSection(sections[1])
    const expiresAt = payload.exp * 1000

    if (
      !header
      || typeof header !== 'object'
      || typeof header.alg !== 'string'
      || !header.alg
      || !payload
      || typeof payload !== 'object'
      || typeof payload.sub !== 'string'
      || !payload.sub.trim()
      || typeof payload.exp !== 'number'
      || !Number.isFinite(expiresAt)
      || expiresAt <= Date.now()
    ) {
      return null
    }

    return {
      email: payload.sub,
      expiresAt,
    }
  } catch {
    return null
  }
}
