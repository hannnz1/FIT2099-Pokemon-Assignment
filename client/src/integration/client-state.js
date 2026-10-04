const freeze = value => {
  if (value && typeof value === 'object' && !Object.isFrozen(value)) {
    Object.values(value).forEach(freeze); Object.freeze(value);
  }
  return value;
};
export class ClientState {
  #view; #listeners = new Set(); #events = new Set();
  reset() { this.#view = undefined; this.#events.clear(); }
  applySnapshot(snapshot) {
    if (snapshot.protocolVersion !== 1 || snapshot.mapVersion !== 1 || snapshot.mapId !== 'chapter1') throw Error('地图或协议版本不匹配，请更新客户端');
    if (!snapshot.sessionId || !snapshot.roomId || !Number.isSafeInteger(snapshot.seq) || snapshot.seq < 0 || !Array.isArray(snapshot.entities) || !snapshot.selfAssets || !snapshot.quest) throw Error('无效快照');
    if (this.#view && (snapshot.sessionId !== this.#view.sessionId || snapshot.roomId !== this.#view.roomId || snapshot.seq <= this.#view.seq)) return false;
    this.#view = freeze(structuredClone(snapshot));
    this.#listeners.forEach(fn => fn(this.#view)); return true;
  }
  applyEvent(event) {
    if (!this.#view) return 'RESYNC';
    if (event.sessionId !== this.#view.sessionId || event.seq <= this.#view.seq || this.#events.has(event.eventId)) return 'IGNORED';
    if (event.seq !== this.#view.seq + 1 || !event.snapshot || event.snapshot.seq !== event.seq || event.snapshot.sessionId !== event.sessionId || event.snapshot.roomId !== this.#view.roomId) return 'RESYNC';
    if (!this.applySnapshot(event.snapshot)) return 'IGNORED';
    this.#events.add(event.eventId);
    if(this.#events.size > 512) this.#events.delete(this.#events.values().next().value);
    return 'APPLIED';
  }
  getView() { return this.#view; }
  subscribe(fn) { this.#listeners.add(fn); return () => this.#listeners.delete(fn); }
}
