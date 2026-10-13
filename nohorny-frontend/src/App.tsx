import './lib/sentry';
import { Loading } from 'solid-js';
import { ConfirmHost } from './components/dialog';
import { OverlayScrollbar } from './components/scrollbar';
import { TooltipHost } from './components/tooltip';
import { LoadingSpinner } from './components/ui';
import { Router } from './router';
import './App.css';

export default function App() {
  return (
    <Router>
      {(props) => (
        <>
          <Loading fallback={<LoadingSpinner class="min-h-dvh" size="size-6" />}>{props.children}</Loading>
          <ConfirmHost />
          <OverlayScrollbar />
          <TooltipHost />
        </>
      )}
    </Router>
  );
}
