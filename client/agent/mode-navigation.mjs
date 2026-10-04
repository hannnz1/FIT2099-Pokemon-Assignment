// Stop current AI work before leaving its controls. Only these local game pages are allowed.
export async function navigateMode(client, href, navigate = url => { globalThis.location.assign(url); }) {
  if (!['/quest/','/training/'].includes(href)) throw new Error('INVALID_MODE');
  if (!client.connected) throw new Error('DISCONNECTED');
  if (client.pendingRequest) throw new Error('REQUEST_PENDING');
  const view = await client.poll();
  if (['PARSING','READY'].includes(view.status)) throw new Error('CANCEL_BEFORE_SWITCH');
  await client.command('SWITCH_MODE', { mode: href === '/training/' ? 'training' : 'quest' });
  navigate(href);
}
