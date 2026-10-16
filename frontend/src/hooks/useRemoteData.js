import { useEffect, useState } from 'react'

// Callers describe all loader inputs in dependencies. Hide stale data during transitions.
export function useRemoteData(loader, dependencies) {
  const [revision, setRevision] = useState(0)
  const key = JSON.stringify([...dependencies, revision])
  const [state, setState] = useState({ requestKey: null, data: null, error: null, loading: true })
  useEffect(() => {
    let active = true
    Promise.resolve().then(() => {
      if (active) setState({ requestKey: key, data: null, error: null, loading: true })
      return loader()
    }).then((data) => {
      if (active) setState({ requestKey: key, data, error: null, loading: false })
    }).catch((error) => {
      if (active) setState({ requestKey: key, data: null, error, loading: false })
    })
    return () => { active = false }
    // loader inputs are explicitly represented by key.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])
  return {
    ...(state.requestKey === key ? state : { data: null, error: null, loading: true }),
    reload: () => setRevision((value) => value + 1),
  }
}
