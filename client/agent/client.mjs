// Only snapshots can change the displayed world. Command ACKs carry no game assets.
export class AgentClient {
  constructor(fetchImpl = globalThis.fetch.bind(globalThis), makeId = () => crypto.randomUUID(), apiBase = '/api/agent') {
    if (!['/api/agent','/api/quest','/api/training','/api/growth','/api/duel'].includes(apiBase)) throw new Error('INVALID_API_BASE');
    Object.defineProperty(this, 'apiBase', { value: apiBase });
    this.fetch = fetchImpl; this.makeId = makeId; this.view = null;
    this.connected = false; this.pendingRequest = null; this.csrf = null; this.roomId = null;
    this.epoch = 0;this.waitingCount=0;this.onWaiting=()=>{};
  }
  async request(url, options = {}) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 5000);let waiting=false;const notify=()=>{try{this.onWaiting(this.waitingCount>0);}catch{}};const hint=setTimeout(()=>{waiting=true;this.waitingCount++;notify();},300);
    try {
      const response = await this.fetch(url, { credentials: 'same-origin', ...options, signal: controller.signal });
      const body = await response.json();
      if (!response.ok) {
        const error = new Error(typeof body.reasonCode === 'string' && /^[A-Z_]+$/.test(body.reasonCode) ? body.reasonCode : `HTTP_${response.status}`);
        error.definitive = true; error.status = response.status; throw error;
      }
      return body;
    } finally { clearTimeout(timer);clearTimeout(hint);if(waiting){this.waitingCount--;notify();} }
  }
  async connect() {
    const epoch = ++this.epoch;
    try {
      const session = await this.request(`${this.apiBase}/session`);
      if (typeof session.csrfToken !== 'string') throw new Error('INVALID_SESSION');
      this.csrf = session.csrfToken;
      const room = await this.request(`${this.apiBase}/rooms`, this.postOptions({}));
      if (typeof room.roomId !== 'string') throw new Error('INVALID_ROOM');
      if (epoch !== this.epoch) return;
      if (this.view?.roomId !== room.roomId) { this.view = null; this.pendingRequest = null; }
      this.roomId = room.roomId; await this.poll();
    } catch (error) { if (epoch === this.epoch) this.connected = false; throw error; }
  }
  postOptions(body) {
    return { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': this.csrf }, body: typeof body === 'string' ? body : JSON.stringify(body) };
  }
  async poll() {
    const epoch = this.epoch, roomId = this.roomId;
    try {
      if (!roomId) throw new Error('NO_SESSION');
      const view = await this.request(`${this.apiBase}/rooms/${encodeURIComponent(roomId)}/snapshot`);
      if (epoch !== this.epoch || roomId !== this.roomId) return this.view;
      if (view.roomId !== roomId || typeof view.taskId !== 'string' || !Number.isSafeInteger(view.revision) || view.revision < 1 ||
          typeof view.status !== 'string' || !view.world || !Array.isArray(view.trace)) throw new Error('INVALID_SNAPSHOT');
      if (!this.view || view.revision >= this.view.revision) {
        if (this.view && view.revision === this.view.revision && view.taskId !== this.view.taskId) throw new Error('INVALID_SNAPSHOT');
        this.view = view;
      }
      this.connected = true; return this.view;
    } catch (error) {
      if (epoch !== this.epoch || roomId !== this.roomId) return this.view;
      this.connected = false;
      if (error.status === 401 || error.status === 404) { this.roomId = null; this.csrf = null; this.pendingRequest = null; this.epoch++; }
      throw error;
    }
  }
  async command(command, params = {}) {
    if (!this.connected || !this.view) throw new Error('DISCONNECTED');
    if (this.pendingRequest && command !== 'CANCEL') throw new Error('REQUEST_PENDING');
    this.pendingRequest = {
      url: `${this.apiBase}/rooms/${encodeURIComponent(this.roomId)}/commands`,
      body: JSON.stringify({ requestId: this.makeId(), taskId: this.view.taskId, expectedRevision: this.view.revision, command, params })
    };
    return this.retry();
  }
  async retry() {
    const pending = this.pendingRequest;
    if (!pending) throw new Error('NO_PENDING_REQUEST');
    try {
      const ack = await this.request(pending.url, this.postOptions(pending.body));
      if (ack.outcome !== 'ACCEPTED') throw new Error('INVALID_ACK');
      if (this.pendingRequest === pending) this.pendingRequest = null;
      return ack;
    } catch (error) {
      if (error.definitive) { if (this.pendingRequest === pending) this.pendingRequest = null; }
      else this.connected = false;
      throw error;
    }
  }
}
