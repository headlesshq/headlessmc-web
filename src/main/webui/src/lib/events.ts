// Websocket client for the server sent events (/ws/events), reconnects automatically.

export interface ServerEvent {
  type: string
  payload: unknown
}

type Listener = (event: ServerEvent) => void

export class EventClient {
  private socket: WebSocket | null = null
  private listeners = new Set<Listener>()
  private stateListeners = new Set<(connected: boolean) => void>()
  private retryDelay = 500
  private stopped = false

  constructor(private readonly url: string) {}

  start(): void {
    this.stopped = false
    this.connect()
  }

  stop(): void {
    this.stopped = true
    this.socket?.close()
    this.socket = null
  }

  onEvent(listener: Listener): () => void {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  onConnectionChange(listener: (connected: boolean) => void): () => void {
    this.stateListeners.add(listener)
    return () => this.stateListeners.delete(listener)
  }

  private connect(): void {
    const socket = new WebSocket(this.url)
    this.socket = socket
    socket.onopen = () => {
      this.retryDelay = 500
      this.stateListeners.forEach((listener) => listener(true))
    }
    socket.onmessage = (message) => {
      let event: ServerEvent
      try {
        event = JSON.parse(String(message.data)) as ServerEvent
      } catch {
        return
      }
      this.listeners.forEach((listener) => listener(event))
    }
    socket.onclose = () => {
      if (this.socket === socket) {
        this.socket = null
        this.stateListeners.forEach((listener) => listener(false))
      }
      if (!this.stopped) {
        setTimeout(() => this.connect(), this.retryDelay)
        this.retryDelay = Math.min(this.retryDelay * 2, 10_000)
      }
    }
  }
}

export function eventsUrl(location: Location = window.location): string {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${location.host}/ws/events`
}
