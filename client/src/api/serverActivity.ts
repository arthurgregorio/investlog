let lastServerActivity = Date.now()

export function recordServerActivity() {
  lastServerActivity = Date.now()
}

export function lastServerActivityAt(): number {
  return lastServerActivity
}
