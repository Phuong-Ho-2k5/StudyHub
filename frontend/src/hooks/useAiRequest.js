import { useEffect, useRef, useState } from 'react'
import { aiErrorMessage } from '../components/ai/aiDisplay.js'

// Panels are keyed by Course/token; cleanup rejects results from their old scope.
export function useAiRequest(operation) {
  const [state, setState] = useState({ pending: false, data: null, error: '' })
  const alive = useRef(false)
  const lock = useRef(false)
  useEffect(() => { alive.current = true; return () => { alive.current = false } }, [])
  async function run(request) {
    if (lock.current) return
    lock.current = true
    setState({ pending: true, data: null, error: '' })
    try {
      const data = await request()
      if (alive.current) setState({ pending: false, data, error: '' })
    } catch (error) {
      if (alive.current) setState({ pending: false, data: null, error: aiErrorMessage(error, operation) })
    } finally { lock.current = false }
  }
  return { ...state, run }
}
