import { useQuery } from '@tanstack/react-query'
import { Cloud, RefreshCw } from 'lucide-react'
import { Button } from './components/ui/button'

type HealthResponse = {
  status: string
  service: string
  timestamp: string
}

async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch('/api/health')
  if (!response.ok) {
    throw new Error(`Health check failed (${response.status})`)
  }
  return response.json()
}

export default function App() {
  const health = useQuery({
    queryKey: ['health'],
    queryFn: fetchHealth,
  })

  return (
    <main className="min-h-screen bg-slate-950 text-slate-100">
      <div className="mx-auto flex min-h-screen max-w-3xl flex-col justify-center px-6">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-8 shadow-2xl">
          <div className="flex items-center gap-3">
            <Cloud className="h-8 w-8 text-cyan-400" aria-hidden="true" />
            <div>
              <h1 className="text-2xl font-semibold">CloudHub</h1>
              <p className="text-sm text-slate-400">Sprint 0 foundation</p>
            </div>
          </div>

          <div className="mt-8 rounded-lg border border-slate-800 bg-slate-950 p-4">
            {health.isPending && <p className="text-slate-400">Connecting to backend...</p>}
            {health.isError && (
              <p className="text-rose-400">Backend unavailable: {health.error.message}</p>
            )}
            {health.isSuccess && (
              <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
                <dt className="text-slate-400">Status</dt>
                <dd className="font-medium text-emerald-400">{health.data.status}</dd>
                <dt className="text-slate-400">Service</dt>
                <dd>{health.data.service}</dd>
                <dt className="text-slate-400">Timestamp</dt>
                <dd className="font-mono text-slate-300">{health.data.timestamp}</dd>
              </dl>
            )}
          </div>

          <Button className="mt-6" onClick={() => health.refetch()}>
            <RefreshCw className="h-4 w-4" />
            Refresh health
          </Button>
        </div>
      </div>
    </main>
  )
}
