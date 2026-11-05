import { isSessionExpired } from './session';

export interface ActionHost {
  /** Called with true as the action starts and with false once it ends. */
  busy: (busy: boolean) => void;
  report: (text: string, tone: 'success' | 'error') => void;
}

/**
 * Runs an action of a page while it is busy. The text the action returns is reported as a success, the text `failed`
 * returns for its error as a failure, and undefined stays silent. An expired session reports nothing, the sign in
 * form shows by then.
 */
export async function runAction(
  host: ActionHost,
  action: () => Promise<string | undefined>,
  failed: (error: unknown) => string | undefined,
): Promise<void> {
  host.busy(true);
  try {
    const text = await action();
    if (text) host.report(text, 'success');
  } catch (error) {
    if (isSessionExpired(error)) return;
    const text = failed(error);
    if (text) host.report(text, 'error');
  } finally {
    host.busy(false);
  }
}
